# 知序 (Zhixu)

> AI 原生的个人知识与任务管理系统 | 隐私优先

知序是一个「原生优先」的个人知识与任务管理系统，专注于解决**信息管理混乱**与**多端同步困难**两大痛点。采用 Android 原生端优先的策略，同时在底层统一数据格式、协议与语义，为未来 Web / Desktop 复用核心逻辑奠定基础。

## 核心特性

- **Vault 文档管理** - 基于文件夹的本地知识库，兼容 Markdown 生态
- **任务管理** - 内置任务语法，自动补全唯一 ID，轻松追踪待办
- **Markdown 编辑** - 纯文本编辑 + 实时预览，支持 LaTeX 公式、Mermaid 图表
- **多端同步** - 支持 WebDAV / Git 同步协议
- **OCR 识别** - 集成 PaddleOCR v5，支持图片文字提取
- **隐私优先** - 数据完全本地存储，无云端依赖

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17+
- Android SDK (minSdk 24, targetSdk 35)
- NDK (用于 OCR 模块编译)

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/TGU-HansJack/ZhiXu.git
cd ZhiXu

# 构建 Debug 版本
./gradlew :apps:zhixu-android:assembleDebug

# 构建 Release 版本（需要配置签名）
./gradlew :apps:zhixu-android:assembleRelease
```

### 配置签名（Release 构建）

1. 生成您自己的签名密钥：
   ```bash
   keytool -genkey -v -keystore your-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias your-alias
   ```

2. 在 `local.properties` 中配置签名信息：
   ```properties
   RELEASE_STORE_FILE=your-release-key.jks
   RELEASE_STORE_PASSWORD=your-store-password
   RELEASE_KEY_ALIAS=your-alias
   RELEASE_KEY_PASSWORD=your-key-password
   ```

> **安全提示**：请勿将签名密钥 (`.jks` / `.keystore`) 和密码提交到版本控制！

## 项目结构

```
zhixu/
├── apps/
│   ├── zhixu-android/          # Android 主应用
│   └── zhixu-android-benchmark/ # 性能基准测试
├── core/                        # 跨平台核心库
│   ├── model/                   # 数据模型
│   ├── parser/                  # Markdown/任务解析器
│   ├── index/                   # 全文索引
│   ├── sync/                    # 同步协议实现
│   ├── tasks/                   # 任务管理逻辑
│   └── ai/                      # AI 能力集成
└── docs/                        # 项目文档
```

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| UI | Kotlin + Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| Markdown | Markwon (渲染) + markdown-it (预览) |
| 数学公式 | KaTeX |
| 图表 | Mermaid |
| OCR | PaddleOCR v5 (C++ Native) |
| JS 引擎 | J2V8 |
| 同步 | OkHttp + JGit |
| 存储 | DataStore + 文件系统 |

## 文档

项目设计文档位于 `docs/` 目录：

- 架构方案 - `docs/plan-a-native-first.md`
- Vault 规范 - `docs/vault-spec.md`
- 任务语法 - `docs/task-syntax.md`
- 编辑器设计 - `docs/editor-spec.md`
- 同步协议 - `docs/sync-protocol.md`

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 提交 Pull Request

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 开源许可证。

- **客户端** (Android / Desktop) - Apache-2.0（本仓库）
- **云服务** (同步托管 / AI 代理) - 独立闭源服务

## 致谢

感谢以下开源项目：

- [Markwon](https://github.com/noties/Markwon) - Android Markdown 渲染
- [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) - OCR 引擎
- [KaTeX](https://katex.org/) - 数学公式渲染
- [Mermaid](https://mermaid.js.org/) - 图表渲染

---

**知序** - 让知识有序流动
