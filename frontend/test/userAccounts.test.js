import assert from 'node:assert/strict';
import test from 'node:test';

import { saveUserAccount } from '../src/userAccounts.js';

test('a failed student link retry updates the created account instead of creating it again', async () => {
  const editingUser = { value: null };
  const userForm = {
    value: {
      username: 'student-test',
      role: 'STUDENT',
      enabled: true,
      password: 'not-a-real-password',
      newPassword: '',
      studentId: 17,
    },
  };
  const requests = [];
  let linkAttempts = 0;
  const request = async (method, url, body) => {
    requests.push({ method, url, body });
    if (url === '/admin/users') {
      return { id: 23, username: 'student-test', role: 'STUDENT', studentId: null };
    }
    if (url === '/admin/users/23') {
      return { id: 23, username: 'student-test', role: 'STUDENT', studentId: null };
    }
    if (url === '/admin/users/23/student-link' && ++linkAttempts === 1) {
      throw new Error('Student link conflict');
    }
    return { id: 23, username: 'student-test', role: 'STUDENT', studentId: 17 };
  };

  await assert.rejects(
    saveUserAccount({ editingUser, userForm, request }),
    /Student link conflict/,
  );
  assert.equal(editingUser.value.id, 23);
  assert.equal(userForm.value.password, '');

  assert.equal(await saveUserAccount({ editingUser, userForm, request }), true);
  assert.deepEqual(requests.map(({ method, url }) => [method, url]), [
    ['post', '/admin/users'],
    ['put', '/admin/users/23/student-link'],
    ['put', '/admin/users/23'],
    ['put', '/admin/users/23/student-link'],
  ]);
  assert.equal(editingUser.value.studentId, 17);
});
