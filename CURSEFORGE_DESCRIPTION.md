# Apricity Quests

**A refreshed quest-book interface for FTB Quests.** Your quests, progress, and rewards stay exactly as your pack defines them — only the way you read them changes.

---

## English

### What this is

Apricity Quests is an **independent add-on mod for FTB Quests**. It presents the chapters, quest maps, objectives, and rewards of your existing quest pack in a fast, animated interface built on ApricityUI (HTML + CSS). It does not add quests, change progression, rewrite quest data, or replace FTB Quests.

### Opening it

Nothing new to learn. Open it **the same way you already open FTB Quests** — your usual keybind, the command, or the quest book — and the new interface appears in place of the built-in quest screen. **ESC** closes it. There is no separate keybind and nothing you have to configure.

### Features

- **Chapters and quest maps.** Browse chapter groups (they collapse), hide the chapter column, and drag around large quest maps.
- **Zoom that follows the cursor.** Hold **Shift** and scroll to zoom the map around the mouse position.
- **Per-chapter map memory.** Zoom level and map center are remembered for every chapter separately.
- **Search that helps instead of guessing.** Search the current chapter or all chapters, with independent switches for chapter names, quest names and descriptions, tasks, and rewards. Cross-chapter search is off by default and can be enabled in the search panel.
- **One focused details panel.** Status, description, progress, objectives, and rewards together. For quests that cannot be started yet, details are held back instead of spoiled.
- **Six accent themes.** Green, yellow, blue, cyan, purple, pink. Your choice is remembered between sessions.
- **FTB Quests logic untouched.** Completing objectives and claiming rewards still run through FTB Quests; players with edit permission can open the FTB Quests editor directly from the interface.
- **Restylable by players.** See “Customize the interface”.

### Requirements

- Minecraft **Java 1.20.1**, **Forge 47.x**
- **FTB Quests** 2001.4.22 or newer (required, installed separately)
- **ApricityUI** 1.2.5 or newer (required, installed separately)
- This is a Forge build; it is not a Fabric or NeoForge build.
- The bundled interface text is currently Simplified Chinese. Quest names and descriptions come from your quest pack, in whatever language the pack uses.

### Customize the interface

The mod installs its default page files into your instance folder:

- `<instance>/apricity/questui/questbook-v8.html`
- `<instance>/apricity/questui/questbook-v8.css`

Edit them to change layout, colors, fonts, or spacing. When a mod update ships new defaults, **only files you have not modified are replaced** — your customized files are kept, and a file whose origin cannot be verified is preserved under `apricity/questui/_obsolete/` rather than overwritten.

### Credits, attribution, and how it differs from the original

**Apricity Quests is an independent add-on — not a fork, port, or re-upload of anyone else's mod.** The released jar contains only its own classes and files (`dev.modernftb.questui.*` and `assets/questui/*`); no code, resources, textures, sounds, or interface files from FTB Quests or ApricityUI are bundled, copied, or redistributed. Both dependencies are installed separately by every player and remain under their own licenses.

- **FTB Quests** — by the FTB Team, <https://www.curseforge.com/minecraft/mc-mods/ftb-quests>. All quest content, progression, objectives, and rewards belong to FTB Quests and are computed by it. Apricity Quests reads that data through FTB Quests' public API and passes complete/claim actions back to it. FTB Quests stays under its own terms (its `mods.toml` declares *All Rights Reserved*); we do not modify it and we do not ship it.
- **ApricityUI (晴雪UI)** — by MerrySnow / Tower of Sighs, <https://modrinth.com/mod/apricityui>, licensed **LGPL-2.1-only** (open source). It is the HTML/CSS interface runtime this add-on renders through, and it is installed as a separate mod.
- **Apricity Quests itself** — © 2026 Modern FTB, licensed **LGPL-3.0**, public source at <https://github.com/QLNPLUS/questui>.

How it differs from FTB Quests' built-in quest book:

- **Different technology stack.** The built-in quest book is drawn with FTB Library panel widgets. This interface is built on ApricityUI (HTML + CSS pages) and shares no interface files with FTB.
- **Search.** Chapter-wide or all-chapter search with per-category switches (chapter names, quest names and descriptions, tasks, rewards) — the built-in book offers no equivalent control set.
- **Navigation.** Collapsible chapter groups plus a hideable chapter column.
- **Map behaviour.** Per-chapter saved zoom and center, Shift-scroll zoom anchored to the cursor, hover titles that stay above map nodes while fading, and dragging that continues across node hit areas.
- **Appearance.** Six selectable accent themes, and player-editable page files.
- **Quest logic.** Identical — because it is still executed by FTB Quests.

Apricity Quests is **not affiliated with, endorsed by, or approved by** the FTB Team; “FTB Quests” is named only to identify the mod this add-on requires. If you are the FTB team and you would like this presentation changed, credited differently, or taken down, contact me through the GitHub issues page or a CurseForge private message and I will act on it.

### License

Licensed under the **GNU Lesser General Public License v3.0** (or, at your option, any later version). Copyright © 2026 Modern FTB. Full text in the `LICENSE` file of the repository; the source of every release is published alongside it.

---

## 中文说明

### 这是什么

