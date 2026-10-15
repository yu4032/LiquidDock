# Open Folder Liquid Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 文件夹玻璃随原生开合动画连续变形，展开后保留折射、高光及后方桌面模糊。

**Architecture:** 在现有文件夹 Hook 上接入独立的打开态协调器。复用 ShortcutPopup 的安全桌面 GPU 采样及稳定全屏输出，新增文件夹用途配置，保留原快捷菜单默认行为。纯状态与几何策略负责所有权、过期回调和动画交接，原生 View 适配仅提供实际边界与生命周期。

**Tech Stack:** Java 17、Android View/TextureView、API 101 Hook、RootPassBlurBackend、OpenGL ES / Prismal、JUnit 4、Gradle / R8。

**Spec:** `docs/superpowers/specs/2026-10-15-open-folder-liquid-glass-design.md`（用户已审阅确认）。

## Global Constraints

- 基线 main `fda290670be85c3a119ccee87bf39ac22672c889`；版本保持 `2.7.2` / `34`。
- 内部图标、文字清晰，面板后方的桌面图标及壁纸模糊可见。
- 原生动画作为唯一几何来源；背景固定于屏幕，不拉伸采样后的整张纹理。
- 打开后冻结安全背景，不递归采入面板、标题或内部图标。
- 首帧确认后接管原生背景，失败时完整保留原生显示。
- 沿用全局玻璃及对应小/大文件夹开关；不新增设置页面，不修改原生网格与操作。
- 先交付本地签名测试包，真机确认后再提交文件夹功能独立 PR。

## Review Focus

- 极快点击导致安全背景未准备好：不能隐藏原生内容或把打开中的文件夹采入背景（Task 1、3）。
- 动画副本淡出但面板仍在扩展：玻璃几何继续跟随，透明度不能随副本背景归零（Task 2、3）。
- A 文件夹关闭回调晚于 B 打开：旧回调不能释放 B 的背景或恢复 B 管理的材质（Task 1、4）。
- 旋转/工作台尺寸突变：旧几何背景不得拉伸到新窗口，失败则恢复原生显示（Task 2、3、4）。
- 改名、拖出图标或无动画关闭：内容仍可操作，关闭后 Workspace 玻璃恢复且无原生值泄漏（Task 1、4、5）。

## File Map

根包路径均为 `src/main/java/com/hellovoid/liquiddock/`；测试对应 `src/test/java/com/hellovoid/liquiddock/`。

- 新建 `OpenFolderGlassState.java`：Android-free 生命周期、打开序号、首帧与过期回调判断。
- 新建 `OpenFolderGlassGeometry.java`：Android-free 原生矩形验证、交接与当前几何。
- 新建 `OpenFolderGlassCoordinator.java`：背景准备、显示所有权、背景恢复、观察器和资源释放。
- 新建 `OpenFolderGlassHook.java`：原生 FolderCling/Folder/FolderAnimController 适配；调用协调器。
- 修改 `MiuixFolderGlassHook.java`：注册新 Hook，转发源图标按下及已有功能开关变化。
- 修改 `PassBlurDomain.java`、`PassBlurBindRequest.java`：新增 `OPEN_FOLDER` 采样用途。
- 修改 `ShortcutPopupSourceOverlay.java`、`ShortcutPopupGlassSession.java`：以重载参数提供文件夹用途，原有构造器与 attach 行为保持不变。
- 修改 `prismal/.../PrismalRenderer.java`：提供已准备背景纹理的只读访问，供打开态背景合成使用。
- 新建上述状态、几何与合成策略的行为测试，补充现有 PassBlur 用途测试。

### Task 1: 生命周期与首帧所有权

**Interfaces:** `OpenFolderGlassState` 提供 `long prepare()`、`boolean onBackdropReady(long serial)`、`boolean beginOpen(long serial)`、`boolean onPresented(long serial)`、`boolean beginClose(long serial)`、`boolean finishClose(long serial)`、`void release()`、`boolean owns(long serial)`、`boolean maySuppressNative()`。状态仅为 CLOSED、PREPARING、OPENING、OPEN、CLOSING；同一实例只允许当前序号操作。

- [x] 写失败测试：准备完成前 `maySuppressNative()==false`；背景就绪且打开、玻璃呈现后才为 true；关闭完成恢复 false。
- [x] 写失败测试：`prepare()` 创建新序号后，旧 `onPresented/finishClose/onBackdropReady` 返回 false，新状态不变；release 后重复完成回调无效。
- [x] 写失败测试：无动画关闭、打开中取消、关闭中再次打开都能结束到确定状态，不留下原生抑制标记。
- [x] 运行 `:testDebugUnitTest --tests '*OpenFolderGlassStateTest'`，确认针对缺失状态行为失败。
- [x] 实现上述接口；所有异步事件必须校验序号，不引入计时器模拟完成。
- [x] 重跑测试并提交 `feat: model open-folder glass ownership`。

