package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds a fourth, explicitly selected style to the native Launcher FolderSheet.
 * FolderInfo.itemType=21 is already the vendor four-preview type, so its
 * custom four-large-icon variant needs a per-folder marker rather than a
 * made-up itemType that the vendor model cannot persist or render.
 */
final class LauncherLargeFolderPickerHook {
    private static final String TAG = "[DC][FolderFourPicker]";
    private static final String PREFS = "liquiddock_folder_styles";
    private static final String PREFIX = "four_large_";
    private static final int NATIVE_FOUR_TYPE = 21;
    private static final Map<Object, PickerState> STATES =
            Collections.synchronizedMap(new WeakHashMap<Object, PickerState>());
    private static boolean installed;

    private static final class PickerState {
        Object info;
        final View option;
        boolean selected;

        PickerState(Object info, View option, boolean selected) {
            this.info = info;
            this.option = option;
            this.selected = selected;
        }
    }

    private LauncherLargeFolderPickerHook() {}

    static boolean isMarked(Context context, Object info) {
        if (context == null || info == null) return false;
        long id = stableId(info);
        return id >= 0 && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(PREFIX + id, false);
    }

    private static long stableId(Object info) {
        try {
            return HookUtil.getLongField(info, "id");
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static void setMarked(Context context, Object info, boolean marked) {
        if (context == null || info == null) return;
        long id = stableId(info);
        if (id < 0) return;
        SharedPreferences.Editor edit =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (marked) edit.putBoolean(PREFIX + id, true);
        else edit.remove(PREFIX + id);
        edit.apply();
        Api101Bridge.log(TAG + "picker-confirm folder=" + id
                + " selected=" + marked
                + " type=" + getItemType(info)
                + " span=" + getSpan(info, "spanX") + "x" + getSpan(info, "spanY"));
    }

    static boolean install(ClassLoader loader) {
        if (loader == null) return false;
        if (installed) return true;
        try {
            Class<?> sheet = Class.forName(
                    "com.miui.home.launcher.folder.FolderSheet", false, loader);
            Class<?> info = Class.forName(
                    "com.miui.home.launcher.FolderInfo", false, loader);

            // Independent lifecycle hooks: Android variants can inline
            // initListener(), while attached FolderSheet remains the actual
            // visible UI. Either entry point is sufficient to add the row.
            boolean initialized = installOptional(
                    sheet, "initListener", new Class<?>[]{info}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        attachOption(chain.getThisObject(), chain.getArg(0));
                        return result;
                    });
            boolean attached = installOptional(
                    sheet, "onAttachedToWindow", new Class<?>[0], chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (owner instanceof View) {
                            View shown = (View) owner;
                            Object infoValue = getFieldOrNull(owner, "mFolderInfo");
                            if (infoValue != null) attachOption(owner, infoValue);
                            shown.post(() -> {
                                Object latest = getFieldOrNull(owner, "mFolderInfo");
                                if (latest != null) attachOption(owner, latest);
                            });
                        }
                        return result;
                    });
            if (!initialized && !attached) {
                Api101Bridge.log(TAG + " no FolderSheet lifecycle hook available");
                return false;
            }
            HookUtil.hook(sheet.getDeclaredMethod("onClick", View.class), chain -> {
                Object owner = chain.getThisObject();
                PickerState state = STATES.get(owner);
                if (state == null || !(owner instanceof View)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                View clicked = (View) chain.getArg(0);
                Object ok = getFieldOrNull(owner, "mFolderPickerOk");
                Object cancel = getFieldOrNull(owner, "mFolderPickerCancel");
                if (clicked == ok) {
                    boolean selected = state.selected;
                    if (selected) {
                        HookUtil.setIntField(owner, "mFolderType", NATIVE_FOUR_TYPE);
                    }
                    // Marker must be visible before the native asynchronous
                    // bindFolderResize constructs the replacement FolderIcon.
                    setMarked(((View) owner).getContext(), state.info, selected);
                    int originalType = getItemType(state.info);
                    int originalX = getSpan(state.info, "spanX");
                    int originalY = getSpan(state.info, "spanY");
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (selected && LargeFolderPickerPolicy.mustRequestResize(
                            originalType, originalX, originalY)) {
                        // FolderSheet.handleClose() may detach the view in the
                        // original onClick; View.post() on a detached sheet
                        // can remain queued forever. Use the main Looper.
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> requestNativeResize(state.info));
                    }
                    return result;
                }
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                // Stock style selections always deselect the fourth option.
                if (clicked != cancel && isStockStyleControl(owner, clicked)) {
                    state.selected = false;
                    updateAppearance(state);
                }
                return result;
            });
            installed = true;
            Api101Bridge.log(TAG + "picker-hook-installed");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native FolderSheet unsupported: " + error);
            return false;
        }
    }

