from pathlib import Path

path = Path('src/test/java/com/hellovoid/liquiddock/WidgetComponentSelectionContractTest.java')
text = path.read_text()

old = '''    @Test public void remoteDiscoveryRetriesWhenProviderPopulatesSameRoot() throws Exception {
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));
        String controller = Files.readString(ROOT.resolve("LauncherWidgetBackgroundController.java"));

        assertTrue(discovery.contains("Map<View, Integer> DUMPED_REMOTE_ROOTS"));
        assertTrue(discovery.contains("remoteSnapshotSignature"));
        assertTrue(discovery.contains("previousSignature"));
        assertTrue(discovery.contains("previousSignature == snapshotSignature"));

        int discoveryCall = controller.indexOf("LauncherWidgetComponentDiscovery.scan(host)");
        int suppressorCall = controller.indexOf("LauncherGlassVendorMaterialSuppressor.claimWidgetMaterial(host)");
        assertTrue(discoveryCall >= 0 && suppressorCall > discoveryCall);
    }
'''
new = '''    @Test public void remoteDiscoveryTracksSameRootPopulationSignature() throws Exception {
        String discovery = Files.readString(ROOT.resolve("LauncherWidgetComponentDiscovery.java"));

        assertTrue(discovery.contains("Map<View, Integer> DUMPED_REMOTE_ROOTS"));
        assertTrue(discovery.contains("remoteSnapshotSignature"));
        assertTrue(discovery.contains("previousSignature"));
        assertTrue(discovery.contains("previousSignature == snapshotSignature"));
    }
'''
if text.count(old) != 1:
    raise SystemExit(f'remote discovery ownership-order block count={text.count(old)}')
text = text.replace(old, new, 1)

old = '''    @Test public void oldRemoteSelectorsAreRejectedAndRuntimeUsesExactPropertyMutation() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));

        assertTrue(store.contains("REMOTE.equals(parts[0])"));
        assertTrue(store.contains("return null;"));
        assertTrue(executor.contains("resolveExactRemoteView"));
        assertTrue(executor.contains("selector.hierarchyPath"));
        assertTrue(executor.contains("selector.className.equals"));
        assertTrue(executor.contains("selector.name.equals"));

        assertTrue(executor.contains("getBackground()"));
        assertTrue(executor.contains("setBackground(null)"));
        assertTrue(executor.contains("setBackground(item.originalBackground)"));
        assertTrue(executor.contains("getDrawable()"));
        assertTrue(executor.contains("setImageDrawable(null)"));
        assertTrue(executor.contains("setImageDrawable(item.originalImage)"));

        assertTrue(executor.contains("View.INVISIBLE"));
        assertFalse(executor.contains("View.GONE"));
    }
'''
new = '''    @Test public void oldRemoteSelectorsAreRejectedAndExactSelectorIdentityIsRequired() throws Exception {
        String store = Files.readString(ROOT.resolve("WidgetComponentStore.java"));
        String executor = Files.readString(ROOT.resolve("LauncherWidgetComponentSelectionExecutor.java"));

        assertTrue(store.contains("REMOTE.equals(parts[0])"));
        assertTrue(store.contains("return null;"));
        assertTrue(executor.contains("resolveExactRemoteView"));
        assertTrue(executor.contains("selector.hierarchyPath"));
        assertTrue(executor.contains("selector.className.equals"));
        assertTrue(executor.contains("selector.name.equals"));
    }
'''
if text.count(old) != 1:
    raise SystemExit(f'remote selector runtime-mutation block count={text.count(old)}')
text = text.replace(old, new, 1)

for forbidden in (
        'suppressorCall > discoveryCall',
        'setBackground(item.originalBackground)',
        'setImageDrawable(item.originalImage)'):
    if forbidden in text:
        raise SystemExit(f'ownership source assertion remains: {forbidden}')

path.write_text(text)
