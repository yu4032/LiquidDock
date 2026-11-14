# OS4 Native Hook 编码规范与架构标准

本规范适用于 `os4/native/` 下针对 `libapp.so`（Dart AOT）与 `libapp_launcher.so`（Rust / FRB）的所有 Native Hook 开发、审查与维护。

---

## 1. 目标定位优先级（Target Discovery Hierarchy）

按稳定性从高到低依次选用定位方式，禁止跳过高优先级方案直接使用硬编码虚拟地址：

1. **稳定 C ABI 导出符号（`.dynsym` / `dlsym`）**
   - 优先使用 `extern "C"` 导出接口（如 `frb_pde_ffi_dispatcher_primary`、`frb_pde_ffi_dispatcher_sync`）。
   - 若通过统一分发器拦截（如 FRB `SseCodec`），必须核对 `func_id` 稳定性及负载对下游调用链的影响，避免篡改参数导致后续逻辑异常。

2. **运行时符号解析（`.symtab` / `.gnu_debugdata`）**
   - 对保留调试符号的 Rust 库（如 `libapp_launcher.so`），使用 `rust_symbol_resolver.h`（配合无外部依赖的 `xz_mini.h`）在运行时解压 `.gnu_debugdata` 并解析 `.symtab`。
   - Rust v0 符号匹配必须**忽略 crate 哈希**（如 `Cs<hash>_`），按完整模块路径与函数名精确匹配，且强制要求**全局唯一命中（`matches == 1`）**。

3. **AOT 指令模式扫描（`aot_scanner.h`）**
   - 仅用于无符号目标（如 Dart AOT），或作为**非 strict 模式**下的受控兜底。
   - 禁止在外部调用处对 `ld_find_aot_pattern` 重复传入更短前缀（如外层再手动试 `24` 条指令）；退化阶梯由扫描器内部统一控制。

---

## 2. 定位与安全校验解耦（Fail-Closed）

所有 Hook 必须将**目标发现**与**安全门控**分离，并在调用 `api->hook(...)` **之前**完成全部校验：

1. **严格 ELF / PT_LOAD 校验前置**
   - 不论目标位于 `libapp.so` 还是 `libapp_launcher.so`，都必须在安装 Hook 前通过 `ld_cross_check_strict` 验证其所在 `PT_LOAD` 段的运行时映射与文件偏移一致性。
   - 禁止在 `strict` 检查之前安装任何功能性 Hook。

2. **边界与语义校验**
   - 通过符号解析或模式匹配拿到目标后，必须校验：
     - 函数大小在合理区间内，且起止地址均满足 `executable_range`。
     - AArch64 函数序言符合预期（如 `stp x29, x30, [sp, ...]` / `sub sp, sp, ...`）。
     - 函数体内包含关键语义特征（如预期的调用指令 `bl`、核心数据加载/比较指令形态）。

3. **Fail-Closed 原则**
   - 当 `hook_safety_enabled`（`strict` 模式）开启时，只要符号解析失败、命中不唯一、边界越界或语义校验不通过，**必须直接禁用该 Hook 并写入 `liquiddock4-boot.log`**。
   - 严禁在 `strict` 模式下静默退化到无约束短特征匹配或猜测地址。

---

## 3. 边界选择与副作用控制

1. **优先选择接口/DTO 边界**
   - 新增能力优先在 Dart AOT DTO 边界或 FRB `wire__crate__api__*` 边界拦截，减少直接 Hook Rust 内部业务函数的数量。
   - 若拦截 FRB 入口无法阻止原生侧前置越界/崩溃（例如问题发生在 Rust 预加载阶段），再针对具体 Rust 内部函数建立“符号解析 + 语义校验”Hook。

2. **完整审计内部函数副作用**
   - 任何跳过或替换内部业务函数的 Hook（如 `repl_rust_orientation` 跳过 `check_database_data_orientation_when_loading`），在编写前必须通过反汇编确认其全部调用目标、数据库事务及偏好读写，确保不会遗漏无关状态副作用；否则只能改写目标分支，不可整函数直接 `return`。

3. **ABI 与调用约定约束**
   - 原生 Rust ABI 不保证跨版本稳定；仅对 `extern "C"` 函数或经反汇编确认符合 AAPCS64 参数/返回值约定的目标按原签名调用。

---

## 4. 构建与发布验证标准

1. **编译零警告与零外部依赖**
   - `os4/native/build.sh` 必须保持 `-std=c11 -Wall -Wextra -Werror` 编译零警告，且仅链接 `-llog -ldl`。

2. **16 KiB 对齐打包与真机日志验收**
   - 使用 `package_native.py` → `apksigner` → `zipalign -P 16 -f 4` → `apksigner` → `verify_installer.py` 完成打包与校验。
   - 每次修改 Native Hook 后，需在真机重启 `com.miui.home`，确认进程存活且 `/data/user_de/0/com.miui.home/files/liquiddock4-boot.log` 明确记录了安装来源（如 `via sym+semantic`）与 `result=0`。
