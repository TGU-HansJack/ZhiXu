# 桌面端（Tauri，本地文件夹库）实施方案

本文目标：在**不做 Web 端**的前提下，为“知序”实现一个 **Tauri 桌面端**，并且桌面端仍然以**本地文件夹 Vault**作为数据源（类似 Android 端当前的本地文件夹库）。

内容结构：
1) 当前 Android 端的主要功能与底层实现（结合仓库现状与关键代码位置）
2) 桌面端 Tauri 的总体架构与分阶段落地方案（含模块拆分、数据流、索引与任务体系、打包与分发）

## 当前落地情况（repo 现状）

- 已有 `apps/zhixu-desktop`（Tauri v2 + Vite + React）。
- 已实现 Phase 1 核心：选择 Vault、本地文件系统读写/新建/重命名/删除、编辑器（含 Markdown 预览）。
- 当前限制：仅展示/编辑 `.md`（附件、索引/任务/日历属于 Phase 2+）。

---

## 1. 当前 Android 端：功能与底层实现（代码导读）

> 结论先说：当前仓库以 `apps/zhixu-android` 为核心，采用 **Jetpack Compose + Kotlin + Android 平台能力（SAF/WorkManager/通知等）**；文件夹 Vault 通过 `Uri/DocumentFile` 访问；任务/搜索/日历依赖本地 `index.sqlite`（由索引仓库维护）；同步包含官方服务与 WebDAV。

### 1.1 模块与工程结构

- Gradle 模块（`settings.gradle.kts`）：
  - `:apps:zhixu-android`：Android 客户端（绝大多数业务）
  - `:apps:zhixu-android-benchmark`：baseline profile/benchmark
  - `:core`：Kotlin/JVM 的少量“纯逻辑”代码（目前主要是任务语法解析）

### 1.2 UI 与交互层（Compose）

- UI 采用 Jetpack Compose（Material3），页面在：
  - `apps/zhixu-android/src/main/java/app/zhixu/ui/screens/`
  - 入口与路由/状态聚合在 `apps/zhixu-android/src/main/java/app/zhixu/ui/ZhixuApp.kt`

典型页面与能力：
- 空间（文件夹树/文档列表）：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/SpaceScreen.kt`
  - 通过 `VaultDrawer` 展示 Vault 目录树与文档条目
- 任务列表：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/TasksScreen.kt`
  - 依赖索引就绪（“索引构建中/未就绪”提示），列表可 toggle 任务
- 日历任务：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/CalendarTasksScreen.kt`
  - 选择日期后从索引取当日到期任务
- 编辑器：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/EditorScreen.kt`
  - 任务行 toggle（例如 `toggleTaskAtCursor()`）与 Markdown 预览
- 设置/同步/通知/关于等：
  - 设置：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/SettingsScreen.kt`
  - 同步：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/SyncScreen.kt`
  - 通知：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/NotificationSettingsScreen.kt`
  - 关于：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/AboutScreen.kt`
  - 番茄设置：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/PomodoroSettingsScreen.kt`
  - UI 设置：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/UiSettingsScreen.kt`

### 1.3 Vault（本地文件夹库）与文件访问

核心仓库：`apps/zhixu-android/src/main/java/app/zhixu/data/VaultRepository.kt`

当前实现特点：
- Vault 根目录使用 `Uri` 表示（Android SAF / DocumentFile）。
- 文档/附件读写通过 repository 封装的方法完成（读文本、写文本、列目录等）。
- Space 页面通过 `VaultDrawer` 访问 repository 列出目录树、打开文档。

与桌面端强相关的“抽象点”：
- Android 侧很多逻辑依赖 `Uri/DocumentFile` 与 content resolver；
- 桌面端需要用 `Path`（本地文件系统）来替换，并保持相同的“vault 相对路径语义”（便于索引与同步策略复用）。

### 1.4 本地索引（index.sqlite）与搜索/任务/日历

索引相关关键类：
- `apps/zhixu-android/src/main/java/app/zhixu/data/VaultIndexDb.kt`
- `apps/zhixu-android/src/main/java/app/zhixu/data/VaultIndexRepository.kt`
- `apps/zhixu-android/src/main/java/app/zhixu/data/VaultIndexUpdater.kt`
- `apps/zhixu-android/src/main/java/app/zhixu/data/DocumentIndex.kt`

