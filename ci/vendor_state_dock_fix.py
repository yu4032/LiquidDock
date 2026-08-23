from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def read(name):
    return (ROOT / name).read_text()


def write(name, text):
    (ROOT / name).write_text(text)


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


# Recents coverage must follow the semantic state dispatcher recovered from the system Launcher DEX.
write("LauncherGlassRecentsHook.java", r'''package com.hellovoid.liquiddock;

/** Uses HyperOS's semantic Recents dispatcher instead of guessing state from a mounted View. */
final class LauncherGlassRecentsHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String RECENTS_DISPATCHER =
            "com.miui.home.recents.RecentsServiceDispatcher";
    private static boolean installed;

    private LauncherGlassRecentsHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed || config == null || !config.enabled || !config.glass.enabled) return;
        try {
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewShow", chain -> {
                // Hide Workspace glass before the vendor starts dispatching the visible Recents state.
                LauncherGlassSceneController.setRecentsCoveredForAll(true);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hookMethod(classLoader, RECENTS_DISPATCHER, "onRecentViewHide", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                // Reveal only after the vendor has left Recents; the controller still requires a fresh frame.
                LauncherGlassSceneController.setRecentsCoveredForAll(false);
                return result;
            });
            installed = true;
            MainHook.log(TAG + " semantic Recents dispatcher hooks installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " semantic Recents dispatcher unavailable: " + error);
        }
    }
}
''')

# Replace the brittle mounted-Recents-View heuristic with process semantic coverage signals.
text = read("LauncherGlassSceneController.java")
text = replace_once(text,
        "import java.lang.ref.WeakReference;\nimport java.util.WeakHashMap;",
        "import java.lang.ref.WeakReference;\nimport java.util.ArrayList;\nimport java.util.WeakHashMap;",
        "controller import")
text = replace_once(text,
        "    private static final WeakHashMap<View, LauncherGlassSceneController> BY_ROOT = new WeakHashMap<>();\n",
        "    private static final WeakHashMap<View, LauncherGlassSceneController> BY_ROOT = new WeakHashMap<>();\n"
        "    private static boolean vendorRecentsCovered;\n"
        "    private static boolean vendorFolderCovered;\n",
        "controller globals")
text = replace_once(text,
        "    private WeakReference<View> recentsRef = new WeakReference<>(null);\n",
        "",
        "controller recents ref")
old = '''    static void bindRecentsView(View anyView, View recents) {
        LauncherGlassSceneController controller = find(anyView);
        if (controller != null) controller.recentsRef = new WeakReference<>(recents);
    }

    static void syncRecentsForRoot(View root) {
        LauncherGlassSceneController controller = findRoot(root);
        if (controller == null) return;
        View recents = controller.recentsRef.get();
        boolean covered = recents != null && recents.getVisibility() == View.VISIBLE && recents.isShown();
        controller.setRecentsCovered(covered);
    }
'''
new = '''    static void setRecentsCoveredForAll(boolean covered) {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            vendorRecentsCovered = covered;
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.setRecentsCovered(covered);
        }
    }

    static void setFolderCoveredForAll(boolean covered) {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            vendorFolderCovered = covered;
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.setFolderCovered(covered);
        }
    }
'''
text = replace_once(text, old, new, "controller recents heuristic")
old = '''        LauncherGlassSceneController created =
                new LauncherGlassSceneController(root, session, glassConfig);
        BY_ROOT.put(root, created);
        return created;
'''
new = '''        LauncherGlassSceneController created =
                new LauncherGlassSceneController(root, session, glassConfig);
        created.recentsCovered = vendorRecentsCovered;
        created.folderCovered = vendorFolderCovered;
        if (created.recentsCovered || created.folderCovered) {
            created.state.setCovered(true);
        }
        BY_ROOT.put(root, created);
        return created;
'''
text = replace_once(text, old, new, "controller acquire coverage")
write("LauncherGlassSceneController.java", text)

# No per-pre-draw polling of a Recents View remains.
text = read("LauncherGlassSession.java")
text = replace_once(text,
        "        LauncherGlassSceneController.syncRecentsForRoot(root);\n",
        "",
        "session recents polling")
write("LauncherGlassSession.java", text)

