# libxposed API 102 / 热重载迁移实验

状态：**实验分支，禁止当作已完成的无重启热更新发布**（2026-10-09）。基线 `main`：`05debd6`。

## 已核对的官方资料

- https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API
- https://central.sonatype.com/artifact/io.github.libxposed/api/102.0.0
- https://central.sonatype.com/artifact/io.github.libxposed/service/102.0.0
- https://libxposed.github.io/api/index.html

102 增加 `onHotReloading`/`onHotReloaded`、`HookHandle.replaceHook` 与 Hook ID。热重载后 **onModuleLoaded/onPackageReady 不自动重放**，因此不能把原来的安装函数原样从新入口再调用一遍。

## 第一阶段：API 102 编译兼容实验

- 已把 `api` / `service` 正式依赖换为 `102.0.0`。
- `module.prop`：`minApiVersion=102`、`targetApiVersion=102`，避免误装到 101-only 框架。仅一个 Java 入口。
- **`autoHotReload=false`**：目前仍需要重启作用域进程才能加载新版本。不是全量热重载实现。
- 在 `ModuleMain` 中新增 API 102 回调：如果当前进程已经进入任何可能安装 Hook、View/Surface、广播监听的功能域，`onHotReloading` 明确拒绝；只有尚未进入目标包初始化的安全窗口才允许保存简单进程名，并于 `onHotReloaded` 恢复模块桥。
- 现有 Remote Preferences 机制与配置即时生效能力保持原样。**设置项热更新 ≠ APK/Hooks 无重启热重载**。
- 必须经过 CI 构建和目标机 API 102 框架验证；没有设备证据就不能修改 `autoHotReload`。

## 第二阶段：HookHandle 所有权登记试点（2026-10-09）

已新增 `Api102HookRegistry`，并将只读的 `SystemUiKeyguardGoneSource` 方法 Hook 迁入首个**明确命名 Hook ID** 的试点。

- 基于域标识 + 目标二进制类名 + 方法名 + 参数类型生成稳定 ID（区分同名重载）。仅用于目标进程内的 Hook 身份；跨代 saved state 不传递类、句柄或回调。
- 通过 API 102 的 `hook(method).setId(id).intercept(...)` 取得、登记 `HookHandle`；重复 ID 必须在调用框架前拒绝。安装中途失败时按逆序撤销已安装句柄，`unhook` 失败的句柄继续留在登记簿，避免产生第二份重复回调。
- 以 fake `HookHandle` 单元测试验证 ID 稳定性、重载隔离、重复安装和失败回滚。登记器还提供独立的 `replaceIdentified()` 原子替换原语，并测试替换成功后只保留新句柄、失败时保留旧句柄供回滚；**此原语目前只用于单元测试，不在活跃进程执行**。
- `ModuleMain.onHotReloading` 除原先的 domain ownership gate 外，还要求登记簿中没有任何已安装句柄；**`autoHotReload=false` 不变**。
- 未迁入所有普通 HookUtil / 直接 hook 调用，没有注册 SystemUI 手势白条 / menu 的 receiver、View listener 和 Session，因此不能把这一步解释为全进程 reload-ready。

后续应先制定不可重复的显式 Hook ID 分配表和按 domain 卸载事务，再给更多没有图形生命周期的 owner 接入。**不要**在未能释放 Android 资源前使用 `replaceHook` 覆盖旧 callbacks：HookHandle 的替换与外部 listeners 的脱钩是两件事。

---

## 第三阶段：将现有 HookUtil / 高优先级 Hook 纳入句柄台账

**静态代码完成，尚未在 LSPosed 102 真机环境验证：**

- `HookUtil.hook(Method)` 和 `HookUtil.hook(Constructor)` 均在安装成功后将 `HookHandle` 交给 `Api102HookRegistry` 保存；继承的 `hookMethod()` 路径无需逐个修改。
- `DockBottomGeometryHook` 与 `HomeGridDbOrientationHook` 的直接 `setPriority(PRIORITY_HIGHEST)` 拦截，迁入 `HookUtil.hookWithPriority`；保持原拦截时机、优先级和参数，只有句柄登记方式变化。
- **不为旧 Hook 自动生成稳定 ID。** 同一 vendor 方法合法挂有多个互不相同的拦截器。旧 Hook 句柄单独放在 `unnamedHandles` 中，计入 `installedCount()` 和热重载拒绝条件；它们不是可以原子替换的 Hook。
- 对已命名的 SystemUI GONE 观察 Hook，安装失败时按逆序撤销。如果 `unhook()` 失败则让残余句柄继续登记，并保持 feature installed 标志，避免二次安装产生重复监听。
- 新增 JVM 假句柄测试：具名 + 匿名混合统计、同一目标多回调、失败撤销后残留阻止重新安装。没有实现/执行整个进程的 `unhook all`，因为 View、GL 与 receiver 所有权尚未补齐。
- `autoHotReload=false`，没有向 saved instance state 传递 HookHandle、Android View 或旧模块回调。

