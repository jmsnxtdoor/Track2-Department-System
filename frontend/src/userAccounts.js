export async function saveUserAccount({ editingUser, userForm, request }) {
  const wasEditing = Boolean(editingUser.value);
  const body = wasEditing
    ? {
      username: userForm.value.username,
      role: userForm.value.role,
      enabled: userForm.value.enabled,
      ...(userForm.value.newPassword ? { newPassword: userForm.value.newPassword } : {}),
    }
    : {
      username: userForm.value.username,
      role: userForm.value.role,
      password: userForm.value.password,
    };
  const savedUser = await request(
    wasEditing ? 'put' : 'post',
    wasEditing ? `/admin/users/${editingUser.value.id}` : '/admin/users',
    body,
  );

  if (!wasEditing) {
    editingUser.value = savedUser;
    userForm.value.password = '';
  }

  if (userForm.value.role === 'STUDENT'
    && userForm.value.studentId !== (savedUser.studentId ?? null)) {
    editingUser.value = await request(
      'put',
      `/admin/users/${savedUser.id}/student-link`,
      { studentId: userForm.value.studentId },
    );
  } else {
    editingUser.value = savedUser;
  }
  return wasEditing;
}