# Folder coverage follows the vendor FolderStatusService dispatcher. Keep FolderIcon hooks only for
# source-node press/suppression and as a fallback on launchers where the dispatcher class is absent.
text = read("MiuixFolderGlassHook.java")
text = replace_once(text,
        "    private static WeakReference<LauncherGlassStaticNode> openedFolderSink = new WeakReference<>(null);\n    private static boolean installed;\n",
        "    private static WeakReference<LauncherGlassStaticNode> openedFolderSink = new WeakReference<>(null);\n"
        "    private static boolean folderStatusDispatcherInstalled;\n"
        "    private static boolean installed;\n",
        "folder semantic flag")
text = replace_once(text,
        "        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;\n        try {\n",
        "        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;\n"
        "        folderStatusDispatcherInstalled = installFolderStatusDispatcherHooks(classLoader);\n"
        "        try {\n",
        "folder semantic install")
marker = "    private static void installFolderOpenCloseHooks(ClassLoader classLoader) throws Exception {\n"
method = '''    private static boolean installFolderStatusDispatcherHooks(ClassLoader classLoader) {
        String dispatcher =
                "com.miui.home.launcher.dock.v3.dependencies.FolderStatusServiceImpl";
        try {
            HookUtil.hookMethod(classLoader, dispatcher, "dispatchFolderOpen", chain -> {
                LauncherGlassSceneController.setFolderCoveredForAll(true);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hookMethod(classLoader, dispatcher, "dispatchFolderClose", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                LauncherGlassSceneController.setFolderCoveredForAll(false);
                return result;
            });
            MainHook.log(TAG + " semantic FolderStatus dispatcher hooks installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " semantic FolderStatus dispatcher unavailable; using FolderIcon fallback: "
                    + error);
            return false;
        }
    }

'''
if text.count(marker) != 1:
    raise SystemExit("folder semantic method insertion anchor mismatch")
text = text.replace(marker, method + marker, 1)
text = replace_once(text,
        "        LauncherGlassSceneController.setWorkspaceCovered(owner, suppressed);\n",
        "        if (!folderStatusDispatcherInstalled) {\n"
        "            LauncherGlassSceneController.setWorkspaceCovered(owner, suppressed);\n"
        "        }\n",
        "folder fallback open coverage")
text = replace_once(text,
        "        if (owner != null) LauncherGlassSceneController.setWorkspaceCovered(owner, false);\n",
        "        if (owner != null && !folderStatusDispatcherInstalled) {\n"
        "            LauncherGlassSceneController.setWorkspaceCovered(owner, false);\n"
        "        }\n",
        "folder fallback close coverage")
write("MiuixFolderGlassHook.java", text)

