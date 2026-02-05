#!/usr/bin/env python3
"""
YOLOX TFLite 모델 간단 확인 스크립트
- 입력/출력 shape, dtype 출력
- 더미 입력으로 1회 추론 후 출력 값 범위 확인

사용: python verify_yolox_tflite.py [tflite 경로]
예:   python verify_yolox_tflite.py app/src/main/assets/yolox_float16.tflite
"""
import sys
import os

def main():
    if len(sys.argv) > 1:
        path = sys.argv[1]
    else:
        path = os.path.join(os.path.dirname(__file__), "app", "src", "main", "assets", "yolox_float16.tflite")

    if not os.path.isfile(path):
        print(f"파일 없음: {path}")
        print("사용: python verify_yolox_tflite.py [tflite 경로]")
        sys.exit(1)

    try:
        import tensorflow.lite as tflite
    except ImportError:
        print("tensorflow 필요: pip install tensorflow")
        sys.exit(1)

    print("=" * 60)
    print(f"모델: {path}")
    print("=" * 60)

    interpreter = tflite.Interpreter(model_path=path)
    interpreter.allocate_tensors()

    # 입력
    inp = interpreter.get_input_details()
    print("\n[입력]")
    for i, d in enumerate(inp):
        shape = d["shape"]
        dtype = d["dtype"]
        name = d.get("name", "")
        print(f"  {i}: shape={list(shape)}, dtype={dtype}, name={name}")

    # 출력
    out = interpreter.get_output_details()
    print("\n[출력]")
    for i, d in enumerate(out):
        shape = d["shape"]
        dtype = d["dtype"]
        name = d.get("name", "")
        print(f"  {i}: shape={list(shape)}, dtype={dtype}, name={name}")

    # 더미 입력으로 1회 추론
    input_0 = interpreter.get_input_details()[0]
    shape = input_0["shape"]
    dtype = input_0["dtype"]

    import numpy as np
    if dtype == np.float32:
        dummy = np.zeros(shape, dtype=np.float32)
    elif dtype in (np.uint8, np.int8):
        dummy = np.zeros(shape, dtype=dtype)
    else:
        dummy = np.zeros(shape, dtype=np.float32)

    interpreter.set_tensor(input_0["index"], dummy)
    interpreter.invoke()

    print("\n[추론 1회 (더미 입력) 후 출력 값]")
    for i, d in enumerate(out):
        out_tensor = interpreter.get_tensor(d["index"])
        arr = np.array(out_tensor)
        print(f"  출력 {i}: shape={arr.shape}")
        print(f"    min={arr.min():.4f}, max={arr.max():.4f}, mean={arr.mean():.4f}")
        flat = arr.flatten()
        print(f"    샘플(앞 5개): {flat[:5].tolist()}")
        # [1, 3549, 58] 형태면 행 단위로 처음 2행만 (cx,cy,w,h,obj + 클래스 5개) 출력 → 항상 2개만 나오는 원인 확인용
        if arr.ndim == 3:
            n, d1, d2 = arr.shape
            num_boxes, box_size = (d1, d2) if d1 >= d2 else (d2, d1)
            print(f"    → num_boxes={num_boxes}, boxSize={box_size} (YOLOX: cx,cy,w,h,objectness,53 classes)")
            if d1 >= d2:
                for row_idx in range(min(3, d1)):
                    row = arr[0][row_idx]
                    cx, cy, w, h = row[0], row[1], row[2], row[3]
                    obj = row[4] if box_size > 4 else 0
                    print(f"    행 {row_idx}: cx={cx:.4f} cy={cy:.4f} w={w:.4f} h={h:.4f} obj={obj:.4f} class_max={float(np.max(row[5:5+53])) if box_size >= 58 else float(np.max(row[5:])):.4f}")
            else:
                for col_idx in range(min(3, d2)):
                    cx = arr[0][0][col_idx]
                    cy = arr[0][1][col_idx]
                    w = arr[0][2][col_idx]
                    h = arr[0][3][col_idx]
                    obj = arr[0][4][col_idx] if d1 > 4 else 0
                    print(f"    열 {col_idx}: cx={cx:.4f} cy={cy:.4f} w={w:.4f} h={h:.4f} obj={obj:.4f}")

    print("\n" + "=" * 60)
    print("앱에서 쓰는 shape와 맞는지 확인:")
    print("  - 앱: dim1=3549, dim2=58 → (cx,cy,w,h) 표준 순서, 0~1 정규화 가정")
    print("  - PTH 변환 시 전처리(크기/정규화/RGB)와 출력 decode 여부가 동일한지 확인 권장")
    print("=" * 60)

if __name__ == "__main__":
    main()
