#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing main-dock post anchor in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Preserve main's proven persistent PassBlur flag semantics. Do not pulse SurfaceControl per-vsync.
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java",
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

    /** Workspace-only demand pulse. */''',
'''    /** Workspace-only demand pulse. Dock keeps main's persistent continuous-on-bind mode. */''')

replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;''',
'''import java.util.concurrent.atomic.AtomicBoolean;''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final AtomicLong producerRequestCount = new AtomicLong();
    private final AtomicLong producerFrameCount = new AtomicLong();
    private final float[] textureMatrix = new float[16];
    private final Runnable producerPump = this::pumpProducerFrame;''',
'''    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final float[] textureMatrix = new float[16];''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
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
        removeCallbacks(producerPump);''',
'''    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''        installGeometryObserver();
        removeCallbacks(producerPump);
        postOnAnimation(producerPump);
        updateBackdropMapping();''',
'''        installGeometryObserver();
        updateBackdropMapping();''')
replace_once("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
'''        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            producerFrameCount.incrementAndGet();
            frameAvailable.set(true);''',
'''        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            frameAvailable.set(true);''')

# Runtime disable must restore the vendor Dock body before clearing ownership, then remove the
# injected host after detach has become idempotent.
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java",
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
''',
'''    static void onRuntimeGlassDisabled() {
        View background = currentBackground();
        DockLiquidGlassHostView host = currentHost();
        removeVendorGpuBlurSuppressor();
        Miuix307ZeroCopyRenderer.clear();
        restoreVendorMaterialBody();
        clearTrackedViews();
        if (host != null && host.getParent() instanceof ViewGroup) {
            ((ViewGroup) host.getParent()).removeView(host);
        }
        if (background != null) {
            background.requestLayout();
            background.invalidate();
        }
    }
''')

# Folder material is reversible: save the vendor drawable before transparent handoff and restore it
# whenever the object leaves Workspace or glass is disabled.
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''import java.lang.reflect.Method;
import java.util.Collections;''',
'''import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''    private static final Map<View, WeakReference<LauncherGlassStaticNode>> CLAIMED =
            Collections.synchronizedMap(new WeakHashMap<>());''',
'''    private static final Map<View, WeakReference<LauncherGlassStaticNode>> CLAIMED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Drawable> ORIGINAL_IMAGE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Drawable> ORIGINAL_BACKGROUND =
            Collections.synchronizedMap(new WeakHashMap<>());''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''                    LauncherGlassStaticNode old = claimedSink(material);
                    if (old != null) old.dispose();
                    CLAIMED.remove(material);
                }
            } catch (Throwable ignored) {}
            return;''',
'''                    LauncherGlassStaticNode old = claimedSink(material);
                    if (old != null) old.dispose();
                    CLAIMED.remove(material);
                    restoreMaterial(material);
                }
            } catch (Throwable ignored) {}
            return;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''    private static boolean isTransparentColorDrawable(Drawable drawable) {''',
'''    static void onRuntimeGlassDisabled() {
        for (View material : new ArrayList<>(CLAIMED.keySet())) {
            LauncherGlassStaticNode sink = claimedSink(material);
            if (sink != null) sink.dispose();
            restoreMaterial(material);
        }
        CLAIMED.clear();
        for (ViewGroup icon : new ArrayList<>(FOLDER_ATTACH_LISTENERS.keySet())) {
            View.OnAttachStateChangeListener listener = FOLDER_ATTACH_LISTENERS.remove(icon);
            if (listener != null) icon.removeOnAttachStateChangeListener(listener);
        }
        FOLDER_RECOVERY_PENDING.clear();
        openedFolderOwner = new WeakReference<>(null);
        openedFolderSink = new WeakReference<>(null);
    }

    private static void restoreMaterial(View material) {
        if (material == null) return;
        if (material instanceof ImageView) {
            Drawable original = ORIGINAL_IMAGE.remove(material);
            if (original != null) ((ImageView) material).setImageDrawable(original);
        } else {
            Drawable original = ORIGINAL_BACKGROUND.remove(material);
            if (original != null) material.setBackground(original);
        }
    }

    private static boolean isTransparentColorDrawable(Drawable drawable) {''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''    private static void makeMaterialTransparent(View material) {
        if (material instanceof ImageView) {
            ((ImageView) material).setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        } else {
            material.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }
    }''',
'''    private static void makeMaterialTransparent(View material) {
        if (material instanceof ImageView) {
            ImageView image = (ImageView) material;
            Drawable current = image.getDrawable();
            if (!ORIGINAL_IMAGE.containsKey(material) && current != null
                    && !isTransparentColorDrawable(current)) {
                ORIGINAL_IMAGE.put(material, current);
            }
            image.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        } else {
            Drawable current = material.getBackground();
            if (!ORIGINAL_BACKGROUND.containsKey(material) && current != null
                    && !isTransparentColorDrawable(current)) {
                ORIGINAL_BACKGROUND.put(material, current);
            }
            material.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }
    }''')

# Constructor/attach observers also need to be removed at runtime disable so they cannot recreate
# Workspace or Dock nodes after teardown.
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java",
'''import java.lang.reflect.Field;
import java.util.Collections;''',
'''import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;''')
replace_once("src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java",
'''    private MiuixLauncherStaticGlassHook() {}

    static boolean install''',
'''    private MiuixLauncherStaticGlassHook() {}

    static void onRuntimeGlassDisabled() {
        for (View host : new ArrayList<>(BOOTSTRAP_OBSERVERS.keySet())) {
            View.OnAttachStateChangeListener listener = BOOTSTRAP_OBSERVERS.remove(host);
            if (listener != null) host.removeOnAttachStateChangeListener(listener);
            DockGlassItemRegistry.unregister(host);
            LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
            if (node != null) node.dispose();
        }
        BOOTSTRAP_OBSERVERS.clear();
    }

    static boolean install''')

replace_once("src/main/java/com/hellovoid/liquiddock/GlassRuntimeState.java",
'''        Runnable teardown = () -> {
            DockGlassItemRegistry.clear();
            LauncherGlassDragOverlay.releaseAll();''',
'''        Runnable teardown = () -> {
            MiuixFolderGlassHook.onRuntimeGlassDisabled();
            MiuixLauncherStaticGlassHook.onRuntimeGlassDisabled();
            DockGlassItemRegistry.clear();
            LauncherGlassDragOverlay.releaseAll();''')

print("main Dock lifecycle + visual restore post-fix applied")
