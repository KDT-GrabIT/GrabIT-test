# GrabIT (Android App)

시각장애인 및 저시력자를 위한 온디바이스 AI 기반 실시간 쇼핑 어시스턴트 애플리케이션입니다.

## 📱 주요 기능
* **실시간 객체 인식 (Object Detection)**: YOLOX-Nano 모델을 TFLite로 변환하여 기기 내(On-Device)에서 빠르고 정확하게 진열대의 상품을 인식합니다.
* **손 추적 (Hand Tracking)**: MediaPipe Hands를 활용하여 사용자의 손가락 끝(Index Finger)과 상품 간의 거리를 계산합니다.
* **음성 피드백 (Voice Flow)**: STT(Speech-to-Text)와 TTS(Text-to-Speech)를 활용하여 화면을 보지 않고도 음성으로 앱을 제어하고 상품 위치를 안내받을 수 있습니다.
* **비프음 피드백 (Beep Player)**: 손이 목표 상품에 가까워질수록 비프음 주기를 짧게 하여 직관적인 위치 안내를 제공합니다.
* **검색 기록 (Search History)**: Room Database를 사용하여 최근 찾은 상품 기록을 로컬에 저장하고 관리합니다.

## 🛠 기술 스택
* **Language**: Kotlin
* **Architecture**: MVVM
* **AI/ML**: TensorFlow Lite (YOLOX-Nano), MediaPipe Hands
* **Database**: Room Database
* **Network**: Retrofit2, OkHttp3
* **Media**: CameraX, TextToSpeech, SpeechRecognizer

## 🚀 실행 방법
1. Android Studio에서 `grabit-android` 폴더를 엽니다.
2. Gradle Sync를 완료합니다.
3. 카메라 하드웨어가 필요하므로 가급적 **실제 안드로이드 기기**를 연결하여 빌드 및 실행합니다.

# GrabIT Synonym & Dimension API

GrabIT 앱의 '유사어 검색' 및 '상품 규격(크기) 정보'를 제공하는 백엔드 서버입니다. 동의어 매칭과 자연어 처리를 위해 Node.js 및 Python(E5 임베딩) 서버를 함께 구동합니다.

## ⚙️ 주요 기능
* **유사어 검색 (Synonym Search)**: 사용자가 말한 단어(예: "까까")를 앱에 등록된 정식 상품명(예: "과자")으로 매핑합니다.
* **자연어 임베딩 (E5 Service)**: Python 기반의 `e5-service`를 통해 텍스트 임베딩을 생성하고 코사인 유사도를 계산하여 가장 가까운 상품군을 찾습니다.
* **상품 규격 제공**: 인식된 상품의 실제 크기 데이터(가로, 세로, 높이 등)를 앱에 전달하여 화면 상의 거리 비례를 계산하는 데 도움을 줍니다.

## 🛠 기술 스택
* **Backend**: Node.js, Express
* **AI/NLP**: Python, FastAPI, intfloat/multilingual-e5-small
* **Database**: MongoDB
* **Infra**: Docker, Docker Compose

## 🚀 실행 방법 (Docker Compose 활용)
1. 루트 폴더에서 아래 명령어를 실행하여 컨테이너를 빌드하고 실행합니다.
   `docker-compose up --build -d`
2. Node.js 서버는 `3000` 포트, E5 파이썬 서버는 `8000` 포트로 실행됩니다.
3. 초기 데이터 적재가 필요한 경우 아래 스크립트를 실행합니다.
   `node seed.js`
   `node seed-dimensions.js`

