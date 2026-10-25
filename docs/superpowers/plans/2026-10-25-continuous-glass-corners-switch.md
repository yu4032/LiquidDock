# Continuous glass corner switch

User requested the replacement as a switch on the Liquid Glass page.
Default off preserves main's original rendering. A shared boolean travels through
ConfigSchema, LiquidDockConfig, material parameters and portable PrismalParams.
A uniform branches both coverage and optics to the original functions when off.
Dock outline and border use the same flag. Target apps may require restarting.

- [x] Add regression coverage for default-off, on/off parameter propagation and copies.
- [x] Add registry key and Liquid Glass page control; preserve existing preset behavior.
- [x] Retain original shader geometry alongside the continuous model and upload selector.
- [x] Synchronize Dock CPU geometry; exercise both modes on GPU.
- [x] Run full unit suite and build APK, prepare updated build for the previously selected turner device.
