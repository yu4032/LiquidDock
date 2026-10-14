package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.CompoundButton;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.AttributeSet;

import java.lang.reflect.Constructor;

/**
 * Adds a per-folder real-2x2 toggle ABOVE the vendor recommendation switches.
 * The three native folder size choices and both original switches remain intact.
 * Reuses FolderIcon2x2_4 (itemType 21), with a persisted per-folder marker.
 */
final class LauncherLargeFolderPickerHook {
    private static final String TAG = "[DC][FolderFourPicker]";
    private static final String PREFS = "liquiddock_folder_styles";
    private static final String PREFIX = "four_large_";
    private static final int NATIVE_FOUR_TYPE = 21;
    // The row lives in exactly one FolderSheet. Its tag owns the state.
    // No global map can retain the sheet through a value -> View -> parent path.
    private static final int ROW_ID = View.generateViewId();
    private static boolean installed;

    private static final class PickerState {
        Object info;
        boolean selected;
        boolean syncing;
        boolean selectingNativeStyle;

        PickerState(Object info, boolean selected) {
            this.info = info;
            this.selected = selected;
        }
    }

    private LauncherLargeFolderPickerHook() {}

    private static View optionRow(Object owner) {
        return owner instanceof View ? ((View) owner).findViewById(ROW_ID) : null;
    }

