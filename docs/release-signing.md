# Release signing

本文档记录 LiquidDock 的构建、签名与发布边界。目标是保证公共源码仓库的构建代码永远无法读取 Release 私钥。

## 构建边界

LiquidDock 公共仓库只负责：

- 运行测试和 R8/资源优化；
- 使用固定的公开 Debug key 生成可重复覆盖安装的 Debug APK；
- 生成 **unsigned Release APK**；
- 不保存、不下载、不读取 Release keystore、Release 密码或私钥仓库内容。

Debug 签名仅用于 CI 和真机测试，不代表正式发布身份。

## Release 签名边界

Release 签名由独立的私有签名仓库完成，并拆成两个相互隔离的 GitHub Actions job：

1. **Build unsigned LiquidDock**
   - 不 checkout 私钥仓库；
   - repository permissions 设为 `{}`；
   - 将输入的 branch/tag/commit 先解析为不可变的 40 位 commit SHA；
   - 从公开 LiquidDock 仓库下载该 SHA 的源码；
   - 执行测试和 `assembleRelease`；
   - 生成 unsigned APK、源码 commit 记录和 unsigned APK SHA-256；
   - 通过 Actions artifact 交给下一 job。

2. **Sign isolated release**
   - 使用新的 runner；
   - 只 checkout 私有签名仓库；
   - 下载上一 job 的 unsigned artifact；
   - 核对源码 commit 和 unsigned APK SHA-256；
   - 执行 `zipalign` 和 `apksigner`；
   - 用 `apksigner verify --print-certs` 验证最终 APK；
   - 将 APK 证书 SHA-256 与私有 keystore 中的证书 SHA-256 比较；
   - 输出 signed APK 和 provenance 文件。

签名 job **不得 checkout 或执行 LiquidDock 源码、Gradle wrapper、Gradle plugin 或项目脚本**。

## 发布操作

正式发布时，在私有签名仓库运行对应 Release workflow，并提供要发布的 LiquidDock ref。

允许使用：

- `main`
- Git tag，例如 `v2.6.1`
- 完整 commit SHA

workflow 会在构建开始时立即把 ref 解析为不可变 commit SHA，之后所有 provenance 都绑定到该 SHA。

最终使用签名 job 生成的：

`LiquidDock-signed-release`

其中应至少包含：

- `LiquidDock-release.apk`
- `LiquidDock-release-provenance.txt`
- signer certificate SHA-256 记录

发布前应确认 provenance 中的源码 commit 就是计划发布的提交。

## 安全规则

以下规则不得破坏：

- Release keystore 不得提交到 LiquidDock 公共仓库；
- Release 密码不得写入 LiquidDock、workflow、README、issue、日志或 artifact；
- LiquidDock 的构建 job 不得获得私钥仓库的 contents 权限；
- signing job 不得执行来自 LiquidDock 的任何代码；
- 不得把 keystore 临时复制进 LiquidDock 源码目录后再执行 Gradle；
- 不得把 unsigned artifact 当作正式 Release 分发；
- 修改签名流程后必须重新验证：
  - unsigned build 成功；
  - handoff SHA-256 校验成功；
  - `apksigner verify` 成功；
  - APK signer 证书 SHA-256 与 keystore 证书一致。

## Debug signing

Debug APK 使用仓库内的固定测试签名：

`signing/hellovoid-debug.keystore`

该 key 是公开测试凭据，仅用于让不同 CI runner 生成的 Debug APK 能互相覆盖安装。它不得用于 Release。

---

## English summary

LiquidDock deliberately separates building from release signing.

The public repository may build, test, shrink, and produce Debug or **unsigned Release** APKs, but it must never receive the Release keystore or its credentials.

Release signing runs in a private repository using two isolated jobs:

1. an unprivileged job resolves the requested LiquidDock ref to an immutable commit SHA and builds the unsigned APK;
2. a separate signing runner checks out only the private signing repository, verifies the handoff digest, runs `zipalign` and `apksigner`, verifies the signer certificate, and publishes the signed artifact plus provenance.

The signing runner must never execute LiquidDock source code or Gradle scripts.
