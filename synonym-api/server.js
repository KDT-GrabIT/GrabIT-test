/**
 * 대답/상품 근접단어 API - MongoDB에서 조회
 * - MONGODB_URI 있으면 MongoDB에서 읽고, 없으면 목 데이터 반환
 * - DB 이름: grabit, 컬렉션: answer_synonyms, product_synonyms
 */
require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { MongoClient } = require('mongodb');
const app = express();
const PORT = process.env.PORT || 3000;

const DB_NAME = 'grabit';
const COL_ANSWERS = 'answer_synonyms';
const COL_PRODUCTS = 'product_synonyms';

app.use(cors());
app.use(express.json());

let client = null;
let db = null;

async function getDb() {
  if (db) return db;
  const uri = process.env.MONGODB_URI;
  if (!uri) return null;
  try {
    client = new MongoClient(uri);
    await client.connect();
    db = client.db(DB_NAME);
    console.log('MongoDB 연결됨, DB:', DB_NAME);
    return db;
  } catch (e) {
    console.error('MongoDB 연결 실패:', e.message);
    return null;
  }
}

// 목 데이터 (MongoDB 없을 때)
const mockAnswers = {
  items: [
    { keyword: '예', proximity_words: ['네', '응', '맞아', '맞아요', '그래', '좋아'], type: 'answer' },
    { keyword: '아니', proximity_words: ['아니요', '틀렸', '다른'], type: 'answer' }
  ]
};
const mockProducts = { items: [] };

app.get('/synonyms/answers', async (req, res) => {
  try {
    const database = await getDb();
    if (!database) {
      return res.json(mockAnswers);
    }
    const col = database.collection(COL_ANSWERS);
    const items = await col.find({}).toArray();
    res.json({ items: items.length ? items : mockAnswers.items });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message, items: mockAnswers.items });
  }
});

app.get('/synonyms/products', async (req, res) => {
  try {
    const database = await getDb();
    if (!database) {
      return res.json(mockProducts);
    }
    const col = database.collection(COL_PRODUCTS);
    const items = await col.find({}).toArray();
    res.json({ items });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message, items: [] });
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Synonym API: http://localhost:${PORT}`);
  if (!process.env.MONGODB_URI) {
    console.log('MONGODB_URI 없음 → 목 데이터 사용. .env 설정 후 seed 실행하면 DB에 저장됨.');
  }
});

process.on('SIGINT', async () => {
  if (client) await client.close();
  process.exit(0);
});
