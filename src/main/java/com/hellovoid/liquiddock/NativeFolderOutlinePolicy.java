package com.hellovoid.liquiddock;

/** Effective OEM stroke color, preserving its original RGB and alpha. */
final class NativeFolderOutlinePolicy {
    private NativeFolderOutlinePolicy() {}

    static int strokeColor(int originalColor, boolean showNativeOutline) {
        return showNativeOutline ? originalColor : originalColor & 0x00ffffff;
    }
}
