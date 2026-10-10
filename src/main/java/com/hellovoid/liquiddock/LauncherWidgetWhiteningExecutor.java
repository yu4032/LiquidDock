package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Reversible, per-exact-node foreground whitening for RemoteViews widgets. */
final class LauncherWidgetWhiteningExecutor {
    private static final ColorFilter WHITE_FILTER =
            new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
    // Claims contain only weak node references: a child View must not keep the host
    // alive through a WeakHashMap value and its parent relationship.
    private static final Map<View, List<Claim>> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private LauncherWidgetWhiteningExecutor() {}

    static void claim(View host) {
        if (host == null) return;
        release(host);
        if (LauncherWidgetComponentDiscovery.isMamlHost(host)) return;

        Set<String> selected = ConfigReader.load().stringSet(
                WidgetComponentStore.WHITE_SELECTION_KEY);
        if (selected.isEmpty()) return;
        String provider = LauncherWidgetComponentDiscovery.providerIdentity(host);
        List<WidgetComponentStore.Descriptor> selectors =
                WidgetComponentWhiteningPolicy.selectors(selected, provider);
        if (selectors.isEmpty()) return;
        View content = LauncherWidgetComponentDiscovery.resolveRemoteViewsContent(host);
        if (content == null) return;

        ArrayList<Claim> claims = new ArrayList<>();
        for (WidgetComponentStore.Descriptor selector : selectors) {
            View target = LauncherWidgetComponentSelectionExecutor.resolveExactRemoteView(
                    content, selector.hierarchyPath);
            if (target == null
                    || !selector.className.equals(target.getClass().getName())
                    || !selector.name.equals(
                            LauncherWidgetComponentDiscovery.resourceEntryName(target))) {
                continue;
            }
            if (WidgetComponentStore.ACTION_HIDE_VIEW.equals(selector.action)
                    && target instanceof TextView) {
                TextView text = (TextView) target;
                ColorStateList original = text.getTextColors();
                if (original == null || original.getDefaultColor() == Color.WHITE) continue;
                text.setTextColor(Color.WHITE);
                claims.add(new TextClaim(text, original));
            } else if (WidgetComponentStore.TYPE_IMAGE.equals(selector.componentType)
                    && target instanceof ImageView) {
                ImageView image = (ImageView) target;
                if (image.getDrawable() == null) continue;
                ColorFilter original = image.getColorFilter();
                if (original == WHITE_FILTER) continue;
                image.setColorFilter(WHITE_FILTER);
                claims.add(new ImageClaim(image, original));
            }
        }
        if (!claims.isEmpty()) CLAIMS.put(host, claims);
    }

    static void release(View host) {
        if (host == null) return;
        List<Claim> claims = CLAIMS.remove(host);
        if (claims == null) return;
        for (Claim claim : claims) claim.restore();
    }

    private interface Claim {
        void restore();
    }

    private static final class TextClaim implements Claim {
        private final WeakReference<TextView> view;
        private final ColorStateList original;
        TextClaim(TextView text, ColorStateList colors) {
            view = new WeakReference<>(text);
            original = colors;
        }
        @Override public void restore() {
            TextView text = view.get();
            if (text != null && text.getTextColors().getDefaultColor() == Color.WHITE) {
                text.setTextColor(original);
            }
        }
    }

    private static final class ImageClaim implements Claim {
        private final WeakReference<ImageView> view;
        private final ColorFilter original;
        ImageClaim(ImageView image, ColorFilter filter) {
            view = new WeakReference<>(image);
            original = filter;
        }
        @Override public void restore() {
            ImageView image = view.get();
            if (image == null || image.getColorFilter() != WHITE_FILTER) return;
            if (original == null) image.clearColorFilter();
            else image.setColorFilter(original);
        }
    }
}