Apricity Quests 是一个**为 FTB Quests 提供任务手册界面的独立附属 mod**。它用基于 ApricityUI（HTML + CSS）的动画界面，展示你整合包中已有的任务章节、任务地图、目标与奖励。它不会新增任务、不会改动任务进度、不会改写任务数据，也不会替代 FTB Quests。

### 如何打开

不需要学习新操作。你**照原来的方式打开 FTB 任务手册**（平时的快捷键、指令或任务书）就会看到新界面，它取代的是自带的任务手册画面。**ESC** 关闭。没有额外按键，也没有必须进行的配置。

### 功能

- **章节与任务地图。** 章节组可折叠，章节栏可收起，大型任务地图可拖动查看。
- **跟随鼠标的缩放。** 按住 **Shift** 滚动滚轮，地图以鼠标位置为中心缩放。
- **逐章节记忆地图。** 每个章节的缩放比例与中心位置分别保存。
- **真正有用的搜索。** 可搜索当前章节或全部章节，并分别开关：章节名称、任务名称与说明、目标、奖励。跨章节搜索默认关闭，可在搜索面板中开启。
- **集中的任务详情。** 状态、说明、进度、目标与奖励集中显示；尚不可开始的任务不会提前透露详情。
- **六种主题色。** 绿、黄、蓝、青、紫、粉，选择会在下次启动时保留。
- **任务逻辑不变。** 完成目标与领取奖励仍由 FTB Quests 处理；拥有编辑权限的玩家可直接在界面内打开 FTB Quests 编辑器。
- **界面可自定义。** 见下方说明。

### 运行依赖

- Minecraft Java 版 **1.20.1**，**Forge 47.x**
- **FTB Quests** 2001.4.22 或更新版本（必需，需单独安装）
- **ApricityUI（晴雪UI）** 1.2.5 或更新版本（必需，需单独安装）
- 本版本适用于 Forge，不适用于 Fabric 或 NeoForge。
- 内置界面文字目前为简体中文；任务名称与说明由整合包决定，使用整合包自身的语言。

### 自定义界面

mod 会把默认页面文件写入你的游戏实例目录：

- `<实例目录>/apricity/questui/questbook-v8.html`
- `<实例目录>/apricity/questui/questbook-v8.css`

修改这两个文件即可调整布局、配色、字体与间距。mod 更新默认文件时，**只会替换你未改动过的文件**——你自定义过的文件会被保留；来源无法确认的旧文件会先存入 `apricity/questui/_obsolete/`，不会被直接覆盖。

### 致谢、来源说明与差异说明

**Apricity Quests 是独立的附属 mod，不是任何人 mod 的分叉、移植或重新上传。** 发布的 jar 内只有本 mod 自己的类与文件（`dev.modernftb.questui.*` 与 `assets/questui/*`），**没有打包、复制或再分发** FTB Quests / ApricityUI 的任何代码、资源、贴图、音效或界面文件。两个依赖都由玩家单独安装，并各自适用其原有许可。

- **FTB Quests** —— 作者 FTB Team，<https://www.curseforge.com/minecraft/mc-mods/ftb-quests>。全部任务内容、进度、目标与奖励都归 FTB Quests 所有并由它计算。本 mod 仅通过 FTB Quests 的公开 API 读取这些数据，并把“完成目标 / 领取奖励”交回 FTB Quests 处理。FTB Quests 适用其自身条款（其 `mods.toml` 声明为 *All Rights Reserved*），我们不修改它，也不随本 mod 分发它。
- **ApricityUI（晴雪UI）** —— 作者 MerrySnow / Tower of Sighs，<https://modrinth.com/mod/apricityui>，许可证 **LGPL-2.1-only**（开源）。它是本附属 mod 所依赖的 HTML/CSS 界面运行库，同样作为独立 mod 安装。
- **Apricity Quests 自身** —— © 2026 Modern FTB，许可证 **LGPL-3.0**，公开源码：<https://github.com/QLNPLUS/questui>。

与 FTB Quests 自带任务手册界面的具体差异：

- **技术栈不同。** 自带界面由 FTB Library 的面板控件绘制；本界面基于 ApricityUI 的 HTML + CSS 页面，不共用任何界面文件。
- **搜索。** 支持当前章节 / 全部章节，并按类别分别开关（章节名称、任务名称与说明、目标、奖励）；自带界面没有对应的控制方式。
- **导航。** 章节组折叠 + 章节栏收起。
- **地图行为。** 逐章节保存缩放与中心；Shift + 滚轮以光标为锚点缩放；悬停标题在淡出过程中保持在节点之上；拖动可跨越节点点击区域继续。
- **外观。** 六种可选主题色，且页面文件开放给玩家修改。
- **任务逻辑完全相同。** 因为这些逻辑仍由 FTB Quests 执行。

Apricity Quests **与 FTB Team 无关，也未获得其认可或授权**；文中提到 “FTB Quests” 仅用于指明本附属 mod 所需的 mod。若你是 FTB 团队成员，希望这个界面做出调整、修改署名或下架，请通过 GitHub 的 issues 页面或 CurseForge 私信联系我，我会立即处理。

### 许可证

本 mod 采用 **GNU 宽通用公共许可证 v3.0**（或你选择的更高版本）发布，© 2026 Modern FTB。完整条款见仓库中的 `LICENSE` 文件，每个版本的源码同时公开。
