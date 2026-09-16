# MarkQ agent rules

MarkQ is a team marking / shared checklist Android app. Shared data lives on the user’s own WebDAV server. Do not invent product features beyond this document.

## Product

- The user sets a WebDAV server URL and credentials as the sync server. Default is Nutstore (坚果云) at `https://dav.jianguoyun.com/dav/`, plus a save directory (default `MarkQ`).
- Users add mark entries. Content supports text, images, and files. Each entry has a date/time, defaulting to now.
- Adding images offers a compression checkbox (checked by default). When checked, convert to WebP at 60% quality before storing the blob and uploading. When unchecked, store and upload the original.
- Next to image upload, a camera button opens the camera, captures a photo, and does not keep the original on the phone (app cache only; delete the temp capture file). The same WebP checkbox applies to camera photos.
- Save must ignore a rapid second tap until the first save finishes (one mark only).
- Tapping an image on a mark (list thumbnail or editor) opens an in-app viewer with pinch-to-zoom and pan. Back/dismiss returns to the mark.
- Tap a mark card to open its detail/editor (text, images, files, datetime, author, color, tags). Tap is not a swipe. The editor is editable and saves/syncs over WebDAV.
- Next to the + FAB, a notepad button opens mark templates. Users can create a template or add a mark from a template. Templates can preset tags, color, content (text), and other mark fields that make sense; datetime still defaults to now when creating a mark. Persist templates locally and on WebDAV with incremental sync.
- Pull down on the marks list for incremental WebDAV refresh (not a full download). Show the normal pull-to-refresh indicator.
- In the editor, the color strip scrolls horizontally. That strip must keep those sideways gestures so a parent pager or swipe-to-complete cannot steal them.
- Swipe right marks an item complete. If it is already complete, swipe right uncompletes it. Swipe left deletes. Delete requires a second confirmation. Swipe distance must be high enough that a light flick cannot trigger complete or delete. The complete/delete color and icon must track the finger, and the settle/snap animation must finish.
- Each mark has an editable color that is stored on the entry and synced over WebDAV.
- Marks can have tags. Tags are stored on the entry and synced over WebDAV. The list can be filtered by tag locally (on already-synced entries).
- Completed marks appear only in the 「已完成」 category (strikethrough and/or a gray filter). They must not appear in 「全部」 or in any tag filter. Completing a mark moves it there; uncompleting returns it to the normal list and tags.
- Each mark can attach a location (lat/lng, and a readable place name). Default on when creating or editing. Use GPS or last-known location. Reverse-geocode with Nominatim (OpenStreetMap) using a MarkQ User-Agent, cached and rate-limited; if that fails, Android Geocoder, then raw coordinates. Request runtime location permission; if denied, still allow saving without location. Do not block save waiting for geocode. Location syncs with the entry over WebDAV.
- The top/app bar stays compact (short height, no extra vertical padding).
- Background, top/app bar, and + FAB colors are user-adjustable in Settings and stored on the device. Defaults: top bar green (`#0B6E4F`), background white, cards green border with shadow, + button white with shadow. Per-entry card colors stay on the entry and still sync.
- Cards stay visually distinct from the screen background.
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
- **Always push to `origin/main` after the commit.** Do not wait for the user to say push.
- If a PR or side branch already exists from earlier work, close/abandon the PR and put the work on `main`.

## Release (after every push)

After every push to `main`:

1. Compile a **release** APK (`:app:assembleRelease`), signed with `app/keystore/markq-release.jks` (passwords in `app/keystore.properties`).
2. Publish a GitHub Release on `pipidu/markQ` so in-app update can find it:
   - Tag: `v{versionName}` (example: `v1.0.0`)
   - Title: `MarkQ {versionName}`
   - Asset filename: `MarkQ-{versionName}.apk` (must match this pattern)

In-app update source: `https://api.github.com/repos/pipidu/markQ/releases/latest`

## Sync format (WebDAV)

The WebDAV **server** defaults to Nutstore (坚果云): `https://dav.jianguoyun.com/dav/`. The user also sets a **save directory** on that server (default `MarkQ`). Collection root is `{server}/{remoteDir}/`:

```
{server}/{remoteDir}/
  entries/{entryId}.json
  files/{entryId}/{attachId}
  templates/{templateId}.json
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
  "color": "#5B8DEF",
  "tags": ["work"],
  "latitude": 31.2304,
  "longitude": 121.4737,
  "placeName": "上海市黄浦区",
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

Template JSON (`schemaVersion` 1) lives at `templates/{id}.json`. Same incremental ETag pull/push and tombstone deletes as entries. Fields: `id`, `name`, `text`, `color`, `tags`, `createdAt`, `contentUpdatedAt`, `statusUpdatedAt`, `createdBy`, `updatedBy`, `deleted*`. Templates do not store datetime or attachments; instantiated marks still default datetime to now.

### Incremental pull (required)

- PROPFIND `entries/` (and `templates/`) for href / ETag / Last-Modified only.
- GET an entry or template JSON only when ETag (or Last-Modified) differs from the locally stored cursor for that path, or the file is new.
- GET an attachment only when the local file is missing or its `sha256` no longer matches the entry JSON.
- Never download every entry/attachment on each open.

### Incremental push

- PUT only locally dirty entries/attachments/templates.
- Use `If-Match` with the last known ETag when present. On HTTP 412 Precondition Failed: GET/PROPFIND to refresh the ETag, merge, and retry. If If-Match is still stale, PUT without it. Never show “Precondition Failed” (or 412) to the user.

### Conflict merge

- Content fields (`text`, `occurredAt`, `attachments`, `color`, `tags`, `latitude`, `longitude`, `placeName`) follow newer `contentUpdatedAt`.
- Status fields (`completed*`, `deleted*`) follow newer `statusUpdatedAt`.
- Equal timestamps: deterministic tie-break so clients converge (compare `updatedBy` then payload).

## Share code

Format: `MQ1_` + base64url (no padding) of raw DEFLATE bytes of UTF-8 JSON:

```json
{"u":"https://dav.jianguoyun.com/dav/","d":"MarkQ","l":"username","p":"password"}
```

- `u` (required): WebDAV server URL (Nutstore default `https://dav.jianguoyun.com/dav/`)
- `d` (optional): save directory on that server (default `MarkQ` for new setups)
- `l` / `p` (optional): username and password if needed to join
- Recipients paste the code; the app fills server fields. Nickname is still required locally and is not in the code.

## Out of scope

Do not add accounts, cloud vendors, extra social features, or other product surfaces that are not listed here.
