-- IBANs are personal data: store them encrypted (AES-256-GCM) instead of in clear text.
-- A deterministic blind index (HMAC-SHA256) keeps duplicate detection working.
-- The previous table held clear-text IBANs; dropping it also purges that data.
DROP TABLE iban;

CREATE TABLE iban (
    iban_lookup    CHAR(64)    PRIMARY KEY,         -- hex HMAC-SHA256 of the IBAN (blind index)
    iban_encrypted TEXT        NOT NULL,            -- hex(IV || ciphertext || GCM tag)
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_iban_created_at ON iban (created_at);