对外常用能力（示例）：
- 是否已建立索引：`VaultRepository.hasAnyIndexedDocs()` / `VaultIndexRepository.hasAnyIndexedDocs()`
- 搜索：`VaultRepository.search(...)` / `VaultIndexRepository.search(query)`
- 日历查询：`VaultRepository.getTasksDueOn(day)` / `VaultIndexRepository.getTasksDueOn(day)`

当前 UI 的行为模式：
- 任务页/日历页会先判断索引是否就绪；未就绪时提示并提供“构建索引”入口（任务页）。
- 索引构建在后台空闲时自动触发/或手动触发（见 `VaultIndexUpdater` 的用法，`ZhixuApp.kt` 中创建并管理）。

### 1.5 任务系统：语法、解析、toggle

任务解析“参考实现”（纯逻辑，适合当作跨端标准）：
- `core/src/main/kotlin/app/zhixu/core/tasks/TaskSyntax.kt`
- 单测：`core/src/test/kotlin/app/zhixu/core/tasks/TaskSyntaxTest.kt`

Android 侧任务更新（对文档文本进行局部修改）：
- `apps/zhixu-android/src/main/java/app/zhixu/data/VaultRepository.kt`：`toggleTask(docUri, lineIndex)`
- UI 触发点例如：
  - `apps/zhixu-android/src/main/java/app/zhixu/ui/screens/TasksScreen.kt`（列表中 toggle）
  - `apps/zhixu-android/src/main/java/app/zhixu/ui/screens/EditorScreen.kt`（光标处 toggle）

### 1.6 通知/提醒与后台任务

Android 使用 WorkManager + 通知渠道：
- `apps/zhixu-android/src/main/java/app/zhixu/reminders/TaskReminderWorker.kt`
- `apps/zhixu-android/src/main/java/app/zhixu/reminders/DailyReminderWorker.kt`

这些能力在桌面端对应为：**本地定时器 + 系统通知 + 开机自启（可选）**。

### 1.7 同步：官方服务 & WebDAV

- WebDAV：
  - 配置与 UI：`apps/zhixu-android/src/main/java/app/zhixu/ui/screens/SyncScreen.kt`
  - 客户端：`apps/zhixu-android/src/main/java/app/zhixu/data/WebDavClient.kt`
  - 同步引擎：`apps/zhixu-android/src/main/java/app/zhixu/sync/WebDavSyncEngine.kt`
- 官方同步：
  - 引擎：`apps/zhixu-android/src/main/java/app/zhixu/sync/OfficialVaultSyncEngine.kt`
  - 服务端（Node）：`.server/Server/*`

> 本次桌面端需求明确为“依靠本地文件夹库”，可先把“同步”作为后续阶段；但索引/任务/编辑器都与“文件系统 + 索引”强绑定，属于第一阶段。

---

## 2. 桌面端（Tauri）总体方案（本地文件夹 Vault）

### 2.1 为什么选 Tauri（对当前项目的适配点）

- 你未来需要 Web 端：Tauri 的前端就是 Web 技术栈（Vite/React/Vue/Svelte），桌面端与未来 Web 端可复用 UI 与部分业务逻辑。
- 现阶段只做桌面端：仍可用 Tauri 提供“本地文件系统/系统能力”，不依赖浏览器限制。
- Android 代码很难直接复用到桌面（SAF/WorkManager/AndroidX），因此桌面端需要**重新实现平台层**；Tauri 的 Rust 后端正适合承接这些平台能力。

### 2.2 目标能力清单（桌面端第一阶段建议范围）

第一阶段（MVP，建议 2–4 周）：
1) 选择/记住 Vault 根目录（本地文件夹）
2) 空间页：目录树 + 文档列表（支持新建文件/文件夹、重命名、删除）
3) 编辑器：打开/编辑/保存 Markdown；支持查找；支持预览（前端渲染 Markdown）
4) 任务页：从索引查询任务列表；点击 toggle（写回文件）
5) 日历页：按天查询到期任务
6) 索引：构建/增量更新（文件变更触发）