**下一关**：为每个安装 owner 建立分域安装事务和幂等 stop()/release()，处理 hook closure 内的进程静态引用、延迟任务及 vendor side-effect；再在仅纯 Java 功能上运行真机热重载测试。当前全局句柄台账只是 **可观察所有权**，不能据此宣称进程安全恢复。

---

## 第四阶段：首个 owner-scoped 安装与停止事务（2026-10-09）

为避免“保存了 HookHandle 就能热重载”的误判，新增 **`Api102HookDomain`**，把低风险的 SystemUI GONE 锁屏完成观察器改为按域安装。

- `IDLE → INSTALLING → ACTIVE`：只有全部目标方法 Hook 安装完成，才允许产生观察器副作用。缺失目标方法或中途抛异常时，`abort()` 按逆序回滚本域句柄。
- `ACTIVE → BLOCKED → IDLE`：`stop()` 先通过 `volatile` 状态关闭观察器回调副作用，再逆序 `unhook`。所有句柄撤销成功后允许后续重新安装；失败时留在 `BLOCKED`，禁止第二次安装和意外重复广播。支持之后重复调用 `stop()` 尝试清理残余句柄。
- 每个 Hook 的稳定 ID 含功能域、声明类、方法及参数。事务只撤销本域句柄，不会删除其他功能域或现有匿名 Hook。新实例也不得从旧代 saved state 携带 HookHandle。
- `SystemUiKeyguardGoneSource.stopForFutureReload()` 目前只是**孤立 owner 的预备接口**，不在 `ModuleMain.onHotReloading()` 中调用，也没有关闭 `autoHotReload=false` 的门禁。除了该观察 Hook，SystemUI 还拥有手势白条监听和玻璃相关 owner。
- 新增 `Api102HookDomainTest`，在 JVM 使用假 HookHandle 注入重复安装、部分安装失败、撤销失败、重试清理和不同 owner 并存。通过 CI 只说明 JVM 事务及编译成立；真机热替换仍需补充 in-flight invocation barrier 和完整的 SystemUI 资源关闭流程。

**热更新判断**：本阶段只完成一个 owner 的幂等停止/重启基础，不声称全进程可热重载。若未来需要严格保证 `stop()` 返回后绝无先前进入的回调完成广播，还必须增加受控的在途回调隔离/排空机制；当前只是关闭状态位并在副作用前检查。

---

## 第五阶段：SystemUI 观察回调排空屏障（2026-10-09）

针对上一阶段单纯使用 `DOMAIN.isActive()` 造成的检查与广播之间的竞态，`Api102HookDomain` 新增**受控的在途副作用租约**：

- `runActiveSideEffect(Runnable)` 持有公平 `ReentrantReadWriteLock` 的读锁，并在进入临界区后重新检查 `ACTIVE`；整个锁屏状态判定及 `sendBroadcast()` 由该临界区保护，而 vendor 原方法的 `chain.proceed()` 不被包入锁内。
- `stop()` 先将状态设为 `BLOCKED`，再限时 200ms 获取独占写锁。获取成功表明先前获得租约的同步副作用全部结束，此时才进行 `unhook`，成功后进入 `IDLE`；**获取超时或线程被中断，必须返回 false、保留句柄、维持 BLOCKED 状态**，之后可重试。
- 不允许从已获得读锁的观察回调中直接调用 `stop()`，也不能在受保护的 `Runnable` 中投递未受控异步副作用，否则会绕开排空契约。观察器当前仅使用同步发送广播。被系统消息队列异步接收的广播不属于发送端租约范围。
- `SystemUiKeyguardGoneSource.install()` 与 `stopForFutureReload()` 序列化，避免并发修改 `INSTALLED` 与事务状态。
- `Api102HookDomainConcurrencyTest` 使用 `CountDownLatch` 和独立线程验证：停止不能越过未结束的同步副作用；超时保持阻塞且不卸载；回调抛异常仍释放锁；停止后副作用入口不可再触发。

