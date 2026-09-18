package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Ports the HyperOS 4 2x1 folder as an additional Launcher 4.50 folder size.
 *
 * <p>Launcher 4.50 has three vendor sizes: type 2 (1x1), type 21 (2x2 preview), and type 22
 * (3x3 preview). The 2x1 port deliberately does not replace type 21. It is persisted as the
 * vendor-compatible pair {@code itemType=21, span=2x1}. Only the explicit 2x1 conversion
 * transaction overrides the vendor type-21 span authority; ordinary type-21 conversions remain
 * untouched and therefore continue to create the original 2x2 folder.</p>
 */
final class Folder2x1Hook {
    private static final String TAG = "[DC][Folder2x1]";
    private static final String CONVERT_SIZE =
            "com.miui.home.launcher.convertsize.FolderIconConvertSizeController";
    private static final String BASE_PREVIEW =
            "com.miui.home.launcher.folder.BaseFolderIconPreviewContainer2X2";
    private static final String FOLDER_ICON_2X2_4 =
            "com.miui.home.launcher.folder.FolderIcon2x2_4";
    private static final String FOLDER_INFO = "com.miui.home.launcher.FolderInfo";
    private static final String FOLDER_SHEET =
            "com.miui.home.launcher.folder.FolderSheet";
    private static final String FOLDER_ICON_CONTAINER =
            "com.miui.home.launcher.folder.LauncherFolder2x2IconContainer";
    private static final String FOLDER_ICON_IMAGE =
            "com.miui.home.launcher.folder.LauncherFolder2x2IconImageView";
    private static final String GRID_UTILS = "com.miui.home.launcher.grid.GridUtils";
    private static final String VISUAL_CHECK_BOX = "miuix.visual.check.VisualCheckBox";
    private static final String BORDER_LAYOUT = "miuix.visual.check.BorderLayout";

    private static final int VENDOR_TYPE_2X2_4 = 21;
    private static final ThreadLocal<Boolean> FORCE_2X1_CONVERSION = new ThreadLocal<>();

    private static final Map<View, Folder2x1LayoutPolicy.Layout> LAYOUTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, SheetState> SHEETS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile boolean enabled;
    private static volatile boolean iconSizeEnabled;
    private static volatile int iconSizePercent = Launcher450IconSizePolicy.DEFAULT_PERCENT;
    private static volatile Class<?> gridUtilsClass;
    private static volatile Class<?> visualCheckBoxClass;
    private static volatile Class<?> borderLayoutClass;
    private static volatile Method setMeasuredDimension;
    private static boolean installed;

    private Folder2x1Hook() {}

    static boolean install(ClassLoader classLoader, boolean featureEnabled,
                           boolean scaleWorkspaceIcons, int workspaceIconPercent) {
        enabled = featureEnabled;
        iconSizeEnabled = scaleWorkspaceIcons;
        iconSizePercent = workspaceIconPercent;
        if (installed) return true;
        if (classLoader == null) return false;

        try {
            Class<?> convert = Class.forName(CONVERT_SIZE, false, classLoader);
            Class<?> preview = Class.forName(BASE_PREVIEW, false, classLoader);
            Class<?> folderInfo = Class.forName(FOLDER_INFO, false, classLoader);
            Class<?> folderSheet = Class.forName(FOLDER_SHEET, false, classLoader);
            Class<?> iconContainer = Class.forName(FOLDER_ICON_CONTAINER, false, classLoader);
            Class<?> iconImage = Class.forName(FOLDER_ICON_IMAGE, false, classLoader);
            gridUtilsClass = Class.forName(GRID_UTILS, false, classLoader);
            visualCheckBoxClass = Class.forName(VISUAL_CHECK_BOX, false, classLoader);
            borderLayoutClass = Class.forName(BORDER_LAYOUT, false, classLoader);
            setMeasuredDimension = View.class.getDeclaredMethod(
                    "setMeasuredDimension", int.class, int.class);
            setMeasuredDimension.setAccessible(true);

            installConversionHooks(convert, folderInfo);
            installFolderSheetHooks(folderSheet, folderInfo);
            installWorkspaceGeometryHooks(iconContainer, iconImage, preview);

            installed = true;
            MainHook.log(TAG + " installed enabled=" + enabled);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable on target Launcher: " + error);
            return false;
        }
    }

