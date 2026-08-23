#!/usr/bin/env python3
from pathlib import Path
from textwrap import dedent

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java/com/hellovoid/liquiddock"


def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing patch anchor in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


def write_java(name, content):
    (MAIN / name).write_text(dedent(content).lstrip())


write_java("LauncherGlassHierarchy.java", r'''
package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;

/** Classifies Launcher views into mutually-exclusive GPU glass domains. */
final class LauncherGlassHierarchy {
    enum Domain { WORKSPACE, DOCK, OTHER }

    private LauncherGlassHierarchy() {}

    static Domain classify(View view) {
        View cursor = view;
        while (cursor != null) {
            String name = cursor.getClass().getName();
            String simple = cursor.getClass().getSimpleName();
            if ("com.miui.home.launcher.Workspace".equals(name) || "Workspace".equals(simple)) {
                return Domain.WORKSPACE;
            }
            if ("com.miui.home.launcher.hotseats.HotSeats".equals(name)
                    || "HotSeats".equals(simple)
                    || name.startsWith("com.miui.home.launcher.hotseats.")) {
                return Domain.DOCK;
            }
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return Domain.OTHER;
    }

    static boolean isWorkspace(View view) { return classify(view) == Domain.WORKSPACE; }
    static boolean isDock(View view) { return classify(view) == Domain.DOCK; }

    static View findDockRoot(View view) {
        View cursor = view;
        while (cursor != null) {
            String name = cursor.getClass().getName();
            String simple = cursor.getClass().getSimpleName();
            if ("com.miui.home.launcher.hotseats.HotSeats".equals(name) || "HotSeats".equals(simple)) {
                return cursor;
            }
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }
}
''')

write_java("DockGlassSceneSnapshot.java", r'''
package com.hellovoid.liquiddock;

/** Immutable UI-thread Dock geometry consumed by the Dock GL thread. */
final class DockGlassSceneSnapshot {
    static final DockGlassSceneSnapshot EMPTY =
            new DockGlassSceneSnapshot(new LauncherGlassGeometry.Snapshot[0]);
    final LauncherGlassGeometry.Snapshot[] items;
    DockGlassSceneSnapshot(LauncherGlassGeometry.Snapshot[] items) {
        this.items = items != null ? items.clone() : new LauncherGlassGeometry.Snapshot[0];
    }
    int size() { return items.length; }
}
''')

write_java("DockGlassItemRegistry.java", r'''
package com.hellovoid.liquiddock;

import android.view.View;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Weak candidate registry. Domain ownership is verified again by DockGlassItemNode. */
final class DockGlassItemRegistry {
    private static final WeakHashMap<View, Boolean> ICONS = new WeakHashMap<>();
    private static long revision;
    private DockGlassItemRegistry() {}

    static synchronized void register(View view) {
        if (view == null || ICONS.containsKey(view)) return;
        ICONS.put(view, Boolean.TRUE);
        revision++;
    }
    static synchronized void unregister(View view) {
        if (view != null && ICONS.remove(view) != null) revision++;
    }
    static synchronized void clear() {
        if (!ICONS.isEmpty()) { ICONS.clear(); revision++; }
    }
    static synchronized long revision() { return revision; }
    static synchronized ArrayList<View> snapshotForRoot(View root) {
        ArrayList<View> out = new ArrayList<>();
        if (root == null) return out;
        for (View view : new ArrayList<>(ICONS.keySet())) {
            if (view != null && view.isAttachedToWindow() && view.getRootView() == root) out.add(view);
        }
        return out;
    }
}
''')

