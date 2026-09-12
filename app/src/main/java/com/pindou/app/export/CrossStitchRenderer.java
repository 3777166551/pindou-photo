package com.pindou.app.export;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.pindou.app.R;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;
import com.pindou.app.bead.PatternEngine;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 十字绣图纸导出:把拼豆图纸按 CIEDE2000 就近映射到 DMC 绣线色号
 * (映射逻辑见 DmcMapper),出一张可打印的绣图(网格 + 符号 +
 * 坐标 + DMC 色号清单)。布料格数 = 图纸行列;
 * 14ct 布成品宽高 = 格数 / 14 * 2.54cm。
 * 符号体系沿用拼豆图纸的符号表,清单里给 DMC 色号 + 支数。
 */
public final class CrossStitchRenderer {

    public static Bitmap render(android.content.Context ctx, BeadPattern p, String paletteName) {
        int cols = p.cols;
        int rows = p.rows;
        int cell = (int) Math.max(20, Math.min(48, 2800.0 / Math.max(cols, rows)));
        int band = cell;
        int margin = cell;

        int gridW = cols * cell;
        int gridH = rows * cell;
        int pageW = gridW + band + 2 * margin;

        DmcMapper.DmcMatch[] matches = DmcMapper.mapColors(p);

        // 图例:DMC 色号 + 名称 + 针数
        int entryH = 96;
        int legendColW = 480;
        int legendCols = Math.max(1, Math.min(5, pageW / legendColW));
        int legendRows = (int) Math.ceil(matches.length / (double) legendCols);
        int legendH = matches.length > 0 ? 130 + legendRows * entryH + 20 : 0;

        int titleH = 180;
        int footerH = 100;
        int pageH = margin + titleH + band + gridH + 60 + legendH + footerH + margin;

        Bitmap bmp = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);

        Paint titleP = textPaint(58, 0xFF232323, true);
        Paint infoP = textPaint(30, 0xFF8A8178, false);
        Paint labelP = textPaint((int) (cell * 0.32), 0xFF9A938C, false);
        labelP.setTextAlign(Paint.Align.CENTER);
        Paint symbolP = textPaint((int) (cell * 0.42), 0xFF000000, true);
        symbolP.setTextAlign(Paint.Align.CENTER);
        Paint cellP = new Paint();
        Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridP.setColor(0x33888888);
        gridP.setStrokeWidth(1f);
        Paint boldGridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        boldGridP.setColor(0xFF9A9086);
        boldGridP.setStrokeWidth(Math.max(2f, cell * 0.1f));

        // 标题
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(new Date());
        c.drawText(ctx.getString(R.string.cross_title), margin, margin + 62, titleP);
        float wCm = cols / 14f * 2.54f;
        float hCm = rows / 14f * 2.54f;
        int stitches = p.totalBeads;
        String info = String.format(Locale.CHINA,
                ctx.getString(R.string.fmt_cross_info),
                cols, rows, stitches, matches.length, wCm, hCm)
                + " · " + paletteName + " · " + date;
        c.drawText(info, margin, margin + 118, infoP);

        int gx0 = margin + band;
        int gy0 = margin + titleH + band;

        // 格子底色(就近 DMC 的近似色,直观但不取代色号)
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int idx = p.cellAt(x, y);
                if (idx < 0) continue;
                int dmc = dmcFor(matches, idx);
                cellP.setColor(DmcTable.RGBS[dmc]);
                c.drawRect(gx0 + x * cell, gy0 + y * cell,
                        gx0 + (x + 1) * cell, gy0 + (y + 1) * cell, cellP);
            }
        }
        // 网格线 + 每 10 格加粗(绣图惯例)
        for (int x = 0; x <= cols; x++) {
            c.drawLine(gx0 + x * cell, gy0, gx0 + x * cell, gy0 + gridH,
                    x % 10 == 0 ? boldGridP : gridP);
        }
        for (int y = 0; y <= rows; y++) {
            c.drawLine(gx0, gy0 + y * cell, gx0 + gridW, gy0 + y * cell,
                    y % 10 == 0 ? boldGridP : gridP);
        }
        // 坐标编号
        for (int x = 0; x < cols; x += 10) {
            c.drawText(String.valueOf(x + 1), gx0 + (x + 0.5f) * cell,
                    gy0 - 8, labelP);
        }
        for (int y = 0; y < rows; y += 10) {
            c.drawText(String.valueOf(y + 1), gx0 - 10,
                    gy0 + (y + 0.5f) * cell + labelP.getTextSize() * 0.35f, labelP);
        }
        // 符号
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int idx = p.cellAt(x, y);
                if (idx < 0) continue;
                Paint.FontMetrics fm = symbolP.getFontMetrics();
                float dy = -(fm.ascent + fm.descent) / 2f;
                c.drawText(PatternEngine.symbolFor(idx),
                        gx0 + (x + 0.5f) * cell, gy0 + (y + 0.5f) * cell + dy, symbolP);
            }
        }

        // 图例
        if (matches.length > 0) {
            int ly = gy0 + gridH + 60;
            c.drawText(ctx.getString(R.string.cross_legend), margin, ly + 40, titleP);
            Paint swP = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint codeP = textPaint(28, 0xFF232323, true);
            Paint nameP = textPaint(24, 0xFF6E655C, false);
            for (int i = 0; i < matches.length; i++) {
                DmcMapper.DmcMatch m = matches[i];
                int cx = margin + (i % legendCols) * legendColW;
                int cy = ly + 70 + (i / legendCols) * entryH;
                swP.setColor(DmcTable.RGBS[m.dmc]);
                c.drawRect(cx, cy, cx + 64, cy + 64, swP);
                swP.setColor(0x40000000);
                swP.setStyle(Paint.Style.STROKE);
                swP.setStrokeWidth(1.5f);
                c.drawRect(cx, cy, cx + 64, cy + 64, swP);
                swP.setStyle(Paint.Style.FILL);
                String sym = PatternEngine.symbolFor(m.paletteIndex);
                Paint.FontMetrics fm = symbolP.getFontMetrics();
                float dy = -(fm.ascent + fm.descent) / 2f;
                symbolP.setColor(ColorMath.textColorOn(DmcTable.RGBS[m.dmc]));
                c.drawText(sym, cx + 32, cy + 32 + dy, symbolP);
                c.drawText("DMC " + DmcTable.CODES[m.dmc], cx + 80, cy + 26, codeP);
                c.drawText(DmcTable.NAMES[m.dmc] + " · " + m.count
                        + ctx.getString(R.string.cross_stitches), cx + 80, cy + 56, nameP);
            }
        }

        // 页脚
        Paint footP = textPaint(26, 0xFF9A8FA6, false);
        c.drawText(ctx.getString(R.string.cross_footer), margin,
                pageH - margin * 0.6f, footP);
        return bmp;
    }

    private static int dmcFor(DmcMapper.DmcMatch[] matches, int paletteIndex) {
        for (DmcMapper.DmcMatch m : matches) {
            if (m.paletteIndex == paletteIndex) return m.dmc;
        }
        return 0;
    }

    private static Paint textPaint(int sp, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sp);
        p.setFakeBoldText(bold);
        p.setTypeface(bold ? Typeface.SANS_SERIF : Typeface.DEFAULT);
        return p;
    }

    private CrossStitchRenderer() {
    }
}
