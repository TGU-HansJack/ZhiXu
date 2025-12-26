# WebDAV 同步协议（可靠优先）

## 同步范围

- 同步整个 `VaultRoot/`（包含 `.zhixu/`，但允许策略性忽略可重建文件）
- 建议默认同步：`docs/`、`attachments/`、`.zhixu/settings.json`
- 可选不同步：`.zhixu/index.sqlite`（可重建）

## 变更检测（文件级）

- 以文件为单位对比（不是行级 diff）
- 推荐判定字段：`sha256 + mtime + size`

## 冲突处理（可追溯）

- 本地保留原文件
- 远端或合并失败时生成：`conflict <timestamp> <name>.md`
- 写入冲突日志：`.zhixu/sync/conflicts.jsonl`

## 同步流程（建议实现）

1. 拉取远端清单（path, size, mtime, hash）
2. 本地扫描生成清单
3. 计算需要 upload/download 的集合
4. 执行传输（失败可重试）
5. 生成冲突（不做“聪明合并”）
6. 同步完成后触发索引重建/增量更新

落地计划：
- WebDAV MVP（v0.2）：`docs/webdav-mvp-plan.md`
