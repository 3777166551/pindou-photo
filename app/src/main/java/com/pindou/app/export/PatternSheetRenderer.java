package com.pindou.app.export;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.pindou.app.R;
import com.pindou.app.bead.BeadBrand;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;
import com.pindou.app.bead.PatternEngine;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 渲染可打印的"拼豆图纸"大图:
 * 标题信息 -> 网格(颜色+符号+坐标+拼板分隔线) -> 豆豆颜色清单 -> 页脚。
 */
public final class PatternSheetRenderer {

    public static Bitmap render(android.content.Context ctx, BeadPattern p, String paletteName) {
        int cols = p.cols;
        int rows = p.rows;
        int cell = (int) Math.max(20, Math.min(48, 2800.0 / Math.max(cols, rows)));
        int band = cell;            // 坐标编号带
        int margin = cell;

        int gridW = cols * cell;
        int gridH = rows * cell;
        int pageW = gridW + band + 2 * margin;

        // 图例布局
        int entryH = 96;
        int legendColW = 430;
        int legendCols = Math.max(1, Math.min(6, pageW / legendColW));
        int entries = p.usedColors.size() + (p.emptyCount > 0 ? 1 : 0);
        int legendRows = (int) Math.ceil(entries / (double) legendCols);
        int legendH = entries > 0 ? 130 + legendRows * entryH + 20 : 0;

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

        // 标题
        String date = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA).format(new Date());
        c.drawText(ctx.getString(R.string.sheet_title), margin, margin + 62, titleP);
        String info = String.format(Locale.CHINA,
                ctx.getString(R.string.fmt_sheet_info),
                cols, rows, p.round ? ctx.getString(R.string.sheet_round) : "",
                paletteName, p.totalBeads,
                p.round
                        ? String.format(Locale.CHINA, ctx.getString(R.string.fmt_sheet_dia),
                        cols * 0.5)
                        : String.format(Locale.CHINA,
                        ctx.getString(R.string.fmt_sheet_boards), p.boardsNeeded()))
                + " · " + date;
        c.drawText(info, margin, margin + 118, infoP);

        int gx = margin + band;
        int gy = margin + titleH + band;

