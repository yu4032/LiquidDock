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
- 以 fake `HookHandle` 单元测试验证 ID 稳定性、重载隔离、重复安装和失败回滚。该试点不使用 `replaceHook`，没有重新安装 active SystemUI 的能力。
- `ModuleMain.onHotReloading` 除原先的 domain ownership gate 外，还要求登记簿中没有任何已安装句柄；**`autoHotReload=false` 不变**。
- 未迁入所有普通 HookUtil / 直接 hook 调用，没有注册 SystemUI 手势白条 / menu 的 receiver、View listener 和 Session，因此不能把这一步解释为全进程 reload-ready。

后续应先制定不可重复的显式 Hook ID 分配表和按 domain 卸载事务，再给更多没有图形生命周期的 owner 接入。**不要**在未能释放 Android 资源前使用 `replaceHook` 覆盖旧 callbacks：HookHandle 的替换与外部 listeners 的脱钩是两件事。

---

## 后续真正热重载的必要条件

1. **统一 Hook 所有权**。集中登记全部 `HookHandle`，包括 `HookUtil` 封装之外的直接 `Api101Bridge.module().hook`；为每个逻辑 Hook 指定稳定 ID 和优先级，支持原子 `replaceHook`，禁止重复注册。注意同一目标方法可能挂多个不同 hook，ID 不能仅靠 method signature 推导。
2. **清理生命周期副作用**。每个进程的 owner 都必须可幂等停机：解注册 `BroadcastReceiver`、`OnPreDrawListener` / `OnLayoutChangeListener`、SharedPreferences listener；撤销旧 window material ownership；取消 Handler Runnable / async tasks；关闭 SurfaceTexture、EGL、OES、FBO、force-refresh 租约；让静态 singleton 不再持有旧 ClassLoader 或模块回调。
3. **保存 / 恢复目标进程状态**。只有已校验的系统 ClassLoader / Application 引用或简单不可变值可跨 generation 传递；不能把旧模块的回调或图形 session 对象塞入 saved state。新实例需重建 owner，且不能等待不会重放的 package-ready 回调。
4. **原子切换与失败回滚**。先准备新代，替换或撤销旧 HookHandle，再释放旧 listener / 图形资源；任一步失败必须能够保持旧代或安全禁用，不能留下两套源或两个绘制线程争抢 Surface。
5. **分域试点再扩大**。先选低风险纯 Java Hook；再测试 Gboard/Searchbox、SystemUI、Security Center；最后 Launcher/Home/Dock/Workspace Glass（难度最高）。system_server 不应成为首批试点。
6. **热重载端到端矩阵**。分别测试模块开/关、默认/工作台模式、横竖屏、正在拖动、Dock PassBlur producer 活跃、文件夹/最近任务/手势、后台恢复；验证 Hook 次数不翻倍、没有旧视图监听器、GPU/CPU 资源回收、无额外 EGL 线程、帧时长及远程配置保持正确。

## 发布门槛

- 实验 APK 的 `minApiVersion=102` 会在仅支持 101 的框架上拒绝加载，不能作为 2.6.4 正式包升级。
- 未完成所有目标作用域的 teardown / HookHandle 路径前，不允许把 `autoHotReload=false` 改为 `true` 并合并到 `main`。
- 编译成功只证明 API102 接口与源码兼容，不证明本机框架实现热重载，也不证明渲染/Hook 无重启更新安全。
