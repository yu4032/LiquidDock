from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/MainHook.java")
source = path.read_text(encoding="utf-8")

replacements = [
    (
        '''        LiquidDockConfig.Dock dock = currentNativeShadowConfig();
        if (hotSeats == null || dock == null || workstationMode
                || !VisualRuntimeState.isDockCustomizationEnabled()) {
            return HotSeatsShadowScope.noop();
        }
''',
        '''        LiquidDockConfig.Dock dock = currentNativeShadowConfig();
        if (hotSeats == null || !DockShadowRuntimePolicy.shouldApplyTemporaryOverrides(
                workstationMode, VisualRuntimeState.isDockCustomizationEnabled(), dock != null)) {
            return HotSeatsShadowScope.noop();
        }
''',
    ),
    (
        '''        if (!VisualRuntimeState.isDockCustomizationEnabled() || workstationMode) return;
        refreshVendorDockShadow();
''',
        '''        if (!DockShadowRuntimePolicy.shouldRefreshVendorShadow(
                workstationMode, VisualRuntimeState.isDockCustomizationEnabled())) return;
        refreshVendorDockShadow();
''',
    ),
    (
        '''        if (workstationMode || !VisualRuntimeState.isDockCustomizationEnabled()) return;
        try {
''',
        '''        if (!DockShadowRuntimePolicy.shouldRefreshVendorShadow(
                workstationMode, VisualRuntimeState.isDockCustomizationEnabled())) return;
        try {
''',
    ),
    (
        '''        if (workstationMode || animating(bg)) return;
        try {
''',
        '''        DockShadowRuntimePolicy.GeometrySync sync =
                DockShadowRuntimePolicy.geometrySync(workstationMode, animating(bg));
        if (sync == DockShadowRuntimePolicy.GeometrySync.REMEMBER_ONLY) return;
        try {
''',
    ),
]

for old, new in replacements:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one replacement target, found {count}: {old!r}")
    source = source.replace(old, new, 1)

required = [
    "DockShadowRuntimePolicy.shouldApplyTemporaryOverrides(",
    "DockShadowRuntimePolicy.shouldRefreshVendorShadow(",
    "DockShadowRuntimePolicy.geometrySync(workstationMode, animating(bg))",
]
for token in required:
    if token not in source:
        raise SystemExit(f"missing expected policy wiring: {token}")

path.write_text(source, encoding="utf-8")
