import assert from 'node:assert/strict';
import test from 'node:test';

import { buildExternalApiRequest, validateExternalApiUrl } from '../src/externalApi.js';

test('accepts HTTPS endpoints and the exact loopback development protocol', () => {
  assert.equal(
    validateExternalApiUrl(' https://api.example.test/v1/summary '),
    'https://api.example.test/v1/summary',
  );
  assert.equal(
    validateExternalApiUrl('http://127.0.0.1:8091/v1/demo'),
    'http://127.0.0.1:8091/v1/demo',
  );
  assert.equal(
    validateExternalApiUrl('https://track2-a.lan:8443/api/integration/v1/summary'),
    'https://track2-a.lan:8443/api/integration/v1/summary',
  );
  assert.throws(() => validateExternalApiUrl('http://api.example.test/data'), /HTTPS/);
  assert.throws(() => validateExternalApiUrl('http://localhost:8091/data'), /HTTPS/);
  assert.throws(() => validateExternalApiUrl('https://api.example.test/data#private'), /fragment/);
  assert.throws(() => validateExternalApiUrl('https://api.example.test/data?api_key=secret'), /credentials/);
});

test('rejects credentials embedded in URL authority and validates length', () => {
  assert.throws(
    () => validateExternalApiUrl('https://name:password@api.example.test/data'),
    /username or password/,
  );
  assert.throws(() => validateExternalApiUrl(`https://api.example.test/${'a'.repeat(2049)}`), /too long/);
});

test('builds one-shot API-key and bearer requests without persisting credentials', () => {
  assert.deepEqual(
    buildExternalApiRequest('http://127.0.0.1:8091/v1/demo', 'API_KEY', 'demo-key'),
    {
      url: 'http://127.0.0.1:8091/v1/demo',
      authenticationType: 'API_KEY',
      credential: 'demo-key',
    },
  );
  assert.equal(
    buildExternalApiRequest('https://api.example.test/v1/data', 'BEARER', 'temporary-token').authenticationType,
    'BEARER',
  );
  assert.throws(() => buildExternalApiRequest('https://api.example.test/v1/data', 'API_KEY'), /Enter the API credential/);
  assert.throws(
    () => buildExternalApiRequest('https://api.example.test/v1/data', 'NONE', 'unexpected'),
    /Clear the API credential/,
  );
});

test('requires a credential for API-key authentication and keeps no-auth requests empty', () => {
  assert.throws(
    () => buildExternalApiRequest('https://api.example.test/v1/data', 'API_KEY', '  '),
    /Enter the API credential/,
  );
  assert.deepEqual(
    buildExternalApiRequest('https://api.example.test/v1/data', 'NONE', ''),
    {
      url: 'https://api.example.test/v1/data',
      authenticationType: 'NONE',
      credential: '',
    },
  );
});
