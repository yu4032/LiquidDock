# Contributing

本文档面向当前 `main` / **v2.2.1**。Liquid Glass 主线为 HyperOS 3.0.307+ / MiuiX PassBlur + OES/GLES zero-copy；1.x ScreenCapture 代码只存在于 `archive/1.x`。

## 构建

要求：

- Android SDK / compileSdk 37；
- minSdk 33 / targetSdk 37；
- JDK 17；
- Gradle 9.6.1；
- libxposed API 101。

提交前至少执行：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Debug 与 Release 都经过 AGP optimization / R8。涉及反射入口必须考虑 keep rule 和优化后行为。

## 分支规则

`main` 是当前开发主线。功能/修复从最新 `main` 建独立分支；不要从旧 `api101-migration` 或 1.x 文档推断当前架构。

`archive/1.x` 仅保存历史 ScreenCapture/bitmap-readback 实现。

## 配置规则

新增/修改配置时：

1. 先更新 `ConfigSchema`；
2. 历史升级进入 `ConfigMigration` / `LegacyConfigMigration`；
3. JSON shape / legacy alias 修改 `ConfigCodec` 并加 round-trip test；
4. preset 修改 `PresetManager`；
5. runtime 通过 typed `LiquidDockConfig` snapshot 读取；
6. live visual toggle 进入对应 runtime state，不让 Hook 直接轮询 raw preference；
7. 除明确 breaking migration 外保持历史 key / `_tenths` / import-export 兼容。

## SystemUI 边界

`com.android.systemui` 当前是**只读时序源**。

允许：

- 读取 WMShell HOME transition START/FINISH；
- 读取 Keyguard Gone；
- 发送带 serial/timestamp 的 timing event 给 Launcher；
- source 异常时 fail-open。

禁止：

- 在 SystemUI 创建 LiquidDock Glass renderer；
- 从 SystemUI 捕获 Launcher/Wallpaper 内容；
- 把 SystemUI 变成第二套 scene state owner；
- 在 WMShell callback 中抛出 LiquidDock 异常。

Launcher 4.50 的 transition hook 必须继续作为 fallback，不能假定跨进程 signal 永远先到。

## Zero-copy / producer 规则

唯一 Glass backend：

```text
MiuiX PassBlur -> SurfaceTexture/OES -> GLES -> Prismal
```

禁止恢复：

- `ScreenCapture` fallback；
- bitmap readback；
- CPU backdrop copy；
- 用普通 redraw 代替 fresh-frame barrier。

### Dock hard constraint

**Dock producer 必须保持 continuous-on-bind / realtime。**

不能为了省帧：

- 用缓存背景替换实时 Dock；
- 给 Dock 套 Workspace 的 idle single-update policy；
- 因 `hasWindowFocus()` 为 false 停掉 Floating Dock；
- 从 View hierarchy 猜一个 binding 应该 pulse 还是 continuous。

Workspace shared session 才允许在明确 scene lifecycle 中 pulse/pause。

## Scene / HOME transition 规则

需要同时覆盖：

- App→Home；
- Home→App；
- Recents→Home / Home→Recents；
- Widget→App / App→Widget；
- rotation；
- unlock；
- wallpaper content change；
- Workstation producer rollover。

SystemUI HOME START 可作为更早 authority；Launcher `WindowElement` 是 fallback。matching SystemUI FINISH 到达前，Launcher animation end 不得提前解除已接管的 HOME barrier。

跨进程事件必须保留 serial + monotonic timestamp stale-event 防护。

## Widget transition 规则

Widget 不是 ShortcutIcon。

- Widget→App：vendor host 被隐藏后仍要保留 fade-out 所需 geometry；
- App→Widget：返回 Widget 必须从 cached StaticLayer 中先抑制；
- HOME barrier release 不等于 Widget 可以 reveal；必须等当前 scene generation fresh render；
- 快速二次 launch 要能取消上一次 return fade/suppression；
- vendor `VISIBLE` 只能释放 launch-only ownership，不能绕过 return fresh gate。

## Static geometry 规则

Workspace StaticLayer 与 drag/sink crop 是两种不同语义：

- StaticNode：使用 full-shape geometry，部分离屏不改变 width/center/radius；
- sink/drag：可以使用 clipped visible geometry。

不要全局 clamp StaticLayer shape 到 root，也不要为了修 StaticLayer 删除所有 crop。

## Widget component discovery 规则

- selectorKey 是持久身份，必须向后兼容；
- `Render # / Depth / Area / Z` 是 discovery metadata，不得进入 selectorKey；
- RemoteViews 使用真实 View tree；
- MAML 优先真实 `mInnerGroup -> mElements` render tree，同时保留 name-based selector；
- “疑似底层背景”只改变 GUI 排序/提示，不能自动执行破坏性隐藏；
- 缺失 metadata 的旧 catalog 必须继续可读。

## Runtime ownership

- runtime disable：先 publish disabled，再 teardown；
- queued callback 执行时重查 live state；
- 只恢复实际 snapshot 的 vendor state；
- 不扩大 `MainHook` 全局 mutable state；
- `LiquidDockConfig.load()` 保持 side-effect-free。

## Grid / Widget layout

- 不接管 MIUI occupancy / placement；
- 不 Hook `addOccupied()` / `transformToHVArray()` 猜 matrix；
- Widget adaptation 只改 allocation/frame；
- 当前显式 span：1×1、2×1、2×2、4×2；
- 新 span/类型优先集中到 classifier/registry，而不是继续散落 itemType 分支。

## Workstation / Laptop

仍是实验性路径。修改时至少回归：

- 进入/退出；
- Dock geometry / icon offset；
- Grid / All Apps；
- Divider；
- Recents 连续往返；
- rotation；
- wallpaper freshness；
- Workspace PassBlur suspend/rebind；
- 独立 Dock continuous producer；
- 普通模式无回归。

## Divider

`DockDividerHook` 必须 exact restore：

- 首次 mutation 前 snapshot width/height/margins/background；
- background 复制 Drawable，避免 alias；
- disable 时取消 pending listener；
- restore + `requestLayout()` 后释放 snapshot ownership。

详见 [DIVIDER.md](DIVIDER.md)。

## 文档规则

当前权威根文档：

- [README.md](README.md) — 项目与兼容边界
- [FEATURES.md](FEATURES.md) — 用户功能
- [ARCHITECTURE.md](ARCHITECTURE.md) — runtime/process 架构
- [HOOKS.md](HOOKS.md) — Hook 与 listener
- [DIVIDER.md](DIVIDER.md) — Divider ownership
- [TODO.md](TODO.md) — 当前剩余工作
- [CHANGELOG.md](CHANGELOG.md) — release 历史

当代码改变以下任一不变量时，必须同步文档：SystemUI 角色、Dock producer 语义、HOME freshness、Widget selector 格式、Grid ownership、restart/live 边界。

## 许可

本项目基于 [GPL-3.0](LICENSE) 许可。
