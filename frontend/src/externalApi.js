const CREDENTIAL_QUERY_PARAMETERS = new Set([
  'access_token',
  'api_key',
  'apikey',
  'client_secret',
  'password',
  'secret',
  'token',
]);

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

  const loopbackDemo = url.protocol === 'http:' && url.hostname === '127.0.0.1';
  if (url.protocol !== 'https:' && !loopbackDemo) {
    throw new Error('Use HTTPS; HTTP is allowed only for the loopback development demo.');
  }
  if (url.username || url.password) {
    throw new Error('Do not put a username or password in the API URL.');
  }
  if (url.hash) {
    throw new Error('Remove the fragment from the API URL.');
  }
  if ([...url.searchParams.keys()].some(parameter => CREDENTIAL_QUERY_PARAMETERS.has(parameter.toLowerCase()))) {
    throw new Error('Do not put API credentials in URL query parameters.');
  }

  return url.toString();
}

export function buildExternalApiRequest(value, authenticationType = 'NONE', credential = '') {
  const url = validateExternalApiUrl(value);
  if (!['NONE', 'API_KEY', 'BEARER'].includes(authenticationType)) {
    throw new Error('Choose a supported authentication method.');
  }
  const secret = String(credential || '').trim();
  if (secret.length > 4096) {
    throw new Error('The API credential is too long.');
  }
  if (authenticationType !== 'NONE' && !secret) {
    throw new Error('Enter the API credential for the selected authentication method.');
  }
  if (authenticationType === 'NONE' && secret) {
    throw new Error('Clear the API credential or select its authentication method.');
  }

  return { url, authenticationType, credential: secret };
}
