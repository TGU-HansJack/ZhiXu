#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use std::fs;
use std::io::Read;
use std::path::Component;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::Duration;
use tauri::Manager;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};

mod draw_format;

struct VaultState(Mutex<Option<PathBuf>>);

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PersistedAppState {
    last_vault: Option<String>,
    recent_vaults: Vec<String>,
}

struct PersistedState(Mutex<PersistedAppState>);

struct TrayHandle(tauri::tray::TrayIcon);

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct VaultEntryDto {
    path: String,
    name: String,
    is_dir: bool,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct VaultFileInfoDto {
    path: String,
    size_bytes: u64,
    mtime_ms: i64,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct VaultStatDto {
    path: String,
    is_dir: bool,
    size_bytes: u64,
    mtime_ms: i64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct HttpHeaderDto {
    name: String,
    value: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct HttpRequestDto {
    method: String,
    url: String,
    headers: Option<Vec<HttpHeaderDto>>,
    body: Option<Vec<u8>>,
    timeout_ms: Option<u64>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct HttpResponseDto {
    status: u16,
    ok: bool,
    headers: Vec<HttpHeaderDto>,
    bytes: Vec<u8>,
}

fn persisted_state_path(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| format!("Failed to resolve app data dir: {e}"))?;
    Ok(dir.join("state.json"))
}

fn load_persisted_state(app: &tauri::AppHandle) -> PersistedAppState {
    let path = match persisted_state_path(app) {
        Ok(p) => p,
        Err(_) => return PersistedAppState::default(),
    };
    let bytes = match fs::read(&path) {
        Ok(b) => b,
        Err(_) => return PersistedAppState::default(),
    };
    serde_json::from_slice(&bytes).unwrap_or_default()
}

fn save_persisted_state(app: &tauri::AppHandle, state: &PersistedAppState) -> Result<(), String> {
    let path = persisted_state_path(app)?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create app data dir: {e}"))?;
    }
    let bytes = serde_json::to_vec_pretty(state).map_err(|e| format!("Failed to encode state: {e}"))?;
    fs::write(&path, bytes).map_err(|e| format!("Failed to write state: {e}"))
}

fn to_vault_path(root: &Path, abs: &Path) -> Result<String, String> {
    let rel = abs.strip_prefix(root).map_err(|_| "Path not in vault".to_string())?;
    let s = rel.to_string_lossy().replace('\\', "/");
    Ok(s.trim_matches('/').to_string())
}

fn normalize_rel_path(rel_path: &str) -> Result<PathBuf, String> {
    let rel = rel_path.replace('\\', "/");
    let clean = rel.trim_matches('/');
    if clean.is_empty() {
        return Ok(PathBuf::new());
    }
    let mut out = PathBuf::new();
    for c in Path::new(clean).components() {
        match c {
            Component::Normal(p) => out.push(p),
            Component::CurDir => {}
            Component::ParentDir | Component::RootDir | Component::Prefix(_) => return Err("Invalid path".to_string()),
        }
    }
    Ok(out)
}

fn resolve_in_vault(root: &Path, rel_path: &str) -> Result<PathBuf, String> {
    let rel = normalize_rel_path(rel_path)?;
    let candidate = if rel.as_os_str().is_empty() { root.to_path_buf() } else { root.join(rel) };
    let root_canon = root.canonicalize().map_err(|e| format!("Vault root invalid: {e}"))?;

    if candidate.exists() {
        let c = candidate.canonicalize().map_err(|e| format!("Invalid path: {e}"))?;
        if !c.starts_with(&root_canon) {
            return Err("Path escapes vault".to_string());
        }
        return Ok(c);
    }

    // For non-existing targets (e.g. new file/dir), validate the nearest existing ancestor.
    // This supports create_dir_all while still preventing symlink-escape via existing paths.
    let mut probe = candidate.parent().unwrap_or(root).to_path_buf();
    while probe != *root && !probe.exists() {
        probe = probe.parent().unwrap_or(root).to_path_buf();
    }
    let p = probe.canonicalize().map_err(|e| format!("Invalid path: {e}"))?;
    if !p.starts_with(&root_canon) {
        return Err("Path escapes vault".to_string());
    }
    Ok(candidate)
}

fn ensure_non_empty_path(rel_path: &str) -> Result<(), String> {
    let rel = rel_path.replace('\\', "/");
    let clean = rel.trim_matches('/');
    if clean.is_empty() {
        return Err("Missing path".to_string());
    }
    Ok(())
}

fn ensure_text_path(rel_path: &str) -> Result<(), String> {
    ensure_non_empty_path(rel_path)?;
    let rel = rel_path.replace('\\', "/");
    let clean = rel.trim_matches('/');
    let lower = clean.to_ascii_lowercase();
    if !lower.ends_with(".md") {
        return Err("仅支持 .md 文本文件".to_string());
    }
    Ok(())
}

fn ensure_draw_path(rel_path: &str) -> Result<(), String> {
    ensure_non_empty_path(rel_path)?;
    let rel = rel_path.replace('\\', "/");
    let clean = rel.trim_matches('/');
    let lower = clean.to_ascii_lowercase();
    if !lower.ends_with(draw_format::EXTENSION) {
        return Err("仅支持 .zhixu 绘图文件".to_string());
    }
    Ok(())
}

fn is_supported_file_name(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    lower.ends_with(".md")
        || lower.ends_with(".zhixu")
        || lower.ends_with(".pdf")
        || lower.ends_with(".png")
        || lower.ends_with(".jpg")
        || lower.ends_with(".jpeg")
        || lower.ends_with(".gif")
        || lower.ends_with(".webp")
        || lower.ends_with(".svg")
}

#[tauri::command]
fn set_vault_root(
    path: String,
    app: tauri::AppHandle,
    state: tauri::State<'_, VaultState>,
    persisted: tauri::State<'_, PersistedState>,
) -> Result<String, String> {
    let root = PathBuf::from(path);
    let canon = root.canonicalize().map_err(|e| format!("Failed to open folder: {e}"))?;
    let md = fs::metadata(&canon).map_err(|e| format!("Failed to stat folder: {e}"))?;
    if !md.is_dir() {
        return Err("Not a directory".to_string());
    }
    *state.0.lock().unwrap() = Some(canon.clone());
    let canon_str = canon.to_string_lossy().to_string();

    {
        let mut p = persisted.0.lock().unwrap();
        p.last_vault = Some(canon_str.clone());
        p.recent_vaults.retain(|v| v != &canon_str);
        p.recent_vaults.insert(0, canon_str.clone());
        p.recent_vaults.truncate(10);
        save_persisted_state(&app, &p)?;
    }

    Ok(canon_str)
}

#[tauri::command]
fn list_dir(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<Vec<VaultEntryDto>, String> {
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let dir = resolve_in_vault(&root, &rel_path)?;
    let md = fs::metadata(&dir).map_err(|e| format!("Failed to read dir: {e}"))?;
    if !md.is_dir() {
        return Err("Not a directory".to_string());
    }
    let mut out = Vec::new();
    for entry in fs::read_dir(&dir).map_err(|e| format!("Failed to list dir: {e}"))? {
        let entry = entry.map_err(|e| format!("Failed to read entry: {e}"))?;
        let path = entry.path();
        let name = entry
            .file_name()
            .to_string_lossy()
            .to_string();
        if name == ".DS_Store" {
            continue;
        }
        let is_dir = entry.file_type().map_err(|e| format!("Failed to read type: {e}"))?.is_dir();
        if !is_dir && !is_supported_file_name(&name) {
            continue;
        }
        let rel = to_vault_path(&root, &path)?;
        out.push(VaultEntryDto { path: rel, name, is_dir });
    }
    Ok(out)
}

#[tauri::command]
fn read_text_file(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<String, String> {
    ensure_text_path(&rel_path)?;
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    let md = fs::metadata(&path).map_err(|e| format!("Failed to read file: {e}"))?;
    if !md.is_file() {
        return Err("Not a file".to_string());
    }
    fs::read_to_string(&path).map_err(|e| format!("Failed to read file: {e}"))
}

#[tauri::command]
fn write_text_file(rel_path: String, content: String, state: tauri::State<'_, VaultState>) -> Result<(), String> {
    ensure_text_path(&rel_path)?;
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create parent dirs: {e}"))?;
    }
    fs::write(&path, content).map_err(|e| format!("Failed to write file: {e}"))
}

#[tauri::command]
fn read_draw_document(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<draw_format::DrawDocumentDto, String> {
    ensure_draw_path(&rel_path)?;
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    let md = fs::metadata(&path).map_err(|e| format!("Failed to read file: {e}"))?;
    if !md.is_file() {
        return Err("Not a file".to_string());
    }
    let bytes = fs::read(&path).map_err(|e| format!("Failed to read file: {e}"))?;
    draw_format::decode(&bytes)
}

#[tauri::command]
fn write_draw_document(rel_path: String, document: draw_format::DrawDocumentDto, state: tauri::State<'_, VaultState>) -> Result<(), String> {
    ensure_draw_path(&rel_path)?;
    if document.pages.is_empty() {
        return Err("Document has no pages".to_string());
    }
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create parent dirs: {e}"))?;
    }
    let bytes = draw_format::encode(&document)?;
    fs::write(&path, bytes).map_err(|e| format!("Failed to write file: {e}"))
}

#[tauri::command]
fn write_bytes_abs(path: String, bytes: Vec<u8>) -> Result<(), String> {
    if path.trim().is_empty() {
        return Err("Missing path".to_string());
    }
    let abs = PathBuf::from(&path);
    if let Some(parent) = abs.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create parent dirs: {e}"))?;
    }
    fs::write(&abs, bytes).map_err(|e| format!("Failed to write file: {e}"))
}

#[tauri::command]
fn write_draw_document_abs(path: String, document: draw_format::DrawDocumentDto) -> Result<(), String> {
    if path.trim().is_empty() {
        return Err("Missing path".to_string());
    }
    if document.pages.is_empty() {
        return Err("Document has no pages".to_string());
    }
    let abs = PathBuf::from(&path);
    if let Some(parent) = abs.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create parent dirs: {e}"))?;
    }
    let bytes = draw_format::encode(&document)?;
    fs::write(&abs, bytes).map_err(|e| format!("Failed to write file: {e}"))
}

#[tauri::command]
fn read_bytes_abs(path: String) -> Result<Vec<u8>, String> {
    if path.trim().is_empty() {
        return Err("Missing path".to_string());
    }
    let abs = PathBuf::from(&path);
    let md = fs::metadata(&abs).map_err(|e| format!("Failed to stat file: {e}"))?;
    if !md.is_file() {
        return Err("Not a file".to_string());
    }
    fs::read(&abs).map_err(|e| format!("Failed to read file: {e}"))
}

fn ureq_collect_headers(resp: &ureq::Response) -> Vec<HttpHeaderDto> {
    let mut out: Vec<HttpHeaderDto> = Vec::new();
    let names = resp.headers_names();
    for name in names {
        if let Some(value) = resp.header(&name) {
            out.push(HttpHeaderDto { name, value: value.to_string() });
        }
    }
    out
}

#[tauri::command]
fn http_request(req: HttpRequestDto) -> Result<HttpResponseDto, String> {
    let method = req.method.trim();
    let url = req.url.trim();
    if method.is_empty() {
        return Err("Missing method".to_string());
    }
    if url.is_empty() {
        return Err("Missing url".to_string());
    }

    let mut agent_builder = ureq::AgentBuilder::new();
    if let Some(timeout_ms) = req.timeout_ms {
        let d = Duration::from_millis(timeout_ms.max(1));
        agent_builder = agent_builder.timeout_read(d).timeout_write(d).timeout_connect(d);
    }
    let agent = agent_builder.build();

    let mut request = agent.request(method, url);
    if let Some(headers) = req.headers {
        for h in headers {
            let name = h.name.trim();
            if name.is_empty() {
                continue;
            }
            request = request.set(name, &h.value);
        }
    }

    let response = match req.body {
        Some(body) => request.send_bytes(&body),
        None => request.call(),
    };

    match response {
        Ok(resp) => {
            let status = resp.status();
            let headers = ureq_collect_headers(&resp);
            let mut reader = resp.into_reader();
            let mut bytes: Vec<u8> = Vec::new();
            reader.read_to_end(&mut bytes).map_err(|e| format!("Failed to read response: {e}"))?;
            Ok(HttpResponseDto {
                status: status as u16,
                ok: status >= 200 && status < 300,
                headers,
                bytes,
            })
        }
        Err(ureq::Error::Status(status, resp)) => {
            let headers = ureq_collect_headers(&resp);
            let mut reader = resp.into_reader();
            let mut bytes: Vec<u8> = Vec::new();
            reader.read_to_end(&mut bytes).map_err(|e| format!("Failed to read response: {e}"))?;
            Ok(HttpResponseDto {
                status: status as u16,
                ok: false,
                headers,
                bytes,
            })
        }
        Err(e) => Err(format!("Request failed: {e}")),
    }
}

#[tauri::command]
fn create_dir(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<(), String> {
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    fs::create_dir_all(&path).map_err(|e| format!("Failed to create dir: {e}"))
}

#[tauri::command]
fn create_file(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<(), String> {
    ensure_non_empty_path(&rel_path)?;
    let rel = rel_path.replace('\\', "/");
    let clean = rel.trim_matches('/');
    let lower = clean.to_ascii_lowercase();
    let is_markdown = lower.ends_with(".md");
    let is_drawing = lower.ends_with(draw_format::EXTENSION);
    if !is_markdown && !is_drawing {
        return Err("仅支持创建 .md 或 .zhixu 文件".to_string());
    }
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create parent dirs: {e}"))?;
    }
    if path.exists() {
        return Err("File already exists".to_string());
    }

    if is_markdown {
        fs::write(&path, "").map_err(|e| format!("Failed to create file: {e}"))?;
        return Ok(());
    }

    let now = now_ms();
    let doc = draw_format::blank_document(now);
    let bytes = draw_format::encode(&doc)?;
    fs::write(&path, bytes).map_err(|e| format!("Failed to create file: {e}"))?;
    Ok(())
}

#[tauri::command]
fn rename_entry(from_rel_path: String, to_rel_path: String, state: tauri::State<'_, VaultState>) -> Result<(), String> {
    ensure_non_empty_path(&from_rel_path)?;
    ensure_non_empty_path(&to_rel_path)?;
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let from = resolve_in_vault(&root, &from_rel_path)?;
    if !from.exists() {
        return Err("Source does not exist".to_string());
    }
    let to = resolve_in_vault(&root, &to_rel_path)?;
    if let Some(parent) = to.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create parent dirs: {e}"))?;
    }
    fs::rename(&from, &to).map_err(|e| format!("Failed to rename: {e}"))
}

#[tauri::command]
fn delete_entry(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<(), String> {
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    let md = fs::metadata(&path).map_err(|e| format!("Failed to stat: {e}"))?;
    if md.is_dir() {
        fs::remove_dir_all(&path).map_err(|e| format!("Failed to delete dir: {e}"))
    } else {
        fs::remove_file(&path).map_err(|e| format!("Failed to delete file: {e}"))
    }
}

#[tauri::command]
fn get_persisted_state(state: tauri::State<'_, PersistedState>) -> PersistedAppState {
    state.0.lock().unwrap().clone()
}

#[tauri::command]
fn exit_app(app: tauri::AppHandle) {
    app.exit(0);
}

fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn system_time_to_epoch_ms(t: std::time::SystemTime) -> i64 {
    use std::time::UNIX_EPOCH;
    t.duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn should_skip_walk_dir(rel_path: &str) -> bool {
    let p = rel_path.trim_matches('/').to_ascii_lowercase();
    if p.is_empty() {
        return false;
    }
    if p == ".zhixu/sync" || p.starts_with(".zhixu/sync/") {
        return true;
    }
    if p == ".zhixu/conflicts" || p.starts_with(".zhixu/conflicts/") {
        return true;
    }
    if p == ".zhixu/history" || p.starts_with(".zhixu/history/") {
        return true;
    }
    false
}

#[tauri::command]
fn stat_entry(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<VaultStatDto, String> {
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    let md = fs::metadata(&path).map_err(|e| format!("Failed to stat: {e}"))?;
    let is_dir = md.is_dir();
    let mtime_ms = md.modified().map(system_time_to_epoch_ms).unwrap_or(0);
    Ok(VaultStatDto {
        path: rel_path.replace('\\', "/").trim_matches('/').to_string(),
        is_dir,
        size_bytes: if is_dir { 0 } else { md.len() },
        mtime_ms,
    })
}

#[tauri::command]
fn walk_vault_files(state: tauri::State<'_, VaultState>) -> Result<Vec<VaultFileInfoDto>, String> {
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;

    fn walk(root: &Path, dir: &Path, prefix: &str, out: &mut Vec<VaultFileInfoDto>) -> Result<(), String> {
        for entry in fs::read_dir(dir).map_err(|e| format!("Failed to list dir: {e}"))? {
            let entry = entry.map_err(|e| format!("Failed to read entry: {e}"))?;
            let name = entry.file_name().to_string_lossy().to_string();
            if name == ".DS_Store" {
                continue;
            }

            let child_path = entry.path();
            let ft = entry.file_type().map_err(|e| format!("Failed to read type: {e}"))?;
            if ft.is_symlink() {
                continue;
            }

            let rel = if prefix.is_empty() { name.clone() } else { format!("{prefix}/{name}") };

            if ft.is_dir() {
                if should_skip_walk_dir(&rel) {
                    continue;
                }
                walk(root, &child_path, &rel, out)?;
                continue;
            }

            if !ft.is_file() {
                continue;
            }

            let md = fs::metadata(&child_path).map_err(|e| format!("Failed to stat file: {e}"))?;
            let size_bytes = md.len();
            let mtime_ms = md.modified().map(system_time_to_epoch_ms).unwrap_or(0);
            let rel_path = to_vault_path(root, &child_path)?;
            out.push(VaultFileInfoDto { path: rel_path, size_bytes, mtime_ms });
        }
        Ok(())
    }

    let mut out: Vec<VaultFileInfoDto> = Vec::new();
    walk(&root, &root, "", &mut out)?;
    Ok(out)
}

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_notification::init())
        .setup(|app| {
            let state = load_persisted_state(app.handle());
            app.manage(PersistedState(Mutex::new(state)));

            let show = MenuItem::with_id(app, "show", "打开", true, None::<&str>)?;
            let hide = MenuItem::with_id(app, "hide", "隐藏", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "退出", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &hide, &quit])?;

            let icon = app
                .default_window_icon()
                .cloned()
                .ok_or_else(|| "Missing default window icon".to_string())?;

            let tray = TrayIconBuilder::new()
                .icon(icon)
                .menu(&menu)
                .show_menu_on_left_click(!cfg!(target_os = "windows"))
                .on_tray_icon_event(|tray, event| {
                    if !cfg!(target_os = "windows") {
                        return;
                    }

                    if let TrayIconEvent::Click { button, button_state, .. } = event {
                        if button != MouseButton::Left || button_state != MouseButtonState::Up {
                            return;
                        }

                        let app = tray.app_handle();
                        if let Some(win) = app.get_webview_window("main") {
                            let visible = win.is_visible().unwrap_or(true);
                            if visible {
                                let _ = win.hide();
                            } else {
                                let _ = win.show();
                                let _ = win.set_focus();
                            }
                        }
                    }
                })
                .on_menu_event(|app, event| match event.id().as_ref() {
                    "show" => {
                        if let Some(win) = app.get_webview_window("main") {
                            let _ = win.show();
                            let _ = win.set_focus();
                        }
                    }
                    "hide" => {
                        if let Some(win) = app.get_webview_window("main") {
                            let _ = win.hide();
                        }
                    }
                    "quit" => {
                        app.exit(0);
                    }
                    _ => {}
                })
                .build(app)?;
            app.manage(TrayHandle(tray));
            Ok(())
        })
        .manage(VaultState(Mutex::new(None)))
        .invoke_handler(tauri::generate_handler![
            set_vault_root,
            get_persisted_state,
            exit_app,
            list_dir,
            read_text_file,
            write_text_file,
            read_draw_document,
            write_draw_document,
            write_bytes_abs,
            write_draw_document_abs,
            read_bytes_abs,
            http_request,
            create_dir,
            create_file,
            stat_entry,
            walk_vault_files,
            rename_entry,
            delete_entry
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
