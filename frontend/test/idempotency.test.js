import test from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';

import { clearIdempotencyKey, getIdempotencyKey } from '../src/idempotency.js';

function createStorage() {
  const values = new Map();
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  };
}

test('reuses an idempotency key for the same transaction details', async () => {
  const storage = createStorage();
  const details = { studentId: 7, amount: 25, kind: 'FINE' };

  const firstKey = await getIdempotencyKey(storage, 'ledger', details, webcrypto);
  const retryKey = await getIdempotencyKey(storage, 'ledger', details, webcrypto);

  assert.match(firstKey, /^[0-9a-f-]{36}$/i);
  assert.equal(retryKey, firstKey);
});

test('uses a fresh key for changed details and after successful completion', async () => {
  const storage = createStorage();
  const firstKey = await getIdempotencyKey(storage, 'ledger', { amount: 25 }, webcrypto);
  const changedKey = await getIdempotencyKey(storage, 'ledger', { amount: 30 }, webcrypto);

  assert.notEqual(changedKey, firstKey);
  clearIdempotencyKey(storage, 'ledger', changedKey);
  const nextAttemptKey = await getIdempotencyKey(storage, 'ledger', { amount: 30 }, webcrypto);
  assert.notEqual(nextAttemptKey, changedKey);
});
