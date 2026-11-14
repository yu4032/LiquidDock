package com.hellovoid.liquiddock;

/**
 * Process-wide logging and shared workstation state facade.
 * Legacy Launcher Java hook installation has been removed in favor of OS4 native hooks.
 */
public class MainHook {
    static boolean isWorkstationMode() {
        return WorkstationRuntimeState.isActive();
    }

    // ── process logging ─────────────────────────────────────────────

    static boolean debugLogging;

    static void log(String message) {
        if (!debugLogging) return;
        Api101Bridge.log(message);
        fileLog(message);
    }

    private static void fileLog(String message) {
        try {
            String line = new java.text.SimpleDateFormat(
                    "HH:mm:ss.SSS", java.util.Locale.ROOT)
                    .format(new java.util.Date()) + " " + message + "\n";
            java.io.File dir = new java.io.File("/sdcard/Android/media/com.hellovoid.liquiddock");
            if (!dir.exists()) dir.mkdirs();
            if (!dir.canWrite()) dir = new java.io.File("/sdcard/Download");
            if (!dir.canWrite()) dir = new java.io.File("/data/local/tmp");
            java.io.FileOutputStream out = new java.io.FileOutputStream(
                    new java.io.File(dir, "liquiddock.log"), true);
            out.write(line.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {}
    }
}
