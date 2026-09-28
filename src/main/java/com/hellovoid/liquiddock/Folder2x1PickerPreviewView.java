package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Compact native-looking glyph used only by the injected FolderSheet 2x1 size option. */
final class Folder2x1PickerPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    Folder2x1PickerPreviewView(Context context) {
        super(context);
        setClickable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;

        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        float folderW = Math.min(w * 0.84f, h * 1.55f);
        float folderH = folderW * 0.5f;
        if (folderH > h * 0.72f) {
            folderH = h * 0.72f;
            folderW = folderH * 2f;
        }
        float left = (w - folderW) * 0.5f;
        float top = (h - folderH) * 0.5f;
        float radius = folderH * 0.22f;

        paint.setColor(dark ? 0x4DFFFFFF : 0x26000000);
        rect.set(left, top, left + folderW, top + folderH);
        canvas.drawRoundRect(rect, radius, radius, paint);

        float large = folderH * 0.46f;
        float cy = top + folderH * 0.5f;
        float firstCx = left + folderW * 0.24f;
        float secondCx = left + folderW * 0.50f;
        float clusterCx = left + folderW * 0.76f;
        paint.setColor(dark ? 0xCCFFFFFF : 0xB3000000);
        drawSquare(canvas, firstCx, cy, large, large * 0.22f);
        drawSquare(canvas, secondCx, cy, large, large * 0.22f);

        float miniGap = large * 0.10f;
        float mini = (large - miniGap) * 0.5f;
        float clusterLeft = clusterCx - large * 0.5f;
        float clusterTop = cy - large * 0.5f;
        float miniRadius = mini * 0.22f;
        drawSquareAt(canvas, clusterLeft, clusterTop, mini, miniRadius);
        drawSquareAt(canvas, clusterLeft + mini + miniGap, clusterTop, mini, miniRadius);
        drawSquareAt(canvas, clusterLeft, clusterTop + mini + miniGap, mini, miniRadius);
        drawSquareAt(canvas, clusterLeft + mini + miniGap,
                clusterTop + mini + miniGap, mini, miniRadius);
    }

    private void drawSquare(Canvas canvas, float cx, float cy, float size, float radius) {
        drawSquareAt(canvas, cx - size * 0.5f, cy - size * 0.5f, size, radius);
    }

    private void drawSquareAt(Canvas canvas, float left, float top, float size, float radius) {
        rect.set(left, top, left + size, top + size);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
