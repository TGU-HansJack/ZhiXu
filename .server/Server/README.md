# Zhixu Server (Account)

Node.js + MySQL REST API for Zhixu account management (register/login + storage plans) and vault sync.

## Quick start

1) Copy env:

```bash
cp .env.example .env
```

2) Start:

```bash
docker compose up --build
```

API listens on `http://localhost:3001`.

Vault file contents are stored on disk under `STORAGE_ROOT` (default: `./storage`).

## API

- `GET /health`
- `POST /api/auth/register` `{ "username": "...", "password": "...", "email"?: "..." }`
- `POST /api/auth/login` `{ "username": "...", "password": "..." }` -> `{ "token": "...", "sessionId": "...", "refreshToken": "..." }`
- `POST /api/auth/refresh` `{ "sessionId": "...", "refreshToken": "..." }` -> `{ "token": "...", "sessionId": "...", "refreshToken": "..." }`
- `POST /api/auth/logout` (Bearer token) -> `{ "ok": true }`
- `GET /api/account/me` (Bearer token)
- `POST /api/account/password` (Bearer token) `{ "currentPassword": "...", "newPassword": "..." }`
- `GET /api/account/sessions` (Bearer token)
- `POST /api/account/sessions/revoke` (Bearer token) `{ "sessionId": "..." }`
- `GET /api/plans`
- `POST /api/account/subscription` (Bearer token) `{ "planCode": "storage_1g" | "storage_3g" | "storage_5g" }`
- Vault sync (Bearer token): `GET /api/v2/vault/changes`, `GET/PUT/DELETE /api/v2/vault/file`
