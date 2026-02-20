# EC2 배포 가이드 (synonym-api + e5-service)

## EC2에서 할 일 (순서대로)

### 1. Swap 설정 (1GB RAM 보완, 필수)

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -m   # Swap 확인
```

### 2. Docker 설치

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose
sudo usermod -aG docker $USER
```

**로그아웃 후 다시 SSH 접속**해서 권한 적용.

### 3. 프로젝트 올리기

- 로컬에서 `synonym-api` 폴더 전체를 EC2로 복사 (scp, rsync, git clone 등).

예시 (로컬 PC에서):

```bash
scp -r -i "키.pem" synonym-api ubuntu@EC2_퍼블릭_IP:~/
```

또는 EC2에서 git clone 했다면 해당 저장소 안의 synonym-api 경로로 이동.

### 4. EC2에 .env 만들기

EC2의 `synonym-api` 폴더 안에 **.env** 파일 생성 (MONGODB_URI만 넣으면 됨):

```bash
cd ~/synonym-api
nano .env
```

내용:

```
MONGODB_URI=mongodb+srv://사용자:비밀번호@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
```

저장 후 `docker-compose`가 이 값을 읽습니다.

### 5. 빌드 및 실행

```bash
cd ~/synonym-api
docker-compose up -d --build
```

- 첫 빌드 시 e5-service(모델 다운로드) 때문에 시간이 꽤 걸릴 수 있음.
- 상태 확인: `docker-compose ps` / 로그: `docker-compose logs -f`

### 6. 앱 연결

- **MongoConfig.kt**의 `BASE_URL`을 **EC2 탄력적 IP(또는 퍼블릭 IP)**로 변경.
  - 예: `"http://EC2_고정_IP:3000"`
- APK 빌드 후 배포.

---

## 유용한 명령어

| 명령어 | 설명 |
|--------|------|
| `free -m` | 메모리·Swap 사용량 확인 |
| `docker-compose ps` | 컨테이너 상태 |
| `docker-compose logs -f backend` | 백엔드 로그 보기 |
| `docker-compose down` | 서비스 중지 |
| `docker-compose up -d --build` | 다시 빌드 후 실행 |
