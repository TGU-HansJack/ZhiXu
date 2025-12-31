# Typecho XML-RPC 发布器（Zhixu / J2V8）

通过 XML-RPC（MetaWeblog API）将当前笔记发布到 Typecho 博客。

## 配置（Workshop -> Settings）

编辑 `config.json`：

- `endpoint`：Typecho XML-RPC 地址（通常类似 `https://your.site/action/xmlrpc`）
- `username` / `password`：账号密码
- `blogId`：默认 `0`
- `useFrontmatter`：是否优先使用 frontmatter（如 `title`）作为发布字段
- `useCurrentTime`：发布时是否强制使用当前时间（否则使用 frontmatter 的 `dateCreated`；不存在则用当前时间）
- `publishTimeOffsetHours`：发布时对时间做偏移（小时）
- `syncTimeOffsetHours`：从服务器同步时间时的偏移（小时）
- `managePostsCount`：最近文章列表拉取数量（对应 `metaWeblog.getRecentPosts` 的 `count`）
- `defaultCategories`：当笔记 frontmatter 未填写分类时使用的默认分类（数组，如 `["a","b"]`）；为空则要求在笔记中填写
- `frontmatterKeys`：映射到你笔记 frontmatter 的字段名
  - `title`：标题（默认 `title`；`useFrontmatter=true` 时会回写）
  - `cid`：文章 ID（默认 `typecho_cid`）
  - `slug`：文章 slug（默认 `slug`；首次发布如为空会写入 `cid`）
  - `tags`：标签（默认 `tags`，支持 `a,b` 或 `[a, b]`）
  - `categories`：分类（默认 `categories`，支持 `a,b` 或 `[a, b]`；未填写则使用 `defaultCategories`，两者都为空会报错）
  - `draft`：草稿（默认 `draft`，`true/false`）
  - `dateCreated`：发布时间（默认 `dateCreated`）
  - `lastPublished`：最后一次发布记录（默认 `typecho_lastPublished`）

## 操作

- `发布`：发布/更新（如存在 `cid` 则调用 `metaWeblog.editPost`，否则调用 `metaWeblog.newPost`）
- `同步时间`：从服务器读取文章 `dateCreated` 并回写到笔记 frontmatter
- `最近文章`：获取最近文章列表（`metaWeblog.getRecentPosts`，以 snackbar 文本形式展示）
- `删除`：删除当前笔记对应的文章（`blogger.deletePost`，成功后会清理 `cid/lastPublished`）
