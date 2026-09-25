# LiquidDock Architecture

本文档描述当前 `main` / **v2.5.0** 的生产架构。历史 `docs/superpowers/*` 记录的是阶段性设计与验证过程，不是当前 runtime contract。

当前工程基线：

```text
Android:        minSdk 33 / compileSdk 37 / targetSdk 37
Launcher:       com.miui.home release-4.50.x.x
SystemUI:       com.android.systemui
SecurityCenter: com.miui.securitycenter:ui
Third party:    Gboard / MIUI Searchbox
Hook API:       libxposed API 101
Renderer:       PassBlur + OES/GLES + Prismal
Build:          JDK 17 / Gradle 9.6.1 / AGP 9.3.0
Optimization:   Debug 与 Release 均启用
```

## 1. Process composition

`ModuleMain` 是进程级入口，并按 package/process 分流。

### 1.1 Launcher — `com.miui.home`

Launcher 是主功能进程，负责：

- 配置迁移；
- Dock；
- 主屏幕网格；
- 工作台；
- Launcher 静态玻璃；
- 拖拽玻璃；
- 文件夹、小组件与图标；
- ShortcutMenu；
- Launcher 对话弹窗；
- Recents 背景与操作按钮；
- HOME / wallpaper / rotation freshness。

当前主要组合入口：

- `MainHook`：Dock / Workstation / Grid 的安装顺序与 fallback composition；
- `Miuix307MaterialPipeline`：支持版本的 Dock 主材质路径；
- `MiuixLauncherStaticGlassHook`：Workspace 静态节点；
- `MiuixLauncherDragOverlayHook`：拖拽视觉；
- `MiuixFolderGlassHook`：文件夹；
- `MiuixShortcutMenuGlassHook`：快捷菜单；
- `LauncherUninstallDialogGlassHook`：卸载/移除/二次确认弹窗；
- `LauncherGlassRecentsHook`：Recents 状态与操作按钮；
- `LauncherWallpaperFreshnessHook`：壁纸内容更新；
- `RecentsBackgroundBlurHook`：多任务背景模糊；
- HomeGrid 系列 Hook / policy。

`MainHook` 现在是较薄的 composition root，不再承担大量 feature-level mutable state。历史文档中关于“MainHook God Class”的描述已经过时。

### 1.2 SystemUI — `com.android.systemui`

SystemUI 当前承担两类职责：

1. 为 Launcher 提供 HOME / keyguard 转场时序；
2. 可选地替换应用顶部窗口控制菜单的背景。

相关入口：

- `SystemUiKeyguardGoneSource`；
- `SystemUiHomeTransitionSource`；
- `SystemUiHandleMenuSurfaceAnimationAuthority`；
- `SystemUiHandleMenuGlassHook`。

因此“SystemUI 只负责时序、不渲染任何玻璃”已经不是当前事实。只有 Launcher Workspace 的主要渲染仍然归 Launcher 自己所有。

### 1.3 Security Center — `com.miui.securitycenter:ui`

虽然 Xposed scope 是 `com.miui.securitycenter`，但 `SecurityCenterProcessPolicy` 只允许 `:ui` 进程进入 Security Center 玻璃初始化。

安装顺序要求：

1. `SecurityCenterPassBlurContinuousAuthority`；
2. `SecurityCenterVendorMaterialState`；
3. 可选 `SecurityCenterSourceAuthorityHook`；
4. `SecurityCenterGlassHook`。

核心能力不可用时 fail closed，保留原生界面。

### 1.4 Third-party adapter processes

`ThirdPartyGlassAdapterRegistry` 当前注册：

| Profile | Package | Adapter |
| --- | --- | --- |
| `gboard.floating` | `com.google.android.inputmethod.latin` | Gboard floating keyboard + toolbar |
| `miui.searchbox` | `com.android.quicksearchbox` | MIUI Search main background |

第三方进程不运行 Launcher migration / `MainHook`，只加载自己的 adapter。

---

## 2. Configuration architecture

当前配置链：

```text
Compose settings
      ↓
Remote SharedPreferences
      ↓
LegacyConfigMigration / ConfigMigration
      ↓
ConfigSchema + ConfigCodec + PresetManager
      ↓
ConfigReader
      ↓
LiquidDockConfig immutable snapshot
      ↓
runtime states / feature owners
```

### 2.1 ConfigSchema

`ConfigSchema` 是主配置 key 的登记中心，负责：

- 类型；
- UI fallback；
- runtime fallback；
- export fallback；
- 范围；
- storage mode；
- export mode。

第三方适配中少量独立 profile key 由对应 preferences/profile class 管理。

### 2.2 Single default configuration

v2.5.0 只有一套内置默认配置。