# Dock membership and output coordinates are different domains: HotSeats owns the icon, while the
# actual TextureView is the coordinate origin of the EGL output. RecyclerView decorations, spacing,
# the divider holder and all parent transforms are therefore inherited from the real View matrices.
write("DockGlassCompositor.java", r'''package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;
import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Dock body and Dock icon glass are drawn in one output-local Prismal frame/output swap. */
final class DockGlassCompositor {
    private final WeakReference<View> ownershipRootRef;
    private final WeakReference<View> outputRootRef;
    private final ArrayList<DockGlassItemNode> cached = new ArrayList<>();
    private volatile GlassComponentStyle iconStyle = new GlassComponentStyle(false, 0f, 0f);
    private volatile DockGlassSceneSnapshot latestScene = DockGlassSceneSnapshot.EMPTY;
    private long seenRevision = -1L;
    private long lastFingerprint = Long.MIN_VALUE;
    private int lastW = -1, lastH = -1;
    private float lastInsetL = Float.NaN, lastInsetT = Float.NaN;

    DockGlassCompositor(View ownershipRoot, View outputRoot) {
        ownershipRootRef = new WeakReference<>(ownershipRoot);
        outputRootRef = new WeakReference<>(outputRoot);
    }

    void setIconStyle(GlassComponentStyle style) {
        iconStyle = style != null ? style : new GlassComponentStyle(false, 0f, 0f);
        seenRevision = -1L;
        lastFingerprint = Long.MIN_VALUE;
        if (!iconStyle.enabled) latestScene = DockGlassSceneSnapshot.EMPTY;
    }

    void refreshUiSceneIfNeeded(int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View ownershipRoot = ownershipRootRef.get();
        View outputRoot = outputRootRef.get();
        if (ownershipRoot == null || outputRoot == null
                || !GlassRuntimeState.isEnabled() || !iconStyle.enabled) {
            cached.clear();
            latestScene = DockGlassSceneSnapshot.EMPTY;
            return;
        }

        long revision = DockGlassItemRegistry.revision();
        boolean dead = false;
        for (DockGlassItemNode item : cached) {
            if (item.view() == null || !item.belongsTo(ownershipRoot)) {
                dead = true;
                break;
            }
        }
        if (revision != seenRevision || dead) {
            cached.clear();
            for (View candidate : DockGlassItemRegistry.snapshotForRoot(ownershipRoot.getRootView())) {
                DockGlassItemNode item = new DockGlassItemNode(candidate, iconStyle);
                if (item.belongsTo(ownershipRoot)) cached.add(item);
            }
            seenRevision = revision;
            lastFingerprint = Long.MIN_VALUE;
        }

        long fingerprint = 0xcbf29ce484222325L;
        for (DockGlassItemNode item : cached) {
            fingerprint = (fingerprint ^ item.uiFingerprint(ownershipRoot)) * 0x100000001b3L;
        }
        fingerprint = mixOutputRoot(fingerprint, outputRoot);
        boolean mappingChanged = framebufferWidth != lastW || framebufferHeight != lastH
                || Float.compare(sampleInsetLeft, lastInsetL) != 0
                || Float.compare(sampleInsetTop, lastInsetT) != 0;
        if (!mappingChanged && fingerprint == lastFingerprint) return;

        Matrix outputGlobal = new Matrix();
        outputRoot.transformMatrixToGlobal(outputGlobal);
        Matrix outputInverse = new Matrix();
        if (!outputGlobal.invert(outputInverse)) {
            latestScene = DockGlassSceneSnapshot.EMPTY;
            return;
        }

        ArrayList<LauncherGlassGeometry.Snapshot> out = new ArrayList<>();
        for (DockGlassItemNode item : cached) {
            LauncherGlassGeometry.Snapshot geometry = item.capture(
                    ownershipRoot, outputInverse,
                    framebufferWidth, framebufferHeight,
                    sampleInsetLeft, sampleInsetTop, scaleX, scaleY);
            if (geometry != null) out.add(geometry);
        }
        latestScene = new DockGlassSceneSnapshot(
                out.toArray(new LauncherGlassGeometry.Snapshot[0]));
        lastFingerprint = fingerprint;
        lastW = framebufferWidth;
        lastH = framebufferHeight;
        lastInsetL = sampleInsetLeft;
        lastInsetT = sampleInsetTop;
    }

    DockGlassSceneSnapshot latestScene() { return latestScene; }

    int drawFrame(PrismalRenderer renderer, PrismalGeometry dockBody, PrismalParams params,
            DockGlassSceneSnapshot scene, int framebufferWidth, int framebufferHeight) {
        renderer.beginGlassFrame();
        renderer.drawGlass(dockBody, params);
        DockGlassSceneSnapshot stable = scene != null ? scene : DockGlassSceneSnapshot.EMPTY;
        for (LauncherGlassGeometry.Snapshot item : stable.items) {
            renderer.drawGlass(new PrismalGeometry(framebufferWidth, framebufferHeight,
                    item.centerX, item.centerY, item.width, item.height, item.cornerRadius), params);
        }
        return stable.size();
    }

    private static long mixOutputRoot(long hash, View view) {
        hash = (hash ^ view.getLeft()) * 0x100000001b3L;
        hash = (hash ^ view.getTop()) * 0x100000001b3L;
        hash = (hash ^ Float.floatToIntBits(view.getTranslationX())) * 0x100000001b3L;
        hash = (hash ^ Float.floatToIntBits(view.getTranslationY())) * 0x100000001b3L;
        hash = (hash ^ Float.floatToIntBits(view.getScaleX())) * 0x100000001b3L;
        hash = (hash ^ Float.floatToIntBits(view.getScaleY())) * 0x100000001b3L;
        return hash;
    }
}
''')

