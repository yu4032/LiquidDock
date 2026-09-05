package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Applies user-selected widget component suppression and restores original state on release. */
final class LauncherWidgetComponentSelectionExecutor {
    private static final Map<View, Claim> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherWidgetComponentSelectionExecutor() {}

    static void claim(View host) {
        if (host == null) return;
        if (LauncherWidgetComponentDiscovery.isMamlHost(host)) {
            Object root = readField(host, "mRoot");
            if (root != null) claimLoadedMamlRoot(host, root);
            return;
        }
        release(host);
        Set<String> encoded = ConfigReader.load().stringSet(WidgetComponentStore.SELECTION_KEY);
        if (encoded.isEmpty()) return;
        String provider = LauncherWidgetComponentDiscovery.providerIdentity(host);
        List<WidgetComponentStore.Descriptor> selectors = selectors(encoded, true, provider);
        if (selectors.isEmpty()) return;
        View content = LauncherWidgetComponentDiscovery.resolveRemoteViewsContent(host);
        if (content == null) return;

        ArrayList<BackgroundClaim> backgrounds = new ArrayList<>();
        ArrayList<ImageClaim> images = new ArrayList<>();
        ArrayList<ViewClaim> views = new ArrayList<>();
        for (WidgetComponentStore.Descriptor selector : selectors) {
            View target = resolveExactRemoteView(content, selector.hierarchyPath);
            if (target == null) continue;
            String resource = LauncherWidgetComponentDiscovery.resourceEntryName(target);
            if (!selector.className.equals(target.getClass().getName())
                    || !selector.name.equals(resource)) continue;

            if (WidgetComponentStore.ACTION_CLEAR_BACKGROUND.equals(selector.action)) {
                Drawable original = target.getBackground();
                if (original == null) continue;
                backgrounds.add(new BackgroundClaim(target, original));
                target.setBackground(null);
            } else if (WidgetComponentStore.ACTION_CLEAR_IMAGE.equals(selector.action)) {
                if (!(target instanceof ImageView)) continue;
                ImageView image = (ImageView) target;
                Drawable original = image.getDrawable();
                if (original == null) continue;
                images.add(new ImageClaim(image, original));
                image.setImageDrawable(null);
            } else if (WidgetComponentStore.ACTION_HIDE_VIEW.equals(selector.action)) {
                views.add(new ViewClaim(target, target.getVisibility()));
                target.setVisibility(View.INVISIBLE);
            }
        }
        if (!backgrounds.isEmpty() || !images.isEmpty() || !views.isEmpty()) {
            CLAIMS.put(host, new Claim(backgrounds, images, views, List.of()));
        }
    }

    static View resolveExactRemoteView(View content, String hierarchyPath) {
        if (content == null || hierarchyPath == null || hierarchyPath.isEmpty()) return null;
        String[] parts = hierarchyPath.split("/");
        if (parts.length == 0 || !"0".equals(parts[0])) return null;
        View current = content;
        for (int i = 1; i < parts.length; i++) {
            if (!(current instanceof ViewGroup)) return null;
            int index;
            try { index = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { return null; }
            ViewGroup group = (ViewGroup) current;
            if (index < 0 || index >= group.getChildCount()) return null;
            current = group.getChildAt(index);
            if (current == null) return null;
        }
        return current;
    }

    static void claimLoadedMamlRoot(View host, Object root) {
        if (host == null || root == null) return;
        release(host);
        WidgetBackgroundIdentity identity = identity(host);
        if (identity == null || identity.productId == null) return;
        Set<String> encoded = ConfigReader.load().stringSet(WidgetComponentStore.SELECTION_KEY);
        List<WidgetComponentStore.Descriptor> selectors =
                selectors(encoded, false, identity.productId);
        if (selectors.isEmpty()) return;
        ArrayList<MamlClaim> claims = new ArrayList<>();
        Set<Object> claimed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (WidgetComponentStore.Descriptor selector : selectors) {
            Object target;
            if (selector.isExactMamlRender()) {
                target = resolveExactMamlElement(root, selector.hierarchyPath);
                if (target == null) continue;
                String targetName = stringField(target, "mName");
                if (targetName == null) targetName = "";
                if (!selector.name.equals(targetName)) continue;
            } else {
                target = invokeOptional(root, "findElement", selector.name);
            }
            if (target == null || !selector.className.equals(target.getClass().getName())) continue;
            if (!claimed.add(target)) continue;
            boolean originalShow = readBooleanField(target, "mShow", true);
            if (invokeOptionalMutation(target, "show", false)) {
                claims.add(new MamlClaim(target, originalShow));
            }
        }
        if (!claims.isEmpty()) {
            CLAIMS.put(host, new Claim(List.of(), List.of(), List.of(), claims));
        }
    }

    /** Resolve only through Launcher 4.50's actual ScreenElementRoot.mInnerGroup render tree. */
    static Object resolveExactMamlElement(Object root, String hierarchyPath) {
        if (root == null || hierarchyPath == null || hierarchyPath.isEmpty()) return null;
        String[] parts = hierarchyPath.split("/");
        if (parts.length < 2 || !"render".equals(parts[0])) return null;
        Object current = readField(root, "mInnerGroup");
        if (current == null) return null;
        for (int i = 1; i < parts.length; i++) {
            int index;
            try { index = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { return null; }
            Object childrenValue = readField(current, "mElements");
            if (!(childrenValue instanceof List)) return null;
            List<?> children = (List<?>) childrenValue;
            if (index < 0 || index >= children.size()) return null;
            current = children.get(index);
            if (current == null) return null;
        }
        return current;
    }

    static void release(View host) {
        if (host == null) return;
        Claim claim = CLAIMS.remove(host);
        if (claim == null) return;
        for (BackgroundClaim item : claim.backgrounds) {
            if (item.view != null) item.view.setBackground(item.originalBackground);
        }
        for (ImageClaim item : claim.images) {
            if (item.view != null) item.view.setImageDrawable(item.originalImage);
        }
        for (ViewClaim item : claim.views) {
            if (item.view != null) item.view.setVisibility(item.originalVisibility);
        }
        for (MamlClaim item : claim.maml) {
            invokeOptionalMutation(item.element, "show", item.originalShow);
        }
    }

    private static Object invokeOptional(Object target, String methodName, Object... args) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(target, methodName, args);
        if (!result.succeeded()) {
            MainHook.log("[DC][WidgetComponent] " + methodName + " unavailable: " + result.failure());
            return null;
        }
        return result.value();
    }

    private static boolean invokeOptionalMutation(
            Object target, String methodName, Object... args) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(target, methodName, args);
        if (!result.succeeded()) {
            MainHook.log("[DC][WidgetComponent] " + methodName + " unavailable: " + result.failure());
            return false;
        }
        return true;
    }

