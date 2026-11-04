ALTER TABLE ledger_entry
    ADD COLUMN IF NOT EXISTS idempotency_key varchar(100),
    ADD COLUMN IF NOT EXISTS request_hash varchar(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_ledger_entry_idempotency_key
    ON ledger_entry (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE fund_entry
    ADD COLUMN IF NOT EXISTS idempotency_key varchar(100),
    ADD COLUMN IF NOT EXISTS request_hash varchar(64);

CREATE UNIQUE INDEX IF NOT EXISTS ux_fund_entry_idempotency_key
    ON fund_entry (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
