/**
 * product_dimensions.json 을 MongoDB product_dimensions 컬렉션에 저장
 * - .env 에 MONGODB_URI 넣어둔 뒤 실행
 * - 사용: node seed-dimensions.js [product_dimensions.json 경로]
 *   경로 생략 시 data/product_dimensions.json 사용
 */
require('dotenv').config();
const { MongoClient } = require('mongodb');
const path = require('path');
const fs = require('fs');

const DB_NAME = 'grabit';
const COL_DIMENSIONS = 'product_dimensions';

function loadDimensionsJson(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  const data = JSON.parse(raw);
  return Array.isArray(data) ? data : [data];
}

async function run() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.error('.env 파일에 MONGODB_URI를 넣고 다시 실행하세요.');
    process.exit(1);
  }

  const jsonPath = process.argv[2]
    ? path.resolve(process.argv[2])
    : path.join(__dirname, 'data', 'product_dimensions.json');

  if (!fs.existsSync(jsonPath)) {
    console.error('파일 없음:', jsonPath);
    console.error('사용: node seed-dimensions.js [product_dimensions.json 경로]');
    console.error('  예: node seed-dimensions.js "../상품 이미지/product_dimensions.json"');
    process.exit(1);
  }

  let docs;
  try {
    docs = loadDimensionsJson(jsonPath);
  } catch (e) {
    console.error('JSON 로드 실패:', e.message);
    process.exit(1);
  }

  const client = new MongoClient(uri);
  try {
    await client.connect();
    const db = client.db(DB_NAME);
    const col = db.collection(COL_DIMENSIONS);

    const ops = docs
      .filter(d => d && d.barcd)
      .map(doc => ({
        updateOne: {
          filter: { barcd: doc.barcd },
          update: {
            $set: {
              barcd: doc.barcd,
              item_no: doc.item_no || null,
              img_prod_nm: doc.img_prod_nm || null,
              width_cm: doc.width_cm != null ? doc.width_cm : null,
              length_cm: doc.length_cm != null ? doc.length_cm : null,
              height_cm: doc.height_cm != null ? doc.height_cm : null
            }
          },
          upsert: true
        }
      }));

    if (ops.length === 0) {
      console.log('barcd 있는 문서가 없습니다.');
      return;
    }

    const result = await col.bulkWrite(ops);
    const count = (result.upsertedCount || 0) + (result.modifiedCount || 0);
    console.log('product_dimensions:', count, '건 저장 완료. (파일:', jsonPath, ')');
  } catch (e) {
    console.error('시드 실패:', e);
    process.exit(1);
  } finally {
    await client.close();
  }
}

run();