`PresetManager.defaultValues()` 保存完整默认 snapshot。首次真正空配置启动时，`ConfigMigration` 会写入该 snapshot；已有配置不会被默认值覆盖。

`PresetManager.applyDefault()` 会清理当前受管理 key 后写回同一套默认配置。

安全例外固定为：

- `liquid_security_center_glass=false`；
- `liquid_systemui_handle_menu_glass=false`；
- `liquiddock_debug_log=false`。

因此默认配置不会自动接管 Security Center / SystemUI 高风险界面，也不会自动开启日志。

### 2.3 Runtime state owners

当前主要 runtime gate：

- `GlassRuntimeState`：Launcher glass component gates；
- `VisualRuntimeState`：Dock visual ownership；
- `SecurityCenterGlassRuntimeState`：Security Center 独立状态；
- `AnimationRuntimeState`：动画参数；
- `WorkstationRuntimeState`：工作台状态。

通用 disable 原则：

```text
publish disabled
    ↓
pending callback sees disabled
    ↓
release LiquidDock presentation
    ↓
restore captured vendor state
```

结构性 Hook 安装通常仍是 restart-bound。

---

## 3. PassBlur domain model

`PassBlurBindRequest` 显式携带 domain。当前 `PassBlurDomain`：

- `LAUNCHER_WORKSPACE`
- `SHORTCUT_POPUP`
- `DRAG_OVERLAY`
- `DOCK`
- `SECURITY_CENTER`
- `GBOARD_FLOATING`
- `MIUI_SEARCHBOX`
- `RECENTS_CAPSULE`
- `SYSTEMUI_HANDLE_MENU`
- `LAUNCHER_DIALOG`

不同 domain 可以有不同 host、exclusion 与生命周期，不能把任意 ViewRoot 当成可互换的 source authority。

---

## 4. Active glass pipeline

活动玻璃背景路径：

```text
native PassBlur producer
        ↓
Surface / SurfaceTexture
        ↓
GL_TEXTURE_EXTERNAL_OES
        ↓
GPU normalization / sampling
        ↓
Prismal
        ↓
feature-specific output
```

核心约束：

- active backdrop 不回退 ScreenCapture；
- 不使用 PixelCopy 作为玻璃背景 fallback；
- 不进行 CPU backdrop Bitmap readback；
- source bind 成功不等于内容已经 fresh；
- OES source drain 与昂贵 Prismal render 是两件事；
- render FPS 限制不能阻塞 source drain；
- fresh generation 可绕过普通 render throttle；
- fixed delay 不能替代真实 lifecycle/freshness authority。

ShortcutMenu 的小型 drawable 颜色分类 Bitmap 不属于 backdrop capture。

---

## 5. RootPassBlurBackend and sessions

`RootPassBlurBackend` 是多个 root-based feature 的底层 source owner，集中处理：

- native endpoint；
- Surface / SurfaceTexture；
- OES；
- source freshness；
- EGL source lifecycle。

上层 feature session 负责自己的：

- geometry；
- presentation；
- vendor material handoff；
- output lifecycle；
- failure recovery。

不同 feature 不应因为都使用 PassBlur 就被强行合成一个生命周期。

---

## 6. Dock architecture

`MainHook` 安装：

1. Workstation runtime；
2. config；
3. Dock foundation；
4. Grid；
5. Dock shadow ownership；
6. glass owner / fallback Dock customization。

当支持的 `Miuix307MaterialPipeline` 成功拥有 Dock 时，旧 fallback Dock customization 不再继续安装。

Dock 相关主要 owner：

- `DockBottomGeometryHook`；
- `DockResizeAnimationHook`；
- `DockStrokeRenderer`；
- `DockShadowOwnership`；
- `DockDividerHook`；
- `DockMirrorShortcutHook`；
- `DockIconAnimationGlassHook`。

Stroke、whole-Dock shadow、stroke shadow、Divider 分别维护自己的可恢复状态。

---

## 7. Launcher shared Workspace glass

Workspace 图标、小组件、文件夹等静态节点共享 Launcher root-wide source/session。

主要组件：

- `LauncherGlassSession`；
- `LauncherGlassSessionRegistry`；
- `LauncherGlassSceneController`；
- `LauncherGlassStaticLayer`；
- `LauncherGlassStaticNode`；
- `LauncherGlassVendorMaterialSuppressor`。

静态节点共享 source，但各自维护 geometry、node kind、visibility 与 presentation state。

### Freshness authority

Workspace freshness 需要区分：

- scene generation；
- wallpaper generation；
- producer generation；
- root replacement；
- fresh output frame。

普通 `invalidate()` 或 View redraw 不能充当“背景已经更新”的证明。

---

## 8. App transition and HOME recovery

Launcher app launch / return HOME 时，MIUI floating icon proxy 是 transition presentation authority。

