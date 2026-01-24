# 待办列表（todo-zhixu-mobile）

用于 Android 端「可扩展列表」的待办插件：扫描 Vault 内的 Markdown 任务（`- [ ]` / `- [x]`），并提供待办列表与勾选切换。

## 使用

1. 在手机端打开：设置 → 创意工坊
2. 安装并启用 `todo-zhixu-mobile`
3. 回到首页 → 「可扩展列表」→ 「待办列表」

## 配置（config.json）

- `ignorePrefixes`：扫描时忽略的路径前缀（相对 Vault 根目录）
- `enableCache`：是否启用缓存（默认 `true`，写入 `.zhixu/plugins/todo-zhixu-mobile/tasks-cache.json`）
- `maxFiles`：最多扫描文件数
- `maxTasks`：最多展示任务数

## 返回数据协议（供宿主渲染）

插件 action `page` 返回：

```json
{
  "ok": true,
  "page": {
    "title": "待办列表",
    "items": [
      {
        "title": "任务标题",
        "subtitle": "文件名.md",
        "checked": false,
        "docUri": "content://...",
        "lineIndex": 12,
        "toggleActionId": "toggleTask"
      }
    ]
  }
}
```

