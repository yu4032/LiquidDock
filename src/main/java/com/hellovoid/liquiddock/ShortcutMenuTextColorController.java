package com.hellovoid.liquiddock;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.WeakHashMap;

/** Applies optional white text/icon styling to shortcut-menu content without vendor listener replacement. */
final class ShortcutMenuTextColorController {
    private static final ColorStateList WHITE_TINT = ColorStateList.valueOf(Color.WHITE);
    private static final WeakHashMap<View, ViewTreeObserver.OnGlobalLayoutListener> LISTENERS =
            new WeakHashMap<>();

    private ShortcutMenuTextColorController() {}

    static synchronized void attach(View root) {
        if (root == null) return;
        applyDarkMode(root);
        if (LISTENERS.containsKey(root)) return;

        ViewTreeObserver.OnGlobalLayoutListener listener = () -> applyDarkMode(root);
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive()) return;
        observer.addOnGlobalLayoutListener(listener);
        LISTENERS.put(root, listener);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                detach(v);
                v.removeOnAttachStateChangeListener(this);
            }
        });
    }

    private static synchronized void detach(View root) {
        ViewTreeObserver.OnGlobalLayoutListener listener = LISTENERS.remove(root);
        if (listener == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(listener);
        }
    }

    private static void applyDarkMode(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setTextColor(Color.WHITE);
            textView.setCompoundDrawableTintList(WHITE_TINT);
        }
        if (view instanceof ImageView) {
            ((ImageView) view).setImageTintList(WHITE_TINT);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            applyDarkMode(group.getChildAt(i));
        }
    }
}
