# LiquidDock Third-Party Notices

本文档对应当前 `main` / **v2.5.0** 的依赖与实现参考。

## Bundled / build dependencies

LiquidDock 当前使用或编译依赖：

- **Compose Miuix 0.9.3** — `miuix-ui-android`, `miuix-preference-android` — Apache License 2.0 — <https://github.com/compose-miuix-ui/miuix>
- **AndroidX Activity Compose 1.13.0** — Apache License 2.0 — <https://source.android.com/docs/setup/about/licenses>
- **AndroidX Preference 1.2.1** — Apache License 2.0 — <https://source.android.com/docs/setup/about/licenses>
- **AndroidX AppCompat 1.7.0** — Apache License 2.0 — <https://source.android.com/docs/setup/about/licenses>
- **LSPosed / libxposed API 101.0.1** — compile-time API — GPL-3.0 — <https://github.com/LSPosed/LSPosed>
- **libxposed service 101.0.0** — Remote Preferences / companion service — GPL-3.0 — <https://github.com/LSPosed/LSPosed>

## Bundled / adapted renderer reference

- **Prismal** — MIT License — <https://github.com/styropyr0/Prismal>

LiquidDock 仓库包含独立 `prismal` 模块，并基于 Prismal 的光学/Shader/参数设计进行适配。具体许可证与上游归属以仓库内对应声明为准。

## Engineering references

- **HyperCeiler** — GPL-3.0 — <https://github.com/ReChronoRain/HyperCeiler>

LiquidDock 参考了其 HyperOS 模块工程实践、设置组织与兼容处理思路，但当前功能实现由 LiquidDock 自己维护。

## Historical reference

- **HyperLight** 曾为早期 1.x 的降采样与屏幕捕获实验提供思路。

当前 `main` 不使用 1.x 的 ScreenCapture/Bitmap backdrop pipeline；相关旧实现只保留在历史分支/归档中。

## Target applications and platform components

LiquidDock 当前可以对以下系统组件/应用提供可选适配：

- Xiaomi/HyperOS System Launcher；
- Android/SystemUI 与 WMShell；
- Xiaomi Security Center；
- Gboard；
- MIUI Search / QuickSearchBox。

这些目标应用的名称仅用于兼容说明。LiquidDock 不因此捆绑或再分发它们的专有源码、资源或商标资产。

LiquidDock 会在运行时调用 Android/HyperOS/目标应用提供的公开或私有接口。此类平台/应用接口不属于 LiquidDock 所捆绑的第三方源码。

## Trademarks

Xiaomi、HyperOS、Android、Gboard 以及其他项目或产品名称的商标权归各自权利人所有。LiquidDock 与这些项目或公司不存在隶属关系。
