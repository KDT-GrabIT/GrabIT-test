# GrabIT-test

YOLOX 객체 검출을 사용하는 Android 앱입니다.

## 빌드 및 실행

- Android Studio에서 프로젝트 열기 후 Run
- 또는 터미널: `./gradlew assembleDebug` 후 APK는 `app/build/outputs/apk/debug/`

## 주요 리소스

- 모델: `app/src/main/assets/yolox_float16.tflite`
- 클래스 이름: `app/src/main/assets/classes.txt`

## PTH → TFLite 변환

다시 모델을 변환할 때는 루트의 `convert_pth_to_tflite.sh`, `verify_yolox_tflite.py`를 사용하고, YOLOX 폴더와 `best_ckpt.pth`가 필요합니다.
