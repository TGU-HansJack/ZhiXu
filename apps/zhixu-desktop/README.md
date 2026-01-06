# Zhixu Desktop (Tauri MVP)

This is the desktop MVP for Zhixu, built with Tauri + Vite + React.

## Features (MVP)

- Select a local vault folder
- Browse folders + Markdown files (Explorer-like sidebar, `.md` only for now)
- Open / edit / save (`Ctrl+S` / `Cmd+S`)
- Rename / delete the active file
- Split Markdown preview (toggle via `Show/Hide Preview`)
- Recent vaults + reopen last vault (persisted in app data `state.json`)

## Dev

Prerequisites:
- Node.js (LTS)
- Rust toolchain
- Tauri CLI (v2)

Commands:
- `npm install`
- `npm run dev`
- `npm run tauri dev`

If `cargo check` / `tauri dev` fails with missing Windows icons, regenerate them:
- `.\node_modules\.bin\tauri icon .\src-tauri\icons\icon.png`
