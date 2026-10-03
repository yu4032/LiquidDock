package com.hellovoid.liquiddock;

/** Pure key namespace for per-page, per-runtime-grid rotation positions. */
final class HomeGridRotationMemoryKey {
    private HomeGridRotationMemoryKey() {}

    static String layout(int columns, int rows, long screenId, long itemId) {
        return "layout:" + columns + "x" + rows
                + ":screen=" + screenId
                + itemSuffix(itemId);
    }

    static String owner(long itemId) {
        return "owner:item=" + itemId;
    }

    static String itemSuffix(long itemId) {
        return ":item=" + itemId;
    }

    static Long itemIdFromStoredKey(String key) {
        if (key == null) return null;
        int marker = key.lastIndexOf(":item=");
        if (marker < 0) return null;
        String value = key.substring(marker + 6);
        if (value.isEmpty()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