第二阶段（增强）：
- 全文搜索（FTS）、标签/优先级筛选、最近打开
- 后台提醒（任务提醒、每日提醒）
- 同步（WebDAV、官方）
- OCR 等（可选、后置）

### 2.3 建议的仓库结构（新增内容）

在仓库根新增：
- `apps/zhixu-desktop/`（Tauri 项目）
  - `apps/zhixu-desktop/src-tauri/`（Rust 后端）
  - `apps/zhixu-desktop/src/`（前端）
- `packages/shared/`（可选，TypeScript 共享业务逻辑，比如 TaskSyntax 的 TS 版本）

> 也可以用 `apps/zhixu-tauri` 命名；关键是与 Android 模块隔离。

### 2.4 桌面端分层架构（关键）

**前端（Web UI）**
- 页面与状态管理（Space/Editor/Tasks/Calendar/Settings）
- 只通过一个 `VaultApi` 调用底层能力（避免把文件/索引逻辑散落在 UI）

**Tauri Rust 后端**
1) `vault_fs`：本地文件访问、目录遍历、文件变更监听（watcher）
2) `index`：索引数据库（SQLite）与增量更新
3) `tasks`：任务解析与 toggle（基于“Android 的 TaskSyntax 作为标准”）
4) `search`：全文/字段搜索（可基于 SQLite FTS5 或 tantivy）
5) `app_state`：当前 vault、最近打开、窗口状态、配置持久化

### 2.5 Vault 文件语义（必须先定标准）

桌面端必须建立“跨平台一致”的路径与元数据规则：
- VaultRoot：用户选择的文件夹绝对路径
- VaultPath：Vault 内相对路径（统一用 `/` 作为分隔符，存入 DB）
- 系统目录：建议沿用 Android 侧的 `.zhixu/` 目录
  - `.zhixu/index.sqlite`：索引数据库（可选：也可放到应用数据目录，但放在 vault 内便于迁移/备份）
  - `.zhixu/sync/`：同步日志/状态（未来扩展）

**建议策略**
- 默认：索引文件作为“派生文件”，不参与同步（与 Android 侧“includeIndexSqlite”开关一致）
- 允许高级用户开启“同步索引”（体验更快，但会引入冲突/平台差异风险）

### 2.6 索引数据库（SQLite）方案

推荐 SQLite（与 Android 的 `.zhixu/index.sqlite` 思路一致），最小可用 schema：

- `docs`：文档元信息
  - `id`（PK）
  - `path`（VaultPath, UNIQUE）
  - `title`（可从 front matter/首行推导）
  - `mtime_ms`（用于增量更新）
  - `size`（可选）
- `tasks`：任务条目（面向任务页/日历）
  - `id`（ULID/UUID）
  - `doc_path`（VaultPath）
  - `line_index`（行号）
  - `checked`（bool）
  - `title`（任务文本）
  - `due_date`（YYYY-MM-DD，可为空）
  - `due_time`（可为空）
  - `priority`（可为空）
  - `tags`（可序列化为 JSON/text，或拆表）
- `fts_docs`（可选，全文索引）
  - 使用 SQLite FTS5：存 `path` + `content`（或 `title/content` 分字段）

增量更新策略：
- 启动时扫描：对比 `mtime_ms`，更新变更文档
- 文件监听：捕获 create/modify/delete，更新对应条目
- 安全降级：监听不可靠时（网络盘/权限），允许手动“重建索引”

### 2.7 任务解析与 toggle（与 Android 保持一致）

桌面端必须把任务语法当作“产品契约”，建议以 Android 现有实现为标准：
- 参考：`core/src/main/kotlin/app/zhixu/core/tasks/TaskSyntax.kt`
- 迁移方式建议二选一：
  1) **Rust 重写 TaskSyntax**（推荐，性能好、与索引同一语言）
     - 同时把 `core/src/test/...` 的用例迁移为 Rust 单测，确保行为一致
  2) TypeScript 重写 TaskSyntax（放 `packages/shared`）
     - 索引由 Rust 做时，解析逻辑会分裂（Rust/TS 两份），长期维护成本更高

