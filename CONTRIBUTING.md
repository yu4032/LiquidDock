# Contributing

本文档面向当前 `main` / **v2.4.1**。当前 Launcher Liquid Glass 主线为 HyperOS 3.0.307+ / MiuiX PassBlur + OES/GLES + Prismal；Security Center 使用独立的 `:ui` 进程和 capability-driven semantic contract。

不要把 `docs/superpowers/plans` / `specs` 中的阶段性设计当作当前实现。开发基线始终是当前生产源码、ConfigSchema、build/CI 和根文档。

---

## 1. Build baseline

要求：

- Android minSdk 33；
- compileSdk / targetSdk 37；
- JDK 17；
- Gradle 9.6.1；
- Android Gradle Plugin 9.3.0；
- libxposed API 101。

本地最低验证：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Release：

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

Debug 与 Release **都启用 optimization / R8**。不要假设 debug APK 未混淆；CI debug build 就是 shrinker regression gate。

主 CI 还会扫描 Security Center / RootPassBlur 源码，禁止重新引入 `ScreenCapture`、`PixelCopy`、backdrop `Bitmap.createBitmap` 和固定 `postDelayed` capture workaround。

---

## 2. Branch / scope rules

- 从最新 `main` 建独立分支；
- 一个 PR 聚焦一个问题或一个明确架构批次；
- `archive/1.x` 只用于旧 screenshot-era 实现，不是新开发基线；
- vendor 反编译产物不提交到普通源码树，只提交可复现 metadata / contract / derived findings；
- 修改 HyperOS 私有行为时记录目标 build、真实调用边界和 fail-closed 条件。

---

## 3. Configuration contract

新增/修改 persisted setting：

1. `ConfigSchema` 先登记 key / type / UI default / runtime fallback / export default / range / storage mode；
2. 历史 SharedPreferences 迁移放 `ConfigMigration` / `LegacyConfigMigration`；
3. JSON shape/alias 由 `ConfigCodec` 负责；
4. preset 由 `PresetManager` 负责；
5. injected runtime 使用 `ConfigReader` -> immutable `LiquidDockConfig`；
6. live visual toggle 进入相应 runtime state；
7. 不在 feature Hook 内直接解释历史 JSON 或私自创建 persisted key。

`uiDefault`、`runtimeFallback`、`exportDefault` 允许有意不同。不要为了“看起来统一”破坏兼容语义。

历史 key 仍存在不等于历史实现仍 active；例如 screenshot-era quality keys 可能仅为 import/export compatibility。

---

## 4. Process ownership

### Launcher

`com.miui.home` 承载主要 Dock/Grid/Glass/Recents/Workstation 功能。

### SystemUI

`com.android.systemui` 仅作为 HOME/keyguard timing source。不要把 Launcher renderer/session 搬入 SystemUI。

### Security Center

Xposed scope 是 package `com.miui.securitycenter`，但 runtime 必须通过 `SecurityCenterProcessPolicy` 限制为 `com.miui.securitycenter:ui`。

Security Center 不运行 Launcher migration，不调用 `MainHook.install()`，也不共享 Launcher feature-level mutable state。

---

## 5. Reflection rule: project-owned code must be typed

这是硬规则。

### LiquidDock 自有对象

禁止：

```java
HookUtil.getField(session, "privateField")
getDeclaredMethod("projectOwnedMethod")
Class.forName("com.hellovoid.liquiddock.SomeInternalClass")
```

只要对象和被访问成员都属于 LiquidDock，就应增加 typed Java / package-private API。

原因不是代码风格，而是 R8 correctness：private field/method 可以被 rename/inline/merge，而字符串不会自动保持正确语义。

不要用：

```proguard
-keep class com.hellovoid.liquiddock.** { *; }
```

来掩盖自反射设计问题。

### Vendor/framework object

Android/HyperOS 私有边界可以使用反射，但要区分：