        // 颜色格
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int idx = p.cellAt(x, y);
                if (idx < 0) continue;
                cellP.setColor(0xFF000000 | p.palette.get(idx).rgb);
                c.drawRect(gx + x * cell, gy + y * cell,
                        gx + (x + 1) * cell, gy + (y + 1) * cell, cellP);
            }
        }

        // 空格小叉(板外格不画)
        if (p.emptyCount > 0) {
            Paint crossP = new Paint(Paint.ANTI_ALIAS_FLAG);
            crossP.setColor(0xFFCFCFCF);
            crossP.setStyle(Paint.Style.STROKE);
            crossP.setStrokeWidth(Math.max(1f, cell * 0.06f));
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (p.outsideShape(x, y)) continue;
                    if (p.cellAt(x, y) >= 0) continue;
                    float x0 = gx + x * cell + cell * 0.3f;
                    float y0 = gy + y * cell + cell * 0.3f;
                    float x1 = gx + (x + 1) * cell - cell * 0.3f;
                    float y1 = gy + (y + 1) * cell - cell * 0.3f;
                    c.drawLine(x0, y0, x1, y1, crossP);
                    c.drawLine(x1, y0, x0, y1, crossP);
                }
            }
        }

        // 细网格线(圆形板画弦段)
        Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridP.setColor(0x33888888);
        gridP.setStrokeWidth(1f);
        float ccx = gx + gridW / 2f;
        float ccy = gy + gridH / 2f;
        float crad = Math.min(gridW, gridH) / 2f;
        for (int x = 1; x < cols; x++) {
            float lx = gx + x * cell;
            if (p.round) {
                chord(c, gridP, lx, ccy, ccx, crad, true, gy, gy + gridH);
            } else {
                c.drawLine(lx, gy, lx, gy + gridH, gridP);
            }
        }
        for (int y = 1; y < rows; y++) {
            float ly = gy + y * cell;
            if (p.round) {
                chord(c, gridP, ly, ccx, ccy, crad, false, gx, gx + gridW);
            } else {
                c.drawLine(gx, ly, gx + gridW, ly, gridP);
            }
        }

        // 每 29 格拼板分隔线 + 外框
        Paint boardP = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardP.setColor(0xFF9A9086);
        boardP.setStyle(Paint.Style.STROKE);
        boardP.setStrokeWidth(Math.max(2f, cell * 0.1f));
        for (int x = 29; x < cols; x += 29) {
            float lx = gx + x * cell;
            if (p.round) {
                chord(c, boardP, lx, ccy, ccx, crad, true, gy, gy + gridH);
            } else {
                c.drawLine(lx, gy, lx, gy + gridH, boardP);
            }
        }
        for (int y = 29; y < rows; y += 29) {
            float ly = gy + y * cell;
            if (p.round) {
                chord(c, boardP, ly, ccx, ccy, crad, false, gx, gx + gridW);
            } else {
                c.drawLine(gx, ly, gx + gridW, ly, boardP);
            }
        }
        Paint borderP = new Paint(boardP);
        borderP.setStrokeWidth(3f);
        borderP.setColor(0xFF5B534B);
        if (p.round) {
            c.drawCircle(ccx, ccy, crad - 1.5f, borderP);
        } else {
            c.drawRect(gx, gy, gx + gridW, gy + gridH, borderP);
        }

        // 符号
        Paint.FontMetrics sfm = symbolP.getFontMetrics();
        float sdy = -(sfm.ascent + sfm.descent) / 2f;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int idx = p.cellAt(x, y);
                if (idx < 0) continue;
                int rgb = p.palette.get(idx).rgb;
                symbolP.setColor(ColorMath.textColorOn(rgb));
                c.drawText(PatternEngine.symbolFor(idx),
                        gx + (x + 0.5f) * cell, gy + (y + 0.5f) * cell + sdy, symbolP);
            }
        }

        // 坐标编号(顶部/左侧)
        Paint.FontMetrics lfm = labelP.getFontMetrics();
        float ldy = -(lfm.ascent + lfm.descent) / 2f;
        int step = cell >= 28 ? 1 : 5;
        for (int x = 0; x < cols; x += step) {
            c.drawText(String.valueOf(x + 1), gx + (x + 0.5f) * cell,
                    gy - band * 0.4f + ldy, labelP);
        }
        Paint rowLabelP = textPaint((int) (cell * 0.32), 0xFF9A938C, false);
        rowLabelP.setTextAlign(Paint.Align.RIGHT);
        for (int y = 0; y < rows; y += step) {
            c.drawText(String.valueOf(y + 1), gx - cell * 0.2f,
                    gy + (y + 0.5f) * cell + ldy, rowLabelP);
        }

        // 图例
        if (entries > 0) {
            int top = gy + gridH + 60;
            Paint legendTitleP = textPaint(40, 0xFF232323, true);
            c.drawText(ctx.getString(R.string.sheet_bom_title), margin, top + 46, legendTitleP);

            Paint swatchP = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint symSmallP = textPaint(26, 0xFF000000, true);
            symSmallP.setTextAlign(Paint.Align.CENTER);
            Paint nameP = textPaint(32, 0xFF333333, false);
            Paint cntP = textPaint(32, 0xFF8A8178, false);

            for (int i = 0; i < entries; i++) {
                int col = i % legendCols;
                int row = i / legendCols;
                float ex = margin + col * legendColW;
                float ey = top + 90 + row * entryH;

                int rgb;
                String sym;
                String label;
                String count;
                if (i < p.usedColors.size()) {
                    BeadPattern.UsedColor uc = p.usedColors.get(i);
                    rgb = uc.color.rgb;
                    sym = uc.symbol;
                    label = uc.color.fullLabel()
                            + (uc.color.hasOfficialCode() ? ctx.getString(R.string.sheet_official)
                              : " " + BeadBrand.shortTagOf(uc.color.rgb));
                    count = "× " + String.format(Locale.CHINA, "%,d", uc.count);
                } else {
                    rgb = 0xFFFFFF;
                    sym = "×";
                    label = ctx.getString(R.string.sheet_empty);
                    count = "× " + String.format(Locale.CHINA, "%,d", p.emptyCount);
                }
                swatchP.setColor(0xFF000000 | rgb);
                float scx = ex + 34;
                float scy = ey + 34;
                c.drawCircle(scx, scy, 28, swatchP);
                Paint sw = new Paint(Paint.ANTI_ALIAS_FLAG);
                sw.setColor(0xFFAAAAAA);
                sw.setStyle(Paint.Style.STROKE);
                sw.setStrokeWidth(2f);
                c.drawCircle(scx, scy, 28, sw);
                symSmallP.setColor(ColorMath.textColorOn(rgb));
                c.drawText(sym, scx, scy + 9, symSmallP);

                c.drawText(label, ex + 76, ey + 28, nameP);
                c.drawText(count, ex + 76, ey + 68, cntP);
            }
        }

        // 页脚
        Paint footP = textPaint(26, 0xFFB0A79E, false);
        String foot = String.format(Locale.CHINA,
                ctx.getString(R.string.fmt_sheet_footer),
                p.totalBeads, p.usedColors.size(), ctx.getString(R.string.app_name));
        c.drawText(foot, margin, pageH - margin - 30, footP);
        return bmp;
    }

    /** 圆内弦段:vertical=true 画竖线(x 固定),否则画横线(y 固定) */
    private static void chord(Canvas c, Paint p, float fixed, float cx, float cy,
                              float r, boolean vertical, float min, float max) {
        float d = fixed - (vertical ? cx : cy);
        float h2 = r * r - d * d;
        if (h2 <= 0) return;
        float half = (float) Math.sqrt(h2);
        if (vertical) {
            c.drawLine(fixed, Math.max(min, cy - half), fixed, Math.min(max, cy + half), p);
        } else {
            c.drawLine(Math.max(min, cx - half), fixed, Math.min(max, cx + half), fixed, p);
        }
    }

    private static Paint textPaint(int sizePx, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sizePx);
        if (bold) p.setTypeface(Typeface.DEFAULT_BOLD);
        return p;
    }

    private PatternSheetRenderer() {
    }
}
