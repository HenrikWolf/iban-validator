# IBAN Validator

Monorepo for an IBAN validation service and its web frontend.

## Structure

| Path | Description |
|------|-------------|
| `api/iban-validator.yaml` | OpenAPI spec, single source of truth for the REST contract |
| `backend/` | Spring Boot 4 (Java 21, Maven). Server interface and DTOs are generated from the spec |
| `frontend/` | React 19 + TypeScript, built with Vite (template only, no features yet) |

## Backend

Requires JDK 21.

```powershell
cd backend
.\mvnw.cmd test            # build and run tests
.\mvnw.cmd spring-boot:run # start on http://localhost:8080
```

## Frontend

Requires Node.js (LTS).

```powershell
cd frontend
npm install     # install dependencies
npm run dev     # start dev server on http://localhost:5173
npm run build   # type-check and build to dist/
```

### API

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

Change the contract in `api/iban-validator.yaml`; the Maven build regenerates the code.

## Deployment

The backend runs on DigitalOcean Kubernetes (namespace `iban-validator`).
Deploy manually via GitHub: *Actions* > *Backend* > *Run workflow*.
The workflow (manual only) builds and tests the backend, pushes the image
`registry.digitalocean.com/iban-validator-registry/iban-validator-backend:<commit-sha>`
and applies the manifests in `backend/k8s/`.

The internal service `iban-validator-backend` (ClusterIP) is always available. Until the frontend exists,
`backend/k8s/service-public.yaml` additionally exposes the backend through a DigitalOcean Load Balancer
(HTTP only, no authentication, billed separately). Get its address with:

```powershell
kubectl -n iban-validator get service iban-validator-backend-public
```

Call `http://<EXTERNAL-IP>/api/v1/iban/validation` (it can take a few minutes until the IP is assigned).

Without the load balancer, test through a tunnel instead:

```powershell
doctl kubernetes cluster kubeconfig save k8s-1-36-3-do-5-fra1-1790953586443
kubectl -n iban-validator port-forward service/iban-validator-backend 8080:80
```

Then call `http://localhost:8080/api/v1/iban/validation`.
