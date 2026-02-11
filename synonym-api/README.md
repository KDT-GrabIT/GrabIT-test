# Synonym API (MongoDB 저장·조회)

STT 유의어, 대답 근접단어, 상품 근접단어를 **MongoDB에 저장**해 두고, 앱이 이 API로 **꺼내 씁니다**.

## 1. MongoDB에 DB/컬렉션 만들기 (한 번만)

MongoDB Atlas에서 클러스터만 있으면 됩니다. **DB는 데이터를 처음 넣을 때 자동으로 생깁니다.**

1. `synonym-api` 폴더에 `.env` 파일 만듦 (`.env.example` 복사 후 이름 변경)
2. `.env` 안에 연결 문자열 넣음:
   ```
   MONGODB_URI=mongodb+srv://gmango777:비밀번호@cluster0.l2mb08x.mongodb.net/?retryWrites=true&w=majority
   ```
   (본인 Atlas 연결 문자열로 교체)
3. 시드 실행:
   ```bash
   cd synonym-api
   npm install
   npm run seed
   ```
4. Atlas 화면 새로고침 → **Database**에 `grabit` DB가 보이고, 안에 `answer_synonyms`, `product_synonyms` 컬렉션이 생깁니다.

## 2. 서버 실행

```bash
npm start
```

이후 앱에서 BASE_URL(`http://10.0.2.2:3000` 등)로 호출하면, **MongoDB에 넣어 둔 데이터**를 그대로 꺼내서 줍니다.

## 3. 데이터 수정

- Atlas → Database → `grabit` → `answer_synonyms` / `product_synonyms` 에서 문서 추가·수정·삭제하면, 앱이 다음 요청부터 그 내용을 사용합니다.
- 대답 근접단어: `keyword`, `proximity_words` 배열, `type: "answer"`
- 상품 근접단어: `class_id`, `display_name`, `proximity_words` 배열, `type: "product"`
