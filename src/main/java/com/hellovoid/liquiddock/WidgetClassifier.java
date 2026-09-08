package com.hellovoid.liquiddock;

/** Single compatibility boundary for Launcher ItemInfo Widget classification. */
final class WidgetClassifier {
    private WidgetClassifier() {}

    static boolean isWidgetFallbackType(int itemType) {
        return itemType == 4 || itemType == 5 || itemType == 19;
    }

    static boolean isWidget(Object itemInfo) {
        if (itemInfo == null) return false;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(itemInfo, "isWidget");
        if (result.succeeded() && Boolean.TRUE.equals(result.value())) return true;
        return isWidgetFallbackType(HookUtil.getIntField(itemInfo, "itemType"));
    }
}
