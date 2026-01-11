use serde::{Deserialize, Serialize};
use std::io::{Cursor, Read, Write};

use zip::write::FileOptions;
use zip::{CompressionMethod, ZipArchive, ZipWriter};

pub const MIME_TYPE: &str = "application/zhixu-drawing";
pub const EXTENSION: &str = ".zhixu";

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DrawMetaDto {
    pub format_version: u32,
    pub created_at_ms: i64,
    pub modified_at_ms: i64,
    pub page_order: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DrawDocumentDto {
    pub meta: DrawMetaDto,
    pub pages: Vec<DrawPageDto>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DrawPageDto {
    pub id: String,
    pub width: f32,
    pub height: f32,
    pub background_color_argb: i32,
    pub elements: Vec<DrawElementDto>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum DrawElementDto {
    Stroke {
        id: String,
        tool: String,
        #[serde(rename = "colorArgb")]
        color_argb: i32,
        width: f32,
        alpha: f32,
        points: Vec<[f32; 2]>,
    },
    Shape {
        id: String,
        shape: String,
        #[serde(rename = "colorArgb")]
        color_argb: i32,
        width: f32,
        alpha: f32,
        start: [f32; 2],
        end: [f32; 2],
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct MetaJson {
    format: String,
    format_version: u32,
    created_at_ms: i64,
    modified_at_ms: i64,
    pages: Vec<MetaJsonPageRef>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct MetaJsonPageRef {
    file: String,
}

pub fn blank_document(now_ms: i64) -> DrawDocumentDto {
    DrawDocumentDto {
        meta: DrawMetaDto {
            format_version: 1,
            created_at_ms: now_ms,
            modified_at_ms: now_ms,
            page_order: vec![page_file_name(0)],
        },
        pages: vec![DrawPageDto {
            id: "page_001".to_string(),
            width: 595.0,
            height: 842.0,
            background_color_argb: -1,
            elements: Vec::new(),
        }],
    }
}

pub fn decode(bytes: &[u8]) -> Result<DrawDocumentDto, String> {
    if bytes.is_empty() {
        return Err("Empty file".to_string());
    }

    let mut zip = ZipArchive::new(Cursor::new(bytes)).map_err(|e| format!("Invalid zip: {e}"))?;

    let mut meta_bytes = Vec::new();
    {
        let mut meta_file = zip.by_name("meta.json").map_err(|_| "Invalid drawing file: missing meta.json".to_string())?;
        meta_file
            .read_to_end(&mut meta_bytes)
            .map_err(|e| format!("Failed to read meta.json: {e}"))?;
    }

    let meta_value: serde_json::Value =
        serde_json::from_slice(&meta_bytes).map_err(|e| format!("Invalid meta.json: {e}"))?;
    let format_version = meta_value
        .get("formatVersion")
        .and_then(|v| v.as_u64())
        .unwrap_or(1)
        .max(1) as u32;
    let created_at_ms = meta_value
        .get("createdAtMs")
        .and_then(|v| v.as_i64())
        .unwrap_or_else(|| now_ms());
    let modified_at_ms = meta_value
        .get("modifiedAtMs")
        .and_then(|v| v.as_i64())
        .unwrap_or(created_at_ms);

    let pages_arr = meta_value.get("pages").and_then(|v| v.as_array()).cloned().unwrap_or_default();
    let mut page_order: Vec<String> = Vec::with_capacity(pages_arr.len());
    for item in pages_arr {
        let file = item.get("file").and_then(|v| v.as_str()).unwrap_or("").trim().to_string();
        if !file.is_empty() {
            page_order.push(file);
        }
    }
    if page_order.is_empty() {
        return Err("No pages found".to_string());
    }

    let mut pages: Vec<DrawPageDto> = Vec::with_capacity(page_order.len());
    for file in &page_order {
        let mut page_bytes = Vec::new();
        let mut page_file = zip.by_name(file).map_err(|_| format!("Missing page file: {file}"))?;
        page_file
            .read_to_end(&mut page_bytes)
            .map_err(|e| format!("Failed to read {file}: {e}"))?;
        pages.push(parse_page(&page_bytes, file)?);
    }
    if pages.is_empty() {
        return Err("No pages found".to_string());
    }

    Ok(DrawDocumentDto {
        meta: DrawMetaDto {
            format_version,
            created_at_ms,
            modified_at_ms,
            page_order,
        },
        pages,
    })
}

pub fn encode(document: &DrawDocumentDto) -> Result<Vec<u8>, String> {
    let mut out = Cursor::new(Vec::<u8>::new());
    let mut writer = ZipWriter::new(&mut out);

    let stored = FileOptions::<()>::default().compression_method(CompressionMethod::Stored);
    let deflated = FileOptions::<()>::default().compression_method(CompressionMethod::Deflated);

    writer
        .start_file("mimetype", stored)
        .map_err(|e| format!("Zip error: {e}"))?;
    writer
        .write_all(MIME_TYPE.as_bytes())
        .map_err(|e| format!("Zip error: {e}"))?;

    writer.add_directory("META-INF/", deflated).map_err(|e| format!("Zip error: {e}"))?;
    writer
        .start_file("META-INF/version", deflated)
        .map_err(|e| format!("Zip error: {e}"))?;
    writer
        .write_all(b"current=1\nmin=1\n")
        .map_err(|e| format!("Zip error: {e}"))?;

    writer.add_directory("pages/", deflated).map_err(|e| format!("Zip error: {e}"))?;
    writer.add_directory("assets/", deflated).map_err(|e| format!("Zip error: {e}"))?;
    writer.add_directory("assets/images/", deflated).map_err(|e| format!("Zip error: {e}"))?;
    writer.add_directory("assets/pdf/", deflated).map_err(|e| format!("Zip error: {e}"))?;

    let page_files: Vec<String> = document.pages.iter().enumerate().map(|(i, _)| page_file_name(i)).collect();
    let meta_json = MetaJson {
        format: "zhixud".to_string(),
        format_version: document.meta.format_version.max(1),
        created_at_ms: document.meta.created_at_ms,
        modified_at_ms: document.meta.modified_at_ms,
        pages: page_files
            .iter()
            .map(|file| MetaJsonPageRef { file: file.clone() })
            .collect(),
    };
    let meta_bytes = serde_json::to_vec(&meta_json).map_err(|e| format!("Failed to encode meta.json: {e}"))?;

    writer
        .start_file("meta.json", deflated)
        .map_err(|e| format!("Zip error: {e}"))?;
    writer
        .write_all(&meta_bytes)
        .map_err(|e| format!("Zip error: {e}"))?;

    for (index, page) in document.pages.iter().enumerate() {
        let file = page_file_name(index);
        let page_bytes = serde_json::to_vec(page).map_err(|e| format!("Failed to encode {file}: {e}"))?;
        writer
            .start_file(&file, deflated)
            .map_err(|e| format!("Zip error: {e}"))?;
        writer
            .write_all(&page_bytes)
            .map_err(|e| format!("Zip error: {e}"))?;
    }

    writer.finish().map_err(|e| format!("Zip error: {e}"))?;
    Ok(out.into_inner())
}

fn now_ms() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn page_file_name(index: usize) -> String {
    format!("pages/page_{:03}.json", index + 1)
}

fn parse_page(bytes: &[u8], _file: &str) -> Result<DrawPageDto, String> {
    let v: serde_json::Value = serde_json::from_slice(bytes).map_err(|e| format!("Invalid page json: {e}"))?;
    let obj = v.as_object().ok_or_else(|| "Invalid page json: expected object".to_string())?;

    let id = obj.get("id").and_then(|x| x.as_str()).unwrap_or("page").trim().to_string();
    let width = obj.get("width").and_then(|x| x.as_f64()).unwrap_or(595.0) as f32;
    let height = obj.get("height").and_then(|x| x.as_f64()).unwrap_or(842.0) as f32;
    let bg = obj
        .get("backgroundColorArgb")
        .and_then(|x| x.as_i64())
        .unwrap_or(-1) as i32;

    let mut page = DrawPageDto {
        id: if id.is_empty() { "page".to_string() } else { id },
        width: if width > 1.0 { width } else { 595.0 },
        height: if height > 1.0 { height } else { 842.0 },
        background_color_argb: bg,
        elements: Vec::new(),
    };

    let els = obj.get("elements").and_then(|x| x.as_array()).cloned().unwrap_or_default();
    for (i, el) in els.iter().enumerate() {
        let el_obj = match el.as_object() {
            Some(o) => o,
            None => continue,
        };
        let ty = el_obj.get("type").and_then(|x| x.as_str()).unwrap_or("").trim();
        let id = el_obj
            .get("id")
            .and_then(|x| x.as_str())
            .unwrap_or("")
            .trim()
            .to_string();
        let id = if id.is_empty() { format!("el_{i}") } else { id };

        match ty {
            "stroke" => {
                let tool_raw = el_obj.get("tool").and_then(|x| x.as_str()).unwrap_or("").to_ascii_lowercase();
                let tool = match tool_raw.as_str() {
                    "highlighter" => "highlighter",
                    "shape" => "shape",
                    _ => "pen",
                }
                .to_string();

                let color = el_obj.get("colorArgb").and_then(|x| x.as_i64()).unwrap_or(0xFF000000u32 as i64) as i32;
                let width = el_obj.get("width").and_then(|x| x.as_f64()).unwrap_or(3.0) as f32;
                let alpha = el_obj.get("alpha").and_then(|x| x.as_f64()).unwrap_or(1.0) as f32;
                let points_arr = el_obj.get("points").and_then(|x| x.as_array()).cloned().unwrap_or_default();
                let mut points: Vec<[f32; 2]> = Vec::with_capacity(points_arr.len());
                for p in points_arr {
                    let pair = match p.as_array() {
                        Some(a) if a.len() >= 2 => a,
                        _ => continue,
                    };
                    let x = pair[0].as_f64().unwrap_or(0.0) as f32;
                    let y = pair[1].as_f64().unwrap_or(0.0) as f32;
                    points.push([x, y]);
                }
                page.elements.push(DrawElementDto::Stroke {
                    id,
                    tool,
                    color_argb: color,
                    width: width.max(0.2),
                    alpha: alpha.clamp(0.0, 1.0),
                    points,
                });
            }
            "shape" => {
                let shape_raw = el_obj
                    .get("shape")
                    .and_then(|x| x.as_str())
                    .unwrap_or("")
                    .to_ascii_lowercase();
                let shape = match shape_raw.as_str() {
                    "rectangle" => "rectangle",
                    "ellipse" => "ellipse",
                    _ => "line",
                }
                .to_string();

                let color = el_obj.get("colorArgb").and_then(|x| x.as_i64()).unwrap_or(0xFF000000u32 as i64) as i32;
                let width = el_obj.get("width").and_then(|x| x.as_f64()).unwrap_or(3.0) as f32;
                let alpha = el_obj.get("alpha").and_then(|x| x.as_f64()).unwrap_or(1.0) as f32;

                let start = parse_pair(el_obj.get("start"));
                let end = parse_pair(el_obj.get("end"));

                page.elements.push(DrawElementDto::Shape {
                    id,
                    shape,
                    color_argb: color,
                    width: width.max(0.2),
                    alpha: alpha.clamp(0.0, 1.0),
                    start,
                    end,
                });
            }
            _ => {}
        }
    }

    Ok(page)
}

fn parse_pair(v: Option<&serde_json::Value>) -> [f32; 2] {
    let Some(arr) = v.and_then(|x| x.as_array()) else {
        return [0.0, 0.0];
    };
    if arr.len() < 2 {
        return [0.0, 0.0];
    }
    let x = arr[0].as_f64().unwrap_or(0.0) as f32;
    let y = arr[1].as_f64().unwrap_or(0.0) as f32;
    [x, y]
}
