# -*- coding: utf-8 -*-
""" 프로젝트 루트에서 상품 이미지 추출 스크립트 실행 (한글 경로 인코딩 회피용) """
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parent
image_dir = root / "상품 이미지"
script = image_dir / "extract_meta_dimensions.py"

if not script.exists():
    print("찾을 수 없음:", script, file=sys.stderr)
    sys.exit(1)

r = subprocess.run([sys.executable, str(script)], cwd=str(image_dir))
sys.exit(r.returncode)
