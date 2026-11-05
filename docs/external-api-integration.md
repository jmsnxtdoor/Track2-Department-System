# External API Integration

Track 2 exposes a read-only summary API for authorized systems and provides an Administrator-only proxy for reading JSON from an allowlisted external API. No partner system, URL, credentials, or write operations are assumed by this document.

## Track 2 as an API provider

### `GET /api/integration/v1/summary`

- **Base URL:** `http://127.0.0.1:8080` for the local development setup. Remote deployment requires approved HTTPS termination and network configuration.
- **Authentication:** `X-API-Key: <TRACK2_INTEGRATION_API_KEY>`.
- **Authorization:** shared-key filter specific to `/api/integration/**`; separate from user HTTP Basic authentication.
- **Success:** `200 application/json`.

```json
{
  "system": "Track 2 Department System",
  "dataClassification": "NON_PERSONAL_SUMMARY",
  "status": "Available",
  "availableData": [
    "service-status",
    "available-data-categories"
  ],
  "generatedAt": "2026-11-05T00:00:00Z"
}
```

`generatedAt` is the response generation time. The DTO intentionally contains no student or financial records.

| HTTP status | Meaning |
| --- | --- |
| `200` | API key accepted; summary returned. |
| `401` | `X-API-Key` missing, incorrect, or too long. |
| `503` | Provider key is not configured. |

## Track 2 as an API consumer

### `POST /api/admin/external-api/fetch`

- **Authentication:** Track 2 user authentication with HTTP Basic; requires the `ADMIN` role. The endpoint does not accept the provider's shared key as user authentication.
- **Content type:** `application/json`.
- **Operation:** one outbound `GET`; no external write methods are implemented.
- **Outbound headers:** `Accept: application/json`, plus the selected authentication header:
  - `authenticationType: "API_KEY"` sends `X-API-Key: <credential>`.
  - `authenticationType: "BEARER"` sends `Authorization: Bearer <credential>`.
  - `authenticationType: "NONE"` sends neither credential header.
- **Target restrictions:** the exact URL must be configured in the backend endpoint allowlist. External endpoints require HTTPS and public DNS addresses; only configured `http://127.0.0.1` loopback demo URLs may use HTTP. Redirects are not followed.
- **Response restrictions:** JSON content type is required; the response limit is 1 MiB; connect timeout is 3 seconds and request timeout is 10 seconds.

Request:

```json
{
  "url": "https://partner.example/api/approved-read-endpoint",
  "authenticationType": "API_KEY",
  "credential": "<supplied-at-request-time>"
}
```

The sample hostname above is illustrative only, not a configured partner URL. Credentials are not persisted by the application; do not put them in the URL or share them in browser screenshots, logs, or support requests.

Successful Track 2 HTTP envelope:

```json
{
  "status": 200,
  "data": {
    "items": []
  }
}
```

`status` is the upstream HTTP status; `data` is the upstream JSON body. A JSON upstream `401`, `403`, or other non-2xx response is returned inside an outer Track 2 HTTP `200` envelope so the page can distinguish provider errors from proxy/authentication errors. The page labels this explicitly as the upstream status.

| Track 2 HTTP status | Meaning |
| --- | --- |
| `200` | Request was proxied and the upstream returned JSON. Check response `status` for the upstream result, including upstream `401`/`403`. |
| `400` | Invalid request, unsupported auth selection, invalid URL, or rejected non-HTTPS/credential URL. |
| `401` | Track 2 HTTP Basic authentication is missing or invalid. |
| `403` | User is not an Administrator, or the exact endpoint is not allowlisted. |
| `502` | Upstream could not be reached, timed out, or returned non-JSON/invalid JSON. |
| `503` | No trusted endpoint allowlist is configured. |

Controller validation, allowlist, configuration, and transport errors use Spring `ProblemDetail` JSON with a `detail` message. Spring Security authentication/authorization failures may have a different body. The endpoint does not retry or use a circuit breaker.

## Local demonstration mock

The mock listens only on `127.0.0.1:8091`. Its data is synthetic and marked `DEMONSTRATION_ONLY`.

| Method and path | Authentication | Success response |
| --- | --- | --- |
| `GET /v1/demo` | `X-API-Key` using `DEMO_EXTERNAL_API_KEY` | `200 application/json`, sample `items` array. |
| `GET /v1/track2-summary` | `X-API-Key` using `DEMO_EXTERNAL_API_KEY` | `200 application/json`, `track2` contains the provider summary fetched with `TRACK2_INTEGRATION_API_KEY`. |

The mock returns `401` for missing/incorrect keys, `404` for unknown paths, and `405` for methods other than GET. Its API-key rejection body is `{"error":"A valid X-API-Key is required.","source":"DEMONSTRATION_ONLY"}`. The reverse summary route returns `502` when Track 2 cannot be reached or rejects the configured provider key. The mock is a PowerShell `HttpListener`, not a production partner service.

## Partner onboarding requirements

Before connecting a real system, obtain the partner's approved base URL and exact read endpoint, authentication mechanism and header format, credential provisioning/rotation policy, TLS requirements, JSON schema and content type, status/error semantics, rate limits, timeout expectations, and an authorized non-production test environment. Confirm that the endpoint resolves only to public addresses under this application's current network checks. Add only the exact approved HTTPS URL to the ignored local allowlist, and test with partner-provided credentials without recording them in source control or screenshots.

If the partner requires custom header names, OAuth token exchange, request bodies, pagination, retries, or writes, agree on a contract first; those behaviors are not implemented here. Production hosting also needs an approved TLS/reverse-proxy and secrets-management design; local `.env` files are for development only.

## Safe demonstration key rotation

Both demonstration keys were exposed and should be replaced. `scripts/manage-api-integration-keys.ps1` provides a staged workflow:

1. Run `.\scripts\manage-api-integration-keys.ps1 -Mode Prepare`. It creates a cryptographically random replacement pair in `backend/.env.integration.pending.local`; both the live and pending files are Git-ignored, and the settings ACL is restricted to the current Windows user and SYSTEM. Active processes continue using their already-loaded keys.
2. Schedule a cutover. Stop Spring Boot and the mock manually during the approved window. The script never stops or starts a process.
3. Run `.\scripts\manage-api-integration-keys.ps1 -Mode Activate`. Activation refuses while either port `8080` or `8091` is listening, rejects stale pending settings, and atomically replaces the local configuration.
4. Start the mock and Spring Boot manually so each loads the new pair. Then run `.\scripts\manage-api-integration-keys.ps1 -Mode CopyExternalKey` to copy only the external API key to the clipboard when needed. Paste it into the Admin External API page, then clear the clipboard after use. Also clear any old key that may remain on the clipboard from an earlier demo setup.
5. Run `.\scripts\test-api-integration-demo.ps1` and verify the External API page returns upstream HTTP `200`.

Do not delete or overwrite a pending rotation casually; activation is deliberately gated on inactive API listeners and unchanged non-key settings. If preparation is stale, inspect and remove the ignored pending file manually, then prepare a new rotation.
