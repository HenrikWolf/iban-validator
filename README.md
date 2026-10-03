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

Backend and frontend run on DigitalOcean Kubernetes (namespace `iban-validator`) and are deployed
manually via GitHub: *Actions* > *Backend* / *Frontend* > *Run workflow*.

| Workflow | What it does | Image | Manifests |
|----------|--------------|-------|-----------|
| *Backend* | builds and tests, pushes the image, deploys | `registry.digitalocean.com/iban-validator-registry/iban-validator-backend:<commit-sha>` | `backend/k8s/` |
| *Frontend* | builds, pushes the image (nginx), deploys | `registry.digitalocean.com/iban-validator-registry/iban-validator-frontend:<commit-sha>` | `frontend/k8s/` |

Run *Backend* first: it creates the namespace and the image pull secret the frontend relies on, and the frontend needs the
backend service.

The backend service `iban-validator-backend` is only reachable inside the cluster. The only public entry point is the
frontend, exposed through a DigitalOcean Load Balancer (`frontend/k8s/service-public.yaml`, HTTP only, billed separately).
Its nginx serves the React app and forwards `/api/` to the backend, so the browser talks to a single address.
Get the address with:

```powershell
kubectl -n iban-validator get service iban-validator-backend-public
```

Open `http://<EXTERNAL-IP>/` (it can take a few minutes until the IP is assigned). The API is available at
`http://<EXTERNAL-IP>/api/v1/iban/validation`.

To reach the backend directly, use a tunnel:

```powershell
doctl kubernetes cluster kubeconfig save k8s-1-36-3-do-5-fra1-1790953586443
kubectl -n iban-validator port-forward service/iban-validator-backend 8080:80
```

Then call `http://localhost:8080/api/v1/iban/validation`.
