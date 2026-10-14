package com.hellovoid.liquiddock;

import java.lang.reflect.Method;

/**
 * Makes the vendor 2x2_4 folder a four-equal-icon preview, without changing its
 * 2x2 Workspace occupancy, FolderInfo contents, animation or click dispatch.
 *
 * <p>Launcher 4.50's 2x2_4 preview normally has three large clickable icons
 * followed by four small non-clickable icons. Its native preSetup2x2() already
 * supports a fourth large item: we only set the two vendor item limits to four.
 * The 2x2_9 variant and ordinary folders are never touched.</p>
 */
final class LauncherLargeFolderFourIconHook {
    private static final String TAG = "[DC][FolderFour]";
    private static final String LARGE_FOLDER =
            "com.miui.home.launcher.folder.FolderIcon2x2";
    private static final String FOUR_FOLDER =
            "com.miui.home.launcher.folder.FolderIcon2x2_4";
    private static final String FOUR_PREVIEW =
            "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_4";
    private static final int FOUR = 4;

    private static boolean installed;
    private static boolean enabled;
    private static boolean failureLogged;

    private LauncherLargeFolderFourIconHook() {}

    static boolean install(ClassLoader classLoader, boolean featureEnabled) {
        if (!featureEnabled || classLoader == null) return false;
        if (installed) return true;
        try {
            Class<?> folderType = Class.forName(LARGE_FOLDER, false, classLoader);
            Class<?> fourFolderType = Class.forName(FOUR_FOLDER, false, classLoader);
            Class<?> previewType = Class.forName(FOUR_PREVIEW, false, classLoader);

            // Resolve every member before installing any hook. An unknown Launcher
            // variant must retain the original native layout.
            Method inflate = HookUtil.findMethodExact(
                    folderType, "onFinishInflate", new Class<?>[0]);
            Method createOrRemove = HookUtil.findMethodExact(
                    folderType, "createOrRemoveView", new Class<?>[0]);
            Method bindClicks = HookUtil.findMethodExact(
                    folderType, "addItemOnclickListener", new Class<?>[0]);
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
