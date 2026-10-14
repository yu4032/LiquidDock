package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Adapts the vendor 2x2_4 folder into four full-size shortcuts on a real 2x2
 * Workspace footprint. Size conversion belongs to the native Launcher model,
 * never to a View scale or unsaved ItemInfo mutation.
 *
 * <p>Launcher 4.50's 2x2_4 preview normally has three large clickable icons
 * followed by four small non-clickable icons. Its native preSetup2x2() already
 * supports a fourth large item: the two preview limits become four. Native
 * FolderInfo.convertIconSize(21) is used to repair any inconsistent 1x1
 * footprint, retaining vendor collision checks and database persistence.
 * Ordinary one-cell four-app folders are promoted only after explicit feature opt-in.\n * Nine-icon and non-four-app folders are left untouched.</p>
 */
final class LauncherLargeFolderFourIconHook {
    private static final String TAG = "[DC][FolderFour]";
    private static final String LARGE_FOLDER =
            "com.miui.home.launcher.folder.FolderIcon2x2";
    private static final String SMALL_FOLDER =
            "com.miui.home.launcher.folder.FolderIcon1x1";
    private static final String FOUR_FOLDER =
            "com.miui.home.launcher.folder.FolderIcon2x2_4";
    private static final String FOUR_PREVIEW =
            "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_4";
    private static final int FOUR = 4;
    private static final int NATIVE_2X2_FOUR_FOLDER_TYPE = 21;
    private static final Set<View> CONVERSION_ATTEMPTED =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private static boolean installed;
    private static boolean enabled;
    private static boolean failureLogged;

    static boolean isEnabled() {
        return enabled;
    }