write_java("DockGlassItemNode.java", r'''
package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Lightweight Dock-local icon glass node. Owns no TextureView, Surface, or EGLSurface. */
final class DockGlassItemNode {
    private final WeakReference<View> viewRef;
    private final GlassComponentStyle style;
    DockGlassItemNode(View view, GlassComponentStyle style) {
        viewRef = new WeakReference<>(view);
        this.style = style;
    }
    View view() { return viewRef.get(); }
    boolean belongsTo(View dockRoot) {
        View cursor = viewRef.get();
        if (cursor == null || dockRoot == null || !cursor.isAttachedToWindow()) return false;
        while (cursor != null) {
            if (cursor == dockRoot) return true;
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
    long uiFingerprint(View dockRoot) {
        View cursor = viewRef.get();
        if (cursor == null || !belongsTo(dockRoot)) return Long.MIN_VALUE;
        long hash = 0xcbf29ce484222325L;
        while (cursor != null && cursor != dockRoot) {
            hash = mix(hash, System.identityHashCode(cursor));
            hash = mix(hash, cursor.getVisibility());
            hash = mix(hash, cursor.getLeft()); hash = mix(hash, cursor.getTop());
            hash = mix(hash, cursor.getRight()); hash = mix(hash, cursor.getBottom());
            hash = mix(hash, cursor.getScrollX()); hash = mix(hash, cursor.getScrollY());
            hash = mix(hash, Float.floatToIntBits(cursor.getTranslationX()));
            hash = mix(hash, Float.floatToIntBits(cursor.getTranslationY()));
            hash = mix(hash, Float.floatToIntBits(cursor.getScaleX()));
            hash = mix(hash, Float.floatToIntBits(cursor.getScaleY()));
            hash = mix(hash, Float.floatToIntBits(cursor.getRotation()));
            hash = mix(hash, Float.floatToIntBits(cursor.getAlpha()));
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        hash = mix(hash, dockRoot.getVisibility());
        hash = mix(hash, Float.floatToIntBits(dockRoot.getAlpha()));
        return hash;
    }
    LauncherGlassGeometry.Snapshot capture(View dockRoot, Matrix rootInverse,
            int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View view = viewRef.get();
        if (view == null || dockRoot == null || rootInverse == null || style == null || !style.enabled
                || !belongsTo(dockRoot) || !LauncherGlassVisibility.isVisible(view, dockRoot)
                || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(view);
        float left = icon != null ? icon.left : 0f;
        float top = icon != null ? icon.top : 0f;
        float right = icon != null ? icon.right : view.getWidth();
        float bottom = icon != null ? icon.bottom : view.getHeight();
        float density = view.getResources().getDisplayMetrics().density;
        float[] b = LauncherGlassBoundsPolicy.apply(left, top, right, bottom,
                style.sizeOffsetDp * density);
        float[] points = new float[]{b[0], b[1], b[2], b[3]};
        Matrix global = new Matrix();
        view.transformMatrixToGlobal(global);
        global.mapPoints(points);
        rootInverse.mapPoints(points);
        float width = Math.max(1f, (points[2] - points[0]) * scaleX);
        float height = Math.max(1f, (points[3] - points[1]) * scaleY);
        float x = sampleInsetLeft + points[0] * scaleX;
        float y = sampleInsetTop + points[1] * scaleY;
        Drawable drawable = null;
        if (view instanceof TextView) {
            Drawable[] drawables = ((TextView) view).getCompoundDrawables();
            if (drawables.length > 1) drawable = drawables[1];
        }
        float fallback = Math.min(width, height) * 0.22f;
        float radius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density * Math.min(scaleX, scaleY)
                : LauncherGlassIconShapeResolver.resolveAutoRadius(drawable, width, height, fallback);
        return LauncherGlassGeometry.resolve(framebufferWidth, framebufferHeight,
                x, y, x + width, y + height,
                LauncherGlassBoundsPolicy.capRadius(radius, width, height));
    }
    private static long mix(long h, long v) { return (h ^ v) * 0x100000001b3L; }
}
''')

