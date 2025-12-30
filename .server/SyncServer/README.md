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

## Vault file sync (experimental)

These endpoints sync a Vault as *files* (e.g. `docs/*.md`, `attachments/*`, `.zhixu/settings.json`).

Notes:
- `path` max length is 1024; uniqueness is enforced by `path_hash = SHA-256(path)` in the DB index.
- Server also exposes a v2 API with per-file `rev`, an incremental change stream, and built-in version history.

### Manifest

`GET /api/vault/manifest` (Bearer token required)

Returns:

```json
{
  "serverTime": 1730000000000,
  "files": [
    { "path": "docs/Inbox.md", "updatedAt": 1730000000000, "mtimeMs": 1730000000000, "size": 123, "sha256": "...", "deleted": false }
  ]
}
```

### Download file

`GET /api/vault/file?path=docs/Inbox.md` (Bearer token required)

Returns raw bytes with headers `X-Zhixu-Mtime-Ms`, `X-Zhixu-Size`, `X-Zhixu-Sha256`.

### Upload file

`PUT /api/vault/file?path=docs/Inbox.md&mtimeMs=1730000000000` (Bearer token required)

Body: raw bytes (e.g. `application/octet-stream`).

### Delete file (tombstone)

`DELETE /api/vault/file?path=docs/Inbox.md` (Bearer token required)

## Vault sync v2 (recommended)

v2 adds:
- Per-path optimistic concurrency control (`baseRev` required for writes).
- `GET /changes` incremental pull (`since` cursor).
- Version history (`/versions` + `/version`).

### Changes (incremental pull)

`GET /api/v2/vault/changes?since=0&limit=2000` (Bearer token required)

Returns:
- `snapshot=true` when `since=0` (full current state).
- `cursor` is the current server cursor; subsequent calls use `since=<lastSeenChangeId>`.

### Download latest file

`GET /api/v2/vault/file?path=docs/Inbox.md` (Bearer token required)

Returns bytes with headers `X-Zhixu-Rev`, `X-Zhixu-Mtime-Ms`, `X-Zhixu-Size`, `X-Zhixu-Sha256`.

### Upload with `baseRev`

`PUT /api/v2/vault/file?path=docs/Inbox.md&mtimeMs=...&baseRev=12` (Bearer token required)

`409 rev_conflict` means the file changed on the server; client must pull/merge and retry with the latest `rev`.

### Delete with `baseRev`

`DELETE /api/v2/vault/file?path=docs/Inbox.md&baseRev=12` (Bearer token required)

### List versions

`GET /api/v2/vault/versions?path=docs/Inbox.md&limit=50` (Bearer token required)

### Download a specific version

`GET /api/v2/vault/version?path=docs/Inbox.md&rev=12` (Bearer token required)