text = read("DockGlassItemNode.java")
text = text.replace("LauncherGlassGeometry.Snapshot capture(View dockRoot, Matrix rootInverse,",
                    "LauncherGlassGeometry.Snapshot capture(View ownershipRoot, Matrix outputInverse,")
text = text.replace("view == null || dockRoot == null || rootInverse == null || style == null",
                    "view == null || ownershipRoot == null || outputInverse == null || style == null")
text = text.replace("|| !belongsTo(dockRoot) || !LauncherGlassVisibility.isVisible(view, dockRoot)",
                    "|| !belongsTo(ownershipRoot) || !LauncherGlassVisibility.isVisible(view, ownershipRoot)")
text = text.replace("rootInverse.mapPoints(points);", "outputInverse.mapPoints(points);")
if "belongsTo(ownershipRoot)" not in text or "outputInverse.mapPoints(points);" not in text:
    raise SystemExit("DockGlassItemNode output mapping replacement failed")
write("DockGlassItemNode.java", text)

# TextureView itself is the exact EGL-output coordinate root. Add low-frequency power diagnostics;
# they observe producer/draw cadence but never request or schedule a frame.
text = read("Miuix307PassBlurTextureView.java")
text = replace_once(text,
        "import android.os.HandlerThread;\n",
        "import android.os.HandlerThread;\nimport android.os.SystemClock;\n",
        "PBTX SystemClock import")
old = '''        View dockRoot = LauncherGlassHierarchy.findDockRoot(materialHost);
        dockCompositor = new DockGlassCompositor(dockRoot != null ? dockRoot : materialHost);
'''
new = '''        View ownershipRoot = LauncherGlassHierarchy.findDockRoot(materialHost);
        dockCompositor = new DockGlassCompositor(
                ownershipRoot != null ? ownershipRoot : materialHost, this);
'''
text = replace_once(text, old, new, "PBTX dock compositor roots")
text = replace_once(text,
        "    private boolean prismalMappingLogged;\n    private ViewTreeObserver preDrawObserver;\n",
        "    private boolean prismalMappingLogged;\n"
        "    private long producerFrameCount;\n"
        "    private long renderedFrameCount;\n"
        "    private long powerWindowStartedMs = SystemClock.uptimeMillis();\n"
        "    private ViewTreeObserver preDrawObserver;\n",
        "PBTX power fields")
text = replace_once(text,
        '''        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            frameAvailable.set(true);
            drawLatestFrame(true);
        }, renderHandler);
''',
        '''        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            producerFrameCount++;
            frameAvailable.set(true);
            drawLatestFrame(true);
        }, renderHandler);
''',
        "PBTX producer counter")
text = replace_once(text,
        '''            if (!EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface)) {
                throw new IllegalStateException("eglSwapBuffers error=0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }

            Miuix307PassBlurBridge.Binding currentBinding = binding;
''',
        '''            if (!EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface)) {
                throw new IllegalStateException("eglSwapBuffers error=0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }
            renderedFrameCount++;
            maybeLogPowerStats();

            Miuix307PassBlurBridge.Binding currentBinding = binding;
''',
        "PBTX rendered counter")
anchor = "    private void renderNormalizationPass(BackdropSnapshot mapping) {\n"
method = '''    private void maybeLogPowerStats() {
        long now = SystemClock.uptimeMillis();
        long elapsed = now - powerWindowStartedMs;
        if (elapsed < 5000L) return;
        float seconds = Math.max(0.001f, elapsed / 1000f);
        float producerFps = producerFrameCount / seconds;
        float drawFps = renderedFrameCount / seconds;
        DockGlassSceneSnapshot scene = dockCompositor.latestScene();
        MainHook.log("[DC][PBTX][Power] producerFps=" + producerFps
                + " drawFps=" + drawFps
                + " dockItems=" + (scene != null ? scene.size() : 0)
                + " attached=" + isAttachedToWindow()
                + " shown=" + isShown());
        producerFrameCount = 0L;
        renderedFrameCount = 0L;
        powerWindowStartedMs = now;
    }

'''
if text.count(anchor) != 1:
    raise SystemExit("PBTX power method insertion anchor mismatch")
text = text.replace(anchor, method + anchor, 1)
write("Miuix307PassBlurTextureView.java", text)

print("vendor state / dock mapping / power diagnostics patch applied")