write_java("DockGlassCompositor.java", r'''
package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;
import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Dock body and Dock icon glass are drawn in one Dock-local Prismal frame/output swap. */
final class DockGlassCompositor {
    private final WeakReference<View> dockRootRef;
    private final ArrayList<DockGlassItemNode> cached = new ArrayList<>();
    private volatile GlassComponentStyle iconStyle = new GlassComponentStyle(false, 0f, 0f);
    private volatile DockGlassSceneSnapshot latestScene = DockGlassSceneSnapshot.EMPTY;
    private long seenRevision = -1L;
    private long lastFingerprint = Long.MIN_VALUE;
    private int lastW = -1, lastH = -1;
    private float lastInsetL = Float.NaN, lastInsetT = Float.NaN;

    DockGlassCompositor(View dockRoot) { dockRootRef = new WeakReference<>(dockRoot); }
    void setIconStyle(GlassComponentStyle style) {
        iconStyle = style != null ? style : new GlassComponentStyle(false, 0f, 0f);
        seenRevision = -1L; lastFingerprint = Long.MIN_VALUE;
        if (!iconStyle.enabled) latestScene = DockGlassSceneSnapshot.EMPTY;
    }
    void refreshUiSceneIfNeeded(int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View dockRoot = dockRootRef.get();
        if (dockRoot == null || !GlassRuntimeState.isEnabled() || !iconStyle.enabled) {
            cached.clear(); latestScene = DockGlassSceneSnapshot.EMPTY; return;
        }
        long revision = DockGlassItemRegistry.revision();
        boolean dead = false;
        for (DockGlassItemNode item : cached) if (item.view() == null || !item.belongsTo(dockRoot)) { dead = true; break; }
        if (revision != seenRevision || dead) {
            cached.clear();
            for (View candidate : DockGlassItemRegistry.snapshotForRoot(dockRoot.getRootView())) {
                DockGlassItemNode item = new DockGlassItemNode(candidate, iconStyle);
                if (item.belongsTo(dockRoot)) cached.add(item);
            }
            seenRevision = revision; lastFingerprint = Long.MIN_VALUE;
        }
        long fingerprint = 0xcbf29ce484222325L;
        for (DockGlassItemNode item : cached) fingerprint = (fingerprint ^ item.uiFingerprint(dockRoot)) * 0x100000001b3L;
        boolean mappingChanged = framebufferWidth != lastW || framebufferHeight != lastH
                || Float.compare(sampleInsetLeft, lastInsetL) != 0 || Float.compare(sampleInsetTop, lastInsetT) != 0;
        if (!mappingChanged && fingerprint == lastFingerprint) return;
        Matrix rootGlobal = new Matrix(); dockRoot.transformMatrixToGlobal(rootGlobal);
        Matrix rootInverse = new Matrix();
        if (!rootGlobal.invert(rootInverse)) { latestScene = DockGlassSceneSnapshot.EMPTY; return; }
        ArrayList<LauncherGlassGeometry.Snapshot> out = new ArrayList<>();
        for (DockGlassItemNode item : cached) {
            LauncherGlassGeometry.Snapshot g = item.capture(dockRoot, rootInverse,
                    framebufferWidth, framebufferHeight, sampleInsetLeft, sampleInsetTop, scaleX, scaleY);
            if (g != null) out.add(g);
        }
        latestScene = new DockGlassSceneSnapshot(out.toArray(new LauncherGlassGeometry.Snapshot[0]));
        lastFingerprint = fingerprint; lastW = framebufferWidth; lastH = framebufferHeight;
        lastInsetL = sampleInsetLeft; lastInsetT = sampleInsetTop;
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
}
''')

write_java("GlassRuntimeState.java", r'''
package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.hellovoid.liquiddock.config.ConfigSchema;

/** Process-local glass kill switch. Preference changes tear down GPU resources immediately. */
final class GlassRuntimeState {
    private static volatile boolean enabled;
    private static SharedPreferences prefs;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;
    private GlassRuntimeState() {}

    static synchronized void initialize(SharedPreferences nextPrefs, boolean initialEnabled) {
        if (prefs != null && listener != null) {
            try { prefs.unregisterOnSharedPreferenceChangeListener(listener); } catch (Throwable ignored) {}
        }
        prefs = nextPrefs;
        enabled = initialEnabled;
        if (nextPrefs == null) return;
        listener = (sharedPreferences, key) -> {
            if (!ConfigSchema.Glass.ENABLED.name().equals(key)
                    && !ConfigSchema.Core.ENABLED.name().equals(key)) return;
            boolean next = sharedPreferences.getBoolean(ConfigSchema.Core.ENABLED.name(),
                    ConfigSchema.Core.ENABLED.runtimeDefault())
                    && sharedPreferences.getBoolean(ConfigSchema.Glass.ENABLED.name(),
                    ConfigSchema.Glass.ENABLED.runtimeDefault());
            apply(next);
        };
        nextPrefs.registerOnSharedPreferenceChangeListener(listener);
        MainHook.log("[DC][GlassRuntime] initialized enabled=" + enabled);
    }

    static boolean isEnabled() { return enabled; }

    private static void apply(boolean next) {
        if (enabled == next) return;
        enabled = next;
        MainHook.log("[DC][GlassRuntime] enabled=" + next);
        if (next) return;
        Runnable teardown = () -> {
            DockGlassItemRegistry.clear();
            LauncherGlassDragOverlay.releaseAll();
            LauncherGlassSessionRegistry.shutdownAll();
            Miuix307MaterialPipeline.onRuntimeGlassDisabled();
            MiuixGlassHook.onRuntimeGlassDisabled();
            MainHook.log("[DC][GlassRuntime] GPU glass teardown complete");
        };
        Looper main = Looper.getMainLooper();
        if (Looper.myLooper() == main) teardown.run();
        else new Handler(main).post(teardown);
    }
}
''')

