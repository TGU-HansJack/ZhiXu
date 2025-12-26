# WebDAV MVP 落地计划（v0.2）

目标：按 `docs/sync-protocol.md` 落地**可靠优先**的 WebDAV 全量同步，做到“能用、可恢复、可追溯”。

关联规范：
- 协议：`docs/sync-protocol.md`
- Vault：`docs/vault-spec.md`

## 1) v0.2 同步范围（默认策略）

默认同步（真数据 + 必要配置）
- `docs/`
- `attachments/`
- `.zhixu/settings.json`
- `.zhixu/sync/`（日志/冲突记录）

可配置不同步（派生数据）
- `.zhixu/index.sqlite`

## 2) 最小配置（Settings）

必须有
- Server URL（WebDAV base）
- Username / Password（或 app password）
- Remote folder（默认 `ZhixuVault/`）
- Enable/Disable

可后置
- 同步频率（手动/定时）
- 仅 Wi‑Fi
- 是否同步 `.zhixu/index.sqlite`

## 3) 数据模型（建议）

清单（manifest）
- 本地扫描生成：`path, size, mtime, sha256`
- 远端拉取生成：同字段（若服务端不支持 hash，可先用 `etag`/`mtime+size` 近似，但最终仍建议补 sha256）

日志（jsonl）
- `.zhixu/sync/log.jsonl`：start/end、upload/download 数量、失败原因（可包含 request id）
- `.zhixu/sync/conflicts.jsonl`：冲突文件路径、来源、时间戳

## 4) 同步流程（v0.2 全量版）

1. 校验本地 Vault 结构（确保 `docs/attachments/.zhixu` 存在）
2. 拉取远端清单（递归列目录；必要时创建远端根目录）
3. 本地扫描生成清单
4. 计算差集：
   - 远端缺失 → upload
   - 本地缺失 → download
   - 同路径都存在但指纹不同 → conflict（不做合并）
5. 执行传输（断点/重试）：
   - 上传：PUT
   - 下载：GET
6. 冲突处理（只在“同路径不同内容”时）：
   - 本地保留原文件
   - 将远端版本下载为 `conflict <timestamp> <name>.md`（或附件同理）
   - 记录 conflicts.jsonl
7. 同步完成后：
   - 触发索引重建/增量更新（如果不同步 index.sqlite）

## 5) 失败可恢复（必须实现）

- 传输失败不删除任何一端“唯一副本”
- 每次同步写入 start/end 日志，end 记录是否成功及错误
- 对单文件失败可跳过并继续，最终返回汇总（失败列表）

## 6) 验收用例（DoD）

- A→B 迁移：设备 A 新建/编辑/删除多份文档 + 附件，B 能完整同步结果。
- 网络中断：同步过程中断网/杀进程，重新发起同步能继续并最终一致。
- 冲突：A/B 对同一文件做不同修改后同步，不丢任一版本，并生成 conflict 文件 + 记录日志。