    private static boolean installOptional(
            Class<?> owner, String name, Class<?>[] signature,
            io.github.libxposed.api.XposedInterface.Hooker callback) {
        try {
            // Do not walk up to android.view.View: intercepting a
            // framework-wide onAttachedToWindow would be catastrophic.
            HookUtil.hook(owner.getDeclaredMethod(name, signature), callback);
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " optional " + name + " unavailable: " + error);
            return false;
        }
    }

    private static boolean isStockStyleControl(Object owner, View clicked) {
        if (clicked == null) return false;
        String[] nativeStyleControls = {
            "mDefaultFolderCheckBox", "mBigFolderCheckBox2x2_4",
            "mBigFolderCheckBox2x2_9", "mDefaultFolderName",
            "mBigFolderName2x2_4", "mBigFolderName2x2_9",
            "mDefaultFolderSelectBorder", "mBigFolderSelectBorder2x2_4",
            "mBigFolderSelectBorder2x2_9"
        };
        for (String field : nativeStyleControls) {
            if (clicked == getFieldOrNull(owner, field)) return true;
        }
        return false;
    }

    private static void attachOption(Object owner, Object info) {
        if (!(owner instanceof View) || info == null) return;
        View sheet = (View) owner;
        PickerState existing = STATES.get(owner);
        if (existing != null && existing.option.getParent() != null) {
            existing.info = info;
            existing.selected = isMarked(sheet.getContext(), info);
            updateAppearance(existing);
            return;
        }
        if (existing != null) STATES.remove(owner);
        Object scroller = getFieldOrNull(owner, "mFolderPickerBody");
        ScrollView scroll = scroller instanceof ScrollView
                ? (ScrollView) scroller : findScrollView(sheet);
        if (scroll == null) {
            Api101Bridge.log(TAG + " folder picker scroll container not found for "
                    + sheet.getClass().getName());
            return;
        }
        // The fourth size must be a CHILD of MIUI's VisualCheckGroup, not
        // an extra card beneath the group. The first three are native
        // VisualCheckBoxes in folder_sheet.xml.
        View root = scroll.getChildCount() == 1 ? scroll.getChildAt(0) : null;
        View nativeGroup = root == null ? null : findNativeCheckGroup(root);
        if (!(nativeGroup instanceof ViewGroup)) {
            Api101Bridge.log(TAG + " VisualCheckGroup unavailable");
            return;
        }
        ViewGroup group = (ViewGroup) nativeGroup;
        View newOption = makeNativeSizeOption(sheet.getContext(), group);
        if (newOption == null) {
            Api101Bridge.log(TAG + " fourth native size option unavailable");
            return;
        }
        newOption.setId(View.generateViewId());
        PickerState state = new PickerState(
                info, newOption, isMarked(sheet.getContext(), info));
        updateAppearance(state);
        newOption.setOnClickListener(v -> {
            state.selected = true;
            HookUtil.setIntField(owner, "mFolderType", NATIVE_FOUR_TYPE);
            for (String field : new String[]{
                    "mDefaultFolderCheckBox", "mBigFolderCheckBox2x2_4",
                    "mBigFolderCheckBox2x2_9"}) {
                Object stock = getFieldOrNull(owner, field);
                if (stock instanceof android.widget.Checkable) {
                    ((android.widget.Checkable) stock).setChecked(false);
                }
            }
            updateAppearance(state);
        });
        try {
            // Match vendor VisualCheckBox layout rules and let native group
            // manage its fourth child exactly like the first three options.
            View reference = group.getChildCount() > 0 ? group.getChildAt(0) : null;
            ViewGroup.LayoutParams lp = reference == null
                    ? new ViewGroup.LayoutParams(dp(sheet.getContext(), 76),
                            ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new ViewGroup.LayoutParams(reference.getLayoutParams());
            group.addView(newOption, lp);
            group.post(() -> fitFourChoices(group));
            STATES.put(owner, state);
            Api101Bridge.log(TAG + " fourth native folder size option added inside VisualCheckGroup");
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native picker choice failed: " + error);
            if (newOption.getParent() == group) group.removeView(newOption);
        }
    }

    private static View makeNativeSizeOption(Context context, ViewGroup group) {
        if (group.getChildCount() == 0) return null;
        View sample = group.getChildAt(0);
        try {
            java.lang.reflect.Constructor<?> constructor =
                    sample.getClass().getConstructor(Context.class, android.util.AttributeSet.class);
            Object o = constructor.newInstance(context, null);
            if (!(o instanceof ViewGroup)) return null;
            ViewGroup nativeCheck = (ViewGroup) o;
            nativeCheck.setFocusable(true);
            nativeCheck.setClickable(true);
            nativeCheck.setContentDescription("2×2 四宫格大文件夹");
            // Build only the chooser thumbnail INSIDE the native checkbox.
            // Actual desktop content is NEVER custom drawn.
            LinearLayout preview = buildOption(context);
            nativeCheck.addView(preview, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            preview.setClickable(false);
            return nativeCheck;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native VisualCheckBox constructor unavailable: " + error);
            return null;
        }
    }

    private static void fitFourChoices(ViewGroup group) {
        if (group.getChildCount() != 4) return;
        ViewGroup parent = group.getParent() instanceof ViewGroup
                ? (ViewGroup) group.getParent() : null;
        int width = parent == null ? 0 : parent.getWidth()
                - parent.getPaddingLeft() - parent.getPaddingRight();
        if (width <= 0) return;
        int childWidth = Math.max(1, width / 4);
        for (int i = 0; i < 4; i++) {
            View v = group.getChildAt(i);
            ViewGroup.LayoutParams p = v.getLayoutParams();
            if (p != null && p.width > childWidth) {
                p.width = childWidth;
                v.setLayoutParams(p);
            }
        }
    }

    private static View findNativeCheckGroup(View root) {
        if (root.getClass().getName().endsWith("VisualCheckGroup")) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = findNativeCheckGroup(group.getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private static ScrollView findScrollView(View root) {
        if (root instanceof ScrollView) return (ScrollView) root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView candidate = findScrollView(group.getChildAt(i));
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static LinearLayout buildOption(Context context) {
        LinearLayout option = new LinearLayout(context);
        option.setOrientation(LinearLayout.VERTICAL);
        option.setGravity(Gravity.CENTER);
        option.setPadding(dp(context, 3), dp(context, 6),
                dp(context, 3), dp(context, 6));

        // Selector sample only: three normal icons, with a 2x2 miniature
        // icon grid in quadrant four just like MIUI FolderIcon2x2_4.
        LinearLayout sample = new LinearLayout(context);
        sample.setOrientation(LinearLayout.VERTICAL);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setGravity(Gravity.CENTER);
            for (int col = 0; col < 2; col++) {
                View icon;
                if (row == 1 && col == 1) {
                    LinearLayout mini = new LinearLayout(context);
                    mini.setOrientation(LinearLayout.VERTICAL);
                    for (int y = 0; y < 2; y++) {
                        LinearLayout strip = new LinearLayout(context);
                        for (int x = 0; x < 2; x++) {
                            View dot = new View(context);
                            dot.setBackground(previewDrawable(context, 0x998DA0C2, 2));
                            LinearLayout.LayoutParams dotLp =
                                    new LinearLayout.LayoutParams(dp(context, 9), dp(context, 9));
                            dotLp.setMargins(1, 1, 1, 1);
                            strip.addView(dot, dotLp);
                        }
                        mini.addView(strip);
                    }
                    icon = mini;
                } else {
                    icon = new View(context);
                    icon.setBackground(previewDrawable(context, 0x998DA0C2, 5));
                }
                LinearLayout.LayoutParams cell =
                        new LinearLayout.LayoutParams(dp(context, 22), dp(context, 22));
                cell.setMargins(dp(context, 2), dp(context, 2),
                        dp(context, 2), dp(context, 2));
                line.addView(icon, cell);
            }
            sample.addView(line);
        }
        option.addView(sample);
        TextView title = new TextView(context);
        title.setText("2×2 四宫格");
        title.setGravity(Gravity.CENTER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        title.setMaxLines(2);
        title.setTextColor(themeColor(context, android.R.attr.textColorPrimary, 0xffeeeeee));
        option.addView(title);
        return option;
    }

    private static GradientDrawable previewDrawable(Context c, int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(c, radiusDp));
        return bg;
    }

    private static int themeColor(Context context, int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) return value.data;
            if (value.resourceId != 0) {
                try { return context.getColor(value.resourceId); }
                catch (Throwable ignored) {}
            }
        }
        return fallback;
    }

    private static void updateAppearance(PickerState state) {
        state.option.setSelected(state.selected);
        if (state.option instanceof android.widget.Checkable) {
            ((android.widget.Checkable) state.option).setChecked(state.selected);
        }
    }

    private static void requestNativeResize(Object info) {
        if (info == null) return;
        int x = getSpan(info, "spanX"), y = getSpan(info, "spanY");
        if (x == 2 && y == 2) return;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(
                info, "convertIconSize", NATIVE_FOUR_TYPE);
        Api101Bridge.log(TAG + " explicit native resize status=" + result.succeeded()
                + " before=" + x + "x" + y
                + (result.succeeded() ? "" : " failure=" + result.failure()));
    }

    private static int getItemType(Object info) {
        return getSpan(info, "itemType");
    }

    private static int getSpan(Object info, String field) {
        try { return HookUtil.getIntField(info, field); }
        catch (Throwable ignored) { return -1; }
    }

    private static Object getFieldOrNull(Object owner, String name) {
        try { return HookUtil.getField(owner, name); }
        catch (Throwable ignored) { return null; }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
