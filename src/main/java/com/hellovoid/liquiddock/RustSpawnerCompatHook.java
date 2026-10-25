package com.hellovoid.liquiddock;

import java.io.Writer;
import java.lang.reflect.Method;

/**
 * Bridges the OS3 hyperos rust-runtime spawner protocol to the OS4 one.
 *
 * <p>OS3's {@code android.os.RustProcessImpl} sends a single {@code --binary-path=<path>}
 * argument to {@code hyos_spawner}. OS4's spawner no longer parses that field: it expects
 * {@code --app-entry-soname}, {@code --app-lib-search-paths} and
 * {@code --library-permitted-paths} (see OS4 {@code RustProcessManagerImpl}). The three values
 * are derivable from the OS3 payload, so the command line is rewritten in place inside
 * system_server before it reaches the spawner socket.
 *
 * <p>Only strings that start with {@code --start-child} and carry {@code --binary-path=} are
 * touched, so non-rust process starts are unaffected.
 */
final class RustSpawnerCompatHook {
    private static final String TAG = "[DC][RustSpawnerCompat]";
    private static final String BINARY_MARK = "--binary-path=";
    private static final String DATA_DIR_MARK = "--app-data-dir=";

    private static volatile boolean installed;

    private RustSpawnerCompatHook() {}

    static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            Method write = Writer.class.getDeclaredMethod("write", String.class);
            HookUtil.hook(write, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length == 1 && args[0] instanceof String) {
                    String original = (String) args[0];
                    if (original.startsWith("--start-child") && original.contains(BINARY_MARK)) {
                        String rewritten = rewrite(original);
                        if (rewritten != null) {
                            Api101Bridge.log(TAG + " spawner args rewritten: " + rewritten);
                            args[0] = rewritten;
                            return chain.proceed(args);
                        }
                    }
                }
                return chain.proceed();
            });
            Api101Bridge.log(TAG + " installed");
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " install failed", error);
        }
    }

    /** Returns the OS4 argument form, or null when the payload cannot be converted. */
    static String rewrite(String args) {
        int mark = args.indexOf(BINARY_MARK);
        if (mark < 0) return null;
        int valueStart = mark + BINARY_MARK.length();
        int valueEnd = args.indexOf(' ', valueStart);
        if (valueEnd < 0) valueEnd = args.length();
        String binary = args.substring(valueStart, valueEnd);

        int slash = binary.lastIndexOf('/');
        if (slash <= 0 || slash == binary.length() - 1) return null;
        String soname = binary.substring(slash + 1);
        String searchPath = binary.substring(0, slash);

        String dataDir = "";
        int dataMark = args.indexOf(DATA_DIR_MARK);
        if (dataMark >= 0) {
            int dataStart = dataMark + DATA_DIR_MARK.length();
            int dataEnd = args.indexOf(' ', dataStart);
            if (dataEnd < 0) dataEnd = args.length();
            dataDir = args.substring(dataStart, dataEnd);
        }
        StringBuilder permitted = new StringBuilder();
        if (!dataDir.isEmpty()) permitted.append(dataDir).append(':');
        permitted.append("/data:/mnt/expand");

        StringBuilder out = new StringBuilder(args.length() + 96);
        out.append(args, 0, mark);
        out.append("--app-entry-soname=").append(soname);
        out.append(" --app-lib-search-paths=").append(searchPath);
        out.append(" --library-permitted-paths=").append(permitted);
        out.append(args, valueEnd, args.length());
        return out.toString();
    }
}
