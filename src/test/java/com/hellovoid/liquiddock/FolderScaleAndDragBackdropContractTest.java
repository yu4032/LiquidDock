package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for HyperOS 4.50 small-folder preview scaling and drag glass authority. */
public class FolderScaleAndDragBackdropContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");


    @Test public void fourthSizeUsesTheSingleNativeTwoByTwoFolder() throws Exception {
        String nativeHook = Files.readString(MAIN.resolve("LauncherNativeFourFolderHook.java"));
        String startup = Files.readString(MAIN.resolve("ModuleMain.java"));
        String picker = Files.readString(MAIN.resolve("LauncherLargeFolderPickerHook.java"));
        String nativeSpan = Files.readString(MAIN.resolve("LauncherLargeFolderNativeSpanHook.java"));
        String geometry = Files.readString(MAIN.resolve("HomeGridCellGeometryHook.java"));

        assertTrue(startup.contains("LauncherLargeFolderPickerHook.install("));
        assertTrue(startup.contains("LauncherLargeFolderNativeSpanHook.install("));
        assertTrue(startup.contains("LauncherNativeFourFolderHook.install("));
        assertFalse(startup.contains("LauncherFourGridLayoutHook.install("));
        assertFalse(startup.contains("LauncherFourGridAnimationHook.install("));
        assertFalse(startup.contains("LauncherLargeFolderVisualHook.install("));

        assertTrue(nativeHook.contains("FolderIcon2x2_4"));
        assertTrue(nativeHook.contains("LauncherFolder2x2IconContainer"));
        assertTrue(nativeHook.contains("getMiuiWidgetSizeSpec"));
        assertTrue(nativeHook.contains("resizeNativePreviewChildren(child, frame[0], frame[1])"));
        assertTrue(nativeHook.contains("LauncherIconImageView"));
        assertFalse(nativeHook.contains("imageBase.getDeclaredMethod(dimension, int.class)"));
        assertTrue(nativeHook.contains("HookUtil.findMethodExact("));
        assertTrue(nativeHook.contains("nativeImageClass, \"onMeasure\""));
        assertTrue(nativeHook.contains("nativePreviewClass, \"onMeasure\""));
        assertTrue(nativeHook.contains("FolderIconPreviewContainer2X2_4"));
        assertTrue(nativeHook.contains("forceMeasuredFrame(surface, frame, \"image\")"));
        assertTrue(nativeHook.contains("forceMeasuredFrame(previewView, frame, \"preview\")"));
        assertTrue(nativeHook.contains("setMeasuredDimension"));
        assertTrue(nativeHook.contains("before="));
        assertTrue(nativeHook.contains("success="));
        assertTrue(nativeHook.contains("new Object[]{"));
        assertTrue(nativeHook.contains("exact(frame[0]), exact(frame[1])"));
        assertTrue(nativeHook.contains("native folder hook coverage="));
        assertTrue(nativeHook.contains("resizeNativePreviewChildren(inner, w, h)"));
        assertTrue(nativeHook.contains("tracePreviewChildren("));
        assertTrue(nativeHook.contains("reconcileNativeMeasuredChildren(child, frame)"));
        assertTrue(nativeHook.contains("child.measure(exact(frame[0]), exact(frame[1]))"));
        assertTrue(nativeHook.contains("fallback-preview"));
        assertTrue(nativeHook.contains("fallback-image"));
        assertTrue(nativeHook.contains("LauncherFolder2x2IconImageView"));
        assertTrue(nativeHook.contains("FolderIconPreviewContainer2X2_4"));
        assertTrue(nativeHook.contains("params.width = width"));
        assertTrue(nativeHook.contains("params.height = height"));
        assertTrue(nativeHook.contains("view.setLayoutParams(params)"));
        assertTrue(nativeHook.contains("installedHooks++"));
        assertTrue(nativeHook.contains("vendorClass(loader"));
        assertTrue(nativeHook.contains("class-specific compatibility requires runtime trace"));
        assertTrue(nativeHook.contains("class unavailable"));
        String audit = Files.readString(MAIN.resolve(
                "LauncherLargeFolderRuntimeAuditHook.java"));
        assertTrue(audit.contains("logVendorContract("));
        assertTrue(audit.contains("native fromXml audit variants="));
        assertTrue(audit.contains("viewTree(child, 0)"));
        assertTrue(audit.contains("getDeclaredMethods()"));
        assertTrue(audit.contains("if (!marked && !(type == 21 && spanX == 2 && spanY == 2)) continue"));
        assertTrue(nativeHook.contains("itemIcon.getDeclaredMethod("));
        assertTrue(nativeHook.contains("\"onMeasure\", int.class, int.class"));
        assertTrue(nativeHook.contains("\"getCellWidth\""));
        assertTrue(nativeHook.contains("ROOT_FRAME.set(frame)"));
        assertTrue(nativeHook.contains("args[0] = exact(frame[0])"));
        assertTrue(nativeHook.contains("args[1] = exact(frame[1])"));
        assertTrue(nativeHook.contains("frame = ROOT_FRAME.get()"));
        assertTrue(nativeHook.contains("desiredFrame(host,"));
        assertTrue(nativeHook.contains("lp != null && lp.width > 0"));
        assertFalse(nativeHook.contains("NATIVE_SIZE.set(new int[]{width, height})"));
        assertTrue(nativeHook.contains("traceMeasure(\"inner\""));
        assertTrue(nativeHook.contains("traceMeasure(\"root\""));
        assertTrue(nativeHook.contains("FolderIcon.fromXml()"));
        assertTrue(nativeHook.contains("getBuddyIconView"));
        assertTrue(nativeHook.contains("invalidated cached native icon"));
        assertTrue(nativeHook.contains("CUSTOM_SIZED.remove(old)"));
        assertTrue(nativeHook.contains("mXs"));
        assertTrue(nativeHook.contains("mYs"));
        assertTrue(nativeHook.contains("WidgetGridSizing.centeredFrame"));
        assertFalse(nativeHook.contains("int side = Math.min(w, h)"));
        assertTrue(nativeHook.contains("wrapperParams.width = w"));
        assertTrue(nativeHook.contains("wrapperParams.height = h"));
        assertTrue(nativeHook.contains("innerParams.width = w"));
        assertTrue(nativeHook.contains("innerParams.height = h"));
        assertTrue(nativeHook.contains("preSetup2x2"));
        assertTrue(nativeHook.contains("onMeasureChild2x2"));
        assertFalse(nativeHook.contains("new ImageView("));
        assertFalse(nativeHook.contains("addView("));
        assertFalse(nativeHook.contains("setScaleX("));
        assertFalse(nativeHook.contains("Bitmap"));

        assertTrue(picker.contains("findNativeCheckGroup"));
        assertTrue(picker.contains("makeNativeSizeOption"));
        assertTrue(picker.contains("panel.addView(newOption, optionLp)"));
        assertTrue(picker.contains("\"topToBottom\", stockRow.getId()"));
        assertTrue(picker.contains("\"startToStart\", stockRow.getId()"));
        assertTrue(picker.contains("\"topToBottom\", newOption.getId()"));
        assertFalse(picker.contains("stockRow.addView(newOption"));
        assertFalse(picker.contains("fitFourChoices("));
        assertTrue(picker.contains("preview.setOnClickListener(v -> nativeCheck.performClick())"));
        assertTrue(picker.contains("newOption.setOnClickListener(v ->"));
        assertTrue(picker.contains("state.selected = true"));
        assertTrue(picker.contains("clearStockChecks(owner)"));
        assertTrue(picker.contains("setMarked(((View) owner).getContext(), state.info, selected)"));
        assertTrue(picker.contains("if (state.selected) clearStockChecks(owner)"));
        assertTrue(picker.contains("state.option.setBackground(outline)"));
        assertTrue(picker.contains("sample.getClass().getConstructor("));
        assertTrue(picker.contains("2×2 四宫格"));
        assertTrue(picker.contains("PREFIX + id"));
        assertTrue(picker.contains("boolean wasCustom = isMarked("));
        assertTrue(picker.contains("!selected && wasCustom"));
        assertTrue(picker.contains("convertIconSize"));
        assertFalse(picker.contains("group.getChildCount() != 4"));
        assertFalse(picker.contains("root.addView(original"));
        assertFalse(picker.contains("panel.addView(option"));
        assertTrue(nativeSpan.contains("FolderIconConvertSizeController"));
        assertTrue(nativeSpan.contains("ThreadLocal<Boolean> FORCE_LARGE"));
        assertTrue(nativeSpan.contains("LauncherLargeFolderPickerHook.isMarked("));
        assertTrue(geometry.contains("LauncherNativeFourFolderHook.isFourGridInfo(info)"));
        assertFalse(Files.exists(MAIN.resolve("LauncherFourGridContentView.java")));
        assertFalse(Files.exists(MAIN.resolve("LauncherFourGridLayoutHook.java")));
        assertFalse(Files.exists(MAIN.resolve("LauncherFourGridAnimationHook.java")));

        String schema = Files.readString(MAIN.resolve("config/ConfigSchema.java"));
        assertFalse(schema.contains("LARGE_FOLDER_FOUR_ICONS"));
    }

    @Test public void smallFolderPreviewContainerOwnsItsScaledRemeasureDomain() throws Exception {
        String hook = Files.readString(MAIN.resolve("Launcher450IconSizeHook.java"));

        assertTrue(hook.contains("FOLDER_PREVIEW_CONTAINER_1X1"));
        assertTrue(hook.contains("com.miui.home.launcher.folder.FolderIconPreviewContainer1X1"));
        assertTrue(hook.contains("installFolderPreviewMeasureTransaction"));
        assertTrue(hook.contains("\"onMeasure\""));
        assertTrue(hook.contains("MeasureDomain.FOLDER"));
    }

    @Test public void dragGlassUsesIndependentUpperWorkspaceBackdropAuthority() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String source = Files.readString(MAIN.resolve("LauncherDragSourceOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String domain = Files.readString(MAIN.resolve("PassBlurDomain.java"));
        String request = Files.readString(MAIN.resolve("PassBlurBindRequest.java"));

        assertTrue(source.contains("WindowManager.LayoutParams.TYPE_APPLICATION_PANEL"));
        assertTrue(source.contains("FLAG_NOT_TOUCHABLE"));
        assertTrue(source.contains("LiquidDockDragBackdropSource"));
        assertTrue(overlay.contains("LauncherDragSourceOverlay.attach"));
        assertTrue(domain.contains("DRAG_OVERLAY"));
        assertTrue(request.contains("static PassBlurBindRequest dragOverlay(View authoritativeRoot)"));
        assertTrue(overlay.contains("PassBlurBindRequest.dragOverlay(sourceOverlay)"));
        assertTrue(session.contains("PassBlurBindRequest bindRequest"));
        assertTrue(session.contains("source=RootPassBlurBackend domain="));
    }

    @Test public void liveDragPathNeverFreezesTheWorkspaceBackdrop() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String bridge = Files.readString(MAIN.resolve("LauncherLiveDragSessionBridge.java"));

        assertFalse(overlay.contains("freezeAfterNextFreshFrame"));
        assertFalse(overlay.contains("launcher-drag-frozen"));
        assertFalse(session.contains("freezeAfterNextFreshFrame"));
        assertFalse(session.contains("launcher-drag-frozen"));
        assertTrue(overlay.contains("prewarmLiveSource"));
        assertFalse(overlay.contains("gateCleanDragPresentation"));
        assertFalse(overlay.contains("requestCleanBackdropAndReveal"));
        assertTrue(session.contains("launcher-drag-live"));
        assertTrue(session.contains("setUpdatesEnabled(true"));
        assertTrue(session.contains("requestFreshBackdrop"));
        assertTrue(bridge.contains("session.ensureLiveDragSource()"));
    }

    @Test public void dragUpperWindowHostsBothLiveGlassAndVisualMirror() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String source = Files.readString(MAIN.resolve("LauncherDragSourceOverlay.java"));
        String mirror = Files.readString(MAIN.resolve("LauncherDragVisualMirror.java"));

        assertTrue(source.contains("glassHost"));
        assertTrue(source.contains("mirrorHost"));
        assertTrue(overlay.contains("glassHost()"));
        assertTrue(overlay.contains("LauncherDragVisualMirror.attach"));
        assertTrue(overlay.contains("mirror.syncFromDragView(source, sourceOverlay)"));
        assertTrue(mirror.contains("dragView.draw(canvas)"));
        assertTrue(mirror.contains("setWillNotDraw(false)"));
    }

    @Test public void dragMovementPublishesGlassAndMirrorFromTheChoreographerFrame() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(overlay.contains("mirror.syncFromDragView(source, sourceOverlay)"));
        assertTrue(overlay.contains("publishFrameGeometry"));
        assertTrue(overlay.contains("liveSink.captureGeometry(authorityRoot)"));
        assertTrue(overlay.contains("authority.publishDragGeometry(liveSink, geometry)"));
        assertTrue(session.contains("void publishDragGeometry("));
        assertTrue(session.contains("node.geometry = geometry"));
        assertTrue(session.contains("requestDragRedraw()"));
    }

    @Test public void vendorDragViewIsSuppressedOnlyAfterUpperPresentationIsReady() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String mirror = Files.readString(MAIN.resolve("LauncherDragVisualMirror.java"));

        assertTrue(overlay.contains("claimVendorPresentation"));
        assertTrue(overlay.contains("restoreVendorPresentation"));
        assertTrue(overlay.contains("source.setAlpha(0f)"));
        assertTrue(overlay.contains("hasPreparedBackdrop"));
        assertTrue(overlay.contains("sink.isAvailable()"));
        assertTrue(mirror.contains("isReadyForPresentation"));
    }
}