write_java("LauncherGlassRecentsHook.java", r'''
package com.hellovoid.liquiddock;

import android.view.View;

/** Binds Launcher.mOverviewPanel to the Workspace scene visibility state machine. */
final class LauncherGlassRecentsHook {
    private static boolean installed;
    private LauncherGlassRecentsHook() {}
    static void install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed || config == null || !config.enabled || !config.glass.enabled) return;
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher", "setupViews", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                try {
                    Object launcher = chain.getThisObject();
                    Object workspace = HookUtil.getField(launcher, "mWorkspace");
                    Object overview = HookUtil.getField(launcher, "mOverviewPanel");
                    if (workspace instanceof View && overview instanceof View) {
                        LauncherGlassSceneController.bindRecentsView((View) workspace, (View) overview);
                    }
                } catch (Throwable error) {
                    MainHook.log("[DC][GlassScene] recents bind unavailable: " + error);
                }
                return result;
            });
            installed = true;
        } catch (Throwable error) {
            MainHook.log("[DC][GlassScene] recents hook unavailable: " + error);
        }
    }
}
''')

# Runtime initialization and Recents binding hook.
replace_once("src/main/java/com/hellovoid/liquiddock/ModuleMain.java",
'''        ConfigReader configReader = ConfigReader.load();
        LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
        new MainHook().install(classLoader);''',
'''        ConfigReader configReader = ConfigReader.load();
        LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
        GlassRuntimeState.initialize(Api101Bridge.remotePreferences("config"),
                runtimeConfig.enabled && runtimeConfig.glass.enabled);
        new MainHook().install(classLoader);''')
replace_once("src/main/java/com/hellovoid/liquiddock/ModuleMain.java",
'''        MiuixLauncherStaticGlassHook.install(classLoader, runtimeConfig);
        DockBottomGeometryHook.install(classLoader);''',
'''        MiuixLauncherStaticGlassHook.install(classLoader, runtimeConfig);
        LauncherGlassRecentsHook.install(classLoader, runtimeConfig);
        DockBottomGeometryHook.install(classLoader);''')

# Bridge: a realtime Dock pulse must submit a transaction even when the previous flag was already true.
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java",
'''    /** Workspace-only demand pulse. Dock never calls this and therefore stays continuous. */
    static void requestSingleUpdate(Binding binding, View host) {''',
'''    /** Dock realtime pulse: submit an update transaction every display frame. */
    static void requestFrame(Binding binding) {
        if (binding == null || !binding.bound || !binding.rootSurface.isValid()) return;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            binding.setUpdateTextureFlag.invoke(transaction, binding.rootSurface,
                    Boolean.TRUE, Float.valueOf(binding.scale));
            transaction.apply();
            binding.updatesEnabled = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur realtime update failed: " + error);
        }
    }

    /** Workspace-only demand pulse. */
    static void requestSingleUpdate(Binding binding, View host) {''')

