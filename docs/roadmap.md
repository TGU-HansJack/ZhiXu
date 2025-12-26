# 路线图（Android 原生起步）

> 目标：每个版本都能“独立交付可用价值”，优先解决信息管理与多端同步；同时不锁死未来的跨端路线。

## v0.1（MVP：文档 + 待办 + 编辑器）

- Vault：SAF 目录选择 + URI 权限持久化
- 文档管理：Markdown 创建/编辑/删除（文件直读写）
- 编辑器：源码模式 + 基础语法高亮 + 全屏预览（见 `docs/editor-spec.md`）
- 任务管理：最小待办（先支持 `- [ ]/- [x]`）+ 基础提醒
- 同步：v0.1 预留设置入口；协议先对齐（见 `docs/sync-protocol.md`）
- 执行清单：见 `docs/v0.1-execution-plan.md`

## v0.2（效率：索引与任务闭环）

- SQLite 索引：documents/tasks/attachments + FTS5（或同等能力）
- 搜索：标题/正文/任务聚合检索
- 编辑器：分屏预览 + 预览中任务交互（见 `docs/editor-spec.md`）
- 任务视图：Today / Upcoming（以及基础筛选）
- 提醒加强：AlarmManager + WorkManager（重复任务可先简化）
- WebDAV MVP：全量同步整个 Vault + 最小日志/失败可恢复（见 `docs/sync-protocol.md`）
- 执行清单：`docs/v0.2-execution-plan.md`

## v0.3（可靠：同步增强 + 可选 Git）

- WebDAV：增量与冲突处理完善（冲突中心 + 详细日志）
- 同步策略：可配置是否同步 `.zhixu/index.sqlite`
- Git（可选）：把 Vault 当仓库进行同步（偏高级用户，后置实现）
- 编辑器与任务管理（可并行推进）：文档任务视图（从文档提取任务并可回跳定位，见 `docs/editor-spec.md`）

## v0.4+（会议场景与差异化）

- OCR：附件图片转文本，可搜索
- 录音：前台服务 + 产物写入 `attachments/audio/`
- 云端转写：付费能力（独立服务）
- AI 行动项：从转写/会议文档生成任务行（写回 Markdown）