- optional capability -> `tryInvoke*` / explicit `succeeded()`；
- feature invariant -> `requireInvoke*` / fail visibly；
- 不重新引入 silent-null facade，让“调用失败”和“合法返回 null”不可区分。

---

## 6. R8 cross-ClassLoader string hazard

这是与 project self-reflection 不同的第二类风险。

若 LiquidDock 自己也打包某个类，而代码把该类的字符串名交给 **Launcher ClassLoader**：

```java
Class.forName("androidx.recyclerview.widget.RecyclerView$State", false, launcherClassLoader)
```

R8 可能把反射字符串适配成模块自身的混淆名（曾实际变成 `RecyclerView$a`）。该名称在 `com.miui.home` ClassLoader 中不存在，导致精确 Hook 安装失败。

处理顺序：

1. 先证明目标 ClassLoader / exact signature 确实需要字符串二进制名；
2. 使用最窄的 `-keepnames`；
3. 增加 `R8ReleaseKeepContractTest` 或同级静态 architecture contract；
4. 用 R8-enabled debug APK 验证；
5. 不扩大成整包 `-keep`。

当前已有 targeted protection：

```proguard
-keepnames class androidx.recyclerview.widget.RecyclerView
-keepnames class androidx.recyclerview.widget.RecyclerView$State
```

`runtime-reflection.keep` 应继续保持窄范围。

---

## 7. Zero-copy glass rules

活动 backdrop pipeline：

```text
MiuiX PassBlur
 -> Surface / SurfaceTexture
 -> external OES texture
 -> GPU normalization / overscan
 -> Prismal
 -> output surface
```

禁止在 active glass source path 引入：

- ScreenCapture fallback；
- PixelCopy fallback；
- CPU backdrop bitmap readback；
- texture readback + re-upload；
- fixed-delay “等一会再显示”代替 fresh-frame authority。

小型 UI drawable 分析 Bitmap（例如快捷菜单图标 20×20 颜色分类）不属于 backdrop capture，但必须保持 bounded、可缓存且不能进入 render frame loop。

---

## 8. Source, render and freshness are separate

Workspace 规则：

- native PassBlur spatial scale 保持 authority；
- local physical FBO resolution 可以在 OES normalization 后调整；
- `SurfaceTexture.updateTexImage()` drain 不能被 render FPS cap 阻断；
- source-driven producer 不需要 LiquidDock 创建固定 Choreographer capture pump；
- fresh scene generation 可以越过普通 render throttle；
- producer bind/rebind success != fresh content；
- redraw/invalidate != wallpaper/source freshness。

涉及 source lifecycle 的修改至少覆盖：

- HOME <-> APP；
- Recents；
- keyguard/unlock；
- rotation/root replacement；
- wallpaper change；
- Workstation；
- producer rollover/rebind；
- fresh frame 后 reveal。

---

## 9. Shared Launcher glass rules

图标、Widget、小/大文件夹共享 root-wide source/session。不要给每个 material 建独立 PassBlur producer。

每种节点必须保持：

- component-specific live gate；
- independent static node geometry；
- reversible vendor material ownership；
- stale callback 重新检查 runtime state；
- drag / app-launch proxy 不与静态 node 同时拥有可见 presentation。

### Drag

MIUI DragView 必须继续作为 drag/drop 逻辑和移动 geometry authority。LiquidDock overlay 只负责视觉 presentation；不能用原 Workspace View 猜 moving rect。

### Shortcut popup

Popup source/session 与 ordinary static Workspace compositor 分离。dark-mode adapter 只能使用 Android typed View/Drawable API，不允许为了改文字/图标去反射 LiquidDock 自有成员。

---

## 10. Widget rules

- glass background ownership、dark-content adaptation、component hiding 是三条独立 concern；
- RemoteViews update / MAML lifecycle 后要重新 reconcile；
- component hiding 使用受约束 selector 和可恢复 mutation；
- 不开放任意 script/method-call DSL；
- discovery/selector failure 应有 bounded diagnostic，不 silently pretend success。

