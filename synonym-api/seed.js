/**
 * MongoDB에 DB·컬렉션 생성 + 대답/상품 근접단어 초기 데이터 삽입
 * - 한 번만 실행: npm run seed  (또는 node seed.js)
 * - .env 에 MONGODB_URI 넣어둔 뒤 실행하면 Atlas에 DB가 생김
 */
require('dotenv').config();
const { MongoClient } = require('mongodb');
const path = require('path');
const fs = require('fs');

const DB_NAME = 'grabit';
const COL_ANSWERS = 'answer_synonyms';
const COL_PRODUCTS = 'product_synonyms';

const answerDocs = [
  { keyword: '예', proximity_words: ['네', '응', '맞아', '맞아요', '그래', '좋아', 'yes', 'y'], type: 'answer' },
  { keyword: '아니', proximity_words: ['아니요', '틀렸', '다른', 'no', 'n'], type: 'answer' }
];

function loadProductDictionary() {
  const assetPath = path.join(__dirname, '../app/src/main/assets/product_dictionary.json');
  try {
    const raw = fs.readFileSync(assetPath, 'utf8');
    const obj = JSON.parse(raw);
    return Object.entries(obj).map(([classId, v]) => ({
      class_id: classId,
      display_name: v.tts_ko || classId,
      proximity_words: v.aliases && v.aliases.length ? v.aliases : [v.tts_ko || classId],
      type: 'product',
      size: { width: '', length: '', height: '' }
    }));
  } catch (e) {
    console.warn('product_dictionary.json 로드 실패, 기본 상품만 삽입:', e.message);
    return [
      { class_id: 'pocari_sweat_500ml', display_name: '포카리 스웨트', proximity_words: ['포카리', '포카리 스웨트'], type: 'product', size: { width: '', length: '', height: '' } },
      { class_id: 'sprite_500ml', display_name: '스프라이트', proximity_words: ['스프라이트'], type: 'product', size: { width: '', length: '', height: '' } }
    ];
  }
}

async function run() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.error('.env 파일에 MONGODB_URI 를 넣고 다시 실행하세요.');
    process.exit(1);
  }
  const client = new MongoClient(uri);
  try {
    await client.connect();
    const db = client.db(DB_NAME);
    const answerCol = db.collection(COL_ANSWERS);
    const productCol = db.collection(COL_PRODUCTS);

    await answerCol.deleteMany({});
    await answerCol.insertMany(answerDocs);
    console.log('answer_synonyms:', answerDocs.length, '건 삽입');

    const productDocs = loadProductDictionary();
    await productCol.deleteMany({});
    await productCol.insertMany(productDocs);
    console.log('product_synonyms:', productDocs.length, '건 삽입');

    console.log('완료. MongoDB Atlas에서 DB "' + DB_NAME + '" 와 컬렉션', COL_ANSWERS, ',', COL_PRODUCTS, '확인하세요.');
  } catch (e) {
    console.error('시드 실패:', e);
    process.exit(1);
  } finally {
    await client.close();
  }
}

run();
