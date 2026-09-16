# MarkQ

Android 上的团队标记 / 共用清单。数据存在**你自己的 WebDAV** 上，默认是[坚果云](https://www.jianguoyun.com/)：`https://dav.jianguoyun.com/dav/`。应用不提供账号体系，也不托管你的内容。

界面默认中文。需要 Android 8.0（API 26）及以上。

仓库：[github.com/pipidu/markQ](https://github.com/pipidu/markQ)

---

## 安装

从 GitHub Releases 下载 APK，**不要**从浏览器里找别的安装包：

1. 打开 [Releases](https://github.com/pipidu/markQ/releases/latest)。
2. 下载资源 **`MarkQ-{版本号}.apk`**（必须是这个文件名，例如 `MarkQ-1.0.8.apk`）。
3. 用系统安装器安装。若提示「未知来源」，允许 MarkQ 安装未知应用后再回来继续。

安装后，应用会在打开时检查 GitHub 上是否有更新（见下文「应用内更新」）。

---

## 第一次使用

打开应用会进入设置页。需要同时具备：

- **昵称**（必填）。每个人都要设，包括第一个配置 WebDAV 的人，用来区分是谁在标记。
- **WebDAV 地址**（必填）。默认已填坚果云 `https://dav.jianguoyun.com/dav/`。也可以改成其他 `http://` / `https://` 的 WebDAV。
- **保存目录**。标记写在该文件夹下，默认 `MarkQ`。可改成网盘里的其他目录。实际路径是 `{服务器}/{保存目录}/`。
- **账号 / 密码**。坚果云用**邮箱**当账号，密码必须是坚果云后台生成的**应用密码**（不是登录密码）。其他 WebDAV 按对方要求填写，可以为空。

点 **保存并连接**。应用会先测连通，再把配置写到本机，然后做一次增量同步。

### 坚果云

1. 在坚果云开通 WebDAV。
2. 生成应用密码。
3. 地址保持 `https://dav.jianguoyun.com/dav/`，账号填邮箱，密码填应用密码。
4. 保存目录默认 `MarkQ`。应用只会在需要时创建这个保存目录；不会去 MKCOL 坚果云的 `/dav` 根路径。

### 用分享码加入

队友可以在 **设置 → 分享码** 里复制一串 `MQ1_` 开头的码。你在首次设置里粘贴，点 **应用分享码**，会填入服务器地址、保存目录、账号和密码。

**昵称不在分享码里**，加入的人仍要自己填昵称。

也可以不填分享码，手动输入同一套 WebDAV。

---

## 标记

右下角 **+** 新建。列表里点一张卡片进入**可编辑**的详情（不是只读）：文字、图片、文件、日期时间、颜色、标签。保存后先写本地，再同步到 WebDAV。

每条标记包括：

| 内容 | 说明 |
| --- | --- |
| 文字 | 可空，列表会显示「（无文字）」 |
| 图片 / 文件 | 编辑页添加或移除 |
| 日期和时间 | 默认是当前时间，可改 |
| 颜色 | 本条卡片的颜色，存在条目上并随 WebDAV 同步。编辑页颜色条可左右滑动选择 |
| 标签 | 可加多个。存在条目上并同步 |
| 作者 | 显示添加者昵称；完成后还会显示是谁完成的 |

已完成的条目：文字划线，卡片变淡。

### 手势（列表）

- **点卡片**：打开编辑页。
- **右滑**：完成；若已完成，再右滑则取消完成。
- **左滑**：删除。会再确认一次。删除后，共用该服务器的所有人都会看到这条被删掉（删除是同步的墓碑，不是只在本机消失）。
- 轻扫不够长不会触发完成/删除。

顶栏刷新按钮、以及列表**下拉刷新**，都会做一次**增量** WebDAV 同步（不是整库重下）。

### 标签筛选

列表上方会出现已有标签。点某个标签只显示带该标签的条目；点 **全部** 取消筛选。筛选只看本机已同步下来的列表，不会为此去全量下载。

---

## 同步与协作

- **本地优先**：创建、修改、完成、删除都先写入本机，再上传。
- **增量**：打开应用、下拉刷新、顶栏同步，都是先 PROPFIND 条目的 ETag，只有变了或新增的 JSON / 附件才会 GET。不会每次把所有内容重新下完。
- **冲突**：正文、时间、附件、颜色、标签按较新的内容时间合并；完成/删除按较新的状态时间合并。
- 数据在你的 WebDAV 上大致是：

```
{服务器}/{保存目录}/
  entries/{条目id}.json
  files/{条目id}/{附件id}
```

昵称、外观主题只存在本机，不随 WebDAV 同步。

---

## 外观

在 **设置 → 外观** 里改（立刻保存在本机，不必点「保存服务器」）：

| 项 | 默认 |
| --- | --- |
| 顶栏颜色 | 绿 `#0B6E4F` |
| 背景颜色 | 白 `#FFFFFF` |
| + 按钮颜色 | 白 `#FFFFFF`，带阴影 |

卡片本身是绿描边 + 阴影，和背景分开。每条标记自己的颜色仍然跟条目走，不受这里的主题影响。

颜色可用色板，也可以填十六进制（如 `#0B6E4F`）。

---

## 应用内更新

来源：[GitHub `releases/latest`](https://api.github.com/repos/pipidu/markQ/releases/latest)，安装包必须叫 `MarkQ-{版本}.apk`。

- 打开应用时会静默检查。没有新版本就什么也不弹。
- 有新版本时在应用内下载（进度百分比和速度），用系统 PackageInstaller 安装，**不会**跳浏览器下 APK。
- 设置里也可以点 **检查更新**。
- 若系统禁止未知来源，会提示允许 MarkQ 安装未知应用，返回后继续安装。
- 装完会清掉多余的 APK 缓存。

---

## 版本号

每次改动都会升补丁版本并打 GitHub Release。规则见 [`AGENTS.md`](AGENTS.md)（提交到 `main`、推送、发布 `v{版本}` 和 `MarkQ-{版本}.apk`）。不要另开功能分支或 Pull Request。

---

## English

MarkQ is a team marking / shared checklist app for Android 8+. Shared data lives on **your** WebDAV server (default Nutstore 坚果云 at `https://dav.jianguoyun.com/dav/`, save folder `MarkQ`). There is no MarkQ account.

Everyone needs a **nickname**. Optional **share codes** (`MQ1_…`) fill in the server URL, folder, and credentials; the nickname is still entered locally.

Marks support text, images, files, datetime, a per-entry color, and tags. Tap a card to edit. Swipe right to complete (or uncomplete). Swipe left to delete (with confirmation). Pull down to incremental-refresh. Filter the list by tag locally.

Theme (top bar, background, + button) is stored on the device. Defaults: green bar `#0B6E4F`, white background, white + button with shadow; cards have a green border and shadow.

Install `MarkQ-{version}.apk` from [Releases](https://github.com/pipidu/markQ/releases/latest). The app also updates from GitHub Releases in-app (download + PackageInstaller, no browser).

Product/sync/release rules: [`AGENTS.md`](AGENTS.md).
