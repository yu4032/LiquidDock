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
        final Object info;
        final LinearLayout option;
        boolean selected;

        PickerState(Object info, LinearLayout option, boolean selected) {
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
        MainHook.log(TAG + " folderId=" + id + " selected=" + marked);
    }

    static boolean install(ClassLoader loader) {
        if (loader == null) return false;
        if (installed) return true;
        try {
            Class<?> sheet = Class.forName(
                    "com.miui.home.launcher.folder.FolderSheet", false, loader);
            Class<?> info = Class.forName(
                    "com.miui.home.launcher.FolderInfo", false, loader);

            // These public vendor entry points are both required. Fail closed
            // rather than inject an option that never receives confirmation.
            HookUtil.hook(HookUtil.findMethodExact(
                    sheet, "initListener", new Class<?>[]{info}), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                attachOption(chain.getThisObject(), chain.getArg(0));
                return result;
            });
            HookUtil.hook(HookUtil.findMethodExact(
                    sheet, "onClick", new Class<?>[]{View.class}), chain -> {
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
            // Asynchronous inflation and vendor UI variants may populate
            // the picker body after initListener(). The attached view itself
            // is the final authority that this sheet is actually displayed.
            try {
                HookUtil.hook(HookUtil.findMethodExact(
                        sheet, "onAttachedToWindow", new Class<?>[0]), chain -> {
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
            } catch (Throwable error) {
                MainHook.log(TAG + " attach lifecycle unavailable: " + error);
            }
            installed = true;
            MainHook.log(TAG + " native FolderSheet fourth-choice hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " native FolderSheet unsupported: " + error);
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
        if (STATES.containsKey(owner)) return;
        Object scroller = getFieldOrNull(owner, "mFolderPickerBody");
        ScrollView scroll = scroller instanceof ScrollView
                ? (ScrollView) scroller : findScrollView(sheet);
        if (scroll == null) {
            MainHook.log(TAG + " folder picker scroll container not found for "
                    + sheet.getClass().getName());
            return;
        }
        if (scroll.getChildCount() != 1) {
            MainHook.log(TAG + " unexpected picker scroll child count=" + scroll.getChildCount());
            return;
        }
        View original = scroll.getChildAt(0);
        if (original == null) return;

        LinearLayout root = new LinearLayout(sheet.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout option = buildOption(sheet.getContext());
        PickerState state = new PickerState(
                info, option, isMarked(sheet.getContext(), info));
        updateAppearance(state);
        option.setOnClickListener(v -> {
            state.selected = true;
            updateAppearance(state);
            HookUtil.setIntField(owner, "mFolderType", NATIVE_FOUR_TYPE);
            // This is an independent picker state, not one of MIUI's
            // three mutually-exclusive native checked views.
            Object defaultCheck = getFieldOrNull(owner, "mDefaultFolderCheckBox");
            Object fourCheck = getFieldOrNull(owner, "mBigFolderCheckBox2x2_4");
            Object nineCheck = getFieldOrNull(owner, "mBigFolderCheckBox2x2_9");
            for (Object check : new Object[]{defaultCheck, fourCheck, nineCheck}) {
                if (check instanceof android.widget.Checkable) {
                    ((android.widget.Checkable) check).setChecked(false);
                }
            }
        });
        ViewGroup.LayoutParams oldParams = original.getLayoutParams();
        try {
            scroll.removeView(original);
            root.addView(original, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(option, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            scroll.addView(root, oldParams == null
                    ? new ScrollView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT)
                    : oldParams);
            STATES.put(owner, state);
            MainHook.log(TAG + " added independent four-large-icon folder choice");
        } catch (Throwable error) {
            MainHook.log(TAG + " picker row attachment failed: " + error);
            // Avoid leaving the stock picker detached on a layout variant.
            if (original.getParent() instanceof ViewGroup) {
                ((ViewGroup) original.getParent()).removeView(original);
            }
            if (scroll.getChildCount() == 0) {
                scroll.addView(original);
            }
        }
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
        LinearLayout row = new LinearLayout(context);
        row.setPadding(dp(context, 18), dp(context, 14), dp(context, 18), dp(context, 14));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout squares = new LinearLayout(context);
        squares.setOrientation(LinearLayout.VERTICAL);
        for (int y = 0; y < 2; y++) {
            LinearLayout line = new LinearLayout(context);
            for (int x = 0; x < 2; x++) {
                View square = new View(context);
                GradientDrawable tile = new GradientDrawable();
                tile.setColor(0x998DA0C2);
                tile.setCornerRadius(dp(context, 6));
                square.setBackground(tile);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        dp(context, 22), dp(context, 22));
                lp.setMargins(dp(context, 2), dp(context, 2),
                        dp(context, 2), dp(context, 2));
                line.addView(square, lp);
            }
            squares.addView(line);
        }
        row.addView(squares, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout labels = new LinearLayout(context);
        labels.setPadding(dp(context, 14), 0, 0, 0);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText("2×2 四宫格大文件夹");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        TextView detail = new TextView(context);
        detail.setText("四个等大的快捷图标 · 独立的 2×2 占位");
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        title.setTextColor(themeColor(context, android.R.attr.textColorPrimary, 0xffeeeeee));
        detail.setTextColor(themeColor(context, android.R.attr.textColorSecondary, 0xff999999));
        labels.addView(title);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setMinimumHeight(dp(context, 84));
        row.setClickable(true);
        row.setFocusable(true);
        return row;
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
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(state.option.getContext(), 18));
        background.setColor(state.selected ? 0x222F72DB : 0x11000000);
        background.setStroke(dp(state.option.getContext(), state.selected ? 2 : 1),
                state.selected ? 0xff6696ed : 0x33777777);
        state.option.setBackground(background);
        state.option.setSelected(state.selected);
    }

    private static void requestNativeResize(Object info) {
        if (info == null) return;
        int x = getSpan(info, "spanX"), y = getSpan(info, "spanY");
        if (x == 2 && y == 2) return;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(
                info, "convertIconSize", NATIVE_FOUR_TYPE);
        MainHook.log(TAG + " explicit native resize status=" + result.succeeded()
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
