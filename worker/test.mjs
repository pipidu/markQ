import worker, { templateIdFromPath } from "./src/index.js";

const id = "550e8400-e29b-41d4-a716-446655440000";
const cases = [
  [`/t/${id}`, id],
  [`/t/${id}/`, id],
  ["/t/not-a-uuid", null],
  ["/template/" + id, null],
  ["/", null],
];

let failed = 0;
for (const [path, expected] of cases) {
  const got = templateIdFromPath(path);
  if (got !== expected) {
    console.error("fail", path, "expected", expected, "got", got);
    failed += 1;
  }
}

const page = await worker.fetch(new Request(`https://example.test/t/${id}`));
const html = await page.text();
if (
  page.status !== 200 ||
  !html.includes("intent://template/") ||
  !html.includes("markq://template/" + id)
) {
  console.error("fail open page html");
  failed += 1;
}
if (!html.includes("package=com.markq.app") || !html.includes("scheme=markq")) {
  console.error("fail intent extras");
  failed += 1;
}
if (html.includes("browser_fallback_url") || html.includes("stay=1")) {
  console.error("fail auto-open must not fall back to this page");
  failed += 1;
}
if (!html.includes("location.replace")) {
  console.error("fail missing immediate redirect");
  failed += 1;
}
if (!html.includes("https://github.com/pipidu/markQ/releases/latest")) {
  console.error("fail apk link");
  failed += 1;
}

const missing = await worker.fetch(new Request("https://example.test/t/nope"));
if (missing.status !== 404) {
  console.error("fail 404", missing.status);
  failed += 1;
}

if (failed) {
  process.exit(1);
}
console.log("worker path tests ok");
