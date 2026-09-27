const PACKAGE_ID = "com.markq.app";
const LATEST_RELEASE = "https://github.com/pipidu/markQ/releases/latest";
const UUID_RE =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

export function templateIdFromPath(pathname) {
  const match = String(pathname || "").match(/^\/t\/([^/]+)\/?$/);
  if (!match) return null;
  let id;
  try {
    id = decodeURIComponent(match[1]);
  } catch {
    return null;
  }
  if (!UUID_RE.test(id)) return null;
  return id;
}

export default {
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/" || url.pathname === "") {
      return plain("请使用 /t/{模板id} 打开 MarkQ。", 200);
    }
    const id = templateIdFromPath(url.pathname);
    if (!id) {
      return plain("页面不存在。", 404);
    }
    return new Response(openPage(id), {
      headers: {
        "content-type": "text/html; charset=utf-8",
        "cache-control": "no-store",
      },
    });
  },
};

function plain(body, status) {
  return new Response(body, {
    status,
    headers: { "content-type": "text/plain; charset=utf-8" },
  });
}

function openPage(id) {
  const custom = "markq://template/" + id;
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>打开 MarkQ</title>
<style>
body{font-family:sans-serif;margin:1.5rem;line-height:1.5;color:#111}
a{color:#0B6E4F}
</style>
</head>
<body>
<p>正在打开 MarkQ…</p>
<p>未安装 MarkQ？请先安装，然后再打开这个链接。</p>
<p><a href="${LATEST_RELEASE}">下载最新安装包（APK）</a></p>
<p><a id="open" href="${custom}">打开 MarkQ</a></p>
<script>
(function(){
  var id = ${JSON.stringify(id)};
  var custom = "markq://template/" + id;
  var stay = new URLSearchParams(location.search).get("stay") === "1";
  var fallback = location.origin + location.pathname + "?stay=1";
  var intent = "intent://template/" + id +
    "#Intent;scheme=markq;package=${PACKAGE_ID};S.browser_fallback_url=" +
    encodeURIComponent(fallback) + ";end";
  document.getElementById("open").href = stay ? custom : intent;
  if (stay) return;
  location.href = intent;
  setTimeout(function(){ location.href = custom; }, 400);
})();
</script>
</body>
</html>`;
}
