# Zhixu SyncServer

Dockerized Node.js + MySQL sync server for Zhixu (register / login / sync).

## Quick start

1) Copy env:

```bash
cp .env.example .env
```

2) Start:

```bash
docker compose up --build
```

API listens on `http://localhost:3000`.

## Environment

See `.env.example`.

## API

### Register

`POST /api/auth/register`

```json
{ "username": "alice", "password": "pass1234" }
```

### Login

`POST /api/auth/login`

```json
{ "username": "alice", "password": "pass1234" }
```

Returns:

```json
{ "token": "..." }
```

### Me

`GET /api/account/me` (Bearer token required)

Returns:

```json
{ "userId": 1, "username": "alice" }
```

### Devices

`GET /api/account/devices` (Bearer token required)

`POST /api/account/devices/bind` (Bearer token required)

```json
{ "deviceId": "device-1" }
```

`POST /api/account/devices/unbind` (Bearer token required)

```json
{ "deviceId": "device-1" }
```

### Push

`POST /api/sync/push` (Bearer token required)

```json
{
  "deviceId": "device-1",
  "notes": [
    {
      "noteId": "note-1",
      "updatedAt": 1730000000000,
      "deleted": false,
      "encrypted": true,
      "payloadBase64": "AAEC..."
    }
  ]
}
```

### Pull

`GET /api/sync/pull?since=0&deviceId=device-1` (Bearer token required)

Returns:

```json
{
  "serverTime": 1730000000000,
  "notes": [
    {
      "noteId": "note-1",
      "updatedAt": 1730000000000,
      "deleted": false,
      "encrypted": true,
      "payloadBase64": "AAEC...",
      "deviceId": "device-1"
    }
  ]
}
```

## E2EE

This server never decrypts note content. When `encrypted=true`, the server stores the note as an opaque blob (`payloadBase64`).
