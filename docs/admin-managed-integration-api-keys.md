# Admin-managed integration API keys

Administrators can generate per-system API keys in the Track 2 **External API** page.
The backend generates each key and stores only its SHA-256 hash. The full value is
returned once, shown in the page's one-time panel, and is never included in the key
list. Copy it to the external system's approved secret store; do not put it in source
control, browser storage, URLs, or logs. If it is lost, revoke it and generate a
replacement.

## Generate, use, and revoke

1. Sign in to Track 2 as an Administrator and open **External API**.
2. Enter a descriptive key name and select **Generate API key**.
3. Use **Copy key** immediately. The key is cleared from the page after copying,
   dismissal, navigation away from the page, or logout.
4. Configure the external system to send the value in the `X-API-Key` header when
   calling either read-only endpoint:

   - `GET /api/integration/v1/summary`
   - `GET /api/integration/v1/data-summary`

   Example PowerShell usage when the secret is already in the caller's environment:

   ```powershell
   $headers = @{ 'X-API-Key' = $env:TRACK2_API_KEY }
   Invoke-RestMethod -Method Get -Uri "$Track2Base/api/integration/v1/summary" -Headers $headers
   Invoke-RestMethod -Method Get -Uri "$Track2Base/api/integration/v1/data-summary" -Headers $headers
   ```

5. In **External API**, review each key's masked value and status. Select **Revoke**
   to invalidate an individual key. Revocation does not affect the existing
   `TRACK2_INTEGRATION_API_KEY` configuration or other managed keys.

Managed API keys authorize only the allowlisted integration GET endpoints. They do
not authenticate to Track 2's administrator APIs or permit writes to student,
attendance, finance, or other records.

## Database migration

The additive migration is
[`backend/migrations/20261109_integration_api_keys.sql`](../backend/migrations/20261109_integration_api_keys.sql).
Apply it through the approved database change process before deploying the backend
version that validates the `integration_api_key` table. It creates a new table and
does not modify existing records or the existing shared API key. The application
does not apply this migration automatically.
