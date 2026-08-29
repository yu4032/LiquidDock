# LiquidDock TODO

当前主线为 **v2.1.1 / HyperOS 3.0.307+ / MiuiX 307 PassBlur + OES/GLES zero-copy**。旧 ScreenCapture / bitmap readback 架构仅保留在 `archive/1.x`，不再作为当前 TODO。

以下事项按优先级推进。

## 1. PR #65 后续硬化

PR #65 已完成 runtime visual ownership 与 Workstation Recents shared-glass producer recovery，后续仍需收紧：

- 将 `LauncherGlassSessionRegistry.prepareWorkstationRecentsReturn()` 对 `rebindProducer()` 的 `HookUtil.invoke(...)` 反射调用改为明确的 package-private 内部 API，移除对应 R8 keep rule 与“反射失败但日志仍显示 rebound”的假阳性风险。
- Workstation `onRecentViewHide` 只在 scene 确实处于 Recents covered 状态时 rollover shared producer；防止厂商重复派发 hide 时重复重建 BufferQueue endpoint。
- 为 producer rollover 增加明确成功/失败诊断，不要只按 session 数量计数。
- 真机回归：工作台进入 Recents → 返回 HOME，确认整个 shared glass layer 自动恢复，不依赖长按图标或其它交互触发。
- 消除 `MiuixLauncherStaticGlassHook.installMamlBackgroundOwnershipHook(...)` 的 javac varargs warning，明确 `Class<?>[]` 展开语义。

## 2. 工作台 / Laptop 完整适配

**当前仍未视为完整支持。** v2.1.1 已修复 Workstation 从 Recents 返回时 shared PassBlur producer 丢失，但工作台仍横跨 Dock、Grid、All Apps、Divider 与 Launcher glass lifecycle。

真机完成标准至少包括：

- 进入/退出工作台；
- 普通桌面位置 backup/restore；
- Dock 宽度、图标 spacing/offset；
- All Apps 横竖屏；
- Recents 打开/关闭与连续往返；
- 旋转；
- PassBlur/OES producer suspend/rebind/fresh-frame recovery；
- wallpaper freshness generation；
- Liquid Glass suspension/recovery；
- 普通模式无回归。

架构上继续拆出 `WorkstationModeController` 与独立 Workstation module。结构性 Workstation 配置保持 restart-bound，除非未来建立完整、可逆的 runtime restore 路径。

## 3. Widget detection / span extensibility

- 移除 `WidgetGridSizing` 的 static `widgetAdaptationEnabled` 全局状态；由安装模块持有 immutable config。
- 引入 `WidgetClassifier`：集中处理 `ItemInfo.isWidget()`、item type fallback 和未来 HyperOS 变体。
- 引入 `WidgetSpecRegistry`：当前先只登记 1×1、2×1、2×2、4×2，后续新增 span 不修改核心 Hook。
- 删除未接入活动路径的 legacy `HomeGridHook.adaptTwoByOneWidget(...)` 和对应日志缓存。
- Widget adaptation 只修改像素 allocation/frame，不接管 MIUI placement/occupancy。

## 4. `HomeGridHook` 拆分

按低风险到高风险逐步拆出：

1. Widget adaptation；
2. Page indicator；
3. Folder alignment；
4. Cell geometry；
5. Grid rotation / refresh（最后拆）。

必须保持：

- 当前横竖屏布局行为与 orientation-specific memory；
- lazy/off-screen page 几何准备；
- `LayoutTransformRuleGridChanged` metadata；
- 不 Hook `addOccupied()` / `transformToHVArray()`；
- 不改变 MIUI occupied matrix / placement 所有权。

## 5. `MainHook` 收缩为 composition root

- 把 Dock/Grid/Glass/Workstation 安装拆成独立模块。
- 把 mutable static 状态移到对应 controller/view 生命周期所有者。
- 顶层只负责读取 immutable config、安装模块与进程级 wiring。
- 重新审计 master-switch 边界：主开关完整启停仍是 restart-bound，不允许出现“视觉 owner 已释放但结构 Hook 仍活动”的误导语义。

## 6. Launcher-wide zero-copy glass session 收敛

当前共享 glass 已集中到 `LauncherGlassSession` / `LauncherGlassSceneController`，后续优先拆纯职责，不改变现有视觉与 freshness 语义：

- PassBlur producer endpoint lifecycle / rebind policy；
- wallpaper / scene generation 与 fresh-frame barrier；
- EGL/OES input ownership；
- static output 与 drag output registry；
- rotation settle / producer generation transition policy；
- renderer / Prismal scene composition。

必须保持：

- zero-copy only，不恢复 ScreenCapture fallback；
- Recents / HOME 不显示 stale frame；
- Workstation producer 单帧 pulse / pause 语义；
- rotation settle 后才允许新 producer 发布；
- wallpaper freshness generation 仍是内容权威。

## 7. 描边阴影后续决定

foreground `DockStrokeRenderer` 已替代旧描边 overlay。后续二选一：

- 设计适配 foreground renderer 的新描边阴影实现；或
- 正式标记该视觉能力 deprecated，但继续保留历史配置 key 的读入/导入兼容。

在决定前不要删除旧 key，也不要把 1.x overlay 实现重新接回去。

## 8. CI / 构建清理

- `actions/setup-java@v4` 升级到 `v5`。
- 检查 `checkout` / `upload-artifact` / `gradle/actions` 的 Node 24 兼容版本，清掉 Node 20 deprecation warning。
- workflow artifact retention 与仓库实际最大值统一为 2 天，避免每次构建产生自动降级 warning。
- 继续让 Debug 与 Release 都经过 R8/optimization 路径，避免只在 release 出现反射/keep-rule 回归。

## 9. 清理与架构 gate

- 去除过时 compatibility facade / dead helper。
- 尽量让 pure policy 不依赖 Android/Xposed。
- 增加 schema/codec/architecture/runtime-ownership regression tests。
- 每个阶段运行 `testDebugUnitTest` + `assembleDebug`。
- Grid / Workstation / PassBlur producer lifecycle 等高风险改动必须追加真机回归。

## 10. Widget background hide rules 用户自定义化（后期）

当前 `widget_background_rules.xml` 只承载内置、进程启动时读取的保守规则。后续增加用户自定义隐藏规则层，但不能把它变成任意反射/脚本入口：

- 设置页支持逐条启用/禁用规则，并明确内置规则与用户规则的覆盖/优先级策略；
- 支持导入/导出带版本号的规则配置，导入前完成 schema/version/字段白名单校验；
- 自定义 action 仅允许声明式 identity match + `hide-element`，拒绝任意方法调用、字段写入、表达式或脚本；
- 支持 `productId`、`appPackage`、`spanX/spanY`、`configSpanX/configSpanY` 等现有 identity 条件，后续扩展字段必须保持向后兼容；
- 提供安全 preview/restore：所有目标解析成功后才允许提交隐藏，失败或目标缺失不得产生 partial mutation；
- 明确规则冲突、优先级和诊断输出，使用户能知道最终命中了哪条规则；
- 未知小组件默认保持 diagnostic-only，不通过视觉启发式或递归遍历猜测背景元素；
- runtime hot reload 只在 claim/release 完整可逆并通过真机回归后再开放，否则保持进程启动时加载。
