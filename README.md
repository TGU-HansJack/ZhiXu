# 知序（Zhixu）

AI 原生的个人知识与任务管理系统（隐私优先，Vault 本地文件夹）。

- 官网：`https://zhixu.app`
- QQ 群：`892430777`

## 现状 / 进度

- Android：当前主力开发与可用版本（Jetpack Compose + SAF）
- 同步：官方服务 / WebDAV（可选启用）
- 插件：JS 插件（J2V8，实验性）
- Desktop：Tauri + React（实验中，见 `docs/desktop-tauri-plan.md` 与 `apps/zhixu-desktop/`）
- Server：Node.js + MySQL 示例（见 `.server/Server/`）

## 核心功能

- Vault：基于文件夹的本地知识库（Markdown 为主）
- 编辑器：CodeMirror（WebView）+ 源码/实时预览模式，支持表格/任务列表/代码高亮等
- 预览：KaTeX（公式）、Mermaid（图表）、PDF/图片预览、长图导出
- 任务：任务语法 + 完成统计（本地）
- OCR：集成 PaddleOCR v5（本地识别，可从图片生成文本/待办）
- 番茄钟：后台服务与通知
- 插件：从本地文件夹 / 官方列表 / Git 仓库安装（可选启用）

## 数据与安全说明

- 数据默认存放在本地 Vault；同步仅在你启用后才会产生网络访问。
- Android 内的 Markdown/PDF/编辑器 WebView 资源来自本地 `assets/`（无 CDN 远程脚本）。
- 插件是可执行代码：插件可读取当前文档内容并可通过 `api.http()` 发起网络请求；请仅安装信任来源的插件。

## 快速开始（Android）

### 环境要求

- Android Studio（推荐使用最新稳定版）
- JDK 17+
- Android SDK（`minSdk 26`，`targetSdk 35`）
- NDK + CMake（OCR 原生模块）

### 构建

```bash
cd ZhiXu

# Debug
./gradlew :apps:zhixu-android:assembleDebug

# Release（需要签名）
./gradlew :apps:zhixu-android:assembleRelease
```

> Windows 可使用 `gradlew.bat`。

### Release 签名（示例）

1) 生成 keystore：

```bash
keytool -genkey -v -keystore your-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias your-alias
```

2) 在 `local.properties` 配置：

```properties
RELEASE_STORE_FILE=your-release-key.jks
RELEASE_STORE_PASSWORD=your-store-password
RELEASE_KEY_ALIAS=your-alias
RELEASE_KEY_PASSWORD=your-key-password
```

## 快速开始（Server）

位于 `.server/Server/`，提供账号/计划与 Vault 同步接口的示例实现。

```bash
cd ZhiXu/.server/Server
cp .env.example .env
docker compose up --build
```

默认监听 `http://localhost:3001`。

## 快速开始（Desktop，实验性）

```bash
cd ZhiXu/apps/zhixu-desktop
npm i
npm run tauri dev
```

> 需要 Rust 工具链与 Tauri 环境依赖（详见 Tauri 官方文档）。

## 文档

- Desktop 规划：`docs/desktop-tauri-plan.md`
- 官网静态站点：`.web/zhixu.app/`

## 项目结构（主要）

```
ZhiXu/
├── apps/
│   ├── zhixu-android/            # Android 客户端
│   ├── zhixu-android-benchmark/  # Baseline Profile / Macrobenchmark
│   └── zhixu-desktop/            # Desktop（Tauri，实验中）
├── core/                         # 共享核心（tasks / ocr / ai 等）
├── docs/                         # 设计/计划文档
├── .server/Server/               # 服务端示例（账号/同步）
└── .web/zhixu.app/               # 官网静态站点
```

## 许可证

- 本仓库代码：`GPL-3.0`（见 `LICENSE`）
- Android 内置第三方声明：`apps/zhixu-android/src/main/assets/third_party_notices/`

## 贡献

欢迎提 Issue / PR。涉及安全问题请优先提交最小复现与影响说明。