    private static PickerState stateFor(Object owner) {
        View row = optionRow(owner);
        return row != null && row.getTag() instanceof PickerState
                ? (PickerState) row.getTag() : null;
    }

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
                PickerState state = stateFor(owner);
                if (state == null || !(owner instanceof View)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                View clicked = (View) chain.getArg(0);
                Object ok = getFieldOrNull(owner, "mFolderPickerOk");
                Object cancel = getFieldOrNull(owner, "mFolderPickerCancel");
                if (clicked == ok) {
                    boolean selected = state.selected;
                    boolean wasCustom = isMarked(((View) owner).getContext(), state.info);
                    if (selected) {
                        HookUtil.setIntField(owner, "mFolderType", NATIVE_FOUR_TYPE);
                    }
                    int targetType = HookUtil.getIntField(owner, "mFolderType");
                    // Marker must be visible before the native asynchronous
                    // bindFolderResize constructs the replacement FolderIcon.
                    setMarked(((View) owner).getContext(), state.info, selected);
                    int originalType = getItemType(state.info);
                    int originalX = getSpan(state.info, "spanX");
                    int originalY = getSpan(state.info, "spanY");
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (selected && LargeFolderPickerPolicy.mustRequestResize(
                            originalType, originalX, originalY)) {
                        // The current editor can detach after handleClose().
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> requestNativeResize(state.info));
                    } else if (!selected && wasCustom
                            && targetType == NATIVE_FOUR_TYPE
                            && originalType == NATIVE_FOUR_TYPE
                            && originalX == 2 && originalY == 2) {
                        // The stock 2x2_4 and our expanded variant intentionally
                        // share itemType=21. MIUI's onClick only converts if
                        // itemType changes; force its native conversion when
                        // the selection changes back to stock size (1x1).
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .post(() -> HookUtil.tryInvoke(
                                        state.info, "convertIconSize", NATIVE_FOUR_TYPE));
                    }
                    return result;
                }
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                // Switching to an incompatible native folder style disables the 2x2 expansion.
                if (clicked != cancel && !state.selectingNativeStyle
                        && isStockStyleControl(owner, clicked)
                        && !isNativeFourStyleControl(owner, clicked)) {
                    state.selected = false;
                    updateAppearance(owner, state);
                }
                return result;
            });
            installed = true;
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

    private static boolean isNativeFourStyleControl(Object owner, View clicked) {
        return clicked == getFieldOrNull(owner, "mBigFolderCheckBox2x2_4")
                || clicked == getFieldOrNull(owner, "mBigFolderName2x2_4")
                || clicked == getFieldOrNull(owner, "mBigFolderSelectBorder2x2_4");
    }

    private static void attachOption(Object owner, Object info) {
        if (!(owner instanceof View) || info == null) return;
        View sheet = (View) owner;
        PickerState existing = stateFor(owner);
        if (existing != null) {
            existing.info = info;
            existing.selected = isMarked(sheet.getContext(), info);
            updateAppearance(owner, existing);
            return;
        }

        Object scroller = getFieldOrNull(owner, "mFolderPickerBody");
        ScrollView scroll = scroller instanceof ScrollView
                ? (ScrollView) scroller : findScrollView(sheet);
        if (scroll == null) {
            Api101Bridge.log(TAG + " folder picker scroll container unavailable");
            return;
        }
        View root = scroll.getChildCount() == 1 ? scroll.getChildAt(0) : null;
        View nativeGroup = root == null ? null : findNativeCheckGroup(root);
        if (!(root instanceof ViewGroup) || !(nativeGroup instanceof ViewGroup)
                || nativeGroup.getParent() != root || nativeGroup.getId() == View.NO_ID) {
            Api101Bridge.log(TAG + " native folder size section unavailable");
            return;
        }
        ViewGroup panel = (ViewGroup) root;
        View predictionField = getFieldOrNull(owner, "mFolderPickerAppPredictExposed") instanceof View
                ? (View) getFieldOrNull(owner, "mFolderPickerAppPredictExposed")
                : findAppPredict(panel);
        // Keep both vendor switches and their native listeners untouched.
        if (predictionField == null || predictionField.getParent() != panel) {
            Api101Bridge.log(TAG + " native app-prediction row unavailable");
            return;
        }
        ViewGroup.LayoutParams predictionLp = predictionField.getLayoutParams();
        if (predictionLp == null || !predictionLp.getClass().getName()
                .contains("ConstraintLayout$LayoutParams")) {
            Api101Bridge.log(TAG + " native app-prediction constraint unavailable");
            return;
        }

        // Derive the component from the actual OEM option row. No platform Switch fallback.
        CompoundButton exemplar = findNativeToggle(predictionField, 0);
        if (exemplar == null) {
            Api101Bridge.log(TAG + " no OEM switch inside app-prediction row");
            return;
        }
        CompoundButton toggle = makeNativeToggle(sheet.getContext(), exemplar);
        if (toggle == null) {
            Api101Bridge.log(TAG + " cannot reproduce OEM switch class");
            return;
        }
        LinearLayout row = new LinearLayout(sheet.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(predictionField.getMinimumHeight());
        row.setPadding(predictionField.getPaddingLeft(), predictionField.getPaddingTop(),
                predictionField.getPaddingRight(), predictionField.getPaddingBottom());
        row.setId(ROW_ID);
        TextView label = new TextView(sheet.getContext());
        label.setText("开启真实大文件夹");
        TextView nativeLabel = findNativeLabel(predictionField, 0);
        if (nativeLabel != null) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, nativeLabel.getTextSize());
            label.setTextColor(nativeLabel.getTextColors());
            label.setTypeface(nativeLabel.getTypeface());
            label.setGravity(nativeLabel.getGravity());
            label.setIncludeFontPadding(nativeLabel.getIncludeFontPadding());
            label.setLetterSpacing(nativeLabel.getLetterSpacing());
        } else {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            label.setTextColor(themeColor(sheet.getContext(),
                    android.R.attr.textColorPrimary, 0xffeeeeee));
        }
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ViewGroup.LayoutParams exemplarParams = exemplar.getLayoutParams();
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                exemplarParams == null ? ViewGroup.LayoutParams.WRAP_CONTENT : exemplarParams.width,
                exemplarParams == null ? ViewGroup.LayoutParams.WRAP_CONTENT : exemplarParams.height);
        if (exemplarParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) exemplarParams;
            toggleParams.setMargins(margins.leftMargin, margins.topMargin,
                    margins.rightMargin, margins.bottomMargin);
        }
        row.addView(toggle, toggleParams);
        PickerState state = new PickerState(info, isMarked(sheet.getContext(), info));
        row.setTag(state);
        updateAppearance(row, state);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (state.syncing) return;
            state.selected = checked;
            if (checked) selectNativeFourStyle(owner, state);
        });
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription("开启真实大文件夹");
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));

        try {
            Class<?> lpClass = Class.forName(
                    "androidx.constraintlayout.widget.ConstraintLayout$LayoutParams",
                    false, panel.getClass().getClassLoader());
            ViewGroup.MarginLayoutParams rowLp = (ViewGroup.MarginLayoutParams)
                    lpClass.getConstructor(int.class, int.class).newInstance(
                            predictionLp.width, predictionLp.height);
            // Copy horizontal anchors and margins from the vendor's own row.
            for (String anchor : new String[]{"startToStart", "endToEnd",
                    "leftToLeft", "rightToRight", "startToEnd", "endToStart",
                    "leftToRight", "rightToLeft"}) {
                try {
                    HookUtil.setIntField(rowLp, anchor, HookUtil.getIntField(predictionLp, anchor));
                } catch (Throwable ignored) {}
            }
            if (predictionLp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams old = (ViewGroup.MarginLayoutParams) predictionLp;
                rowLp.leftMargin = old.leftMargin;
                rowLp.rightMargin = old.rightMargin;
                rowLp.topMargin = old.topMargin;
                rowLp.bottomMargin = old.bottomMargin;
                // Section spacing belongs above the first option only.
                old.topMargin = successorTopMargin(panel, predictionField);
            }
            HookUtil.setIntField(rowLp, "topToBottom", nativeGroup.getId());
            panel.addView(row, rowLp);

            // Leave the two vendor toggles and their click listeners untouched.
            HookUtil.setIntField(predictionLp, "topToBottom", row.getId());
            predictionField.setLayoutParams(predictionLp);
            panel.requestLayout();
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native folder toggle injection failed: " + error);
            if (row.getParent() == panel) panel.removeView(row);
        }
    }

    private static void selectNativeFourStyle(Object owner, PickerState state) {
        // This toggle operates on FolderIcon2x2_4. When enabled from another
        // style, select that real stock style through the vendor click path.
        Object nativeFour = getFieldOrNull(owner, "mBigFolderCheckBox2x2_4");
        if (nativeFour instanceof View && HookUtil.getIntField(owner, "mFolderType")
                != NATIVE_FOUR_TYPE) {
            state.selectingNativeStyle = true;
            try {
                ((View) nativeFour).performClick();
            } finally {
                state.selectingNativeStyle = false;
            }
        }
        HookUtil.setIntField(owner, "mFolderType", NATIVE_FOUR_TYPE);
    }

    private static View findAppPredict(View root) {
        try {
            int id = root.getResources().getIdentifier(
                    "folder_picker_app_predict", "id", root.getContext().getPackageName());
            return id == 0 ? null : root.findViewById(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int successorTopMargin(ViewGroup panel, View anchor) {
        if (anchor.getId() == View.NO_ID) return 0;
        for (int i = 0; i < panel.getChildCount(); i++) {
            View view = panel.getChildAt(i);
            if (view == anchor) continue;
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (!(lp instanceof ViewGroup.MarginLayoutParams)) continue;
            try {
                if (HookUtil.getIntField(lp, "topToBottom") == anchor.getId())
                    return ((ViewGroup.MarginLayoutParams) lp).topMargin;
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static TextView findNativeLabel(View root, int depth) {
        if (root instanceof TextView && !(root instanceof CompoundButton)) return (TextView) root;
        if (!(root instanceof ViewGroup) || depth >= 9) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView result = findNativeLabel(group.getChildAt(i), depth + 1);
            if (result != null) return result;
        }
        return null;
    }

    private static CompoundButton makeNativeToggle(Context context, CompoundButton exemplar) {
        CompoundButton copy = instantiateToggle(exemplar.getClass(), context);
        if (copy == null || copy.getClass() != exemplar.getClass()) return null;
        copy.setLayoutDirection(exemplar.getLayoutDirection());
        copy.setPadding(exemplar.getPaddingLeft(), exemplar.getPaddingTop(),
                exemplar.getPaddingRight(), exemplar.getPaddingBottom());
        copy.setButtonTintList(exemplar.getButtonTintList());
        copy.setBackgroundTintList(exemplar.getBackgroundTintList());
        if (exemplar instanceof android.widget.Switch && copy instanceof android.widget.Switch) {
            android.widget.Switch original = (android.widget.Switch) exemplar;
            android.widget.Switch next = (android.widget.Switch) copy;
            next.setThumbTintList(original.getThumbTintList());
            next.setTrackTintList(original.getTrackTintList());
        }
        return copy;
    }

    private static CompoundButton instantiateToggle(Class<?> type, Context context) {
        try {
            Constructor<?> ctor = type.getConstructor(Context.class, AttributeSet.class);
            Object result = ctor.newInstance(context, null);
            if (result instanceof CompoundButton) return (CompoundButton) result;
        } catch (Throwable ignored) {}
        try {
            Constructor<?> ctor = type.getConstructor(Context.class);
            Object result = ctor.newInstance(context);
            if (result instanceof CompoundButton) return (CompoundButton) result;
        } catch (Throwable ignored) {}
        return null;
    }

    private static CompoundButton findNativeToggle(View root, int depth) {
        if (root instanceof CompoundButton
                && !root.getClass().getName().contains("VisualCheckBox"))
            return (CompoundButton) root;
        if (!(root instanceof ViewGroup) || depth >= 9) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            CompoundButton result = findNativeToggle(group.getChildAt(i), depth + 1);
            if (result != null) return result;
        }
        return null;
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

    private static void updateAppearance(Object scope, PickerState state) {
        View row = scope instanceof View && ((View) scope).getId() == ROW_ID
                ? (View) scope : optionRow(scope);
        if (!(row instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) row;
        CompoundButton toggle = null;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof CompoundButton) {
                toggle = (CompoundButton) group.getChildAt(i);
                break;
            }
        }
        if (toggle == null) return;
        state.syncing = true;
        try {
            toggle.setChecked(state.selected);
        } finally {
            state.syncing = false;
        }
    }

    private static void requestNativeResize(Object info) {
        if (info == null) return;
        int x = getSpan(info, "spanX"), y = getSpan(info, "spanY");
        if (x == 2 && y == 2) return;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(
                info, "convertIconSize", NATIVE_FOUR_TYPE);
        if (!result.succeeded()) {
            Api101Bridge.log(TAG + " native resize failed: " + result.failure());
        }
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
