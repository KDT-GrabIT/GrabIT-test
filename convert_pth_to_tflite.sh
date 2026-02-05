#!/usr/bin/env bash
#
# PTH → ONNX(decode_in_inference) → TFLite 변환 스크립트
# 사용: ./convert_pth_to_tflite.sh [pth파일경로]
# 예:   ./convert_pth_to_tflite.sh ./best_ckpt.pth
#
# 필요: YOLOX 소스 (YOLOX_ROOT 또는 프로젝트 내 YOLOX/), Python venv 권장
#

set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# .pth 경로
PTH_FILE="${1:-}"
if [ -z "$PTH_FILE" ]; then
  for f in best_ckpt.pth yolox_nano.pth yolox_nano_ckpt.pth "*.pth"; do
    if compgen -G "$f" > /dev/null 2>&1; then
      PTH_FILE="$(ls -1 $f 2>/dev/null | head -1)"
      break
    fi
  done
fi
if [ -z "$PTH_FILE" ] || [ ! -f "$PTH_FILE" ]; then
  echo "사용법: $0 <pth파일경로>"
  echo "예:     $0 ./best_ckpt.pth"
  echo ""
  echo ".pth 파일을 프로젝트 루트나 현재 디렉터리에 넣은 뒤 위처럼 실행하세요."
  exit 1
fi
PTH_FILE="$(cd "$(dirname "$PTH_FILE")" && pwd)/$(basename "$PTH_FILE")"
echo "[1/4] PTH 파일: $PTH_FILE"

# YOLOX 디렉터리
YOLOX_ROOT="${YOLOX_ROOT:-$SCRIPT_DIR/YOLOX}"
if [ ! -d "$YOLOX_ROOT" ] || [ ! -f "$YOLOX_ROOT/tools/export_onnx.py" ]; then
  echo "YOLOX 소스가 없습니다: $YOLOX_ROOT"
  echo "아래처럼 클론한 뒤 다시 실행하세요:"
  echo "  git clone https://github.com/Megvii-BaseDetection/YOLOX.git $YOLOX_ROOT"
  exit 1
fi
echo "[2/4] YOLOX: $YOLOX_ROOT"

# Python
if [ -d "$SCRIPT_DIR/venv" ]; then
  source "$SCRIPT_DIR/venv/bin/activate"
fi
export PYTHONPATH="$YOLOX_ROOT:$PYTHONPATH"

# Step 1: PTH → ONNX (decode_in_inference 필수)
ONNX_NAME="yolox_export.onnx"
ONNX_PATH="$SCRIPT_DIR/$ONNX_NAME"
echo "[3/4] PTH → ONNX (decode_in_inference)..."
python - "$YOLOX_ROOT" "$PTH_FILE" "$ONNX_PATH" << 'PYEXPORT'
import sys
import os
sys.path.insert(0, os.path.expanduser(sys.argv[1]))
from tools.export_onnx import main
# export_onnx.py는 보통 argparse로 -n, -c, --output-name, --no-onnxsim, --decode_in_inference 받음
os.chdir(sys.argv[1])
sys.argv = [
    "export_onnx.py",
    "-n", "yolox-nano",
    "-c", sys.argv[2],
    "--output-name", sys.argv[3],
    "--no-onnxsim",
    "--decode_in_inference",
]
main()
PYEXPORT

if [ ! -f "$ONNX_PATH" ]; then
  echo "ONNX export 실패. 위 Python 호출이 에러 없이 끝났는지 확인하세요."
  exit 1
fi
echo "     생성: $ONNX_PATH"

# Step 2: ONNX → TFLite (onnx2tf)
echo "[4/4] ONNX → TFLite..."
OUT_TF="$SCRIPT_DIR/saved_model_yolox"
mkdir -p "$OUT_TF"
# 입력 고정: 1,3,416,416 (YOLOX-nano 기본)
onnx2tf -i "$ONNX_PATH" -o "$OUT_TF" -b 1 -ois "images:1,3,416,416" -eatfp16 -v info 2>/dev/null || \
onnx2tf -i "$ONNX_PATH" -o "$OUT_TF" -b 1 -ois "images:1,3,416,416" -v info 2>/dev/null || \
onnx2tf -i "$ONNX_PATH" -o "$OUT_TF" -b 1 -v info

# 앱 assets로 복사
ASSETS="$SCRIPT_DIR/app/src/main/assets"
mkdir -p "$ASSETS"
for f in "$OUT_TF"/*_float16.tflite "$OUT_TF"/*_float32.tflite; do
  if [ -f "$f" ]; then
    cp "$f" "$ASSETS/"
    echo "     복사: $(basename "$f") → $ASSETS/"
  fi
done
# 이름 통일 (onnx2tf 출력 이름이 다를 수 있음)
[ -f "$ASSETS/yolox_export_float16.tflite" ] && mv "$ASSETS/yolox_export_float16.tflite" "$ASSETS/yolox_float16.tflite" 2>/dev/null || true
[ -f "$ASSETS/yolox_export_float32.tflite" ] && mv "$ASSETS/yolox_export_float32.tflite" "$ASSETS/yolox_float32.tflite" 2>/dev/null || true

echo ""
echo "변환 완료. TFLite: $ASSETS/"
echo "검증: python verify_yolox_tflite.py $ASSETS/yolox_float16.tflite"