LiquidDock 避免静态玻璃图标与系统 floating icon 同时可见，并通过：

- `LauncherGlassHomePresentationHook`；
- `SystemUiHomeTransitionRuntime`；
- `SystemUiKeyguardGoneRuntime`；
- fresh-frame barrier；

恢复 Workspace presentation。

解锁恢复不再依赖固定 fail-open 时间授权旧帧。

---

## 9. Live drag architecture

拖拽使用独立 live overlay。

大体生命周期：

```text
drag start
  ↓
prepare live source
  ↓
real DragView appears
  ↓
create visual mirror + glass overlay
  ↓
fresh output ready
  ↓
handoff presentation
  ↓
drag end / detach
  ↓
restore vendor presentation
  ↓
release overlay
```

MIUI DragView 继续拥有 drag/drop 逻辑与移动 geometry；LiquidDock 只接管视觉 presentation。

---

## 10. ShortcutMenu architecture

ShortcutMenu 使用独立 `SHORTCUT_POPUP` source/session，不复用普通 Workspace static output。

入口覆盖：

- Workspace touch；
- Home-forwarded Dock touch；
- 非桌面 Dock 自己窗口的 direct touch。

Dock early capture 可以跨系统 drag 产生的 `ACTION_CANCEL` 保留，并允许 DockContainerView/root churn 后继续 rendezvous。

Popup 出现后：

- 独立 full-screen/local output 与菜单绑定；
- 第一帧完成后释放 vendor material；
- dismiss 使用快速淡出；
- detach 时最终清理。

Dark-mode adapter 只修改菜单文字和经过颜色分类的近黑图标，避免把彩色第三方应用图标统一染白。

---

## 11. Launcher dialog architecture

`LauncherUninstallDialogGlassHook` 以 `BaseUninstallDialog` 构造生命周期为入口，覆盖：

- `DeleteDialog`；
- `RemoveDialog`；
- `SecondConfirmDialog`。

`LauncherDialogGlassCoordinator` 在真实 Dialog ViewRoot 内管理：

- behind-content source；
- MIUIX parent panel material handoff；
- dim presentation；
- glass output；
- restore。

### Native dark mode

`LauncherDialogNativeNightBridge` 在 Dialog 构造期间为 MIUIX 提供 night-qualified Context。

它不依赖 `androidx.appcompat.app.AppCompatDialog` 的跨 ClassLoader 类名字符串。当前通过 `AlertController` 构造器的稳定参数语义：

```text
Context + Dialog + Window
```

发现目标构造器。

这是 v2.5.0 的 R8 修复：签名/优化构建曾把类名字符串改写成 `v9`，导致目标进程 ClassLoader 无法解析。

---

## 12. Recents architecture

`LauncherGlassRecentsHook` 负责：

- Recents show/hide；
- Workspace covered state；
- wallpaper settle authority；
- Workstation producer recovery；
- Recents capsule bootstrap。

`LauncherRecentsCapsuleGlassHook` 单独处理：

- Clear All；
- device-interconnect capsule。

`RecentsBackgroundBlurHook` 只控制系统 Recents 背景模糊，与 glass source lifecycle 分离。

---

## 13. Wallpaper freshness

`LauncherWallpaperFreshnessHook` 跟随 Launcher 自己的 wallpaper update transaction。

关键原则：

- 壁纸变化要产生独立 content generation；
- 旧缓存不能因为 root 仍 valid 就继续冒充新壁纸；
- 连续更换壁纸时每次 transaction 都要有独立 freshness；
- Recents return 的 settle authority 与普通 wallpaper change 不能混为同一信号。

---

## 14. Workstation

Workstation 使用独立参数和 runtime state。

当前模块包括：

- `WorkstationModeHook`；
- `WorkstationDockCustomizationHook`；
- `WorkstationDockGeometryHook`；
- `WorkstationGridMarginPolicy`；
- `WorkstationNormalLayoutOwner`；
- `WorkstationProducerPolicy`；
- `WorkstationRecentsRecoveryPolicy`；
- `DockDividerHook`。

单个 visual owner 可以独立 restore，但完整 Workstation composite structure 仍不是完全 live-uninstallable 的事务。

---

## 15. Home grid architecture

`HomeGridHook` 现在是较薄的 composition root。

已拆分的主要 owner：

- `HomeGridProfileOverlayHook`；
- `HomeGridOrientationMemoryHook`；
- `HomeGridMutationCaptureHook`；
- `HomeGridDeviceConfigCountHook`；
- `HomeGridHorizontalCenteringHook`；
- `HomeGridVerticalBoundsHook`；
- `HomeGridCellGeometryHook`；
- `HomeGridFolderAlignmentHook`；
- `HomeGridPageIndicatorHook`；
- `HomeGridRotationRefreshHook`；
- `WorkspaceDropRuleHook`；
- `HomeGridDragBoundsHook`。

