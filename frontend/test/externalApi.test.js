import assert from 'node:assert/strict';
import test from 'node:test';

import { fetchExternalJson, validateExternalApiUrl } from '../src/externalApi.js';

test('accepts HTTPS API URLs without embedded credentials or fragments', () => {
  assert.equal(
    validateExternalApiUrl(' https://api.example.test/v1/students '),
    'https://api.example.test/v1/students',
  );
  assert.throws(() => validateExternalApiUrl('http://api.example.test/data'), /HTTPS/);
  assert.throws(() => validateExternalApiUrl('https://user:pass@api.example.test/data'), /username or password/);
  assert.throws(() => validateExternalApiUrl('https://api.example.test/data#private'), /fragment/);
  assert.throws(() => validateExternalApiUrl('https://api.example.test/data?access_token=secret'), /credentials/);
});

test('fetches JSON with GET and sends an optional bearer token only to the API host', async () => {
  const originalFetch = globalThis.fetch;
  let requestUrl;
  let requestOptions;
  globalThis.fetch = async (url, options) => {
    requestUrl = url;
    requestOptions = options;
    return new Response('{"items":[{"id":1}]}', {
      status: 200,
      headers: { 'content-type': 'application/json' },
    });
  };

  try {
    assert.deepEqual(
      await fetchExternalJson('https://api.example.test/v1/items', 'temporary-token'),
      { items: [{ id: 1 }] },
    );
    assert.equal(requestUrl, 'https://api.example.test/v1/items');
    assert.equal(requestOptions.method, 'GET');
    assert.equal(requestOptions.headers.Authorization, 'Bearer temporary-token');
    assert.equal(requestOptions.credentials, 'omit');
    assert.equal(requestOptions.redirect, 'error');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('reports non-JSON and unsuccessful external API responses', async () => {
  const originalFetch = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response('<html>no</html>', {
      status: 200,
      headers: { 'content-type': 'text/html' },
    });
    await assert.rejects(fetchExternalJson('https://api.example.test/data'), /not JSON/);

    globalThis.fetch = async () => new Response('{"error":"unauthorized"}', {
      status: 401,
      headers: { 'content-type': 'application/json' },
    });
    await assert.rejects(fetchExternalJson('https://api.example.test/data'), /HTTP 401/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