    private static List<WidgetComponentStore.Descriptor> selectors(
            Set<String> encoded, boolean remote, String owner) {
        if (encoded == null || encoded.isEmpty() || owner == null) return List.of();
        ArrayList<WidgetComponentStore.Descriptor> result = new ArrayList<>();
        for (String value : encoded) {
            WidgetComponentStore.Descriptor descriptor = WidgetComponentStore.parseSelector(value);
            if (descriptor == null || descriptor.isRemoteViews() != remote
                    || !owner.equals(descriptor.owner)) continue;
            result.add(descriptor);
        }
        return result;
    }

    private static WidgetBackgroundIdentity identity(View host) {
        Object itemInfo = invokeOptional(host, "getItemInfo");
        if (itemInfo == null) return null;
        return new WidgetBackgroundIdentity(
                "maml",
                stringField(itemInfo, "productId"),
                stringField(itemInfo, "appPackage"),
                intField(itemInfo, "spanX", -1),
                intField(itemInfo, "spanY", -1),
                intField(itemInfo, "configSpanX", -1),
                intField(itemInfo, "configSpanY", -1));
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }

    private static String stringField(Object target, String name) {
        Object value = readField(target, name);
        return value instanceof String ? (String) value : null;
    }

    private static int intField(Object target, String name, int fallback) {
        Object value = readField(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean readBooleanField(Object target, String name, boolean fallback) {
        try { return HookUtil.getBooleanField(target, name); }
        catch (Throwable ignored) { return fallback; }
    }

    private static final class Claim {
        final List<BackgroundClaim> backgrounds;
        final List<ImageClaim> images;
        final List<ViewClaim> views;
        final List<MamlClaim> maml;
        Claim(
                List<BackgroundClaim> backgrounds,
                List<ImageClaim> images,
                List<ViewClaim> views,
                List<MamlClaim> maml) {
            this.backgrounds = List.copyOf(backgrounds);
            this.images = List.copyOf(images);
            this.views = List.copyOf(views);
            this.maml = List.copyOf(maml);
        }
    }

    private static final class BackgroundClaim {
        final View view;
        final Drawable originalBackground;
        BackgroundClaim(View view, Drawable originalBackground) {
            this.view = view;
            this.originalBackground = originalBackground;
        }
    }

    private static final class ImageClaim {
        final ImageView view;
        final Drawable originalImage;
        ImageClaim(ImageView view, Drawable originalImage) {
            this.view = view;
            this.originalImage = originalImage;
        }
    }

    private static final class ViewClaim {
        final View view;
        final int originalVisibility;
        ViewClaim(View view, int originalVisibility) {
            this.view = view;
            this.originalVisibility = originalVisibility;
        }
    }

    private static final class MamlClaim {
        final Object element;
        final boolean originalShow;
        MamlClaim(Object element, boolean originalShow) {
            this.element = element;
            this.originalShow = originalShow;
        }
    }
}
