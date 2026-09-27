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
    const camera = url.searchParams.get("camera") === "1";
    return new Response(openPage(id, camera), {
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

function openPage(id, camera) {
  const query = camera ? "?camera=1" : "";
  const custom = "markq://template/" + id + query;
  const intent =
    "intent://template/" +
    id +
    query +
    "#Intent;scheme=markq;package=" +
    PACKAGE_ID +
    ";end";
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
<script>location.replace(${JSON.stringify(intent)});</script>
</head>
<body>
<p>正在打开 MarkQ…</p>
<p>未安装 MarkQ？请先安装，然后再打开这个链接。</p>
<p><a href="${LATEST_RELEASE}">下载最新安装包（APK）</a></p>
<p><a id="open" href="${intent}">打开 MarkQ</a></p>
<script>
(function(){
  var intent = ${JSON.stringify(intent)};
  var custom = ${JSON.stringify(custom)};
  var a = document.getElementById("open");
  a.href = intent;
  try { a.click(); } catch (e) {}
  setTimeout(function(){
    if (document.hidden || document.webkitHidden) return;
    location.href = custom;
  }, 700);
})();
</script>
</body>
</html>`;
}
