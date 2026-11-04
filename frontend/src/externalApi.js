const MAX_RESPONSE_BYTES = 1024 * 1024;
const REQUEST_TIMEOUT_MS = 15_000;

export function validateExternalApiUrl(value) {
  const rawUrl = String(value || '').trim();
  if (!rawUrl) {
    throw new Error('Enter the external API URL.');
  }
  if (rawUrl.length > 2048) {
    throw new Error('The API URL is too long.');
  }

  let url;
  try {
    url = new URL(rawUrl);
  } catch {
    throw new Error('Enter a valid API URL.');
  }

  if (url.protocol !== 'https:') {
    throw new Error('Use an HTTPS API URL to protect data in transit.');
  }
  if (url.username || url.password) {
    throw new Error('Do not put a username or password in the API URL.');
  }
  if (url.hash) {
    throw new Error('Remove the fragment from the API URL.');
  }
  const credentialParameters = ['access_token', 'api_key', 'apikey', 'client_secret', 'password', 'secret', 'token'];
  if ([...url.searchParams.keys()].some(parameter => credentialParameters.includes(parameter.toLowerCase()))) {
    throw new Error('Do not put API credentials in URL query parameters.');
  }

  return url.toString();
}

async function readJsonResponse(response) {
  const contentType = response.headers.get('content-type') || '';
  if (!/(?:application\/json|application\/[^;]+\+json)/i.test(contentType)) {
    throw new Error('The API response is not JSON.');
  }

  const declaredLength = Number(response.headers.get('content-length'));
  if (Number.isFinite(declaredLength) && declaredLength > MAX_RESPONSE_BYTES) {
    throw new Error('The API response is larger than the 1 MB limit.');
  }

  if (!response.body) {
    const text = await response.text();
    if (new TextEncoder().encode(text).byteLength > MAX_RESPONSE_BYTES) {
      throw new Error('The API response is larger than the 1 MB limit.');
    }
    return JSON.parse(text);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let byteLength = 0;
  let text = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    byteLength += value.byteLength;
    if (byteLength > MAX_RESPONSE_BYTES) {
      await reader.cancel();
      throw new Error('The API response is larger than the 1 MB limit.');
    }
    text += decoder.decode(value, { stream: true });
  }

  text += decoder.decode();
  return JSON.parse(text);
}

export async function fetchExternalJson(value, bearerToken = '') {
  const url = validateExternalApiUrl(value);
  const token = String(bearerToken || '').trim();
  if (token.length > 4096) {
    throw new Error('The bearer token is too long.');
  }
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  const headers = { Accept: 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, {
      method: 'GET',
      headers,
      mode: 'cors',
      credentials: 'omit',
      cache: 'no-store',
      redirect: 'error',
      referrerPolicy: 'no-referrer',
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`The external API returned HTTP ${response.status}.`);
    }

    return await readJsonResponse(response);
  } catch (failure) {
    if (failure.name === 'AbortError') {
      throw new Error('The external API request timed out.');
    }
    if (failure instanceof TypeError) {
      throw new Error('Could not reach the API. Check the URL, network, and whether the API allows this site through CORS.');
    }
    throw failure;
  } finally {
    clearTimeout(timeout);
  }
}
