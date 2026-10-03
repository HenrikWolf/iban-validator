-- Bank data; not personal data, so stored in clear text.
-- For German IBANs the bank code (Bankleitzahl) is stored even without further data,
-- so BIC and bank name stay empty until a validator reports them.
CREATE TABLE bank (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bic          TEXT,
    bank_name    TEXT,
    country_name TEXT,
    bank_code    TEXT,                               -- national bank code, for Germany the BLZ
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- German banks are identified by their bank code, foreign banks (without one) by their BIC.
CREATE UNIQUE INDEX uq_bank_bank_code ON bank (bank_code) WHERE bank_code IS NOT NULL;
CREATE UNIQUE INDEX uq_bank_bic ON bank (bic) WHERE bank_code IS NULL;

ALTER TABLE iban ADD COLUMN bank_id BIGINT REFERENCES bank (id);
CREATE INDEX idx_iban_bank_id ON iban (bank_id);