# Dock view: active producer pump, request/receive diagnostics, and one Dock-local body+icon batch.
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''import java.util.concurrent.atomic.AtomicBoolean;''',
'''import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''    private final WeakReference<View> materialHostRef;
    private final FloatBuffer quadBuffer;''',
'''    private final WeakReference<View> materialHostRef;
    private final DockGlassCompositor dockCompositor;
    private final FloatBuffer quadBuffer;''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final float[] textureMatrix = new float[16];''',
'''    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final AtomicLong producerRequestCount = new AtomicLong();
    private final AtomicLong producerFrameCount = new AtomicLong();
    private final float[] textureMatrix = new float[16];
    private final Runnable producerPump = this::pumpProducerFrame;''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''        materialHostRef = new WeakReference<>(materialHost);
        opticalParams = Miuix307PrismalMaterial.defaults(''',
'''        materialHostRef = new WeakReference<>(materialHost);
        View dockRoot = LauncherGlassHierarchy.findDockRoot(materialHost);
        dockCompositor = new DockGlassCompositor(dockRoot != null ? dockRoot : materialHost);
        opticalParams = Miuix307PrismalMaterial.defaults(''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''        portablePrismalParams = Miuix307PrismalAdapter.toPortable(opticalParams);
        topSamplingExtraPx = glassConfig.samplingExtraTopPx;''',
'''        portablePrismalParams = Miuix307PrismalAdapter.toPortable(opticalParams);
        dockCompositor.setIconStyle(glassConfig.iconStyle);
        topSamplingExtraPx = glassConfig.samplingExtraTopPx;''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;''',
'''    private void pumpProducerFrame() {
        if (shuttingDown || !GlassRuntimeState.isEnabled()) return;
        Miuix307PassBlurBridge.Binding currentBinding = binding;
        if (currentBinding != null && currentBinding.bound && isAttachedToWindow() && isShown()) {
            long requests = producerRequestCount.incrementAndGet();
            Miuix307PassBlurBridge.requestFrame(currentBinding);
            if (requests % 300L == 0L) {
                MainHook.log(TAG + " realtime producer requests=" + requests
                        + " oesFrames=" + producerFrameCount.get());
            }
        }
        postOnAnimation(producerPump);
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        removeCallbacks(producerPump);''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''        installGeometryObserver();
        updateBackdropMapping();''',
'''        installGeometryObserver();
        removeCallbacks(producerPump);
        postOnAnimation(producerPump);
        updateBackdropMapping();''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            frameAvailable.set(true);''',
'''        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            producerFrameCount.incrementAndGet();
            frameAvailable.set(true);''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''            PrismalGeometry prismalGeometry = createPrismalGeometry(mapping);
            int prismalTexture = prismalRenderer.render(
                    rawTexture, prismalGeometry, mapping.prismalParams);
            renderCompositePass(prismalTexture, mapping);''',
'''            PrismalGeometry prismalGeometry = createPrismalGeometry(mapping);
            prismalRenderer.prepareBackdrop(
                    rawTexture, mapping.sampleWidth, mapping.sampleHeight, mapping.prismalParams);
            DockGlassSceneSnapshot dockScene = dockCompositor.latestScene();
            dockCompositor.drawFrame(prismalRenderer, prismalGeometry, mapping.prismalParams,
                    dockScene, mapping.sampleWidth, mapping.sampleHeight);
            int prismalTexture = prismalRenderer.outputTexture();
            renderCompositePass(prismalTexture, mapping);''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''        float nextDockUvHeight = visibleHeight / (float) sampleHeight;

        BackdropSnapshot currentSnapshot = backdropSnapshot;''',
'''        float nextDockUvHeight = visibleHeight / (float) sampleHeight;
        dockCompositor.refreshUiSceneIfNeeded(sampleWidth, sampleHeight,
                insets.left, insets.top, 1f, 1f);

        BackdropSnapshot currentSnapshot = backdropSnapshot;''')

# Domain-aware ShortcutIcon/widget ownership. Keep attach listener so reparenting can be reclassified.
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java",
'''                @Override public void onViewDetachedFromWindow(View v) {}
''',
'''                @Override public void onViewDetachedFromWindow(View v) {
                    DockGlassItemRegistry.unregister(v);
                    LauncherGlassStaticNode node = LauncherGlassStaticNode.find(v);
                    if (node != null) node.dispose();
                }
''')
old_schedule = '''    private static void scheduleBind(
            View host, LauncherGlassDragState.Kind kind,
            LiquidDockConfig.Glass glassConfig, int attempt) {
        if (host == null || !host.isAttachedToWindow() || attempt > MAX_BIND_ATTEMPTS) return;
        if (host.getWidth() <= 0 || host.getHeight() <= 0) {
            host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            return;
        }
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
        if (node == null || node.kind() != kind) {
            float radius = resolveCornerRadius(host, kind);
            node = LauncherGlassStaticNode.attachToMaterial(host, kind, radius, glassConfig);
        } else {
            node.requestLifecycleRefresh();
        }
        if (node != null) {
            if (kind == LauncherGlassDragState.Kind.WIDGET)
                LauncherGlassVendorMaterialSuppressor.claimWidget(host);
            removeBootstrapObserver(host);
        }
    }
'''
new_schedule = '''    private static void scheduleBind(
            View host, LauncherGlassDragState.Kind kind,
            LiquidDockConfig.Glass glassConfig, int attempt) {
        if (!GlassRuntimeState.isEnabled() || host == null || !host.isAttachedToWindow()
                || attempt > MAX_BIND_ATTEMPTS) return;
        if (host.getWidth() <= 0 || host.getHeight() <= 0) {
            host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            return;
        }
        LauncherGlassHierarchy.Domain domain = LauncherGlassHierarchy.classify(host);
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
        if (kind == LauncherGlassDragState.Kind.ICON
                && domain == LauncherGlassHierarchy.Domain.DOCK) {
            if (node != null) node.dispose();
            DockGlassItemRegistry.register(host);
            return;
        }
        DockGlassItemRegistry.unregister(host);
        if (domain != LauncherGlassHierarchy.Domain.WORKSPACE) {
            if (node != null) node.dispose();
            return;
        }
        if (node == null || node.kind() != kind) {
            float radius = resolveCornerRadius(host, kind);
            node = LauncherGlassStaticNode.attachToMaterial(host, kind, radius, glassConfig);
        } else {
            node.requestLifecycleRefresh();
        }
        if (node != null && kind == LauncherGlassDragState.Kind.WIDGET) {
            LauncherGlassVendorMaterialSuppressor.claimWidget(host);
        }
    }
'''
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java", old_schedule, new_schedule)

# Folder glass exists only for Workspace FolderIcon representations.
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''    private static void attachFromFolderIcon(ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        observeFolderIconAttach(icon, glassConfig);
        try {''',
'''    private static void attachFromFolderIcon(ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        observeFolderIconAttach(icon, glassConfig);
        if (!GlassRuntimeState.isEnabled() || !LauncherGlassHierarchy.isWorkspace(icon)) {
            try {
                View material = resolveFolderMaterial(icon);
                if (material != null) {
                    LauncherGlassStaticNode old = claimedSink(material);
                    if (old != null) old.dispose();
                    CLAIMED.remove(material);
                }
            } catch (Throwable ignored) {}
            return;
        }
        try {''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''    private static LauncherGlassStaticNode attachMaterial(
            View material, LiquidDockConfig.Glass glassConfig) {
        if (material == null) return null;''',
'''    private static LauncherGlassStaticNode attachMaterial(
            View material, LiquidDockConfig.Glass glassConfig) {
        if (material == null || !GlassRuntimeState.isEnabled()
                || !LauncherGlassHierarchy.isWorkspace(material)) return null;''')

# Static nodes cannot render/reacquire outside Workspace or after runtime disable.
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java",
'''        if (disposed || material == null || root == null || style == null || !style.enabled
                || suppressedByFolderOpen || suppressedByDrag''',
'''        if (disposed || material == null || root == null || style == null || !style.enabled
                || !LauncherGlassHierarchy.isWorkspace(material)
                || suppressedByFolderOpen || suppressedByDrag''')
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java",
'''    private LauncherGlassSession ensureLiveSession() {
        if (disposed) return null;
        View material = materialRef.get();''',
'''    private LauncherGlassSession ensureLiveSession() {
        if (disposed) return null;
        if (!GlassRuntimeState.isEnabled()) return null;
        View material = materialRef.get();
        if (!LauncherGlassHierarchy.isWorkspace(material)) return null;''')

