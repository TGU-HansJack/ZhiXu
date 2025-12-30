# Zhixu 插件开发指南（Android）

Zhixu 的插件以“文件夹形式”安装到当前 Vault 的 `.zhixu/plugins/<pluginId>/` 下。插件由 `manifest.json` 描述，并通过 JS 入口文件暴露可执行的 `actions`。

## 1. 安装方式

在应用内进入：`设置 -> 创意工坊`：

- **Git 安装**：填写 Git 仓库地址（仓库根目录必须包含 `manifest.json`）。
- **本地安装**：选择一个包含 `manifest.json` 的本地文件夹。

安装后可在列表里启用/停用插件；点 **Settings** 可编辑该插件的 `config.json`。

## 2. 目录结构

插件最小结构示例：

```
my-plugin/
  manifest.json
  main.js
  README.md            # 可选，用于“查看详情”
  config.json          # 可选，默认可由应用创建/编辑
```

## 3. manifest.json 规范

必须字段：

- `id`：插件 ID（同时作为安装目录名），建议使用小写、短横线，如 `typecho-xmlrpc-publisher`。

推荐字段：

- `name`：插件名称
- `version`：版本号
- `description`：简介
- `entry`：JS 入口文件名（默认 `main.js`；如果写成 `foo` 会尝试 `foo` 和 `foo.js`）
- `actions`：动作列表（用于编辑器浮动按钮）

`actions[]` 字段：

- `id`：动作 ID（JS 中对应函数名）
- `label`：按钮文案
- `icon`：图标名（当前内置简单映射：`cloud_upload/upload/publish` -> 上传图标，其它 -> 插件图标）
- `place`：动作位置（目前支持 `editor_fab`，为空也视作 `editor_fab`）
- `ringIndex`：在圆形菜单里的分组/层级（整数）

示例：

```json
{
  "id": "hello-plugin",
  "name": "Hello Plugin",
  "version": "0.1.0",
  "description": "A demo plugin",
  "entry": "main.js",
  "actions": [
    { "id": "hello", "label": "Say Hello", "icon": "publish", "place": "editor_fab", "ringIndex": 0 }
  ]
}
```

## 4. JS 入口与 actions

入口文件需要以 CommonJS 形式导出：

```js
// main.js
function hello(context) {
  api.log('hello called');
  return { ok: true, message: 'Hello from plugin!' };
}

module.exports = {
  actions: {
    hello,
  },
};
```

当用户在编辑器里触发动作时，Zhixu 会调用：`module.exports.actions[actionId](context)`。

## 5. context（上下文）

传入 JS 的 `context` 结构：

- `context.note`
  - `docUri`：当前文档 Uri（字符串）
  - `title`：当前标题（字符串）
  - `fileName`：当前文件名（字符串）
  - `text`：完整文本（字符串）
- `context.config`
  - 插件的 `config.json`（对象；不存在则为空对象）
- `context.plugin`
  - `id` / `name` / `version`
- `context.app`
  - `platform`：`android`
  - `versionName`：应用版本号

## 6. api（内置能力）

当前提供：

- `api.log(any)`：写入日志（Logcat tag：`ZhixuPlugin`）。
- `api.http(method, url, body?, contentType?)`：同步 HTTP 请求，返回响应体字符串；非 2xx 会抛出异常。

示例（发布类插件常用）：

```js
function ping(ctx) {
  const text = api.http('GET', 'https://example.com/');
  return { ok: true, message: 'len=' + text.length };
}
```

## 7. 返回值（动作执行结果）

动作可返回：

- `string`：将作为提示信息展示
- `object`：可包含以下字段：
  - `ok`：是否成功（布尔）
  - `message`：提示信息（字符串）
  - `setText`：如果提供，Zhixu 会把当前文档内容替换为该文本并保存（字符串）

示例：追加一行并保存：

```js
function appendTimestamp(ctx) {
  const next = ctx.note.text + '\\n\\n' + 'updatedAt: ' + new Date().toISOString() + '\\n';
  return { ok: true, message: 'Updated', setText: next };
}
```

---

如果你希望插件支持更多能力（例如：读写附件、弹窗表单配置、异步网络等），可以提需求或提交 PR 扩展 `api`。 

