# Zhixu（知序）

Zhixu 是一个「原生优先」的个人知识与任务系统，优先解决两件事：**信息管理混乱**与**多端同步困难**。当前策略是先把 Android 原生端做稳，同时在底层统一数据格式、协议与语义，确保未来 Web / Desktop 能复用“核心”而不是复用 UI。

产品定位：**AI 原生的个人知识与任务管理系统**（隐私优先），并逐步强化会议场景能力（OCR/录音/转写/行动项）。

## 官方方案（方案 A：原生优先·终局兼容）

- 方案总览：`docs/plan-a-native-first.md`
- Vault 规范：`docs/vault-spec.md`
- 任务语法：`docs/task-syntax.md`
- 编辑器方案：`docs/editor-spec.md`
- 同步协议：`docs/sync-protocol.md`
- WebDAV MVP 计划：`docs/webdav-mvp-plan.md`
- 路线图：`docs/roadmap.md`
- v0.1 执行计划：`docs/v0.1-execution-plan.md`
- v0.2 执行计划：`docs/v0.2-execution-plan.md`

## v0.1 MVP（已开始落地）

- Vault：选择一个文件夹作为 `VaultRoot/`，应用会自动确保 `docs/`、`attachments/`、`.zhixu/` 结构存在，并在 `docs/` 下创建示例 `Inbox.md`
- Docs：支持 Markdown 文档列表/新建/删除，点击进入编辑
- Editor：基础纯文本编辑 + 一键 Preview（Markdown 渲染）
- Tasks：保存时自动为任务行补全缺失的 `@id(<ulid>)`（见 `docs/task-syntax.md`）

## 开发与运行

```bash
cd ZhiXu
./gradlew :apps:zhixu-android:assembleDebug
```

## 许可与商业化（开源 + 收费落地）

- 客户端（Android / Desktop）：Apache-2.0（本仓库）
- 云服务（同步托管 / 转写 / AI 代理）：独立闭源服务或独立仓库
- 第三方项目参考（如 flymd）：不复制代码，仅参考协议/数据设计

## 目录规划（Core-first）

> 代码尚未落地前先对齐边界：Android UI 原生实现；跨端复用走 `core`。

```
zhixu/
├─ apps/
│  └─ zhixu-android/
├─ core/
│  ├─ model/
│  ├─ parser/
│  ├─ index/
│  ├─ sync/
│  ├─ tasks/
│  └─ ai/
└─ docs/
```