# Workspace session registry hard gate + global teardown.
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java",
'''import java.util.WeakHashMap;''',
'''import java.util.ArrayList;
import java.util.WeakHashMap;''')
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java",
'''    static synchronized LauncherGlassSession acquire(
            View materialHost, LiquidDockConfig.Glass glassConfig) {
        View root = resolveStableRoot(materialHost);''',
'''    static synchronized LauncherGlassSession acquire(
            View materialHost, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled()) return null;
        View root = resolveStableRoot(materialHost);''')
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java",
'''    static synchronized void forget(View root, LauncherGlassSession session) {''',
'''    static synchronized void shutdownAll() {
        ArrayList<View> roots = new ArrayList<>(SESSIONS.keySet());
        ArrayList<LauncherGlassSession> sessions = new ArrayList<>(SESSIONS.values());
        for (View root : roots) {
            LauncherGlassSceneController controller = LauncherGlassSceneController.findRoot(root);
            if (controller != null) controller.dispose();
        }
        for (LauncherGlassSession session : sessions) {
            if (session != null) session.shutdown();
        }
        SESSIONS.clear();
    }

    static synchronized void forget(View root, LauncherGlassSession session) {''')

# Drag overlay teardown and runtime gate.
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassDragOverlay.java",
'''    static boolean begin(
            View source,''',