    private static void installConversionHooks(
            Class<?> convert, Class<?> folderInfo) throws NoSuchMethodException {
        Method spanX = HookUtil.findMethodExact(
                convert, "getFolderSpanXFromType", new Class<?>[]{int.class});
        Method spanY = HookUtil.findMethodExact(
                convert, "getFolderSpanYFromType", new Class<?>[]{int.class});
        Method gridSize = HookUtil.findMethodExact(
                folderInfo, "getFolderGridSize", new Class<?>[0]);

        HookUtil.hook(spanX, chain -> {
            Object type = chain.getArgs().get(0);
            if (isForced2x1Type(type)) return 2;
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });
        HookUtil.hook(spanY, chain -> {
            Object type = chain.getArgs().get(0);
            if (isForced2x1Type(type)) return 1;
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });
        HookUtil.hook(gridSize, chain -> {
            Object info = chain.getThisObject();
            if (enabled && isTargetFolderInfo(info)) return "2*1";
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });
    }

    private static boolean isForced2x1Type(Object type) {
        return enabled
                && Boolean.TRUE.equals(FORCE_2X1_CONVERSION.get())
                && type instanceof Integer
                && ((Integer) type) == VENDOR_TYPE_2X2_4;
    }

    private static void installFolderSheetHooks(
            Class<?> folderSheet, Class<?> folderInfo) throws NoSuchMethodException {
        Method initListener = HookUtil.findMethodExact(
                folderSheet, "initListener", new Class<?>[]{folderInfo});
        Method initPreview = HookUtil.findMethodExact(
                folderSheet, "initPreviewIcon", new Class<?>[0]);
        Method onClick = HookUtil.findMethodExact(
                folderSheet, "onClick", new Class<?>[]{View.class});

        HookUtil.hook(initListener, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            if (!enabled || !(chain.getThisObject() instanceof View)) return result;
            View sheet = (View) chain.getThisObject();
            Object info = chain.getArgs().get(0);
            try {
                installFolderSheetOption(sheet, info);
            } catch (Throwable error) {
                MainHook.log(TAG + " folder picker option unavailable: " + error);
            }
            return result;
        });

