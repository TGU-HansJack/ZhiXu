// Typecho XML-RPC Publisher (Zhixu Plugin)
// NOTE: This file is currently not executed by Zhixu Android.
// The UI entry points are declared in `manifest.json` (e.g. `actions[]` for editor FAB long-press menu).

function publish(context) {
  // Planned:
  // - Read current note markdown
  // - Parse frontmatter (title/slug/tags/categories)
  // - Call MetaWeblog API via XML-RPC
  // - Return result (postId/url)
  return {
    ok: false,
    message: 'Not implemented yet',
    received: {
      docUri: context?.docUri ?? null,
      title: context?.title ?? null,
    },
  };
}

module.exports = {
  actions: {
    publish,
  },
};
