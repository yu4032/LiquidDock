package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/** Bounded one-shot hierarchy diagnostics for Searchbox glass ownership debugging. */
final class MiuiSearchboxHierarchyDiagnostics {
    private static final String TAG = "[DC][MiuiSearchboxGlass][Hierarchy]";

    private MiuiSearchboxHierarchyDiagnostics() {}

    static void log(View background, View glass) {
        Api101Bridge.log(TAG + " --- snapshot begin ---");
        logChain("background", background);
        logChain("glass", glass);
        Api101Bridge.log(TAG + " --- snapshot end ---");
    }

    private static void logChain(String label, View start) {
        View current = start;
        int depth = 0;
        while (current != null && depth < 8) {
            Api101Bridge.log(TAG + " " + label + " depth=" + depth + " " + describe(current));
            ViewParent parent = current.getParent();
            if (parent instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) parent;
                int selfIndex = group.indexOfChild(current);
                Api101Bridge.log(TAG + " " + label + " parent=" + group.getClass().getName()
                        + " selfIndex=" + selfIndex + " childCount=" + group.getChildCount()
                        + " clipChildren=" + group.getClipChildren()
                        + " clipToPadding=" + group.getClipToPadding());
                int count = Math.min(group.getChildCount(), 24);
                for (int i = 0; i < count; i++) {
                    View child = group.getChildAt(i);
                    Api101Bridge.log(TAG + " " + label + " sibling[" + i + "] " + describe(child));
                }
                current = group;
            } else {
                if (parent != null) {
                    Api101Bridge.log(TAG + " " + label + " nonViewParent="
                            + parent.getClass().getName());
                }
                break;
            }
            depth++;
        }
    }

    private static String describe(View view) {
        if (view == null) return "null";
        Drawable background = view.getBackground();
        return "class=" + view.getClass().getName()
                + " id=0x" + Integer.toHexString(view.getId())
                + " visibility=" + view.getVisibility()
                + " alpha=" + view.getAlpha()
                + " z=" + view.getZ()
                + " elevation=" + view.getElevation()
                + " translationX=" + view.getTranslationX()
                + " translationY=" + view.getTranslationY()
                + " size=" + view.getWidth() + "x" + view.getHeight()
                + " xy=" + view.getX() + "," + view.getY()
                + " background=" + (background == null ? "null" : background.getClass().getName())
                + " textureView=" + (view instanceof TextureView)
                + " surfaceView=" + (view instanceof SurfaceView)
                + " hardware=" + view.isHardwareAccelerated();
    }
}
