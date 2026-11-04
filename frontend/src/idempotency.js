export async function getIdempotencyKey(storage, storageKey, details, cryptoApi = globalThis.crypto) {
  if (!cryptoApi?.subtle || !cryptoApi.randomUUID) {
    throw new Error('Secure transaction retry protection is unavailable in this browser.');
  }

  const serializedDetails = JSON.stringify(details);
  const digest = await cryptoApi.subtle.digest('SHA-256', new TextEncoder().encode(serializedDetails));
  const fingerprint = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
  const stored = storage.getItem(storageKey);

  if (stored) {
    try {
      const attempt = JSON.parse(stored);
      if (attempt.fingerprint === fingerprint && typeof attempt.key === 'string') return attempt.key;
    } catch {
      storage.removeItem(storageKey);
    }
  }

  const key = cryptoApi.randomUUID();
  storage.setItem(storageKey, JSON.stringify({ key, fingerprint }));
  return key;
}

export function clearIdempotencyKey(storage, storageKey, key) {
  try {
    if (JSON.parse(storage.getItem(storageKey) || 'null')?.key === key) {
      storage.removeItem(storageKey);
    }
  } catch {
    storage.removeItem(storageKey);
  }
}
