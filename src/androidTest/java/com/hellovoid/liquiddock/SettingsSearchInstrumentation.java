package com.hellovoid.liquiddock;

import android.app.Instrumentation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.Predicate;

/** Black-box accessibility regression: Unicode text enters without an ADB IME. */
public final class SettingsSearchInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            // Xiaomi blocks background starts from an instrumented application UID;
            // launch through the automation shell, as connected test runners do.
            try (java.io.InputStream launch = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    getUiAutomation().executeShellCommand("am start -n com.hellovoid.liquiddock/.ComposeSettingsActivity -f 0x10008000"))) {
                launch.readAllBytes();
            }
            android.accessibilityservice.AccessibilityServiceInfo service = getUiAutomation().getServiceInfo();
            service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(service);
            getUiAutomation().waitForIdle(500, 5000);
            openSearch();
            setQuery("阴影红");
            await(n -> "阴影红".equals(String.valueOf(n.getText())));
            // Target by config key so this also proves Chinese and ASCII inputs
            // share the same model, with an unambiguous global destination.
            setQuery("liquid_outer_shadow_r");
            click(await(n -> "阴影红".equals(String.valueOf(n.getText()))));
            String global = verifyLocated("GlassEdge", "liquid_outer_shadow_r");
            report(global);
            click(await(n -> "返回".equals(String.valueOf(n.getContentDescription()))));
            SystemClock.sleep(700);
            openSearch();
            setQuery("liquid_scope_dock_outer_shadow_r");
            click(await(n -> "阴影红".equals(String.valueOf(n.getText()))));
            String scoped = verifyLocated("DockEdge", "liquid_scope_dock_outer_shadow_r");
            result.putString("stream", "PASS Unicode search, offscreen global/scoped row navigation and visible target rows\n"
                    + global + "\n" + scoped + "\n");
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL " + android.util.Log.getStackTraceString(failure) + "\n");
            finish(0, result);
        }
    }

    private void report(String text) {
        Bundle status = new Bundle();
        status.putString("stream", text + "\n");
        sendStatus(0, status);
    }

    private void openSearch() {
        Rect bounds = new Rect();
        appRoot().getBoundsInScreen(bounds);
        int x = bounds.centerX(), y = bounds.top + bounds.height() / 3;
        long start = SystemClock.uptimeMillis();
        motion(start, start, MotionEvent.ACTION_DOWN, x, y);
        for (int i = 1; i <= 16; i++) {
            SystemClock.sleep(16);
            motion(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE,
                    x, y + i * bounds.height() / 40f);
        }
        motion(start, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                x, y + 16 * bounds.height() / 40f);
        await(n -> n.isEditable() && "com.hellovoid.liquiddock".equals(String.valueOf(n.getPackageName())));
        SystemClock.sleep(1000);
    }

    private void motion(long down, long time, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, time, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        getUiAutomation().injectInputEvent(event, true);
        event.recycle();
    }

    private void setQuery(String query) {
        // Wait out the search transition and the IME's initial editing session.
        // A stale accessibility node may acknowledge an action before a newly
        // focused editor publishes its final value, so reacquire it on retries.
        String actual = "";
        for (int attempt = 0; attempt < 4; attempt++) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
            if (!await(n -> n.isEditable() && "com.hellovoid.liquiddock".equals(String.valueOf(n.getPackageName()))).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
                throw new AssertionError("Search field rejected ACTION_SET_TEXT");
            SystemClock.sleep(600);
            getUiAutomation().clearCache();
            AccessibilityNodeInfo editor = find(appRoot(), n -> n.isEditable() && "com.hellovoid.liquiddock".equals(String.valueOf(n.getPackageName())));
            actual = editor == null ? "<missing>" : String.valueOf(editor.getText());
            if (query.equals(actual)) return;
        }
        throw new AssertionError("Search text mismatch: expected=" + query + " actual=" + actual);
    }

    private void click(AccessibilityNodeInfo node) {
        while (node != null && !node.isClickable()) node = node.getParent();
        if (node == null || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            throw new AssertionError("Result was not clickable");
    }

    private String verifyLocated(String page, String key) {
        String id = "setting:" + page + ":" + key;
        await(n -> id.equals(n.getViewIdResourceName()));
        SystemClock.sleep(1000); // Finish page transition and the measured scroll.
        Rect row = new Rect(), viewport = new Rect();
        await(n -> id.equals(n.getViewIdResourceName())).getBoundsInScreen(row);
        await(n -> ("settings-list:" + page).equals(n.getViewIdResourceName())).getBoundsInScreen(viewport);
        int error = Math.abs(row.centerY() - viewport.centerY());
        if (row.isEmpty() || !viewport.contains(row))
            throw new AssertionError("Target row not visible: " + row + " viewport=" + viewport);
        return page + " row=" + row + " viewport=" + viewport + " center error=" + error + "px";
    }

    private AccessibilityNodeInfo appRoot() {
        for (android.view.accessibility.AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null && "com.hellovoid.liquiddock".equals(String.valueOf(root.getPackageName()))) return root;
        }
        return getUiAutomation().getRootInActiveWindow();
    }

    private AccessibilityNodeInfo await(Predicate<AccessibilityNodeInfo> predicate) {
        long deadline = SystemClock.uptimeMillis() + 8000;
        do {
            AccessibilityNodeInfo found = find(appRoot(), predicate);
            if (found != null) return found;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Accessibility target missing");
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> predicate) {
        if (node == null) return null;
        if (predicate.test(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = find(node.getChild(i), predicate);
            if (found != null) return found;
        }
        return null;
    }
}
