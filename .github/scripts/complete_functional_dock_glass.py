from pathlib import Path

static_path = Path('src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java')
text = static_path.read_text()
old = '''    private static boolean isIconHostEligible(View host) {
        if (host == null || !GlassRuntimeState.isAnyIconEnabled()) return false;
        if (GlassRuntimeState.isIconEnabled()) return true;
        return GlassRuntimeState.isFunctionalDockIconEnabled()
                && LauncherGlassHierarchy.classify(host) == LauncherGlassHierarchy.Domain.DOCK
                && Launcher450DockFunctionalIconRegistry.isFunctional(host);
    }
'''
new = '''    private static boolean isIconHostEligible(View host) {
        if (host == null) return false;
        return Launcher450DockFunctionalIconPolicy.shouldRender(
                GlassRuntimeState.isIconEnabled(),
                GlassRuntimeState.isFunctionalDockIconEnabled(),
                LauncherGlassHierarchy.classify(host) == LauncherGlassHierarchy.Domain.DOCK,
                Launcher450DockFunctionalIconRegistry.isFunctional(host));
    }
'''
if text.count(old) != 1:
    raise SystemExit(f'StaticGlass eligibility block count={text.count(old)}')
static_path.write_text(text.replace(old, new, 1))

anim_path = Path('src/main/java/com/hellovoid/liquiddock/DockIconAnimationGlassHook.java')
text = anim_path.read_text()
old_visible = 'visibility == View.VISIBLE && GlassRuntimeState.isIconEnabled()'
new_visible = 'visibility == View.VISIBLE && GlassRuntimeState.isAnyIconEnabled()'
if text.count(old_visible) != 1:
    raise SystemExit(f'Dock visibility gate count={text.count(old_visible)}')
text = text.replace(old_visible, new_visible, 1)
old_frame = '''                                if (GlassRuntimeState.isIconEnabled()) {
                                    DockGlassItemRegistry.observeLaunchAnimationFrame(
                                            dockTarget, progress);
                                }
'''
new_frame = '''                                if (GlassRuntimeState.isAnyIconEnabled()) {
                                    DockGlassItemRegistry.observeLaunchAnimationFrame(
                                            dockTarget, progress);
                                }
'''
if text.count(old_frame) != 1:
    raise SystemExit(f'Dock frame gate count={text.count(old_frame)}')
anim_path.write_text(text.replace(old_frame, new_frame, 1))
