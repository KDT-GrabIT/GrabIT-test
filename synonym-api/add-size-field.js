/**
 * 기존 product_synonyms 문서에 size 필드 추가 (없을 때만)
 * - 한 번만 실행: node add-size-field.js
 * - .env 에 MONGODB_URI 필요
 */
const path = require('path');
const fs = require('fs');

function loadEnv() {
  try {
    require('dotenv').config();
  } catch (_) {}
  if (process.env.MONGODB_URI) return;
  const envPath = path.join(__dirname, '.env');
  if (!fs.existsSync(envPath)) return;
  let raw = fs.readFileSync(envPath, 'utf8');
  if (raw.charCodeAt(0) === 0xFEFF) raw = raw.slice(1);
  raw.split(/\r?\n/).forEach(line => {
    const m = line.match(/^\s*([^#=]+)=(.*)$/);
    if (m) {
      const val = m[2].trim().replace(/\r/g, '').replace(/^["']|["']$/g, '');
      process.env[m[1].trim()] = val;
    }
  });
}
loadEnv();

const { MongoClient } = require('mongodb');

const DB_NAME = 'grabit';
const COL_PRODUCTS = 'product_synonyms';

async function run() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.error('.env 에 MONGODB_URI 를 넣고 다시 실행하세요.');
    process.exit(1);
  }
  const client = new MongoClient(uri, {
    serverSelectionTimeoutMS: 15000
  });
  try {
    await client.connect();
    const db = client.db(DB_NAME);
    const col = db.collection(COL_PRODUCTS);

    const result = await col.updateMany(
      { type: 'product', $or: [{ size: { $exists: false } }, { size: null }] },
      { $set: { size: { width: '', length: '', height: '' } } }
    );
    console.log('product_synonyms: size 필드 추가 완료, 수정된 문서 수:', result.modifiedCount);
  } catch (e) {
    console.error('실패:', e);
    process.exit(1);
  } finally {
    await client.close();
  }
}

run();
