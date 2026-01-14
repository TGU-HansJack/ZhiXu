# WebDAV 同步（webdav-zhixu）

将当前 Vault 与 WebDAV 远端目录进行同步的示例插件（桌面端优先）。

## 安装

- 打开 Zhixu 桌面端 → 进入「工坊」
- 在「官方插件」中找到 `webdav-zhixu` → 点击「安装」

安装后插件会位于当前 Vault：

`./.zhixu/plugins/webdav-zhixu/`

## 配置

编辑 `config.json`：

- `baseUrl`：WebDAV 服务地址，例如 `https://dav.example.com/remote.php/dav/files/user`
- `remoteRoot`：远端根目录，例如 `/zhixu/`
- `username` / `password`：认证信息（Basic Auth）
- `ignore`：忽略路径前缀（以 `/` 结尾表示目录前缀）

## 使用

在插件详情页：

- 「测试连接」：验证 WebDAV 可用性（PROPFIND Depth 0）
- 「立即同步」：执行一次同步（示例实现：上传本地缺失/变更文件、下载远端缺失文件）

## 注意

这是一个示例插件实现，尚未包含企业级同步所需的：冲突解决、删除传播、断点续传、重试/回滚、完整校验（hash）等能力。

