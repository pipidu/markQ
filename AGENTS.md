# MarkQ agent rules

MarkQ is a team marking / shared checklist Android app. Shared data lives on the user’s own WebDAV server. Do not invent product features beyond this document.

## Product

- The user sets a WebDAV server URL and credentials as the sync server.
- Users add mark entries. Content supports text, images, and files. Each entry has a date/time, defaulting to now.
- Swipe right marks an item complete. Swipe left deletes. Delete requires a second confirmation.
- Completed items show strikethrough and/or a gray filter.
- A user can share the WebDAV address via an app-generated share code so others can mark together.
- Each user must set a nickname so markers are distinguishable.
- Every user action must sync to other users: create, complete, delete.
- App open uses incremental sync only (never a full content re-download).
- Local-first: write locally, then sync. Merges must be conflict-safe when two users change the same entry.
- Auto-update from GitHub Releases (see below).

## Versioning

- First shipped version is `1.0.0` (`versionCode` `1`).
- On **every** change, bump version by +1 before committing:
  - `markq.versionName` patch +1 (`1.0.0` → `1.0.1` → `1.0.2`)
  - `markq.versionCode` integer +1 (`1` → `2` → `3`)
- Keep those two values in `gradle.properties`. `app/build.gradle.kts` reads them.
- The version bump belongs in the same commit as the change.

## Git

- After every change, **commit automatically** (include the version bump).
- Commit directly to the default branch `main`. No feature branches. No pull requests.
- “Master” in conversation means this same policy: work on the default branch, not a side branch.
- **Push only when the user explicitly asks.** Do not push on your own.
- If a PR or side branch already exists from earlier work, close/abandon the PR and put the work on `main`.

## Release (only after a user-requested push)

After a user-requested push to `main`:

1. Compile a **release** APK (`:app:assembleRelease`), signed with `app/keystore/markq-release.jks` (passwords in `app/keystore.properties`).
2. Publish a GitHub Release on `pipidu/markQ` so in-app update can find it:
   - Tag: `v{versionName}` (example: `v1.0.0`)
   - Title: `MarkQ {versionName}`
   - Asset filename: `MarkQ-{versionName}.apk` (must match this pattern)
3. Do not publish a release unless that push was explicitly requested.

In-app update source: `https://api.github.com/repos/pipidu/markQ/releases/latest`

## Sync format (WebDAV)

The URL the user enters is the collection root. The app creates two child collections if missing:

```
{root}/
  entries/{entryId}.json     # one JSON document per mark
  files/{entryId}/{attachId} # attachment bytes
```

Entry JSON (`schemaVersion` 1):

```json
{
  "id": "uuid",
  "schemaVersion": 1,
  "occurredAt": "ISO-8601",
  "createdAt": "ISO-8601",
  "contentUpdatedAt": "ISO-8601",
  "statusUpdatedAt": "ISO-8601",
  "text": "string",
  "createdBy": "nickname",
  "updatedBy": "nickname",
  "completed": false,
  "completedBy": null,
  "completedAt": null,
  "deleted": false,
  "deletedBy": null,
  "deletedAt": null,
  "attachments": [
    {
      "id": "uuid",
      "name": "photo.jpg",
      "mime": "image/jpeg",
      "kind": "image",
      "size": 0,
      "sha256": "hex"
    }
  ]
}
```

Deletes are **tombstones** (`deleted: true`) so other clients see the removal. Do not rely on missing files as the delete signal.

### Incremental pull (required)

- PROPFIND `entries/` for href / ETag / Last-Modified only.
- GET an entry JSON only when ETag (or Last-Modified) differs from the locally stored cursor for that path, or the file is new.
- GET an attachment only when the local file is missing or its `sha256` no longer matches the entry JSON.
- Never download every entry/attachment on each open.

### Incremental push

- PUT only locally dirty entries/attachments.
- Use `If-Match` with the last known ETag when present. On 412, GET, merge, retry.

### Conflict merge

- Content fields (`text`, `occurredAt`, `attachments`) follow newer `contentUpdatedAt`.
- Status fields (`completed*`, `deleted*`) follow newer `statusUpdatedAt`.
- Equal timestamps: deterministic tie-break so clients converge (compare `updatedBy` then payload).

## Share code

Format: `MQ1_` + base64url (no padding) of raw DEFLATE bytes of UTF-8 JSON:

```json
{"u":"https://example.com/webdav/markq","l":"username","p":"password"}
```

- `u` (required): WebDAV collection URL
- `l` / `p` (optional): username and password if needed to join
- Recipients paste the code; the app fills server fields. Nickname is still required locally and is not in the code.

## Out of scope

Do not add accounts, cloud vendors, extra social features, or other product surfaces that are not listed here.
