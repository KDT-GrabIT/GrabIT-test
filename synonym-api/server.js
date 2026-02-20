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
const COL_DIMENSIONS = 'product_dimensions';
const E5_SERVICE_URL = process.env.E5_SERVICE_URL || 'http://localhost:5000';

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

// (선택) e5 유사검색: GET /synonyms/search?q=검색어&top_k=5
// 상품 치수 (meta.xml 상단 width/length/height) — barcd 기준 조회
app.get('/product-dimensions', async (req, res) => {
  try {
    const database = await getDb();
    if (!database) return res.json({ items: [] });
    const col = database.collection(COL_DIMENSIONS);
    const barcd = (req.query.barcd || '').trim();
    let items;
    if (barcd) {
      const one = await col.findOne({ barcd });
      items = one ? [one] : [];
    } else {
      items = await col.find({}).toArray();
    }
    res.json({ items });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message, items: [] });
  }
});

// 상품 치수 일괄 저장 (시드 스크립트 또는 관리용)
app.post('/product-dimensions', async (req, res) => {
  try {
    const database = await getDb();
    if (!database) return res.status(503).json({ error: 'MongoDB 연결 없음' });
    const col = database.collection(COL_DIMENSIONS);
    const body = req.body;
    const list = Array.isArray(body) ? body : (body.items || []);
    if (list.length === 0) return res.json({ ok: true, count: 0 });
    const ops = list.map(doc => ({
      updateOne: {
        filter: { barcd: doc.barcd },
        update: { $set: { ...doc, barcd: doc.barcd } },
        upsert: true
      }
    }));
    const result = await col.bulkWrite(ops);
    res.json({ ok: true, count: result.upsertedCount + result.modifiedCount });
  } catch (e) {
    console.error(e);
    res.status(500).json({ error: e.message });
  }
});

app.get('/synonyms/search', async (req, res) => {
  const q = (req.query.q || '').trim();
  const topK = Math.min(parseInt(req.query.top_k, 10) || 5, 50);
  try {
    const database = await getDb();
    if (!database || !q) {
      return res.json({ items: [] });
    }
    const col = database.collection(COL_PRODUCTS);
    const products = await col.find({}).toArray();
    if (products.length === 0) {
      return res.json({ items: [] });
    }
    const candidates = products.map(p => [p.display_name, ...(p.proximity_words || [])].join(' '));
    const response = await fetch(`${E5_SERVICE_URL.replace(/\/$/, '')}/search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: q, candidates, top_k: topK })
    });
    if (!response.ok) {
      return res.json({ items: products.slice(0, topK) });
    }
    const body = await response.json();
    const results = (body.results || []).map(r => r.text);
    const byCandidate = new Map();
    candidates.forEach((c, i) => byCandidate.set(c, products[i]));
    const seen = new Set();
    const sorted = results
      .map(text => byCandidate.get(text))
      .filter(p => p && !seen.has(p.class_id) && (seen.add(p.class_id) || true));
    res.json({ items: sorted });
  } catch (e) {
    console.error('search error:', e.message);
    const database = await getDb();
    if (database) {
      const col = database.collection(COL_PRODUCTS);
      const items = await col.find({}).limit(topK).toArray();
      return res.json({ items });
    }
    res.json({ items: [] });
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