'''    static boolean begin(
            View source,''')
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassDragOverlay.java",
'''            float cornerRadiusPx,
            float[] visualBounds) {
        LauncherGlassDragOverlay overlay = acquire(source, glassConfig);''',
'''            float cornerRadiusPx,
            float[] visualBounds) {
        if (!GlassRuntimeState.isEnabled()) return false;
        LauncherGlassDragOverlay overlay = acquire(source, glassConfig);''')
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassDragOverlay.java",
'''    static void end(View source, Object token) {
        LauncherGlassDragOverlay overlay = find(source, token);
        if (overlay != null) overlay.endInternal(token);
    }
''',
'''    static void end(View source, Object token) {
        LauncherGlassDragOverlay overlay = find(source, token);
        if (overlay != null) overlay.endInternal(token);
    }

    static void releaseAll() {
        LauncherGlassDragOverlay[] snapshot;
        synchronized (BY_ROOT) {
            snapshot = BY_ROOT.values().toArray(new LauncherGlassDragOverlay[0]);
        }
        for (LauncherGlassDragOverlay overlay : snapshot) {
            if (overlay == null) continue;
            View root = overlay.rootRef.get();
            if (root != null) overlay.releaseRoot(root);
        }
    }
''')

# Recents is a coverage state just like an opened folder; return to HOME requires a fresh frame.
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java",
'''    private final StateMachine state = new StateMachine();
    private volatile LiquidDockConfig.Glass glassConfig;''',
'''    private final StateMachine state = new StateMachine();
    private volatile LiquidDockConfig.Glass glassConfig;
    private WeakReference<View> recentsRef = new WeakReference<>(null);
    private boolean folderCovered;
    private boolean recentsCovered;''')
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java",
'''    static void setWorkspaceCovered(View anyView, boolean covered) {
        LauncherGlassSceneController controller = find(anyView);
        if (controller != null) controller.setCovered(covered);
    }
''',
'''    static void setWorkspaceCovered(View anyView, boolean covered) {
        LauncherGlassSceneController controller = find(anyView);
        if (controller != null) controller.setFolderCovered(covered);
    }

    static void bindRecentsView(View anyView, View recents) {
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
''')
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassSceneController.java",
'''    private void setCovered(boolean covered) {
        boolean wasCovered = state.state() == State.COVERED;
        state.setCovered(covered);''',
'''    private void setFolderCovered(boolean covered) {
        folderCovered = covered;
        setEffectiveCovered(folderCovered || recentsCovered);
    }

    private void setRecentsCovered(boolean covered) {
        if (recentsCovered == covered) return;
        recentsCovered = covered;
        setEffectiveCovered(folderCovered || recentsCovered);
    }

    private void setEffectiveCovered(boolean covered) {
        boolean wasCovered = state.state() == State.COVERED;
        state.setCovered(covered);''')
replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java",
'''        View root = rootRef.get();
        if (root == null) return;
        int nextWidth = root.getWidth();''',
'''        View root = rootRef.get();
        if (root == null) return;
        LauncherGlassSceneController.syncRecentsForRoot(root);
        int nextWidth = root.getWidth();''')

