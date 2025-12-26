# Vault 规范（可迁移、可导出、可重建）

## 目标

- 用户永远可以迁移：数据不被应用锁死
- 索引可重建：删除索引库不等于丢数据
- 同步以文件为单位：可靠优先

## 目录结构

```
VaultRoot/
├─ docs/                 # 全部 Markdown 文档
├─ attachments/          # 图片 / 音频 / 文件
└─ .zhixu/
   ├─ index.sqlite       # 索引库（可删除重建）
   ├─ settings.json      # 库级设置
   ├─ sync/              # 同步元数据 / 冲突日志
   └─ exports/           # 导出产物（可选）
```

## 导出规则

- MD 导出：`docs/ + attachments/`
- 整体导出：`VaultRoot.zip`（可选 AES 加密）

## 索引与“真数据”的边界

- 真数据：`docs/*.md` 与 `attachments/*`
- 非真数据（可重建）：`.zhixu/index.sqlite` 与 `.zhixu/sync/*`

## 文件命名与稳定性建议

- 文档文件名：可由用户自由命名（不强制 UUID 文件名）
- 任务身份：通过行内 `@id(...)` 保持跨端稳定（见 `docs/task-syntax.md`）
