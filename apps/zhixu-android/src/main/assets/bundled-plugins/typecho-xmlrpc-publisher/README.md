# Typecho XML-RPC Publisher (Zhixu Plugin)

This is a bundled plugin placeholder.

Planned features:
- Publish current note to Typecho via MetaWeblog XML-RPC
- Support tags / categories / draft

## Front Matter (YAML)

When your note starts with a YAML front matter block, Zhixu will read it and publish the body (without front matter):

```yaml
---
title: My Post Title
slug: my-post-slug
tags: [tag1, tag2]
categories:
  - Cat A
  - Cat B
draft: false
typechoPostId: "123" # optional, for update
---
```
