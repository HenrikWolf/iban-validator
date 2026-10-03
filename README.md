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
{ "iban": "DE89 3704 0044 0532 0130 00" }
```

```json
{ "iban": "DE89370400440532013000", "valid": true, "countryCode": "DE" }
```

An invalid IBAN returns `200` with `valid: false` and a `failureReason`
(`INVALID_CHARACTERS`, `UNSUPPORTED_COUNTRY`, `INVALID_LENGTH`, `INVALID_CHECKSUM`).
A missing or empty `iban` returns `400`.

Change the contract in `api/iban-validator.yaml`; the Maven build (server interface and DTOs) and the frontend scripts
(TypeScript types) regenerate the code.

## Backend

Requires JDK 21 and the environment variable `DB_PASSWORD` (password of the database user `iban_validator`).

```powershell
cd backend
.\mvnw.cmd test            # build and run tests (no database needed, the repository is mocked)
.\mvnw.cmd spring-boot:run # start on http://localhost:8080
```

### Database

Every valid IBAN is stored once (normalized, with the time of the first check) in the table `iban` of a DigitalOcean
Managed PostgreSQL 18. Invalid IBANs are not stored. If storing fails, the error is logged and the validation result is
still returned. The schema is managed by Flyway (`backend/src/main/resources/db/migration`) and migrated on startup.

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

- GitHub repository secrets: `DIGITALOCEAN_ACCESS_TOKEN` and `DB_PASSWORD` (the *Backend* workflow stores the latter in
  the Kubernetes secret `iban-validator-db`).
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
