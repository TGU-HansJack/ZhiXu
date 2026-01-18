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
- `POST /api/auth/email/code` `{ "email": "...", "purpose"?: "register" | "verify" }`
- `POST /api/auth/email/verify` `{ "email": "...", "code": "123456" }`
- `POST /api/auth/register` `{ "username": "...", "password": "...", "email"?: "...", "emailCode"?: "123456" }`
- `POST /api/auth/login` `{ "username": "...", "password": "..." }` -> `{ "token": "...", "sessionId": "...", "refreshToken": "..." }`
- `POST /api/auth/refresh` `{ "sessionId": "...", "refreshToken": "..." }` -> `{ "token": "...", "sessionId": "...", "refreshToken": "..." }`
- `POST /api/auth/logout` (Bearer token) -> `{ "ok": true }`
- `GET /api/account/me` (Bearer token)
- `POST /api/account/email` (Bearer token) `{ "email": "..." }` (set/clear email; best-effort sends verify code)
- `GET /api/account/avatar` (Bearer token) (download avatar)
- `PUT /api/account/avatar` (Bearer token) (upload avatar bytes; Content-Type: image/png|image/jpeg|image/webp|image/gif)
- `DELETE /api/account/avatar` (Bearer token)
- `POST /api/account/password` (Bearer token) `{ "currentPassword": "...", "newPassword": "..." }`
- `GET /api/account/sessions` (Bearer token)
- `POST /api/account/sessions/revoke` (Bearer token) `{ "sessionId": "..." }`
- `GET /api/account/sync/logs` (Bearer token)
- `GET /api/plans`
- `POST /api/account/subscription` (Bearer token) `{ "planCode": "storage_1g" | "storage_3g" | "storage_5g" }`
- Storage management (Bearer token): `GET /api/storage/stats`, `GET /api/storage/files`, `GET /api/storage/export`
- Vault sync (Bearer token): `GET /api/v2/vault/changes`, `GET/PUT/DELETE /api/v2/vault/file`
