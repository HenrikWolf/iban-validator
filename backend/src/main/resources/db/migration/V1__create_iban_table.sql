CREATE TABLE iban (
    iban       VARCHAR(34) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
