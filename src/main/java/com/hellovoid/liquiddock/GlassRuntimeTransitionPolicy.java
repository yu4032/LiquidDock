package com.hellovoid.liquiddock;

/** Android-free planner for effective Liquid Glass ownership transitions. */
final class GlassRuntimeTransitionPolicy {
    static final class Snapshot {
        final boolean enabled;
        final boolean icon;
        final boolean widget;
        final boolean widgetDarkContent;
        final boolean smallFolder;
        final boolean largeFolder;

        Snapshot(boolean enabled, boolean icon, boolean widget, boolean widgetDarkContent,
                 boolean smallFolder, boolean largeFolder) {
            this.enabled = enabled;
            this.icon = icon;
            this.widget = widget;
            this.widgetDarkContent = widgetDarkContent;
            this.smallFolder = smallFolder;
            this.largeFolder = largeFolder;
        }
    }

    static final class Transition {
        final boolean fullTeardown;
        final boolean iconRelease;
        final boolean widgetRelease;
        final boolean widgetDarkContentChanged;
        final boolean nextWidgetDarkContent;
        final boolean smallFolderRelease;
        final boolean largeFolderRelease;

        Transition(boolean fullTeardown, boolean iconRelease, boolean widgetRelease,
                   boolean widgetDarkContentChanged, boolean nextWidgetDarkContent,
                   boolean smallFolderRelease, boolean largeFolderRelease) {
            this.fullTeardown = fullTeardown;
            this.iconRelease = iconRelease;
            this.widgetRelease = widgetRelease;
            this.widgetDarkContentChanged = widgetDarkContentChanged;
            this.nextWidgetDarkContent = nextWidgetDarkContent;
            this.smallFolderRelease = smallFolderRelease;
            this.largeFolderRelease = largeFolderRelease;
        }
    }

    private GlassRuntimeTransitionPolicy() {}

    static Transition plan(Snapshot before, Snapshot after) {
        boolean fullTeardown = before.enabled && !after.enabled;
        if (fullTeardown) {
            return new Transition(true, false, false, false,
                    after.widgetDarkContent, false, false);
        }
        return new Transition(
                false,
                before.icon && !after.icon,
                before.widget && !after.widget,
                before.widgetDarkContent != after.widgetDarkContent,
                after.widgetDarkContent,
                before.smallFolder && !after.smallFolder,
                before.largeFolder && !after.largeFolder);
    }
}