### Task 2: 原生矩形与动画交接

**Interfaces:** `OpenFolderGlassGeometry` 提供不可变 `Frame(left, top, right, bottom, radius)`；`Frame acceptAnimation(Frame observed, int rootWidth, int rootHeight)`、`Frame acceptPanel(Frame observed, int rootWidth, int rootHeight)`、`Frame current()`、`void reset()`。输入是已投影到根坐标的原生实际矩形，不在策略中用时间或独立弹簧重建动画。

- [x] 写失败测试：原生矩形从 `(100,200,180,280)` 到 `(40,100,640,800)`，输出严格跟随观测值；圆角不得大于短边一半。
- [x] 写失败测试：传入 NaN、零宽高、无效根尺寸不能发布几何；交接期间缺失观测保持上一有效帧，显式 reset 后没有可用帧。
- [x] 写失败测试：背景矩形扩展时，原生背景 alpha 为 0 不影响几何输入；旋转变尺寸要求调用方 reset，不拉伸旧帧。
- [x] 运行对应测试确认失败；实现接口，重跑并提交 `feat: track native folder glass geometry`。

### Task 3: 桌面 GPU 背景与模糊合成

**Interfaces:** 新增 `PassBlurBindRequest.openFolder(View root)` 与 domain `OPEN_FOLDER`。`ShortcutPopupSourceOverlay.attach(View launcherRoot, Listener listener, String title)` 重载用于独立采样根。`ShortcutPopupGlassSession` 新增用途参数重载，原有构造器使用 SHORTCUT_POPUP 默认用途；文件夹用途先绘制模糊背景，再叠加玻璃。`PrismalRenderer.preparedBlurTexture()` 只读暴露当前 GL 线程拥有的已准备模糊纹理。

- [x] 新增用途行为测试：OPEN_FOLDER 的采样比例、排除根和系统层规则明确，未绑定 Workspace 解锁门；原 SHORTCUT_POPUP 默认值不变。
- [x] 新建纯合成策略 `OpenFolderBackdropPolicy`：`float backdropOpacity(float nativeOpenProgress)` 将有限值限制到 0–1，非法值为 0；玻璃显示不使用动画副本背景 alpha。补充 `0→0`、`.5→.5`、`1→1`、NaN→0 行为测试。
- [x] 运行失败测试；实现用途与合成策略。为 session 增加用途重载时保持现有快捷菜单调用点无需修改。
- [x] 背景仅从未显示文件夹的源捕获；已打开态严禁重新采样。捕获完成后关闭更新，保存 GPU 纹理；同一份原始纹理供模糊背景与玻璃材质使用。
- [x] 合成时模糊背景固定于根坐标，玻璃节点在其上绘制；输出 Surface 尺寸稳定。尺寸失配时通知协调器失败并恢复原生，避免用旧背景伸展。
- [x] 运行用途、合成策略与既有 ShortcutPopup/Prismal 测试；提交 `feat: compose frozen folder backdrop and liquid glass`。

### Task 4: 接入原生文件夹动画与材质恢复

**Interfaces:** `OpenFolderGlassCoordinator` 提供 `prepare(ViewGroup sourceIcon)`、`onOpening(View folderCling)`、`onOpenFinished(View folderCling)`、`onClosing(View folderCling)`、`onClosed(View folderCling)`、`releaseAll()`；每个当前根状态持有 Task 1 序号、Task 2 几何及 Task 3 源/输出。`OpenFolderGlassHook.install(ClassLoader loader, LiquidDockConfig config)` 在现有文件夹 Hook 中注册。

- [x] 源 FolderIcon 的 ACTION_DOWN 提前 prepare；未使用的预备源在取消、拖动或替换时释放。通过原生 `FolderCling.open()`、`close(boolean)`、`onFolderOpenAnimationFinish()` 及 `Folder.onClose` 最终完成边界更新状态，关闭与恢复必须幂等。
- [x] pre-draw 读取 `getFolderAnimIcon().getIconImageView()` 的全局变换、`mCustomWidth/mCustomHeight` 和当前圆角，打开完成后改用 `getFolder().mBackgroundView` 的实际网格面板边界；以 Task 2 策略交接。
- [x] 在模块首帧呈现后才管理原生背景 Drawable 的绘制值；保存原值及所有权，禁止替换原生依赖具体类型的 Drawable，禁止把内部图标/文字一起设透明。
- [x] 使用现有 `OwnedValueState` 的条件恢复方式：仅恢复仍由模块持有的值。快捷打开 B 后，A 的完成回调不得恢复 B 的值。
- [x] 配置关闭、detach、旋转不匹配、GPU 失败和无动画关闭立即回收输出及源、移除观察器、恢复原生值；既有 Workspace 文件夹硬遮挡及关闭恢复保留。
- [x] 增加可直接执行的协调策略测试：背景未就绪不接管、首帧失败恢复、旧序号不释放新资源、重复关闭无副作用。Android 原生调用由最终真机清单验证，不能以源码字符串测试代替行为。
- [x] 运行 Task 1–3 测试及现有文件夹/工作区场景回归；提交 `feat: keep folder glass throughout native open and close`。

