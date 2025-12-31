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
- `POST /api/auth/register` `{ "username": "...", "password": "..." }`
- `POST /api/auth/login` `{ "username": "...", "password": "..." }` -> `{ "token": "..." }`
- `GET /api/account/me` (Bearer token)
- `GET /api/plans`
- `POST /api/account/subscription` (Bearer token) `{ "planCode": "storage_1g" | "storage_3g" | "storage_5g" }`
- Vault sync (Bearer token): `GET /api/v2/vault/changes`, `GET/PUT/DELETE /api/v2/vault/file`