原则仍是：MIUI 拥有 placement/occupancy；LiquidDock 调整 profile、geometry、bounds 与合法 drop，不建立自己的 occupancy matrix。

---

## 16. Widget architecture

Widget 功能分成三条独立链：

1. glass background；
2. dark-content adaptation；
3. component hiding。

相关 owner：

- `LauncherWidgetBackgroundController`；
- `LauncherWidgetDarkContentAdapter`；
- `LauncherWidgetComponentDiscovery`；
- `LauncherWidgetComponentSelectionExecutor`；
- `WidgetBackgroundRuleEngine`；
- `WidgetComponentStore`。

RemoteViews/MAML 更新后需要重新 reconcile，隐藏操作必须可恢复。

`WidgetGridSizing` 目前仍有 process-static adaptation gate，是当前 TODO 之一。

---

## 17. SystemUI Handle Menu

`SystemUiHandleMenuGlassHook` 兼容两类入口：

- Xiaomi caption-menu window；
- AOSP/WMShell handle-menu layout。

它保持系统控件和系统动画 authority，只替换背景 presentation。

`SystemUiHandleMenuSurfaceAnimationAuthority` 提供菜单 Surface 动画 alpha，用于让玻璃显隐跟随系统动画。

能力不满足或 callback 失败时保留/恢复 stock material。

---

## 18. Security Center architecture

Security Center compatibility 以语义/能力为主，不使用固定 versionCode 或混淆名表。

`SecurityCenterSemanticContractResolver` 通过：

- class relation；
- method signature；
- stable semantic getter；
- resource capability；
- unique structural relation；

解析当前 build。

缺失或存在歧义就 reject。

当前主要 presentation modules：

- `SecurityCenterGlassCoordinator`；
- `SecurityCenterGlassSession`；
- `SecurityCenterGlassSinkView`；
- `SecurityCenterPassBlurContinuousAuthority`；
- `SecurityCenterVendorMaterialState`。

支持 Game / Video / Global Dock / All Apps carrier。

---

## 19. Gboard architecture

`ThirdPartyGlassAdapterRegistry` 在 Gboard 进程安装：

- `GboardPassBlurContinuousAuthority`；
- `GboardFloatingGlassHook`；
- `GboardHandwritingCapsuleGlassHook`。

悬浮键盘与工具栏共用 Gboard profile 外观，但拥有各自 geometry/presentation lifecycle。

`GboardFloatingHandlePolicy` 单独控制底部手柄拖动后的 resize 行为。

---

## 20. MIUI Searchbox architecture

`MiuiSearchboxGlassHook` 只替换 Search 主界面的稳定背景层，不接管搜索框自身内容。

主要组件：

- `MiuiSearchboxGlassSession`；
- `MiuiSearchboxGlassView`；
- `MiuiSearchboxPassBlurContinuousAuthority`；
- `MiuiSearchboxVendorMaterial`。

每次重新显示时会请求 fresh content，再完成 vendor material handoff。

---

## 21. Reflection and R8 rules

### Project-owned code

LiquidDock 自己的对象和成员必须优先使用 typed/package-private API。不要通过字符串反射访问自己的 private field/method。

### Vendor boundary

Vendor/private API 可以反射，但必须：

- 有明确语义；
- 区分 optional capability 与 required invariant；
- 失败时可诊断；
- 不把 JADX 混淆名当长期 compatibility contract。

### Cross-ClassLoader R8 hazard

如果模块也打包某个类，而代码把它的字符串类名交给目标进程 ClassLoader，R8 可能改写字符串。

已经出现过两类真实问题：

- RecyclerView 精确签名；
- Dialog native-night 的 AppCompatDialog 字符串被改写成 `v9`。

处理原则：

1. 能用目标进程的结构/参数语义发现，就不用 class-string；
2. 必须保留二进制名时才使用最窄 `-keepnames`；
3. Debug/Release 都走 R8；
4. 加静态 contract 防止退化。

---

## 22. Test architecture

Runtime behavior 应通过生产使用的 typed state/policy 测试。

源码字符串检查只用于：

- static API contract；
- vendor signature；
- build/R8 rule；
- Manifest/scope；
- architecture ban。

`RuntimeBehaviorTestPolicyContractTest` 对 production-source reader 默认 deny，并维护显式 allowlist / legacy debt。

---

## 23. Documentation authority

当前事实来源优先级：

1. 当前 `main` 生产源码；
2. `ConfigSchema` / settings UI / build config；
3. 根目录当前文档；
4. `CHANGELOG.md` 的版本历史；
5. `docs/superpowers/*` 历史记录。

`docs/superpowers/*` 不得覆盖当前生产事实。
