# IBAN Validator

Monorepo for an IBAN validation service and its web frontend, available at **https://iban-validator.henrikwolf.de**.

## Structure

| Path | Description |
|------|-------------|
| `api/iban-validator.yaml` | OpenAPI spec, single source of truth for the REST contract |
| `backend/` | Spring Boot 4 (Java 21, Maven). Validates IBANs and stores valid ones in PostgreSQL |
| `frontend/` | React 19 + TypeScript, built with Vite. Single page that validates an IBAN via the API |

## API

`POST /api/v1/iban/validation`

```json
{ "iban": "DE89 3704 0044 0532 0130 00", "validator": "INTERNAL" }
```

```json
{ "iban": "DE89370400440532013000", "valid": true, "countryCode": "DE", "countryName": "Germany" }
```

Spaces, dots and hyphens are removed and letters are upper-cased before validation. The optional field `validator`
selects how the IBAN is checked:

- `INTERNAL` (default): own check of characters, country, length and checksum (ISO 13616).
- `IBANAPI`: external check via [ibanapi.com](https://ibanapi.com) (`validate-basic`), which sends the IBAN to this
  third party. If the service is unavailable, the API returns `503`; there is no fallback. The frontend only allows
  this after the user has consented to the transfer.
- `IBANAPI_EXTENDED` (frontend: *ibanapi.com [erweitert]*): like `IBANAPI`, but via the endpoint `validate`, which
  additionally returns bank data. A valid response then contains `bankName` and `bic` if the bank is known. These calls
  use the separate bank balance of the ibanapi.com account. Returned bank data is stored in the table `bank` and linked to the stored IBAN. German banks are keyed by their bank code (BLZ), foreign banks by their BIC; an existing entry is overwritten with the newer data. For German IBANs the bank code is always stored and linked to the IBAN, even without BIC and bank name; an extended validation later fills that entry. The other validators return the linked bank data, if any (a few large German banks are seeded by Flyway).

An invalid IBAN returns `200` with `valid: false` and a human-readable `failureMessage` (wording depends on the
validator). A missing `iban`, an `iban` that is empty after removing separators, or an unknown `validator` returns `400`.

Change the contract in `api/iban-validator.yaml`; the Maven build (server interface and DTOs) and the frontend scripts
(TypeScript types) regenerate the code.

## Backend

Requires JDK 21 and the environment variables `DB_PASSWORD` (password of the database user `iban_validator`),
`IBANAPI_KEY` (API key of ibanapi.com) and `IBAN_ENCRYPTION_KEY` (secret key used to encrypt stored IBANs; generate a
random one with `openssl rand -base64 32`). Optional: `IBAN_RETENTION` (e.g. `30d`) and `IBAN_CLEANUP_CRON`.

```powershell
cd backend
.\mvnw.cmd test            # build and run tests (no database needed, the repository is mocked)
.\mvnw.cmd spring-boot:run # start on http://localhost:8080
```

### Database

Every valid IBAN is stored once (with the time of the first check) in the table `iban` of a DigitalOcean
Managed PostgreSQL 18. Invalid IBANs are not stored. If storing fails, the error is logged and the validation result is
still returned. The schema is managed by Flyway (`backend/src/main/resources/db/migration`) and migrated on startup.

Because an IBAN is personal data, it is **not stored in clear text**:

- The IBAN is **encrypted** with AES-256-GCM (`Encryptors.delux` of spring-security-crypto) before it is written
  (`iban_encrypted` column).
- A deterministic **blind index** (HMAC-SHA256, `iban_lookup` column) is stored alongside so duplicates can still be
  detected (`ON CONFLICT`) without keeping the IBAN readable.
- Both use the secret `IBAN_ENCRYPTION_KEY`; the AES key is derived from it and the salt `iban.encryption-salt`
  (`application.yaml`). The salt is not secret, but changing it makes stored IBANs unreadable.
- A scheduled cleanup job enforces **storage limitation**: stored IBANs are deleted once they are older than
  `IBAN_RETENTION` (default 30 days). Schedule and retention are configurable (`iban.retention`, `iban.cleanup-cron`).

Connection settings are in `application.yaml`. Locally, the backend uses the public host of the database, so your IP
must be a trusted source. **This is the same database the deployed backend uses.**

To look into the database, connect a PostgreSQL client (e.g. Beekeeper Studio) to
`db-pgsql-fra1-05477-do-user-45507256-0.f.db.ondigitalocean.com`, port `25060`, database `iban_validator`, with SSL.

## Frontend

Requires Node.js (LTS).

```powershell
cd frontend
npm install     # install dependencies
npm run dev     # start dev server on http://localhost:5173
npm run build   # type-check and build to dist/
```

The dev server proxies `/api` to the deployed app, so validated IBANs end up in the production database.

## Deployment

Backend and frontend run on DigitalOcean Kubernetes (namespace `iban-validator`) and are deployed manually via GitHub:
*Actions* > *Backend* / *Frontend* > *Run workflow*. Both workflows build an image
(`registry.digitalocean.com/iban-validator-registry/iban-validator-<backend|frontend>:<commit-sha>`), create the
namespace and the image pull secret if needed, and apply the manifests in `backend/k8s/` or `frontend/k8s/`. They can
run independently in any order.

### Prerequisites

- GitHub repository secrets: `DIGITALOCEAN_ACCESS_TOKEN`, `DB_PASSWORD`, `IBANAPI_KEY` and `IBAN_ENCRYPTION_KEY` (the
  *Backend* workflow stores the latter three in the Kubernetes secrets `iban-validator-db`, `iban-validator-ibanapi`
  and `iban-validator-encryption`). Losing `IBAN_ENCRYPTION_KEY` makes the stored IBANs unreadable, so keep it safe.
- Managed database: database `iban_validator` owned by the user `iban_validator`; the Kubernetes cluster (and your IP for
  local development) as trusted sources.
- Traefik and cert-manager, installed as DigitalOcean 1-Click Apps.
- DNS: A record for `iban-validator.henrikwolf.de` (at Goneo) pointing to the Traefik load balancer.

### Routing and HTTPS

Traefik owns the only DigitalOcean Load Balancer. The *Frontend* workflow applies the routing:

- `frontend/k8s/ingress.yaml`: `/api` goes to the backend, everything else to the frontend (nginx only serves the React
  app). Plain HTTP is redirected to HTTPS. Other backend paths such as `/actuator` are not exposed.
- `frontend/k8s/clusterissuer.yaml`: Let's Encrypt issuer. cert-manager requests and renews the certificate
  automatically.