Grid Widget adaptation 只改变 allocation/frame，不接管 occupancy matrix。

---

## 11. Grid rules

- MIUI owns placement and occupancy；
- 不 Hook `addOccupied()` / `transformToHVArray()` 来猜 matrix；
- profile 支持 8×4 / 10×6，portrait 自动交换 rows/columns；
- orientation memory 与 off-screen/lazy page preparation 必须保持；
- `WorkspaceDropRuleHook` 可扩展合法坐标，但不变成 placement engine。

Launcher 4.50 icon-size scaling必须继续使用 measure-domain transaction，不能直接修改共享 `GridConfig`。

---

## 12. Workstation rules

Workstation 是 composite experimental path。修改任一子功能时至少考虑：

- 进入/退出；
- Dock geometry；
- icon top/bottom offset；
- Workspace Grid；
- All Apps；
- Divider；
- Recents 往返；
- rotation；
- wallpaper freshness；
- producer endpoint lifetime；
- normal-layout backup/restore。

单一 visual owner 可 live restore，不代表整体 Workstation structure 可热卸载。

---

## 13. Security Center rules

当前 compatibility contract 是 semantic/capability-driven，不是 fixed versionCode / obfuscated field map。

新增兼容时优先使用：

- stable class relation；
- method signature；
- public semantic getter；
- resource capability；
- unique structural relation；
- explicit assistant-type discriminator。

歧义必须 reject，不要“选第一个看起来像的”。

`SecurityCenterPassBlurContinuousAuthority` 和 `SecurityCenterVendorMaterialState` 是进入 feature hooks 前的基础 capability；如果它们不可用，应 fail closed。

vendor material release 应重放已观察状态，不调用猜测的 private restore helper。

---

## 14. Runtime ownership / restore

通用要求：

- first mutation 前 snapshot 原 state；
- disable 先 publish false；
- pending callback 执行前重新检查；
- remove listeners/observers；
- restore snapshot；
- release snapshot ownership；
- re-enable 后重新捕获当时 vendor state。

只恢复真正保存过的状态。不要构造“可能的默认 MIUI 参数”。

---

## 15. Runtime tests vs static contracts

Runtime ownership / freshness / animation / recovery 测试必须调用**生产 runtime 真正在用的** typed state/policy。

禁止用以下方式证明 runtime behavior：

- 读取 production source 后 `contains()` 猜状态转换；
- `indexOf()` / `substring()` / `split()` 推断执行顺序；
- 为了访问同包 package-private state 再用 Java reflection。

`RuntimeBehaviorTestPolicyContractTest` default-deny production source readers。

允许 source inspection 的典型场景：

- R8 / keep rules；
- Gradle / build config；
- Manifest/Xposed scope；
- 明确的 architecture/API 禁令；
- vendor signature/static wiring contract。

`LEGACY_SOURCE_DEBT` 当前仍有显式历史清单，只能减少，不能为新测试增加例外。

---

## 16. CI / verification

PR 至少通过 `API101 migration build`：

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

并通过 Security Center/root PassBlur zero-copy audit。

涉及真实 vendor lifecycle 的高风险修改还需要目标设备验证；CI green 不等于 MIUI private behavior 已在设备上验证。

---

## 17. Documentation authority

当前根文档：

- [README.md](README.md) — 项目、安装、兼容边界
- [FEATURES.md](FEATURES.md) — 用户功能/设置
- [ARCHITECTURE.md](ARCHITECTURE.md) — runtime 架构/ownership
- [HOOKS.md](HOOKS.md) — Hook/listener/reflection 边界
- [DIVIDER.md](DIVIDER.md) — Divider contract
- [TODO.md](TODO.md) — active debt
- [CHANGELOG.md](CHANGELOG.md) — release history/current-main changes

历史 plans/specs 不要求随 release 改写，也不能覆盖 production truth。

## License

本项目基于 [GPL-3.0](LICENSE)。
