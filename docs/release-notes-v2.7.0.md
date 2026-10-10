# LiquidDock v2.7.0 — 发布说明草稿

> 发布状态：**待发布**。基于 `main` 的已合并内容；签名构建完成后再固定发布 SHA、签名 APK 的 SHA-256 与 GitHub Release 资产。不会在准备阶段自动创建 Tag/Release。

## 更新内容

**全新设置界面**：采用 MIUIX + Prismal 风格的现代分页面布局，优化设置顶/底栏、滑块和开关弹性反馈、数值输入、玻璃按钮与对话框过渡。设置界面玻璃效果选项现命名为「IOS玻璃效果」。

**玻璃视觉与性能**：修复边缘透镜折射率 IOR 调整无效；改进背景采样边界与部分实时玻璃调度，增加 Workspace Perfetto 诊断能力。

**桌面与小组件**：新增 RemoteViews 精确 TextView/ImageView 白化与隐藏规则互斥，优化网格尺寸改动时的大组件安全拦截及布局稳定性。

**配置维护**：移除历史键别名和平滑迁移链，统一使用当前配置结构；清理部分失效选项及冗余 GUI 实现。

## 升级前请注意

- 先备份 LiquidDock JSON 配置与系统桌面布局。旧键**不会**自动迁移；新版本不认识的历史键不再生效。
- 如启用网格或调整桌面布局，升级后建议重启 Launcher。
- SC 侧边 Dock/All Apps/游戏与视频工具箱的**独立玻璃参数功能仍在 PR #329 测试**，不属于此次发布内容。
- 当前仍使用 libxposed API 101，API 102 升级不属于此次发布。
- Release APK 应使用私有签名仓库生成的 `LiquidDock-signed-release`，不得分发公共 Debug APK 当作正式签名包。

## 构建资料（发布前填写）

- 版本：`2.7.0`
- `versionCode`：`32`
- Source commit：**待固定**
- Signed APK SHA-256：**待填写**
- Signer certificate SHA-256：**由隔离签名工作流校验并写入 provenance**

完整变更边界见 [CHANGELOG.md](../CHANGELOG.md)。历史版本发布记录以 GitHub Releases 为准。