# Material pipeline must stop rebind/recovery when runtime glass is disabled.
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java",
'''    static boolean isInstalled() {
        return installed;
    }
''',
'''    static boolean isInstalled() {
        return installed;
    }

    static void onRuntimeGlassDisabled() {
        clearHierarchyObservation();
        clearHierarchyLayoutRecovery();
        hierarchyRebindPosted = false;
        geometryDeferredLoggedFor = new WeakReference<>(null);
    }
''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java",
'''    private static boolean ensureGlassBound(
            View background, LiquidDockConfig config, ClassLoader classLoader) {
        if (background == null || !isSupportedBackground(background)) return false;''',
'''    private static boolean ensureGlassBound(
            View background, LiquidDockConfig config, ClassLoader classLoader) {
        if (!GlassRuntimeState.isEnabled()) return false;
        if (background == null || !isSupportedBackground(background)) return false;''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java",
'''                if (MainHook.isWorkstationMode()) {
                    Miuix307ZeroCopyRenderer.rebindProducer("workstation-launcher-resume");
                }''',
'''                if (GlassRuntimeState.isEnabled() && MainHook.isWorkstationMode()) {
                    Miuix307ZeroCopyRenderer.rebindProducer("workstation-launcher-resume");
                }''')

# Dock visual teardown: restore vendor material and remove all injected observers/resources.
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''    private static WeakReference<View> transparentMaterialOwner = new WeakReference<>(null);
    private static GradientDrawable transparentMaterialBody;''',
'''    private static WeakReference<View> transparentMaterialOwner = new WeakReference<>(null);
    private static WeakReference<View> originalMaterialOwner = new WeakReference<>(null);
    private static Drawable originalMaterialBody;
    private static GradientDrawable transparentMaterialBody;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''        transparentMaterialOwner = new WeakReference<>(null);
        transparentMaterialBody = null;''',
'''        transparentMaterialOwner = new WeakReference<>(null);
        originalMaterialOwner = new WeakReference<>(null);
        originalMaterialBody = null;
        transparentMaterialBody = null;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''    static void onHostDetached(DockLiquidGlassHostView detachedHost) {''',
'''    static void onRuntimeGlassDisabled() {
        View background = currentBackground();
        DockLiquidGlassHostView host = currentHost();
        removeVendorGpuBlurSuppressor();
        Miuix307ZeroCopyRenderer.clear();
        if (host != null && host.getParent() instanceof ViewGroup) {
            ((ViewGroup) host.getParent()).removeView(host);
        }
        restoreVendorMaterialBody();
        clearTrackedViews();
        if (background != null) {
            background.requestLayout();
            background.invalidate();
        }
    }

    static void onHostDetached(DockLiquidGlassHostView detachedHost) {''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''    static int suppressCompatBackgroundBlurRadius(View dockBg, int requestedRadius) {
        if (dockBg == null || requestedRadius <= 0) return requestedRadius;''',
'''    static int suppressCompatBackgroundBlurRadius(View dockBg, int requestedRadius) {
        if (!GlassRuntimeState.isEnabled()) return requestedRadius;
        if (dockBg == null || requestedRadius <= 0) return requestedRadius;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''    static boolean install(View dockBg, LiquidDockConfig config) {
        if (!(dockBg instanceof ViewGroup) || config == null) return false;''',
'''    static boolean install(View dockBg, LiquidDockConfig config) {
        if (!GlassRuntimeState.isEnabled()) return false;
        if (!(dockBg instanceof ViewGroup) || config == null) return false;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''    private static void scheduleZeroCopyValidation(
            View dockBg, DockLiquidGlassHostView host, int frame) {
        if (dockBg != currentBackground() || host != currentHost()) return;''',
'''    private static void scheduleZeroCopyValidation(
            View dockBg, DockLiquidGlassHostView host, int frame) {
        if (!GlassRuntimeState.isEnabled()) return;
        if (dockBg != currentBackground() || host != currentHost()) return;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''    static void suppressVendorGpuBlur(View dockBg) {
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return;''',
'''    static void suppressVendorGpuBlur(View dockBg) {
        if (!GlassRuntimeState.isEnabled()) return;
        if (dockBg == null || !isNativeVisualOwner(dockBg)) return;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''        if (transparentMaterialOwner.get() != dockBg || transparentMaterialBody == null) {
            transparentMaterialOwner = new WeakReference<>(dockBg);''',
'''        if (transparentMaterialOwner.get() != dockBg || transparentMaterialBody == null) {
            Drawable current = dockBg.getBackground();
            if (current != null && current != transparentMaterialBody) {
                originalMaterialOwner = new WeakReference<>(dockBg);
                originalMaterialBody = current;
            }
            transparentMaterialOwner = new WeakReference<>(dockBg);''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
'''    private static int readDimension(View dockBg, String fieldName, boolean width) {''',
'''    private static void restoreVendorMaterialBody() {
        View owner = originalMaterialOwner.get();
        Drawable original = originalMaterialBody;
        if (owner != null && original != null) owner.setBackground(original);
    }

    private static int readDimension(View dockBg, String fieldName, boolean width) {''')

print("glass device fix applied")
