import assert from 'node:assert/strict';
import test from 'node:test';

import { createApiKeyManagement } from '../src/apiKeyManagement.js';

test('keeps API keys ephemeral, strips key material from list state, and clears after copy', async () => {
  const calls = [];
  const clipboard = [];
  const stored = [];
  const previousStorage = globalThis.localStorage;
  globalThis.localStorage = {
    setItem: (...args) => stored.push(args),
  };
  try {
    const manager = createApiKeyManagement(async (...args) => {
      calls.push(args);
      if (args[0] === 'post') {
        return {
          id: 12,
          name: 'reports',
          key: 't2_generated-only-once',
          createdAt: '2026-01-02T03:04:05Z',
        };
      }
      if (args[0] === 'get') {
        return [{
          id: 12,
          name: 'reports',
          status: 'ACTIVE',
          createdAt: '2026-01-02T03:04:05Z',
          revokedAt: null,
          key: 'must-not-be-shown-again',
          keyHash: 'must-not-be-shown',
        }];
      }
      return undefined;
    }, async key => clipboard.push(key));

    manager.state.name = 'reports';
    await manager.generate();
    assert.equal(manager.state.oneTimeKey, 't2_generated-only-once');
    assert.deepEqual(manager.state.items, [{
      id: 12,
      name: 'reports',
      status: 'ACTIVE',
      createdAt: '2026-01-02T03:04:05Z',
      revokedAt: null,
    }]);
    assert.equal('key' in manager.state.items[0], false);
    assert.equal('keyHash' in manager.state.items[0], false);
    await manager.copyOneTimeKey();
    assert.deepEqual(clipboard, ['t2_generated-only-once']);
    assert.equal(manager.state.oneTimeKey, '');
    assert.deepEqual(stored, []);
    assert.deepEqual(calls.map(([method, path]) => [method, path]), [
      ['post', '/admin/integration-api-keys'],
      ['get', '/admin/integration-api-keys'],
    ]);

    await manager.revoke(12);
    assert.deepEqual(calls.slice(-2), [
      ['delete', '/admin/integration-api-keys/12'],
      ['get', '/admin/integration-api-keys'],
    ]);
  } finally {
    if (previousStorage === undefined) delete globalThis.localStorage;
    else globalThis.localStorage = previousStorage;
  }
});

test('does not discard the one-time key when clipboard access fails', async () => {
  const manager = createApiKeyManagement(async () => ({
    key: 't2_manual-copy',
  }), async () => {
    throw new Error('clipboard denied');
  });
  manager.state.oneTimeKey = 't2_manual-copy';

  await assert.rejects(manager.copyOneTimeKey(), /clipboard denied/);
  assert.equal(manager.state.oneTimeKey, 't2_manual-copy');
  manager.clearOneTimeKey();
  assert.equal(manager.state.oneTimeKey, '');
});
