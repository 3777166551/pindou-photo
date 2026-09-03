package com.pindou.app.export;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;

/** 渲染高清"效果图":拼板底 + 一颗颗圆豆 */
public final class EffectRenderer {

    public static Bitmap render(BeadPattern p) {
        int cols = p.cols;
        int rows = p.rows;
        int cell = (int) Math.max(8, Math.min(64, 3600.0 / Math.max(cols, rows)));
        int m = cell;
        int w = cols * cell + 2 * m;
        int h = rows * cell + 2 * m;

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xFFF0EAE2);
        if (p.round) {
            float rad = Math.min(w, h) / 2f - 2;
            canvas.drawCircle(w / 2f, h / 2f, rad, bg);
        } else {
            canvas.drawRoundRect(0, 0, w, h, m * 0.9f, m * 0.9f, bg);
        }

        Paint peg = new Paint(Paint.ANTI_ALIAS_FLAG);
        peg.setColor(0xFFD8D2C9);

        Paint bead = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        float ringW = Math.max(1f, cell * 0.06f);
        ring.setStrokeWidth(ringW);

        Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        gloss.setColor(0x46FFFFFF);

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (p.outsideShape(x, y)) continue;
                int idx = p.cellAt(x, y);
                float cx = m + (x + 0.5f) * cell;
                float cy = m + (y + 0.5f) * cell;
                if (idx < 0) {
                    canvas.drawCircle(cx, cy, cell * 0.15f, peg);
                    continue;
                }
                int rgb = p.palette.get(idx).rgb;
                bead.setColor(0xFF000000 | rgb);
                canvas.drawCircle(cx, cy, cell * 0.46f, bead);
                ring.setColor(0xFF000000 | ColorMath.darken(rgb, 0.72f));
                canvas.drawCircle(cx, cy, cell * 0.46f - ringW * 0.5f, ring);
                if (cell >= 20) {
                    canvas.drawCircle(cx - cell * 0.14f, cy - cell * 0.16f, cell * 0.11f, gloss);
                }
            }
        }
        return bmp;
    }

    private EffectRenderer() {
    }
}
