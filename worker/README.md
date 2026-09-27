# MarkQ open page (Cloudflare Worker)

NFC cannot store a `markq://` link reliably. This Worker serves an https page that opens the app.

## URL

`{你绑定的域名}/t/{templateId}`

Optional direct camera: `{你绑定的域名}/t/{templateId}?camera=1`

On load the page immediately `location.replace`s an Android `intent://` URL with scheme `markq` and package `com.markq.app` (and `?camera=1` on the intent and `markq://` URLs when that query is present). It does **not** set `browser_fallback_url`. If the page is still visible, it then tries `markq://template/{id}`.

If MarkQ is not installed, the same page stays (Chinese fallback) with a link to the latest GitHub release APK.

This repo does **not** set a production hostname. You bind your own domain after deploy.

## Deploy

```bash
cd worker
npx wrangler deploy
```

Then in the Cloudflare dashboard: **Workers & Pages → markq-open → Settings → Domains & Routes → Custom Domains** — add the hostname you will put in MarkQ **设置 → 打开链接域名** (no path).

`wrangler.toml` has no custom domain on purpose. Do not add one unless it is your domain.
