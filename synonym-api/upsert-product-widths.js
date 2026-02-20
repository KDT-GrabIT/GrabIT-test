/**
 * Upsert width values into MongoDB product_synonyms.
 *
 * Modes:
 * 1) Batch mode by class_id JSON:
 *    node upsert-product-widths.js [jsonPath]
 *
 *    JSON format (either):
 *    [
 *      { "class_id": "woongjin_morning_rice_drink_500ml", "width": 6 },
 *      { "class_id": "pocari_sweat_500ml", "width": 7 }
 *    ]
 *    or
 *    { "items": [ ...same items... ] }
 *
 * 2) Single mode by display_name:
 *    node upsert-product-widths.js --name "아침햇살" --width 6
 */
require('dotenv').config();
const { MongoClient } = require('mongodb');
const path = require('path');
const fs = require('fs');

const DB_NAME = 'grabit';
const COL_PRODUCTS = 'product_synonyms';

function loadItems(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  const parsed = JSON.parse(raw);
  return Array.isArray(parsed) ? parsed : (parsed.items || []);
}

function toWidthNumber(v) {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string') {
    const n = Number(v.trim());
    if (Number.isFinite(n)) return n;
  }
  return null;
}

async function run() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.error('MONGODB_URI is missing. Please set it in .env');
    process.exit(1);
  }

  const args = process.argv.slice(2);
  const nameIdx = args.indexOf('--name');
  const widthIdx = args.indexOf('--width');

  // Single update mode: by display_name
  if (nameIdx >= 0 && widthIdx >= 0 && args[nameIdx + 1] && args[widthIdx + 1]) {
    const displayName = args[nameIdx + 1].trim();
    const width = toWidthNumber(args[widthIdx + 1]);
    if (!displayName || width == null || width <= 0) {
      console.error('Invalid --name or --width value.');
      process.exit(1);
    }

    const client = new MongoClient(uri, { serverSelectionTimeoutMS: 15000 });
    try {
      await client.connect();
      const db = client.db(DB_NAME);
      const col = db.collection(COL_PRODUCTS);
      const result = await col.updateMany(
        { type: 'product', display_name: displayName },
        { $set: { width } }
      );
      console.log(
        'Single width update done.',
        `display_name="${displayName}", width=${width},`,
        `matched=${result.matchedCount}, modified=${result.modifiedCount}`
      );
      return;
    } catch (e) {
      console.error('Failed to update width by display_name:', e.message);
      process.exit(1);
    } finally {
      await client.close();
    }
  }

  const jsonPath = args[0]
    ? path.resolve(args[0])
    : path.join(__dirname, 'data', 'product_widths.json');

  if (!fs.existsSync(jsonPath)) {
    console.error('Input file not found:', jsonPath);
    console.error('Usage: node upsert-product-widths.js [jsonPath]');
    process.exit(1);
  }

  let items = [];
  try {
    items = loadItems(jsonPath);
  } catch (e) {
    console.error('Failed to parse JSON:', e.message);
    process.exit(1);
  }

  const ops = [];
  for (const row of items) {
    const classId = (row.class_id || '').trim();
    const width = toWidthNumber(row.width);
    if (!classId || width == null || width <= 0) continue;
    ops.push({
      updateOne: {
        filter: { class_id: classId, type: 'product' },
        update: { $set: { width } },
        upsert: false
      }
    });
  }

  if (ops.length === 0) {
    console.log('No valid width rows found. Nothing to update.');
    return;
  }

  const client = new MongoClient(uri, { serverSelectionTimeoutMS: 15000 });
  try {
    await client.connect();
    const db = client.db(DB_NAME);
    const col = db.collection(COL_PRODUCTS);
    const result = await col.bulkWrite(ops, { ordered: false });
    console.log(
      'Width update done.',
      `matched=${result.matchedCount},`,
      `modified=${result.modifiedCount}`
    );
  } catch (e) {
    console.error('Failed to update widths:', e.message);
    process.exit(1);
  } finally {
    await client.close();
  }
}

run();

