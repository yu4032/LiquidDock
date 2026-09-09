package com.hellovoid.liquiddock;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns Workstation mode fallback generations and the normal-layout snapshot.
 *
 * <p>The Android/vendor callback wiring stays outside this pure state holder. A delayed fallback
 * may update mode only while its generation is still current and no vendor callback has confirmed
 * a newer state. The published mode remains volatile because renderer/hook readers are not all
 * guaranteed to share the callback thread.
 */
final class WorkstationModeController {
    static final class HomeItemPosition {
        final long screenId;
        final int cellX;
        final int cellY;
        final int spanX;
        final int spanY;

        HomeItemPosition(long screenId, int cellX, int cellY, int spanX, int spanY) {
            this.screenId = screenId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.spanX = spanX;
            this.spanY = spanY;
        }
    }

    private long generation;
    private long pendingFallbackGeneration = -1L;
    private volatile boolean workstationMode;
    private boolean vendorConfirmed;
    private final Map<Long, HomeItemPosition> normalLayoutBackup = new HashMap<>();

    boolean isWorkstationMode() {
        return workstationMode;
    }

    long generation() {
        return generation;
    }

    long beginUnconfirmedProbe() {
        vendorConfirmed = false;
        pendingFallbackGeneration = ++generation;
        return pendingFallbackGeneration;
    }

    boolean acceptFallbackProbe(long expectedGeneration, boolean probedMode) {
        if (vendorConfirmed || pendingFallbackGeneration != expectedGeneration) return false;
        workstationMode = probedMode;
        pendingFallbackGeneration = -1L;
        generation++;
        return true;
    }

    void onVendorModeChanged(boolean mode) {
        workstationMode = mode;
        vendorConfirmed = true;
        pendingFallbackGeneration = -1L;
        generation++;
    }

    void clearNormalLayoutBackup() {
        normalLayoutBackup.clear();
    }

    void rememberNormalItem(
            long itemId, long screenId, int cellX, int cellY, int spanX, int spanY) {
        normalLayoutBackup.put(
                itemId, new HomeItemPosition(screenId, cellX, cellY, spanX, spanY));
    }

    HomeItemPosition normalItem(long itemId) {
        return normalLayoutBackup.get(itemId);
    }

    boolean hasNormalLayoutBackup() {
        return !normalLayoutBackup.isEmpty();
    }

    int normalLayoutBackupSize() {
        return normalLayoutBackup.size();
    }
}
