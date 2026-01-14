# 待办（todo-zhixu）

桌面端 ToDo 插件：聚合 Vault 内所有 Markdown 任务（`- [ ]` / `- [x]`），提供任务列表与快速新增入口。

## 功能

- 主侧栏：添加任务、任务列表
- 编辑区占位：日历、四象限（占位展示）
- 任务语法：兼容安卓端 `TaskSyntax`（`@id()` / `@due()` / `@done()` / `@tag()` / `@priority()` 等）

## 配置

编辑 `config.json`：

- `inboxPath`：新增任务默认写入的 Markdown 文件路径（相对 Vault 根目录）
- `ignorePrefixes`：扫描任务时忽略的路径前缀（默认忽略 `.zhixu/`）

## 说明

- 插件会在 `.zhixu/plugins/todo-zhixu/tasks-cache.json` 生成索引缓存（用于加速二次加载）。

