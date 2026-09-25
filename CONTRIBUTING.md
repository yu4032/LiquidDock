# Contributing

本文档面向当前 `main` / **v2.5.0**。生产源码、设置页、`ConfigSchema`、构建配置与当前根文档是开发事实来源；`docs/superpowers/*` 只保存历史方案与验证记录。

## 1. Build baseline

当前要求：

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

Debug 与 Release 都启用 optimization/R8。不要假设 debug APK 保留原始类名；CI debug build 本身就是 shrinker regression gate。

## 2. Branch and PR rules

- 从最新 `main` 建分支；
- 一个 PR 聚焦一个明确问题或一个可审计的结构批次；
- 不从历史计划文档反推当前实现；
- vendor 反编译结果作为证据使用，不直接把大体量反编译源码复制进生产树；
- 修改 HyperOS 私有行为时，记录目标系统版本、真实生命周期入口和失败策略；
- 真机日志与真实行为优先于静态猜测。

## 3. Documentation rule

文档必须和代码同步更新。

### 用户文档

`README.md` / `README_EN.md` 只描述：

- 能做什么；
- 支持范围；
- 安装与作用域；
- 用户可见限制。

不要把内部 Hook 名、R8 事故、PassBlur pipeline 或调试过程塞进 README。

### 当前技术文档

以下文件必须描述当前 `main`：

- `FEATURES.md`
- `ARCHITECTURE.md`
- `HOOKS.md`
- `CONTRIBUTING.md`
- `TODO.md`
- `DIVIDER.md`
- `THIRD_PARTY_NOTICES.md`
- `docs/DEPRECATED_SOURCE.md`

### 历史文档

`docs/superpowers/plans`、`specs`、`verification` 是时间点记录。保留原内容，但必须带历史归档提示，不能被引用为当前 runtime contract。

## 4. Configuration contract

新增或修改 persisted setting 时：

1. 主配置先登记到 `ConfigSchema`；
2. 历史 SharedPreferences 迁移放 `ConfigMigration` / `LegacyConfigMigration`；
3. JSON shape / alias 由 `ConfigCodec` 管理；
4. 默认配置由 `PresetManager` 管理；
5. runtime 通过 `ConfigReader` -> `LiquidDockConfig`；
6. 可热切换视觉状态进入对应 runtime state；
7. feature Hook 不私自发明未登记的主配置 key。

第三方应用 profile 可以有独立 preferences/profile owner，但必须有明确 namespace 与备份兼容策略。

### Single default profile

当前只有一套默认配置。不要重新引入“归零默认 + 调校预设”双体系。

首次空配置可以 seed 默认 snapshot；升级已有配置不得无条件覆盖。

以下安全默认必须保持显式 opt-in：

- Security Center glass；
- SystemUI app-caption menu glass；
- debug logging。

## 5. Process ownership

当前 scope：

```text
com.miui.home
com.android.systemui
com.miui.securitycenter
com.google.android.inputmethod.latin
com.android.quicksearchbox
```

### Launcher

负责 Dock、Grid、Workstation、Recents、Launcher glass、dialog、ShortcutMenu 等核心功能。

### SystemUI

负责 HOME/keyguard timing，并可选负责 app-caption/handle-menu glass。不要把 Launcher Workspace session 搬进 SystemUI。

### Security Center

只有 `com.miui.securitycenter:ui` 进入对应功能。不要在其他 Security Center 进程安装完整 glass graph。

### Gboard / MIUI Search

通过 `ThirdPartyGlassAdapterRegistry` 安装独立 adapter。不要调用 Launcher migration 或 `MainHook`。

## 6. Project-owned reflection is prohibited

只要对象和成员属于 LiquidDock，就优先使用 typed/package-private API。

禁止用：

```java
Class.forName("com.hellovoid.liquiddock.SomeInternalClass")
getDeclaredField("projectPrivateField")
getDeclaredMethod("projectPrivateMethod")
```

来访问自有代码。

R8 可以 rename/inline/merge private implementation；用 keep rule 掩盖自反射设计不是可接受修复。

## 7. Vendor reflection must be semantic

Android/HyperOS/Gboard 私有边界可以使用反射，但要遵守：

- 优先稳定公开/半公开语义类名；
- 优先继承关系、参数签名、资源名、唯一结构关系；
- 不以 JADX 生成的混淆名作为长期 contract；
- optional capability 和 required invariant 要区分；
- 歧义必须 reject；
- 失败时保留原生界面或明确 fail closed。

## 8. R8 cross-ClassLoader hazards

已经出现过两类真实事故：

1. Launcher RecyclerView 精确签名中的类名字符串被 R8 identifier adaptation；
2. Dialog native-night 曾把 `androidx.appcompat.app.AppCompatDialog` 字符串改写成 `v9`，然后交给 Launcher ClassLoader 导致 ClassNotFoundException。

处理顺序：

1. 能通过目标进程参数类型/继承关系/资源关系发现目标，就不要使用 class-string；
2. 必须保留精确二进制名时，使用最窄 `-keepnames`；
3. 增加静态 R8 contract；
4. 用 R8-enabled debug CI；
5. 高风险路径再用签名包真机验证。

