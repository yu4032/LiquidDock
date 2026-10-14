package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
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
 * The 2x2_9 variant and ordinary folders are never touched.</p>
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
        if (!enabled || info == null) return false;
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

    static boolean install(ClassLoader classLoader, boolean featureEnabled) {
        if (!featureEnabled || classLoader == null) return false;
        if (installed) return true;
        try {
            Class<?> folderType = Class.forName(LARGE_FOLDER, false, classLoader);
            Class<?> fourFolderType = Class.forName(FOUR_FOLDER, false, classLoader);
            Class<?> smallFolderType = Class.forName(SMALL_FOLDER, false, classLoader);
            Class<?> previewType = Class.forName(FOUR_PREVIEW, false, classLoader);

            // Resolve every member before installing any hook. An unknown Launcher
            // variant must retain the original native layout.
            Method inflate = HookUtil.findMethodExact(
                    folderType, "onFinishInflate", new Class<?>[0]);
            Method createOrRemove = HookUtil.findMethodExact(
                    folderType, "createOrRemoveView", new Class<?>[0]);
            Method bindClicks = HookUtil.findMethodExact(
                    folderType, "addItemOnclickListener", new Class<?>[0]);
            Class<?> itemInfoType = Class.forName(
                    "com.miui.home.launcher.ItemInfo", false, classLoader);
            Method rebind = HookUtil.findMethodExact(folderType, "rebindInfo",
                    new Class<?>[]{itemInfoType, ViewGroup.class});
            Class<?> launcherType = Class.forName(
                    "com.miui.home.launcher.Launcher", false, classLoader);
            Class<?> folderInfoType = Class.forName(
                    "com.miui.home.launcher.FolderInfo", false, classLoader);
            Class<?> baseFolderType = Class.forName(
                    "com.miui.home.launcher.FolderIcon", false, classLoader);
            Method fromXml = HookUtil.findMethodExact(baseFolderType, "fromXml",
                    new Class<?>[]{int.class, launcherType, ViewGroup.class, folderInfoType});
            Method bindFolderInfo = HookUtil.findMethodExact(baseFolderType, "bindFolderInfo",
                    new Class<?>[]{launcherType, folderInfoType});
            Method folderChanged = HookUtil.findMethodExact(
                    folderInfoType, "notifyDataSetChanged", new Class<?>[0]);
            Method setup = HookUtil.findMethodExact(
                    previewType, "preSetup2x2", new Class<?>[0]);

            HookUtil.hook(inflate, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (fourFolderType.isInstance(chain.getThisObject())) {
                    configureFolder(chain.getThisObject());
                }
                return result;
            });
            HookUtil.hook(createOrRemove, chain -> {
                if (fourFolderType.isInstance(chain.getThisObject())) {
                    configureFolder(chain.getThisObject());
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(bindClicks, chain -> {
                if (fourFolderType.isInstance(chain.getThisObject())) {
                    configureFolder(chain.getThisObject());
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            // Initial Workspace binding uses FolderIcon.fromXml() directly,
            // not necessarily rebindInfo(). Cover both first placement and
            // subsequent model rebinds before considering the feature installed.
            HookUtil.hook(fromXml, chain -> {
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
            // New folders created by dragging two shortcuts can use
            // bindFolderInfo without the XML inflate path.
            HookUtil.hook(bindFolderInfo, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof View) scheduleNativeFootprintRepair((View) owner);
                return result;
            });
            // An existing ordinary folder becomes eligible when its fourth
            // shortcut is dropped in. No Launcher restart should be needed
            // after that model event once the feature is installed.
            HookUtil.hook(folderChanged, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object info = chain.getThisObject();
                if (folderChildCount(info) == FOUR) {
                    HookUtil.InvocationResult<Object> view =
                            HookUtil.tryInvoke(info, "getBuddyIconView");
                    if (view.succeeded() && view.value() instanceof View) {
                        scheduleNativeFootprintRepair((View) view.value());
                    }
                }
                return result;
            });
            HookUtil.hook(rebind, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (fourFolderType.isInstance(owner) && owner instanceof View) {
                    configureFolder(owner);
                    scheduleNativeFootprintRepair((View) owner);
                }
                return result;
            });
            HookUtil.hook(setup, chain -> {
                configurePreview(chain.getThisObject());
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            enabled = true;
            installed = true;
            MainHook.log(TAG + " native 2x2_4 four-icon preview enabled");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable, preserving stock layout: " + error);
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
            boolean promoteSmall = SMALL_FOLDER.equals(folderView.getClass().getName())
                    && LargeFolderFootprintPolicy.needsNativePromotion(
                            type, spanX, spanY, folderChildCount(info),
                            id, screenId, container);
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

    private static void configureFolder(Object owner) {
        if (!enabled || owner == null || !FOUR_FOLDER.equals(owner.getClass().getName())) {
            return;
        }
        // The container is bound during onFinishInflate. Never act on an
        // uninitialized instance or on the native nine-icon folder variant.
        HookUtil.InvocationResult<Object> lookup =
                HookUtil.tryInvoke(owner, "getMPreviewContainer");
        if (!lookup.succeeded() || lookup.value() == null
                || !FOUR_PREVIEW.equals(lookup.value().getClass().getName())) {
            return;
        }
        if (!setCounts(owner) || !setCounts(lookup.value())) {
            reportFailure();
        }
    }

    private static void configurePreview(Object preview) {
        if (!enabled || preview == null
                || !FOUR_PREVIEW.equals(preview.getClass().getName())) {
            return;
        }
        if (!setCounts(preview)) reportFailure();
    }

    private static boolean setCounts(Object target) {
        // Native FolderIcon2x2.addItemOnclickListener() makes indices below
        // mLargeIconNum clickable. Four means all four are direct app targets.
        return HookUtil.tryInvoke(target, "setMItemsMaxCount", FOUR).succeeded()
                && HookUtil.tryInvoke(target, "setMLargeIconNum", FOUR).succeeded();
    }

    private static void reportFailure() {
        if (failureLogged) return;
        failureLogged = true;
        MainHook.log(TAG + " Launcher vendor setters changed; four-icon preview unavailable");
    }
}
