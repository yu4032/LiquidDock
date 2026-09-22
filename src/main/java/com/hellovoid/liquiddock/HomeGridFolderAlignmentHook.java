package com.hellovoid.liquiddock;

/** Owns only FolderIcon1x1 vertical alignment against the real CellLayout cell size. */
final class HomeGridFolderAlignmentHook {
    private HomeGridFolderAlignmentHook() {}

    static void install(ClassLoader classLoader) {
        final Class<?> folder;
        try {
            folder = Class.forName(
                    "com.miui.home.launcher.folder.FolderIcon1x1", false, classLoader);
        } catch (ClassNotFoundException error) {
            throw new RuntimeException(error);
        }
        HookUtil.hookMethod(folder, "onMeasure", new Class<?>[]{int.class, int.class}, chain -> {
            android.view.View view = (android.view.View) chain.getThisObject();
            Object parent = view.getParent();
            Object gridConfig = null;
            Integer originalCellSize = null;
            if (parent != null && parent.getClass().getName().endsWith("CellLayout")) {
                try {
                    int cell = HookUtil.getIntField(parent, "mCellHeight");
                    gridConfig = HookUtil.getField(parent, "mGridConfig");
                    if (cell > 0 && gridConfig != null) {
                        originalCellSize = HookUtil.getIntField(gridConfig, "cellSize");
                        HookUtil.setIntField(gridConfig, "cellSize", cell);
                    }
                } catch (Throwable error) {
                    MainHook.log("[DC] small folder alignment failed: " + error);
                }
            }

            Object result;
            try {
                result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            } catch (Throwable error) {
                restore(gridConfig, originalCellSize);
                throw error;
            }
            restore(gridConfig, originalCellSize);
            return result;
        });
    }

    private static void restore(Object gridConfig, Integer originalCellSize) {
        if (gridConfig == null || originalCellSize == null) return;
        try {
            HookUtil.setIntField(gridConfig, "cellSize", originalCellSize);
        } catch (Throwable error) {
            MainHook.log("[DC] small folder grid restore failed: " + error);
        }
    }
}
