/**
 * MongoDB 연결 테스트 (원인 확인용)
 * 실행: node test-db-connection.js
 */
const path = require('path');
const fs = require('fs');
const dns = require('dns').promises;

function loadEnv() {
  try { require('dotenv').config(); } catch (_) {}
  if (process.env.MONGODB_URI) return;
  const envPath = path.join(__dirname, '.env');
  if (!fs.existsSync(envPath)) return;
  let raw = fs.readFileSync(envPath, 'utf8');
  if (raw.charCodeAt(0) === 0xFEFF) raw = raw.slice(1);
  raw.split(/\r?\n/).forEach(line => {
    const m = line.match(/^\s*([^#=]+)=(.*)$/);
    if (m) process.env[m[1].trim()] = m[2].trim().replace(/\r/g, '').replace(/^["']|["']$/g, '');
  });
}
loadEnv();

const uri = process.env.MONGODB_URI;

async function main() {
  console.log('1. MONGODB_URI 로드:', uri ? 'OK (길이 ' + uri.length + ')' : 'FAIL - .env 확인');
  if (!uri) process.exit(1);

  const hostname = uri.match(/@([^/?]+)/)?.[1] || '';
  const srvName = hostname ? '_mongodb._tcp.' + hostname : '';
  console.log('2. SRV 조회:', srvName || '(없음)');

  try {
    const records = await dns.resolveSrv(srvName);
    console.log('   DNS SRV OK,', records.length, '개 호스트');
  } catch (e) {
    console.log('   DNS SRV 실패:', e.code || e.message);
    console.log('   → 이 환경/네트워크에서 Atlas SRV 조회가 막혀 있음. 로컬 터미널에서 실행하거나 방화벽 확인.');
    process.exit(1);
  }

  console.log('3. MongoClient 연결 시도...');
  const { MongoClient } = require('mongodb');
  const client = new MongoClient(uri, { serverSelectionTimeoutMS: 10000 });
  try {
    await client.connect();
    await client.db('grabit').command({ ping: 1 });
    console.log('   연결 성공.');
  } catch (e) {
    console.log('   연결 실패:', e.message);
    process.exit(1);
  } finally {
    await client.close();
  }
  console.log('완료.');
}
main();
