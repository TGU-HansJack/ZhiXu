#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Component;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use tauri::Manager;

struct VaultState(Mutex<Option<PathBuf>>);

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PersistedAppState {
    last_vault: Option<String>,
    recent_vaults: Vec<String>,
}

struct PersistedState(Mutex<PersistedAppState>);

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct VaultEntryDto {
    path: String,
    name: String,
    is_dir: bool,
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

    // For non-existing targets (e.g. new file), validate parent.
    let parent = candidate.parent().unwrap_or(root);
    let p = parent.canonicalize().map_err(|e| format!("Invalid path: {e}"))?;
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
    if !(lower.ends_with(".md") || lower.ends_with(".zhixu")) {
        return Err("仅支持 .md 或 .zhixu 文本文件".to_string());
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
fn create_dir(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<(), String> {
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    fs::create_dir_all(&path).map_err(|e| format!("Failed to create dir: {e}"))
}

#[tauri::command]
fn create_file(rel_path: String, state: tauri::State<'_, VaultState>) -> Result<(), String> {
    ensure_text_path(&rel_path)?;
    let root = state.0.lock().unwrap().clone().ok_or_else(|| "No vault selected".to_string())?;
    let path = resolve_in_vault(&root, &rel_path)?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create parent dirs: {e}"))?;
    }
    if path.exists() {
        return Err("File already exists".to_string());
    }
    fs::write(&path, "").map_err(|e| format!("Failed to create file: {e}"))
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

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let state = load_persisted_state(app.handle());
            app.manage(PersistedState(Mutex::new(state)));
            Ok(())
        })
        .manage(VaultState(Mutex::new(None)))
        .invoke_handler(tauri::generate_handler![
            set_vault_root,
            get_persisted_state,
            list_dir,
            read_text_file,
            write_text_file,
            create_dir,
            create_file,
            rename_entry,
            delete_entry
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