### Task 5: 构建、审查与本地真机交付

- [ ] 运行 `testDebugUnitTest assembleRelease --console=plain`；检查 app 与 prismal 全部测试 XML，报告失败、错误、跳过及总数。
- [ ] 检查最终 diff、原生材质条件恢复、每帧输出队列和快速切换行为，完成独立代码审查并修复发现的问题。
- [ ] 在源码目录之外使用已有 Release key 签名；验证签名、16 KB 对齐及证书与正式 2.7.2 一致。保留此前工作区测试包，另命名文件夹测试包。
- [ ] 输出提交 SHA、APK SHA-256、构建/签名结果和真机待办。暂不上传文件夹 PR。
- [ ] 真机验收：小/大/九宫格文件夹慢开快开、连续开合、打开中取消、A→B 切换、旋转、改名、拖出图标、启动应用及返回桌面；检查玻璃连续性、桌面背景模糊、内容清晰和关闭恢复。

## 审阅与执行选择

计划已对照设计逐项自检：视觉分层对应 Task 3，动画交接对应 Task 2/4，首帧与回收对应 Task 1/4，配置和原生行为对应 Task 4，自动及真机验收对应 Task 5。建议在当前专用仓库分支由本会话直接实现，最后统一独立审查；这些任务共享生命周期与输出接口，串行推进更容易验证交接。

## 用户验收后的修订（2026-10-15）

用户明确将背景改为仅模糊壁纸，并要求动画连续、终点对应真实面板，直接隔离 Launcher/Dock 采集后方壁纸；不为采样隐藏 Dock 或等待关闭其图层。

- 读取 `FolderAnimController.setIconBgAnim` 的原生目标平移，按 X（或退化时 Y）计算背景弹簧进度。始终在关闭态和真实展开面板之间计算几何，不再切换到动画副本的矩形。
- 内容图标的完成回调与背景弹簧分别处理；背景继续跟随原生采样。中断打开后从当前进度关闭，关闭时冻结展开目标，避免容器恢复布局导致漂移。
- 独立 PassBlur 源排除运行时 Launcher Surface 名称和 DockAssistantView；无法确认桌面 Surface 身份时不采完整桌面。
- 活动玻璃文件夹沿用 Recents 的 capture-only 缓存层状态和 Workspace 原生 alpha；仍暂停 Workspace producer。Dock 继续走原生 `FolderStatusServiceImpl→DockStateManager→FolderState` 的 alpha 事务与 pop 恢复，不伪造 APP/Recents 状态。
- 新增几何终点、原生 X/Y 进度、采样排除、原生透明度、背景晚于内容结束、中断关闭与禁用动画测试；均先观察失败再实现。
- 本地签名第三版测试包，视觉验收仍由用户确认；不创建或上传 PR。

## 开合对称与零阻塞壁纸采样修订（2026-10-15）

- 排除 Launcher 根 Surface 和 DockAssistantView 的 PassBlur 已是仅采壁纸路径；不要调用 `WallpaperManager` 静态位图，也不要手动隐藏 Dock。Native FolderState 自身的视觉淡出不属于采样遮挡，不进行全局行为修改。
- **移除 Launcher.openFolder 的预采样门控及 1000ms 失败等待**。原生打开动画立即执行；采样源可随后附着和冻结，首个实际输出帧完成之前保留原生材质，防止空白。取消无用预备源只影响模块，不影响 Launcher。
- 关闭动画与打开共用 FolderAnimController 的几何端点和归一化进度。位移为零时使用 `scaleX`，而不错误地依赖不变的测量宽度；中断开启动画后由当前已绘制进度沿原生弹簧回退。
- 关闭开始即锁定展开面板边界，拒绝关闭期间迟到的首次材质接管；旧 serial 的完成事件不得清理新文件夹。
- 关闭结束后释放 PassBlur/EGL/监听器并恢复原生材质。后续必须用真机录屏确认首帧材质接管时间、动态壁纸、Dock 与工作区同步；单元测试不等同视觉验收。