        HookUtil.hook(initPreview, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            if (enabled && chain.getThisObject() instanceof View) {
                SheetState state = SHEETS.get((View) chain.getThisObject());
                if (state != null && state.selected2x1) {
                    setChecked(state.customCheckBox, true);
                }
            }
            return result;
        });

        HookUtil.hook(onClick, chain -> {
            if (!enabled || !(chain.getThisObject() instanceof View)) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            View sheet = (View) chain.getThisObject();
            SheetState state = SHEETS.get(sheet);
            Object rawClicked = chain.getArgs().get(0);
            if (state == null || !(rawClicked instanceof View)) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }

            View clicked = (View) rawClicked;
            int clickedId = clicked.getId();
            if (state.nativeSizeIds.contains(clickedId)) {
                state.selected2x1 = false;
            }

            if (clickedId != state.okId) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }

            Object info = state.folderInfo;
            boolean was2x1 = isTargetFolderInfo(info);
            int originalType = intField(info, "itemType", -1);
            int selectedVendorType = intField(sheet, "mFolderType", originalType);

            if (state.selected2x1) {
                FORCE_2X1_CONVERSION.set(Boolean.TRUE);
                try {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    // Vendor code skips conversion when both old/new itemType are 21. A native 2x2
                    // and our 2x1 share that itemType, so explicitly run the semantic converter.
                    if (!was2x1 && originalType == VENDOR_TYPE_2X2_4) {
                        invokeConvertIconSize(info, VENDOR_TYPE_2X2_4);
                    }
                    return result;
                } finally {
                    FORCE_2X1_CONVERSION.remove();
                }
            }

            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            // Symmetric case: converting an existing 2x1 back to the original type-21 2x2 also
            // looks like "same itemType" to FolderSheet, so force one ordinary vendor conversion.
            if (was2x1 && selectedVendorType == VENDOR_TYPE_2X2_4) {
                invokeConvertIconSize(info, VENDOR_TYPE_2X2_4);
            }
            return result;
        });
    }

    private static void installFolderSheetOption(View sheet, Object info) throws Throwable {
        Context context = sheet.getContext();
        int groupId = resourceId(context, "visual_check_group");
        if (groupId == 0) return;
        View groupView = sheet.findViewById(groupId);
        if (!(groupView instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) groupView;

        SheetState old = SHEETS.get(sheet);
        if (old != null && old.customCheckBox.getParent() == group) return;

        LinearLayout checkBox =
                (LinearLayout) newVendorViewGroup(visualCheckBoxClass, context);
        checkBox.setId(View.generateViewId());
        checkBox.setGravity(Gravity.CENTER_HORIZONTAL);
        checkBox.setContentDescription("2×1");

        int groupWidth = group.getLayoutParams() != null ? group.getLayoutParams().width : 0;
        if (groupWidth <= 0) groupWidth = dp(context, 330);
        int optionWidth = Math.max(dp(context, 72), groupWidth / 4);
        resizeExistingFolderOptions(group, optionWidth);
        checkBox.setLayoutParams(new ViewGroup.MarginLayoutParams(
                optionWidth, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout border =
                (LinearLayout) newVendorViewGroup(borderLayoutClass, context);
        border.setGravity(Gravity.CENTER);
        int borderSize = Math.max(dp(context, 54), Math.min(dp(context, 72), optionWidth - dp(context, 8)));
        border.setLayoutParams(new LinearLayout.LayoutParams(borderSize, borderSize));
        Folder2x1PickerPreviewView pickerPreview = new Folder2x1PickerPreviewView(context);
        border.addView(pickerPreview, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(context);
        title.setText("2×1");
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        copyFolderPickerTitleStyle(sheet, context, title);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        checkBox.addView(border);
        checkBox.addView(title);
        group.addView(checkBox, Math.min(1, group.getChildCount()));

        SheetState state = new SheetState(info, checkBox);
        state.okId = resourceId(context, "folder_picker_ok");
        addNativeSizeIds(state.nativeSizeIds, context);
        state.selected2x1 = isTargetFolderInfo(info);
        SHEETS.put(sheet, state);

        View.OnClickListener click = ignored -> select2x1(sheet, state);
        border.setOnClickListener(click);
        title.setOnClickListener(click);
        pickerPreview.setOnClickListener(click);
        checkBox.setOnTouchListener((ignored, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                select2x1(sheet, state);
            }
            return false;
        });

        if (state.selected2x1) setChecked(checkBox, true);
        group.requestLayout();
    }

    private static void resizeExistingFolderOptions(ViewGroup group, int optionWidth) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp == null) continue;
            lp.width = optionWidth;
            child.setLayoutParams(lp);
        }
    }

    private static void copyFolderPickerTitleStyle(
            View sheet, Context context, TextView title) {
        int id = resourceId(context, "big_folder_name");
        View source = id == 0 ? null : sheet.findViewById(id);
        if (!(source instanceof TextView)) return;
        TextView text = (TextView) source;
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, text.getTextSize());
        title.setTextColor(text.getTextColors());
        title.setTypeface(text.getTypeface());
    }

    private static void addNativeSizeIds(Set<Integer> ids, Context context) {
        String[] names = {
                "default_folder_check_box", "default_folder_name", "default_folder_select_border",
                "big_folder_check_box", "big_folder_name", "big_folder_select_border",
                "big_folder_check_box_2x2_9", "big_folder_name_2x2_9",
                "big_folder_select_border_2x2_9"
        };
        for (String name : names) {
            int id = resourceId(context, name);
            if (id != 0) ids.add(id);
        }
    }

    private static void select2x1(View sheet, SheetState state) {
        if (!enabled || state == null) return;
        state.selected2x1 = true;
        HookUtil.InvocationResult<Object> switched =
                HookUtil.tryInvoke(sheet, "switchFolderType", VENDOR_TYPE_2X2_4);
        if (!switched.succeeded()) {
            MainHook.log(TAG + " picker switch fallback: " + switched.failure());
            return;
        }
        setChecked(state.customCheckBox, true);
    }

    private static void setChecked(View checkBox, boolean checked) {
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(checkBox, "setChecked", checked);
        if (!result.succeeded()) {
            MainHook.log(TAG + " picker checked-state fallback: " + result.failure());
        }
    }

    private static ViewGroup newVendorViewGroup(
            Class<?> type, Context context) throws Throwable {
        Constructor<?> ctor = type.getConstructor(Context.class, AttributeSet.class);
        ctor.setAccessible(true);
        return (ViewGroup) ctor.newInstance(context, null);
    }

    private static int resourceId(Context context, String name) {
        if (context == null) return 0;
        return context.getResources().getIdentifier(name, "id", context.getPackageName());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void invokeConvertIconSize(Object info, int type) {
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(info, "convertIconSize", type);
        if (!result.succeeded()) {
            MainHook.log(TAG + " convertIconSize fallback: " + result.failure());
        }
    }

    private static void installWorkspaceGeometryHooks(
            Class<?> iconContainer, Class<?> iconImage, Class<?> preview)
            throws NoSuchMethodException {
        Method containerMeasure = HookUtil.findMethodExact(
                iconContainer, "onMeasure", new Class<?>[]{int.class, int.class});
        Method imageMeasure = HookUtil.findMethodExact(
                iconImage, "onMeasure", new Class<?>[]{int.class, int.class});
        Method previewMeasure = HookUtil.findMethodExact(
                preview, "onMeasure", new Class<?>[]{int.class, int.class});
        Method previewLayout = HookUtil.findMethodExact(
                preview, "onLayout",
                new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});

        HookUtil.hook(containerMeasure, chain -> {
            Object owner = chain.getThisObject();
            if (!(owner instanceof View) || !enabled || !isTargetGeometryView((View) owner)) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            int iconSize = resolveWorkspaceIconSize((View) owner);
            if (iconSize <= 0) return chain.proceed(chain.getArgs().toArray(new Object[0]));
            int widthSpec = View.MeasureSpec.makeMeasureSpec(iconSize * 2, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(iconSize, View.MeasureSpec.EXACTLY);
            return chain.proceed(new Object[]{widthSpec, heightSpec});
        });

        // LauncherFolder2x2IconImageView inherits LauncherIconImageView#onMeasure, whose vendor
        // square size authority is correct for 2x2 but cannot express a rectangular 2x1 surface.
        // Keep its content/drawable setup, then replace only the measured result for our target.
        HookUtil.hook(imageMeasure, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            Object owner = chain.getThisObject();
            if (owner instanceof View && enabled && FOLDER_ICON_IMAGE.equals(
                    owner.getClass().getName()) && isTargetGeometryView((View) owner)) {
                forceMeasured2x1((View) owner);
            }
            return result;
        });

        HookUtil.hook(previewMeasure, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            Object owner = chain.getThisObject();
            if (enabled && owner instanceof ViewGroup) {
                applyPreviewMeasure((ViewGroup) owner);
            }
            return result;
        });
        HookUtil.hook(previewLayout, chain -> {
            Object owner = chain.getThisObject();
            if (!(owner instanceof ViewGroup) || !enabled) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            ViewGroup group = (ViewGroup) owner;
            Folder2x1LayoutPolicy.Layout layout = LAYOUTS.get(group);
            if (layout == null || !isTargetPreview(group)) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            layoutChildren(group, layout);
            return null;
        });
    }

    private static void forceMeasured2x1(View view) {
        int iconSize = resolveWorkspaceIconSize(view);
        if (iconSize <= 0) return;
        try {
            setMeasuredDimension.invoke(view, iconSize * 2, iconSize);
        } catch (Throwable error) {
            MainHook.log(TAG + " image geometry fallback: " + error);
        }
    }

    private static void applyPreviewMeasure(ViewGroup group) {
        if (!isTargetPreview(group)) {
            LAYOUTS.remove(group);
            return;
        }
        int iconSize = resolveWorkspaceIconSize(group);
        if (iconSize <= 0) {
            LAYOUTS.remove(group);
            return;
        }
        int contentCount = resolveContentCount(group);
        Folder2x1LayoutPolicy.Layout layout = Folder2x1LayoutPolicy.resolve(
                iconSize, contentCount, group.getChildCount());
        try {
            group.setPadding(0, 0, 0, 0);
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                Folder2x1LayoutPolicy.Slot slot =
                        i < layout.slots.length ? layout.slots[i] : null;
                if (slot == null) {
                    child.setVisibility(View.INVISIBLE);
                    continue;
                }
                child.setVisibility(View.VISIBLE);
                child.measure(
                        View.MeasureSpec.makeMeasureSpec(slot.width(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(slot.height(), View.MeasureSpec.EXACTLY));
            }
            setMeasuredDimension.invoke(group, layout.width, layout.height);
            LAYOUTS.put(group, layout);
        } catch (Throwable error) {
            LAYOUTS.remove(group);
            MainHook.log(TAG + " preview measure fallback: " + error);
        }
    }

    private static void layoutChildren(
            ViewGroup group, Folder2x1LayoutPolicy.Layout layout) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Folder2x1LayoutPolicy.Slot slot =
                    i < layout.slots.length ? layout.slots[i] : null;
            if (slot == null) {
                child.layout(0, 0, 0, 0);
                continue;
            }
            child.layout(slot.left, slot.top, slot.right, slot.bottom);
        }
    }

    private static boolean isTargetPreview(View preview) {
        Object info = findFolderInfo(preview);
        return isTargetFolderInfo(info);
    }

    private static boolean isTargetGeometryView(View view) {
        return isTargetFolderInfo(findFolderInfo(view));
    }

    private static boolean isTargetFolderInfo(Object info) {
        if (info == null) return false;
        return intField(info, "itemType", -1) == VENDOR_TYPE_2X2_4
                && intField(info, "spanX", -1) == 2
                && intField(info, "spanY", -1) == 1;
    }

    private static int intField(Object target, String name, int fallback) {
        if (target == null) return fallback;
        try {
            return HookUtil.getIntField(target, name);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static Object findFolderInfo(View view) {
        for (View current = view; current != null; ) {
            if (FOLDER_ICON_2X2_4.equals(current.getClass().getName())) {
                return current.getTag();
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static int resolveContentCount(View preview) {
        Object info = findFolderInfo(preview);
        if (info == null) return 0;
        HookUtil.InvocationResult<Object> count = HookUtil.tryInvoke(info, "count");
        if (count.succeeded() && count.value() instanceof Integer) {
            return Math.max(0, (Integer) count.value());
        }
        return preview instanceof ViewGroup ? ((ViewGroup) preview).getChildCount() : 0;
    }

    private static int resolveWorkspaceIconSize(View view) {
        Class<?> gridUtils = gridUtilsClass;
        if (gridUtils == null) return 0;
        HookUtil.InvocationResult<Object> grid =
                HookUtil.tryInvokeStatic(gridUtils, "findParentUsedGridConfig", view);
        if (!grid.succeeded() || grid.value() == null) return 0;
        HookUtil.InvocationResult<Object> iconSize =
                HookUtil.tryInvoke(grid.value(), "getIconSize");
        if (!iconSize.succeeded() || !(iconSize.value() instanceof Integer)) return 0;
        return Launcher450IconSizePolicy.scaledPx(
                (Integer) iconSize.value(), iconSizeEnabled, iconSizePercent);
    }

    private static final class SheetState {
        final Object folderInfo;
        final View customCheckBox;
        final Set<Integer> nativeSizeIds = new HashSet<>();
        int okId;
        boolean selected2x1;

        SheetState(Object folderInfo, View customCheckBox) {
            this.folderInfo = folderInfo;
            this.customCheckBox = customCheckBox;
        }
    }
}