    static boolean isNativeFourFolderInfo(Object info) {
        // CellLayout geometry must follow a persisted 2x2 model even when
        // optional vendor preview setters cannot be hooked. Menu availability
        // and real workspace occupancy do not depend on preview compatibility.
        if (info == null) return false;
        try {
            return LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(
                    HookUtil.getIntField(info, "itemType"),
                    HookUtil.getIntField(info, "spanX"),
                    HookUtil.getIntField(info, "spanY"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private LauncherLargeFolderFourIconHook() {}

    static boolean install(ClassLoader classLoader) {
        if (classLoader == null) return false;
        if (installed) return enabled;
        try {
            Class<?> folderType = Class.forName(LARGE_FOLDER, false, classLoader);
            Class<?> fourFolderType = Class.forName(FOUR_FOLDER, false, classLoader);
            Class<?> smallFolderType = Class.forName(SMALL_FOLDER, false, classLoader);
            Class<?> previewType = Class.forName(FOUR_PREVIEW, false, classLoader);
            Class<?> itemInfoType = Class.forName(
                    "com.miui.home.launcher.ItemInfo", false, classLoader);
            Class<?> launcherType = Class.forName(
                    "com.miui.home.launcher.Launcher", false, classLoader);
            Class<?> folderInfoType = Class.forName(
                    "com.miui.home.launcher.FolderInfo", false, classLoader);
            Class<?> baseFolderType = Class.forName(
                    "com.miui.home.launcher.FolderIcon", false, classLoader);
            // Vendor 4.50 variants sometimes inline or rename individual methods.
            // Do not let a missing optional lifecycle callback suppress all
            // remaining hooks. Check minimum viable coverage before enabling.
            boolean inflate = installOptional(folderType, "onFinishInflate",
                    new Class<?>[0], chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (fourFolderType.isInstance(chain.getThisObject())) {
                            configureFolder(chain.getThisObject());
                        }
                        return result;
                    });
            boolean createOrRemove = installOptional(folderType, "createOrRemoveView",
                    new Class<?>[0], chain -> {
                        if (fourFolderType.isInstance(chain.getThisObject())) {
                            configureFolder(chain.getThisObject());
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            boolean bindClicks = installOptional(folderType, "addItemOnclickListener",
                    new Class<?>[0], chain -> {
                        if (fourFolderType.isInstance(chain.getThisObject())) {
                            configureFolder(chain.getThisObject());
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            boolean setup = installOptional(previewType, "preSetup2x2",
                    new Class<?>[0], chain -> {
                        configurePreview(chain.getThisObject());
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });

            boolean fromXml = installOptional(baseFolderType, "fromXml",
                    new Class<?>[]{int.class, launcherType, ViewGroup.class, folderInfoType},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (result instanceof View) {
                            if (fourFolderType.isInstance(result)) {
                                configureFolder(result);
                                scheduleNativeFootprintRepair((View) result);
                            } else if (smallFolderType.isInstance(result)) {
                                scheduleNativeFootprintRepair((View) result);
                            }
                        }
                        return result;
                    });
            boolean bindFolder = installOptional(baseFolderType, "bindFolderInfo",
                    new Class<?>[]{launcherType, folderInfoType},
                    chain -> {
                        Object owner = chain.getThisObject();
                        Object info = chain.getArg(1);
                        if (owner instanceof View && fourFolderType.isInstance(owner)) {
                            configureFolderForInfo(owner, info);
                        }
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (owner instanceof View) {
                            configureFolder(owner);
                            scheduleNativeFootprintRepair((View) owner);
                        }
                        return result;
                    });
            boolean folderChanged = installOptional(folderInfoType, "notifyDataSetChanged",
                    new Class<?>[0], chain -> {
                        Object info = chain.getThisObject();
                        HookUtil.InvocationResult<Object> bound =
                                HookUtil.tryInvoke(info, "getBuddyIconView");
                        // The vendor constructs its preview children during
                        // notifyDataSetChanged; set the four-item limit BEFORE
                        // that happens. A post-bind count update alone never
                        // removes the stock extra preview children.
                        if (bound.succeeded() && bound.value() instanceof View) {
                            configureFolder(bound.value());
                        }
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (bound.succeeded() && bound.value() instanceof View) {
                            scheduleNativeFootprintRepair((View) bound.value());
                        }
                        return result;
                    });
            boolean rebind = installOptional(folderType, "rebindInfo",
                    new Class<?>[]{itemInfoType, ViewGroup.class},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (fourFolderType.isInstance(owner) && owner instanceof View) {
                            configureFolder(owner);
                            scheduleNativeFootprintRepair((View) owner);
                        }
                        return result;
                    });
            // If preview or model conversion coverage is incomplete, fail closed.
            // Individual callbacks remain registered but will return stock results.
            enabled = setup && bindClicks && (inflate || createOrRemove)
                    && (fromXml || bindFolder || folderChanged);
            installed = true;
            MainHook.log(TAG + " installed=" + enabled
                    + " inflate=" + inflate
                    + " previewCreate=" + createOrRemove
                    + " click=" + bindClicks
                    + " previewSetup=" + setup
                    + " fromXml=" + fromXml
                    + " bind=" + bindFolder
                    + " changed=" + folderChanged
                    + " rebind=" + rebind);
            return enabled;
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable, preserving stock layout: " + error);
            return false;
        }
    }

    private static boolean installOptional(Class<?> owner, String name, Class<?>[] signature,
                                           io.github.libxposed.api.XposedInterface.Hooker callback) {
        try {
            HookUtil.hook(HookUtil.findMethodExact(owner, name, signature), callback);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " optional " + owner.getSimpleName()
                    + "#" + name + " unavailable: " + error);
            return false;
        }
    }

    /**
     * The view class name is not the placement authority: a folder with a 1x1
     * FolderInfo still reserves one cell. Restore a true 2x2 only through the
     * Launcher's native conversion transaction, which searches for free cells
     * and updates the persisted model. Never set spanX/spanY directly.
     */
    private static void scheduleNativeFootprintRepair(View folderView) {
        if (!enabled || folderView == null
                || !LauncherLargeFolderPickerHook.isMarked(
                        folderView.getContext(), folderView.getTag())) return;
        if (!enabled || folderView == null
                || (!FOUR_FOLDER.equals(folderView.getClass().getName())
                && !SMALL_FOLDER.equals(folderView.getClass().getName()))) return;
        if (SMALL_FOLDER.equals(folderView.getClass().getName())) {
            Object tag = folderView.getTag();
            if (tag == null || folderChildCount(tag) != FOUR) return;
        }
        if (folderView.isAttachedToWindow()) {
            folderView.post(() -> repairNativeFootprintIfNeeded(folderView));
            return;
        }
        folderView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                view.post(() -> repairNativeFootprintIfNeeded(view));
            }
            @Override public void onViewDetachedFromWindow(View view) {}
        });
    }

    private static void repairNativeFootprintIfNeeded(View folderView) {
        if (!enabled || !folderView.isAttachedToWindow()
                || !LauncherGlassHierarchy.isWorkspace(folderView)) return;
        final Object info;
        try {
            info = HookUtil.getField(folderView, "mInfo");
        } catch (Throwable ignored) {
            return;
        }
        if (info == null) return;
        try {
            int type = HookUtil.getIntField(info, "itemType");
            int spanX = HookUtil.getIntField(info, "spanX");
            int spanY = HookUtil.getIntField(info, "spanY");
            long id = HookUtil.getLongField(info, "id");
            long screenId = HookUtil.getLongField(info, "screenId");
            long container = HookUtil.getLongField(info, "container");
            boolean repairFour = LargeFolderFootprintPolicy.needsNativeConversion(
                    type, spanX, spanY, id, screenId, container);
            // Explicitly marked folders may contain any number of shortcuts;
            // only the first four are previewed, and the remaining children
            // stay accessible when the folder opens.
            boolean promoteSmall = SMALL_FOLDER.equals(folderView.getClass().getName())
                    && type == 2 && spanX == 1 && spanY == 1
                    && id >= 0 && screenId >= 0 && container == -100;
            if (!repairFour && !promoteSmall) return;
        } catch (Throwable ignored) {
            return;
        }
        // A repeated rebind while the asynchronous vendor transaction is
        // in flight must not start a second conversion for the same view.
        if (!CONVERSION_ATTEMPTED.add(folderView)) return;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(
                info, "convertIconSize", NATIVE_2X2_FOUR_FOLDER_TYPE);
        if (!result.succeeded()) {
            CONVERSION_ATTEMPTED.remove(folderView);
            MainHook.log(TAG + " native 2x2 footprint conversion failed: " + result.failure());
        } else {
            MainHook.log(TAG + " delegated normal 1x1 -> real 2x2 size/occupancy to Launcher");
        }
    }

    private static int folderChildCount(Object folderInfo) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(folderInfo, "count");
        return result.succeeded() && result.value() instanceof Integer
                ? (Integer) result.value() : -1;
    }

    private static void configureFolderForInfo(Object owner, Object info) {
        if (!(owner instanceof View) || info == null
                || !FOUR_FOLDER.equals(owner.getClass().getName())) return;
        HookUtil.InvocationResult<Object> lookup =
                HookUtil.tryInvoke(owner, "getMPreviewContainer");
        if (!lookup.succeeded() || lookup.value() == null) return;
        boolean selected = LauncherLargeFolderPickerHook.isMarked(
                ((View) owner).getContext(), info);
        if (!setCounts(owner, selected) || !setCounts(lookup.value(), selected)) {
            reportFailure();
        }
    }

    private static void configureFolder(Object owner) {
        if (!enabled || owner == null || !FOUR_FOLDER.equals(owner.getClass().getName())
                || !(owner instanceof View)) {
            return;
        }
        Object info;
        try { info = HookUtil.getField(owner, "mInfo"); }
        catch (Throwable ignored) { return; }
        boolean selected = LauncherLargeFolderPickerHook.isMarked(((View) owner).getContext(), info);
        // The container is bound during onFinishInflate. Never act on an
        // uninitialized instance or on the native nine-icon folder variant.
        HookUtil.InvocationResult<Object> lookup =
                HookUtil.tryInvoke(owner, "getMPreviewContainer");
        if (!lookup.succeeded() || lookup.value() == null
                || !FOUR_PREVIEW.equals(lookup.value().getClass().getName())) {
            return;
        }
        if (!setCounts(owner, selected) || !setCounts(lookup.value(), selected)) {
            reportFailure();
        }
    }

    private static void configurePreview(Object preview) {
        if (!enabled || !(preview instanceof View)
                || !FOUR_PREVIEW.equals(preview.getClass().getName())) return;
        View view = (View) preview;
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
            View candidate = (View) parent;
            if (FOUR_FOLDER.equals(candidate.getClass().getName())) {
                Object info = candidate.getTag();
                boolean selected = LauncherLargeFolderPickerHook.isMarked(view.getContext(), info);
                if (!setCounts(preview, selected)) reportFailure();
                return;
            }
            parent = candidate.getParent();
        }
    }

    private static boolean setCounts(Object target, boolean selected) {
        // Native FolderIcon2x2.addItemOnclickListener() makes indices below
        // mLargeIconNum clickable. Four means all four are direct app targets.
        int count = selected ? FOUR : 7;
        int large = selected ? FOUR : 3;
        boolean first = HookUtil.tryInvoke(target, "setMItemsMaxCount", count).succeeded();
        boolean second = HookUtil.tryInvoke(target, "setMLargeIconNum", large).succeeded();
        return first && second;
    }

    private static void reportFailure() {
        if (failureLogged) return;
        failureLogged = true;
        MainHook.log(TAG + " Launcher vendor setters changed; four-icon preview unavailable");
    }
}
