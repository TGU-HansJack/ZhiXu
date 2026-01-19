# Zhixu Server (Account)

Node.js + MySQL REST API for Zhixu account management (register/login + avatar + sessions/devices) and vault sync.

Storage is enforced via a single quota (default: 5GB, configurable via `STORAGE_LIMIT_BYTES`).

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
- `POST /api/auth/email/code` `{ "email": "...", "purpose"?: "register" | "verify" | "login" }`
- `POST /api/auth/email/verify` `{ "email": "...", "code": "123456" }`
- `POST /api/auth/email/login` `{ "email": "...", "code": "123456" }` -> `{ "token": "...", "sessionId": "...", "refreshToken": "...", "isAdmin": true|false }`
- `POST /api/auth/register` `{ "username": "...", "password": "...", "email": "...", "emailCode": "123456" }`
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
- Storage management (Bearer token): `GET /api/storage/stats`, `GET /api/storage/files`, `GET /api/storage/export`
- Vault sync (Bearer token): `GET /api/v2/vault/changes`, `GET/PUT/DELETE /api/v2/vault/file`

### Admin API (requires `ADMIN_EMAIL` + email login)

Admin access is granted only when:

- server `.env` sets `ADMIN_EMAIL`, and
- the session was created via `POST /api/auth/email/login` (not password login), and
- the logged-in user's email matches `ADMIN_EMAIL`.

Endpoints:

- `GET /api/admin/status`
- `GET /api/admin/users` `?q=&limit=&offset=`
- `POST /api/admin/users/:id/sync` `{ "disabled": true|false }`
- `DELETE /api/admin/users/:id` (also wipes vault files on disk)
- `POST /api/admin/email/broadcast` `{ "userIds": [1,2], "subject": "...", "text": "..." }`
- `POST /api/admin/sync/disableAll` `{ "disabled": true|false }`
- `GET /api/admin/sync/summary` `?days=30`
- `GET /api/admin/users/:id/sync/summary` `?days=30`