**SystemUI 剩余阻断项（源码审计，未改动这些功能）：**

- `SystemUiGestureHandleFadeHook`：除初始 `NavigationBar` Hook 外，还在运行中动态 Hook handle-alpha、TaskStack listener 和 LauncherProxyListener；同时持有 `HOME_HANDLES`、`HOOKED_*_CLASSES` 集合及 `GestureHandleRuntimeState.setListener()`。未具备关闭所有动态句柄、恢复 vendor alpha、重置场景状态的原子 stop/restore。
- `GestureHandleRuntimeState`：有 Remote Preferences 监听注册/取消能力，但尚无明确的 shutdown 流程，并且 Handler 发布的使能回调需通过代际 token 防止旧代被新代重复消费。
- `SystemUiHandleMenuGlassHook` 与 `SystemUiHandleMenuPrismalSession`：维护 active root、SurfaceControl alpha listener、`HandlerThread`、SurfaceTexture、EGLContext/EGLSurface。虽然有单个 root/session 的清理代码，缺少全进程可校验的 owner 汇总排空，以及旧 module classloader 脱离证明。
- Launcher 侧 `SystemUiKeyguardGoneRuntime` 单独持有 `BroadcastReceiver`；其 process lifetime 需与 Launcher 热重载的回收事务配套，不能仅停止 SystemUI 发送方就推断接收方安全。

**本阶段仍不调用 `ModuleMain.onHotReloading` 内的 stop**，`autoHotReload=false` 保持；上述阻断项关闭前，严禁把“单一锁屏观察器可停止”推断为“整个 SystemUI 可热重载”。

---

## 后续真正热重载的必要条件

1. **分域 Hook 所有权与具名迁移**。基础全局句柄登记已建立；下一步按 owner 逐一把匿名 Hook 迁为明确且不冲突的稳定 ID，同时提供安装/回滚事务、priority 保持和安全停止流程。不是所有 Hook 都能直接按 method signature 命名：多个合法拦截器会共享同一个目标方法。
2. **清理生命周期副作用**。每个进程的 owner 都必须可幂等停机：解注册 `BroadcastReceiver`、`OnPreDrawListener` / `OnLayoutChangeListener`、SharedPreferences listener；撤销旧 window material ownership；取消 Handler Runnable / async tasks；关闭 SurfaceTexture、EGL、OES、FBO、force-refresh 租约；让静态 singleton 不再持有旧 ClassLoader 或模块回调。
3. **保存 / 恢复目标进程状态**。只有已校验的系统 ClassLoader / Application 引用或简单不可变值可跨 generation 传递；不能把旧模块的回调或图形 session 对象塞入 saved state。新实例需重建 owner，且不能等待不会重放的 package-ready 回调。
4. **原子切换与失败回滚**。先准备新代，替换或撤销旧 HookHandle，再释放旧 listener / 图形资源；任一步失败必须能够保持旧代或安全禁用，不能留下两套源或两个绘制线程争抢 Surface。
5. **分域试点再扩大**。先选低风险纯 Java Hook；再测试 Gboard/Searchbox、SystemUI、Security Center；最后 Launcher/Home/Dock/Workspace Glass（难度最高）。system_server 不应成为首批试点。
6. **热重载端到端矩阵**。分别测试模块开/关、默认/工作台模式、横竖屏、正在拖动、Dock PassBlur producer 活跃、文件夹/最近任务/手势、后台恢复；验证 Hook 次数不翻倍、没有旧视图监听器、GPU/CPU 资源回收、无额外 EGL 线程、帧时长及远程配置保持正确。

## 发布门槛

- 实验 APK 的 `minApiVersion=102` 会在仅支持 101 的框架上拒绝加载，不能作为 2.6.4 正式包升级。
- 未完成所有目标作用域的 teardown / HookHandle 路径前，不允许把 `autoHotReload=false` 改为 `true` 并合并到 `main`。
- 编译成功只证明 API102 接口与源码兼容，不证明本机框架实现热重载，也不证明渲染/Hook 无重启更新安全。