toggle 的实现建议：
- 按 `doc_path + line_index` 读取文件内容（按行分割）
- 对目标行执行“勾选/取消勾选”文本替换（规则与 Android 一致）
- 写回文件后触发该文档重建索引（或局部更新 tasks 表）

### 2.8 Markdown 预览方案（桌面端）

Android 目前是 WebView + 本地 `android_asset/markdown-preview`。

桌面端建议：
- 直接在前端用 Markdown 渲染库（例如 `markdown-it`/`remark`），并沿用你现有 `markdown-preview` 的 CSS 风格（可复制一份到桌面端前端资源）
- 如果必须保持与 Android 完全一致的渲染：可把现有 `markdown-preview/index.html` 作为前端页面的一部分（iframe/组件），通过 postMessage 传 markdown 与主题变量。

### 2.9 文件树（空间页）实现建议

为了达到 Android “VaultDrawer + 文档列表”的体验，桌面端建议：
- Rust 提供 `list_entries(dir_path)`：返回目录/文件、类型、mtime、大小
- 前端维护“展开状态”（可持久化到 app state）
- 对大目录分页/懒加载（避免一次性递归）

### 2.10 桌面端配置持久化

需要保存：
- 最近 Vault 列表、最后一次打开的 Vault
- UI 偏好（主题/语言等）
- 索引状态（上次扫描时间、上次完成索引版本号等）

建议放在 Tauri 的 app data 目录（`tauri::api::path::app_data_dir`），用 JSON 或 SQLite。

### 2.11 与未来“同步/云”的兼容预留

即便现在不做 Web/同步，也建议：
- Vault 内 `.zhixu/` 目录结构沿用 Android 习惯
- 索引表设计不要写死“仅本地”；`doc_path` + `hash/etag` 字段预留
- 事件日志（可选）：记录文件变更操作，未来用于同步冲突解决

---

## 3. 分阶段落地计划（建议）

### Phase 0：脚手架与基本壳
- 创建 `apps/zhixu-desktop`（Tauri + Vite）
- 前端页面框架：Space/Editor/Tasks/Calendar/Settings 的空壳路由
- Rust command：`select_vault`, `get_vault_info`

### Phase 1：文件系统 + 编辑器 MVP
- Rust：`list_dir`, `read_file`, `write_file`, `create_file`, `create_dir`, `rename`, `delete`
- 前端：空间页（目录树 + 文档列表）+ 编辑器（打开/保存）
- 限制：先只支持 `.md`，附件后置

### Phase 2：索引与任务/日历
- SQLite schema + 索引构建
- 任务解析（Rust）
- API：
  - `index_rebuild()`, `index_status()`
  - `query_tasks(filter...)`, `query_tasks_due_on(date)`
  - `toggle_task(doc_path, line_index)`

### Phase 3：搜索与体验增强
- 全文搜索（FTS5）
- 最近打开/快速切换
- 大文件优化（流式读取、增量渲染）

### Phase 4：提醒与后台
- 系统通知（Windows/macOS/Linux）
- 任务提醒调度（本地定时器）

---

## 4. 风险点与规避

- **任务语法一致性**：必须以 `core/tasks/TaskSyntax.kt` 为标准，迁移单测到桌面端，避免 Android/桌面不一致。
- **文件监听不可靠**：提供手动重建索引；监听只做加速不做强依赖。
- **索引文件位置**：放 vault 内最直观，但可能被用户误删；建议同时支持“放 app data”，并在 UI 给出说明。
- **跨平台路径差异**：DB 内统一使用 `/` 的 VaultPath；对 Windows 路径转换集中在 Rust 层处理。

---

## 5. 你下一步可以做什么（建议的决策点）

为了让桌面端方案可直接开工，需要你确认三件事：
1) 桌面端是否需要“多 Vault 管理”（最近列表）还是只支持单 Vault？
2) `.zhixu/index.sqlite` 放在 Vault 内（默认）还是放在应用数据目录（默认）？
3) 桌面端 Markdown 预览：要“尽量一致”还是“前端 Markdown 渲染即可”？
