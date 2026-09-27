# MarkQ open page (Cloudflare Worker)

NFC cannot store a `markq://` link reliably. This Worker serves an https page that opens the app.

## URL

`{你绑定的域名}/t/{templateId}`

Example: `https://your-domain.example/t/550e8400-e29b-41d4-a716-446655440000`

On load the page tries:

1. Android `intent://template/{id}#Intent;scheme=markq;package=com.markq.app;…;end`
2. then `markq://template/{id}`

If MarkQ is not installed, the same page stays (Chinese fallback) with a link to the latest GitHub release APK.

This repo does **not** set a production hostname. You bind your own domain after deploy.

## Deploy

```bash
cd worker
npx wrangler deploy
```

Then in the Cloudflare dashboard: **Workers & Pages → markq-open → Settings → Domains & Routes → Custom Domains** — add the hostname you will put in MarkQ **设置 → 打开链接域名** (no path).

`wrangler.toml` has no custom domain on purpose. Do not add one unless it is your domain.
