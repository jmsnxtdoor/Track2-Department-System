import assert from 'node:assert/strict';
import test from 'node:test';

import { setRequestFailure } from '../src/requestState.js';

test('a failed request removes stale success notice and shows the new error', () => {
  const error = { value: '' };
  const notice = { value: 'Student added.' };
  const failure = { response: { status: 403 } };

  setRequestFailure(error, notice, failure, value => `Request failed (${value.response.status}).`);

  assert.equal(notice.value, '');
  assert.equal(error.value, 'Request failed (403).');
});