不要扩大为整包 keep。

## 9. Active backdrop rules

活动玻璃 source path 禁止重新引入：

- ScreenCapture fallback；
- PixelCopy fallback；
- CPU backdrop Bitmap readback；
- texture readback + re-upload；
- fixed-delay freshness workaround。

允许的 bounded Bitmap 场景必须与 backdrop capture 无关，例如 ShortcutMenu 小图标颜色分类。

## 10. Source, render and freshness

必须区分：

- source endpoint；
- source frame；
- scene generation；
- wallpaper generation；
- producer generation；
- output frame；
- visible presentation。

禁止把以下信号当成 fresh-content proof：

- bind 成功；
- View invalidate；
- redraw；
- Surface Java 对象仍 valid；
- 固定等待若干毫秒。

涉及 Workspace source lifecycle 的修改至少复测：

- HOME <-> APP；
- Recents；
- unlock；
- rotation/root replacement；
- wallpaper change；
- Workstation；
- producer rollover；
- 连续壁纸切换。

## 11. Dock rules

Dock geometry、stroke、whole shadow、stroke shadow、Divider、mirror shortcut、glass material 是不同 owner。

原则：

- first mutation 前 snapshot；
- disable 先 publish false；
- pending callback 必须重新检查；
- restore 只恢复真实保存状态；
- re-enable 后重新捕获当前 vendor state。

不要通过一个全局 restore 顺便重写其他 owner。

## 12. ShortcutMenu rules

ShortcutMenu 的 source/session 与普通 Workspace static compositor 独立。

必须覆盖：

- Workspace；
- Home-forwarded Dock；
- 非桌面拉出的 direct Dock。

不要重新要求 strict View instance identity；Dock root/owner 在实际系统流程中会变化。

不要在 `ACTION_CANCEL` 上无条件销毁仍可能用于系统长按/拖拽路径的 early state。

## 13. Dialog rules

Launcher dialog glass 只限定在已验证的 uninstall/remove/second-confirmation dialog scope。

Native dark mode：

- 必须在 MIUIX 构建内容前决定；
- 使用原生 night resources；
- AlertController constructor 通过 `Context + Dialog + Window` 语义发现；
- 禁止重新引入 AppCompatDialog class-string 跨 ClassLoader 查找。

Dialog 的 dim View、outside-tap 行为、原生背景 restore 都必须保持。

## 14. Widget rules

Widget 功能分为：

- glass background；
- dark content；
- component hiding；
- grid allocation adaptation。

不要把它们合并成同一个不可恢复 mutation。

Component hiding：

- 只允许受约束 selector；
- 不开放任意 method/script DSL；
- 扫描失败不做部分写入；
- import/export 不混入扫描目录。

Grid adaptation 不接管 MIUI occupancy matrix。

## 15. Workstation rules

Workstation 是组合路径。任何修改都要考虑：

- enter/exit；
- Dock width；
- icon offsets；
- Workspace grid；
- All Apps；
- Divider；
- Recents return；
- rotation；
- wallpaper freshness；
- normal-layout restore。

单个 visual owner 能热恢复，不代表整个 Workstation structure 已支持完整 runtime uninstall。

## 16. SystemUI app-caption menu rules

必须保留系统原生：

- controls；
- click behavior；
- window/surface animation authority。

LiquidDock 只接管支持的背景 visual。

所有 Hook callback / render callback 异常都必须被隔离，不能把异常传播到 SystemUI 主流程。

## 17. Security Center rules

兼容性主 authority 是 semantic/capability resolver。

优先：

- class relation；
- method signature；
- semantic getter；
- resource capability；
- unique structural relation。

禁止：

- versionCode 白名单作为主判定；
- obfuscated field/method map；
- “第一个候选” fallback。

Game / Video / Global Dock / All Apps 都必须在真实设备上验证 presentation 和 cleanup。

## 18. Gboard and MIUI Search rules

第三方 adapter 必须限制在目标应用和已确认结构。

Gboard：

- floating keyboard 与 toolbar lifecycle 分离；
- handle resize policy 不与 glass setting 混为一体；
- 结构识别失败保留 stock UI。

MIUI Search：

- 只替换已确认的 SearchActivity 主背景；
- 不扩大成整个进程任意 blur View replacement；
- resume/reattach 后重新确认 freshness。

## 19. Runtime tests vs static contracts

Runtime ownership/freshness/animation/recovery 应测试生产使用的 typed state/policy。

Source inspection 只适合：

- vendor signature；
- R8/build config；
- Manifest/scope；
- architecture/API ban；
- static wiring。

`RuntimeBehaviorTestPolicyContractTest` 对 production source reader 默认 deny。

现有 legacy source-debt 只能减少，不能为了 CI green 新增例外。

## 20. Verification

PR 最低要求：

```bash
./gradlew testDebugUnitTest assembleDebug --stacktrace
```

并通过仓库 CI 中现有安全审计。

以下类型不能只靠 CI：

- HyperOS 私有时序；
- SystemUI window animation；
- Security Center carrier；
- Gboard/Searchbox vendor lifecycle；
- signed/R8 cross-ClassLoader 问题。

这些需要真实设备日志和视觉结果。
