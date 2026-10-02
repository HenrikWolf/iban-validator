# IBAN Validator

Monorepo for an IBAN validation service and its (planned) web frontend.

## Structure

| Path | Description |
|------|-------------|
| `api/iban-validator.yaml` | OpenAPI spec, single source of truth for the REST contract |
| `backend/` | Spring Boot 4 (Java 21, Maven). Server interface and DTOs are generated from the spec |
| `frontend/` | React app (planned) |

## Backend

Requires JDK 21.

```powershell
cd backend
.\mvnw.cmd test            # build and run tests
.\mvnw.cmd spring-boot:run # start on http://localhost:8080
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
