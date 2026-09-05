# Contributing

本文档面向当前 `main` / **v2.1.1**。当前 Liquid Glass 主线为 HyperOS 3.0.307+ / MiuiX PassBlur + OES/GLES zero-copy；1.x ScreenCapture 代码只存在于 `archive/1.x`，不要把旧 capture 架构重新接回当前实现。

## 构建

要求：

- Android SDK / compileSdk 37；
- JDK 17；
- libxposed API 101；
- Gradle 9.6.1 当前 wrapper。

Debug/CI 基线：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Release：

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

Debug 与 Release 都经过 AGP optimization / R8 路径。涉及反射入口时必须同时考虑 shrinker keep rule 与回归测试。

## 分支规则

`main` 是当前开发主线。功能和修复从 `main` 创建独立分支；不要再从旧 `api101-migration` 文档假定当前架构边界。

`archive/1.x` 仅用于保存旧 ScreenCapture/bitmap-readback 实现，不作为新功能开发基线。

## 配置规则

新增/修改配置时：

1. 先更新 `ConfigSchema`；
2. 历史偏好升级放入 `ConfigMigration`；
3. JSON shape / legacy alias 修改 `ConfigCodec` 并加 round-trip test；
4. preset 修改 `PresetManager`；
5. runtime 通过 `LiquidDockConfig` 读取 typed snapshot；
6. live visual toggle 应进入对应 runtime state，而不是让 Hook 直接读 raw preference；
7. 保持历史 key、`_tenths` 和 export/import 兼容，除非有明确 breaking migration。

`uiDefault`、`runtimeFallback`、`exportDefault` 可以故意不同，不要为了表面一致而合并。

## Hook / runtime ownership 规则

- 系统私有类与反射兼容放在 `*Hook` / `HookUtil` 边界；
- `HookUtil` 仅用于 vendor/system private 边界；LiquidDock 自有类之间禁止通过 `HookUtil` 反射访问，必须使用 typed Java / package-private API；
- optional vendor 调用使用 `tryInvoke*` 并显式检查 `succeeded()`；feature invariant 依赖的调用使用 `requireInvoke*`，禁止重新引入 silent-null facade；
- 纯策略尽量 Android/Xposed-free；
- 不要继续扩大 `MainHook` 的全局 mutable state；
- 不要让 `LiquidDockConfig.load()` 产生跨模块副作用；
- runtime disable 必须遵循“先 publish false，再 teardown ownership”；
- 已排队 callback 在执行前必须再次检查 live state；
- 只有保存过的 vendor state 才能被恢复；未知原生状态不要猜测或伪造。

## Zero-copy glass 规则

当前 glass backend 只有：

```text
MiuiX PassBlur -> SurfaceTexture/OES -> GLES -> Prismal renderer
```

禁止：

- 恢复 `ScreenCapture` fallback；
- 新增 bitmap readback；
- 用普通 redraw 代替 fresh-frame barrier；
- 在 Recents/HOME 返回时直接显示 stale static layer；
- 把 geometry generation 当作 wallpaper content generation。

涉及 producer lifecycle 的修改必须覆盖：

- HOME / APP；
- Recents；
- rotation；
- wallpaper freshness；
- Workstation；
- producer suspend/rebind；
- fresh OES frame 后才 reveal。

## Launcher-wide glass 规则

图标、Widget、小/大文件夹共享 root-wide `LauncherGlassSession`。不要为每个 material 单独建立 PassBlur producer。

组件开关：

- icon glass；
- widget glass；
- small-folder glass；
- large-folder glass。

必须支持运行时 selective release，不能因为关闭一个组件而破坏其它 glass 类型。

RemoteViews、MAML、folder recovery、drag/launch proxy 等异步路径必须在重新声明 ownership 前检查 live state。

## Grid / Widget 规则

- 不修改 MIUI occupancy / placement 所有权；
- 不 Hook `addOccupied()` / `transformToHVArray()` 猜 matrix 方向；
- Widget adaptation 只修改 allocation/frame；
- 当前显式适配 1×1、2×1、2×2、4×2；
- Widget 泛化应走 `WidgetClassifier` / `WidgetSpecRegistry`，不要继续散落 `itemType == ...` 分支；
- `HomeGridHook` 拆分时 rotation / refresh 最后处理。

## Workstation / Laptop 规则

工作台仍是**实验性、未完整支持**路径。

修改工作台代码时至少考虑：

- 进入/退出；
- Dock geometry / icon offset；
- Grid / All Apps；
- Divider；
- Recents 连续往返；
- rotation；
- wallpaper freshness；
- PassBlur producer suspend/rebind；
- 普通模式无回归。

Workstation composite customization 当前保持 restart-bound。不要只恢复其中一个子模块而留下混合状态。

## Divider 规则

`DockDividerHook` 必须保持 exact restore：

- 首次 mutation 前 snapshot width / height / margins / background；
- background 使用独立 Drawable snapshot，避免 alias；
- disable 时取消 pending pre-draw listener；
- restore snapshot + `requestLayout()`；
- restore 后释放 ownership，使下一次 enable 重新捕获当时的 vendor state。

详见 [DIVIDER.md](DIVIDER.md)。

## Stroke / Shadow 规则

`DockStrokeRenderer` 是当前 foreground stroke owner。

- 禁止恢复 1.x overlay 作为默认路径；
- disable stroke 时恢复原 foreground；
- Squircle / Fill-Diff 运行时切换要刷新已安装 renderer；
- whole-Dock shadow 与历史 stroke-shadow 是不同功能；
- 不要伪造未知 MIUI shadow 参数。

历史 stroke-shadow key 在最终方案确定前继续保留配置兼容。

## Runtime behavior 测试规则

涉及 ownership、freshness、animation、recovery、teardown、callback timing 或 lifecycle sequencing 的测试，必须驱动**生产代码实际使用的**纯 state / policy 对象，并对输入与输出做断言。

禁止：

- 读取 `src/main/java` 后通过 `contains()` / `indexOf()` / method slicing 推断 runtime 行为；
- 用 production source 中某几行的相对顺序代替状态转换测试；
- 为了访问同包 package-private state/policy 而使用 Java reflection。

同包 package-private state/policy 应直接 typed 调用。新增的纯模型不能是 test-only 镜像，必须由 production runtime 真正消费。

静态 source/config inspection 只保留给以下场景：

- R8 / keep rules；
- Gradle / build configuration；
- Android Manifest / Xposed scope；
- 明确的 architecture/API 禁令，例如“不得使用某 API / 不得跨自有模块反射”。

`RuntimeBehaviorTestPolicyContractTest` 会对 runtime-category tests 执行这一结构性 gate，防止后续重新退化为 source-string runtime contract。

## 文档规则

当前权威文档：

- [README.md](README.md) — 项目与兼容边界；
- [FEATURES.md](FEATURES.md) — 用户功能与设置边界；
- [ARCHITECTURE.md](ARCHITECTURE.md) — 当前 runtime 架构；
- [HOOKS.md](HOOKS.md) — 主要 Hook 与 listener；
- [DIVIDER.md](DIVIDER.md) — Divider ownership；
- [TODO.md](TODO.md) — 后续开发优先级；
- [CHANGELOG.md](CHANGELOG.md) — release 历史。

`docs/superpowers/plans` / `specs` 是历史设计记录，不要求跟随每个 release 重写。

## 提交前

至少执行：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

高风险 Grid / Workstation / PassBlur producer lifecycle 改动除 CI 外还需要真机回归。

## 许可

本项目基于 [GPL-3.0](LICENSE) 许可。