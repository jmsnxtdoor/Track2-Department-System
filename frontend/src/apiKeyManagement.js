import { reactive } from 'vue';

const endpoint = '/admin/integration-api-keys';

function publicKeyMetadata(key) {
  return {
    id: key.id,
    name: key.name,
    createdAt: key.createdAt,
    revokedAt: key.revokedAt,
    status: key.status,
  };
}

export function createApiKeyManagement(request, writeClipboard) {
  const state = reactive({
    name: '',
    items: [],
    oneTimeKey: '',
    loading: false,
    generating: false,
  });

  async function load() {
    state.loading = true;
    try {
      const result = await request('get', endpoint);
      if (!Array.isArray(result)) {
        throw new Error('The API-key list response was invalid.');
      }
      state.items = result.map(publicKeyMetadata);
    } finally {
      state.loading = false;
    }
  }

  async function generate() {
    state.generating = true;
    try {
      const result = await request('post', endpoint, { name: state.name });
      if (!result || typeof result.key !== 'string' || !result.key) {
        throw new Error('The generated API key response was invalid.');
      }
      state.oneTimeKey = result.key;
      state.name = '';
      await load();
    } finally {
      state.generating = false;
    }
  }

  async function revoke(id) {
    await request('delete', `${endpoint}/${encodeURIComponent(id)}`);
    await load();
  }

  async function copyOneTimeKey() {
    if (!state.oneTimeKey) {
      throw new Error('There is no newly generated API key to copy.');
    }
    if (typeof writeClipboard !== 'function') {
      throw new Error('Clipboard access is unavailable. Select and copy the key manually.');
    }
    await writeClipboard(state.oneTimeKey);
    state.oneTimeKey = '';
  }

  function clearOneTimeKey() {
    state.oneTimeKey = '';
  }

  return { state, load, generate, revoke, copyOneTimeKey, clearOneTimeKey };
}
