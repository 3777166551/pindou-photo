package com.pindou.app.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.pindou.app.R;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;
import com.pindou.app.bead.StandeeKit;
import com.pindou.app.provider.AppFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 立牌方案 PDF(v2.62):装配说明封面(含剖面示意图)+ 主图图纸页 +
 * 底座图纸页 + 合并材料清单(主图/底座/合计分列)。页面结构复用
 * PdfExporter 的 A4/页脚/清单行距体系,单独成类避免扰动既有 PDF 导出。
 */
public final class StandeePdfExporter {

    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 22;
    private static final int BOM_ROWS_PER_PAGE = 30;

    public static Uri export(Context ctx, Bitmap spriteSheet, Bitmap baseSheet,
                             BeadPattern sprite, StandeeKit kit,
                             String paletteName, boolean mini,
                             String fileName) throws Exception {
        PdfDocument doc = new PdfDocument();
        try {
            int drawW = PAGE_W - 2 * MARGIN;
            int drawH = PAGE_H - 2 * MARGIN;

            // 先算总页数:封面 1 + 主图分页 + 底座 1 + 清单分页
            float scale = drawW / (float) spriteSheet.getWidth();
            int stripH = Math.max(1, Math.round(drawH / scale));
            int spritePages = (spriteSheet.getHeight() + stripH - 1) / stripH;
            List<BeadPattern.UsedColor> merged = kit.mergedUsed();
            int bomPages = (merged.size() + BOM_ROWS_PER_PAGE - 1) / BOM_ROWS_PER_PAGE;
            int total = 1 + spritePages + 1 + bomPages;
            int[] counter = {1};

            coverPage(ctx, doc, sprite, kit, paletteName, mini, counter, total);
            for (int top = 0; top < spriteSheet.getHeight(); top += stripH) {
                int bottom = Math.min(spriteSheet.getHeight(), top + stripH);
                PdfDocument.PageInfo info =
                        new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, counter[0]).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas c = page.getCanvas();
                c.drawColor(Color.WHITE);
                Rect src = new Rect(0, top, spriteSheet.getWidth(), bottom);
                RectF dst = new RectF(MARGIN, MARGIN,
                        MARGIN + drawW, MARGIN + (bottom - top) * scale);
                c.drawBitmap(spriteSheet, src, dst, null);
                footer(ctx, c, counter[0], total);
                doc.finishPage(page);
                counter[0]++;
            }
            sheetPage(ctx, doc, baseSheet, ctx.getString(R.string.pdf_col_base),
                    counter, total);
            for (int start = 0; start < bomPages * BOM_ROWS_PER_PAGE;
                 start += BOM_ROWS_PER_PAGE) {
                bomPage(ctx, doc, kit, merged, start, counter, total);
            }

            File out = new File(ctx.getCacheDir(), fileName);
            FileOutputStream fos = new FileOutputStream(out);
            doc.writeTo(fos);
            fos.close();
            return AppFileProvider.forCacheShare(out);
        } finally {
            doc.close();
        }
    }

    /** 封面:套件统计 + 装配三步 + 侧视剖面示意图 */
    private static void coverPage(Context ctx, PdfDocument doc, BeadPattern sprite,
                                  StandeeKit kit, String paletteName, boolean mini,
                                  int[] counter, int total) {
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, counter[0]).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        c.drawText(ctx.getString(R.string.standee_title), MARGIN, MARGIN + 30,
                textPaint(26, 0xFF232323, true));
        c.drawText(ctx.getString(R.string.pdf_tagline),
                MARGIN, MARGIN + 48, textPaint(10, 0xFF8A8F98, false));
        c.drawLine(MARGIN, MARGIN + 60, PAGE_W - MARGIN, MARGIN + 60, linePaint());

        float cm = mini ? 0.26f : 0.5f;
        Paint label = textPaint(12, 0xFF444444, false);
        BeadColor bc = sprite.palette.get(kit.baseColorIndex);
        String[] lines = {
                String.format(Locale.CHINA,
                        ctx.getString(R.string.fmt_standee_stat_sprite),
                        sprite.cols, sprite.rows, sprite.totalBeads),
                String.format(Locale.CHINA,
                        ctx.getString(R.string.fmt_standee_stat_base),
                        kit.base.cols, kit.base.rows, kit.baseBeads),
                String.format(Locale.CHINA,
                        ctx.getString(R.string.fmt_standee_stat_total),
                        kit.mergedTotal,
                        Math.round(kit.mergedTotal * (mini ? 0.0067f : 0.024f)),
                        ctx.getString(mini ? R.string.bead_mini : R.string.bead_std)),
                String.format(Locale.CHINA,
                        ctx.getString(R.string.fmt_standee_base_color), bc.fullLabel()),
                ctx.getString(R.string.pdf_palette_prefix)
                        + (paletteName == null || paletteName.isEmpty() ? "-" : paletteName),
                ctx.getString(R.string.pdf_date_prefix)
                        + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                        .format(new Date()),
        };
        float y = MARGIN + 92;
        for (String line : lines) {
            c.drawText(line, MARGIN, y, label);
            y += 20;
        }

        // 物理尺寸:主图高 + 底座深(立起来的总高)
        y += 2;
        c.drawText(String.format(Locale.CHINA,
                        ctx.getString(R.string.fmt_standee_assembled_size),
                        sprite.cols * cm, (sprite.rows + kit.baseDepth) * cm),
                MARGIN, y, label);
        y += 24;

        // 装配三步
        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, linePaint());
        y += 22;
        String[] steps = {
                ctx.getString(R.string.standee_step1),
                ctx.getString(R.string.standee_step2),
                ctx.getString(R.string.standee_step3),
        };
        for (String step : steps) {
            c.drawText(step, MARGIN, y, textPaint(11, 0xFF333333, false));
            y += 20;
        }

        // 剖面示意图(下半页居中)
        float maxH = PAGE_H - y - 70;
        if (maxH > 90) {
            drawAssemblyDiagram(c, sprite, kit, bc,
                    PAGE_W / 2f, y + 18, Math.min(PAGE_W - 2 * MARGIN, 320f), maxH - 26);
        }
        footer(ctx, c, counter[0], total);
        doc.finishPage(page);
        counter[0]++;
    }

    /**
     * 装配示意:斜视底座板(插槽从前缘贯通到背排)+ 主图竖立插入。
     * 画的是"斜 45° 俯视"的极简示意——底座平行四边形 + 插槽白带 +
     * 立起的主图(淡灰格纹占位,不逐格上色,印刷可读优先)。
     * public:编辑页立牌弹窗里的示意图复用同一份绘制。
     */
    public static void drawAssemblyDiagram(Canvas c, BeadPattern sprite,
                                           StandeeKit kit, BeadColor baseColor,
                                           float cx, float top, float maxW, float maxH) {
        // 比例:主图高占大头,底座深压扁成斜面
        float unit = Math.min(maxW / (sprite.cols + 6f),
                maxH / (sprite.rows + kit.baseDepth * 0.5f + 4f));
        float spriteW = sprite.cols * unit;
        float spriteH = sprite.rows * unit;
        float baseW = kit.base.cols * unit;
        float depthV = kit.baseDepth * unit * 0.45f;   // 斜视压扁后的深度

        float baseFrontY = top + spriteH + depthV + 14;
        float left = cx - baseW / 2f;

        // 底座平行四边形(前缘在下,向后上收)
        float skew = depthV * 0.55f;
        Path baseP = new Path();
        baseP.moveTo(left, baseFrontY);
        baseP.lineTo(left + baseW, baseFrontY);
        baseP.lineTo(left + baseW + skew, baseFrontY - depthV);
        baseP.lineTo(left + skew, baseFrontY - depthV);
        baseP.close();
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFF000000 | baseColor.rgb);
        c.drawPath(baseP, fill);
        Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(1.4f);
        outline.setColor(0xFF5A5464);
        c.drawPath(baseP, outline);

        // 插槽:前缘中点向后方延伸的白带(宽 = 1 格)
        float slotW = unit;
        float slotCx = left + (kit.slotCol + 0.5f) * unit;
        Path slotP = new Path();
        slotP.moveTo(slotCx - slotW / 2f, baseFrontY);
        slotP.lineTo(slotCx + slotW / 2f, baseFrontY);
        slotP.lineTo(slotCx + slotW / 2f + skew, baseFrontY - depthV);
        slotP.lineTo(slotCx - slotW / 2f + skew, baseFrontY - depthV);
        slotP.close();
        Paint slotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        slotFill.setColor(0xFFFFFFFF);
        c.drawPath(slotP, slotFill);
        c.drawPath(slotP, outline);

        // 主图:竖立在插槽上,淡灰板底 + 细网格线 + 深色外框(示意,不逐格上色)
        float sx = cx - spriteW / 2f;
        float sy = baseFrontY - depthV - spriteH;
        Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
        plate.setColor(0xFFEFEAF6);
        c.drawRect(sx, sy, sx + spriteW, sy + spriteH, plate);
        Paint grid = new Paint();
        grid.setStrokeWidth(0.6f);
        grid.setColor(0x33888888);
        for (int x = 1; x < sprite.cols; x++) {
            float gx = sx + x * unit;
            c.drawLine(gx, sy, gx, sy + spriteH, grid);
        }
        for (int y2 = 1; y2 < sprite.rows; y2++) {
            float gy = sy + y2 * unit;
            c.drawLine(sx, gy, sx + spriteW, gy, grid);
        }
        c.drawRect(sx, sy, sx + spriteW, sy + spriteH, outline);

        // 插入箭头:插槽前方一支横向箭头 + "插入"字样
        Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrow.setStrokeWidth(1.6f);
        arrow.setColor(0xFF5A5464);
        float ay = baseFrontY - depthV * 0.5f;
        float ax0 = slotCx - slotW / 2f + skew - unit * 2.6f;
        float ax1 = slotCx - slotW / 2f + skew - unit * 0.5f;
        c.drawLine(ax0, ay, ax1, ay, arrow);
        c.drawLine(ax1, ay, ax1 - unit * 0.5f, ay - unit * 0.32f, arrow);
        c.drawLine(ax1, ay, ax1 - unit * 0.5f, ay + unit * 0.32f, arrow);
    }

    /** 单页图纸(底座等小图):标题 + 整图适配一页 */
    private static void sheetPage(Context ctx, PdfDocument doc, Bitmap sheet,
                                  String title, int[] counter, int total) {
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, counter[0]).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        c.drawText(title, MARGIN, MARGIN + 24, textPaint(16, 0xFF232323, true));
        float maxW = PAGE_W - 2 * MARGIN;
        float maxH = PAGE_H - 2 * MARGIN - 40;
        float s = Math.min(maxW / sheet.getWidth(), maxH / sheet.getHeight());
        float w = sheet.getWidth() * s;
        float h = sheet.getHeight() * s;
        RectF dst = new RectF(MARGIN + (maxW - w) / 2, MARGIN + 36,
                MARGIN + (maxW - w) / 2 + w, MARGIN + 36 + h);
        c.drawBitmap(sheet, null, dst, null);
        c.drawRect(dst, linePaint());
        footer(ctx, c, counter[0], total);
        doc.finishPage(page);
        counter[0]++;
    }

    /** 合并材料清单:色号 | 主图 | 底座 | 合计 | 占比 */
    private static void bomPage(Context ctx, PdfDocument doc, StandeeKit kit,
                                List<BeadPattern.UsedColor> merged, int start,
                                int[] counter, int total) {
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, counter[0]).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        c.drawText(ctx.getString(R.string.standee_bom_title), MARGIN, MARGIN + 24,
                textPaint(18, 0xFF232323, true));
        Paint head = textPaint(10, 0xFF8A8F98, false);
        c.drawText(ctx.getString(R.string.pdf_col_code), MARGIN + 56, MARGIN + 44, head);
        c.drawText(ctx.getString(R.string.pdf_col_sprite), PAGE_W - MARGIN - 176, MARGIN + 44, head);
        c.drawText(ctx.getString(R.string.pdf_col_base), PAGE_W - MARGIN - 130, MARGIN + 44, head);
        c.drawText(ctx.getString(R.string.pdf_col_total), PAGE_W - MARGIN - 84, MARGIN + 44, head);
        c.drawText(ctx.getString(R.string.pdf_col_pct), PAGE_W - MARGIN - 40, MARGIN + 44, head);
        c.drawLine(MARGIN, MARGIN + 52, PAGE_W - MARGIN, MARGIN + 52, linePaint());

        float y = MARGIN + 74;
        Paint tp = textPaint(11, 0xFF333333, false);
        int end = Math.min(merged.size(), start + BOM_ROWS_PER_PAGE);
        for (int i = start; i < end; i++) {
            BeadPattern.UsedColor uc = merged.get(i);
            int idx = uc.index;
            Paint sw = new Paint(Paint.ANTI_ALIAS_FLAG);
            sw.setColor(0xFF000000 | uc.color.rgb);
            c.drawCircle(MARGIN + 14, y - 4, 9, sw);
            c.drawText(uc.symbol, MARGIN + 14, y - 1,
                    textPaint(9, ColorMath.textColorOn(uc.color.rgb), true));
            c.drawText(uc.color.fullLabel(), MARGIN + 56, y, tp);
            c.drawText(String.valueOf(spriteCount(kit, idx)),
                    PAGE_W - MARGIN - 176, y, tp);
            int baseCnt = kit.base.counts[idx];
            c.drawText(baseCnt > 0 ? String.valueOf(baseCnt) : "-",
                    PAGE_W - MARGIN - 130, y, tp);
            c.drawText(String.valueOf(uc.count), PAGE_W - MARGIN - 84, y, tp);
            float pct = kit.mergedTotal > 0 ? uc.count * 100f / kit.mergedTotal : 0f;
            c.drawText(String.format(Locale.CHINA, "%.1f%%", pct),
                    PAGE_W - MARGIN - 40, y, tp);
            y += 24;
        }
        footer(ctx, c, counter[0], total);
        doc.finishPage(page);
        counter[0]++;
    }

    private static int spriteCount(StandeeKit kit, int idx) {
        return kit.sprite.counts[idx];
    }

    private static void footer(Context ctx, Canvas c, int pageNo, int total) {
        Paint fp = textPaint(9, 0xFF9AA0A6, false);
        String txt = String.format(Locale.CHINA, ctx.getString(R.string.fmt_pdf_page),
                pageNo, total);
        float w = fp.measureText(txt);
        c.drawText(txt, (PAGE_W - w) / 2f, PAGE_H - 12, fp);
    }

    private static Paint textPaint(int sp, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(sp);
        p.setFakeBoldText(bold);
        return p;
    }

    private static Paint linePaint() {
        Paint p = new Paint();
        p.setStrokeWidth(1);
        p.setColor(0xFFDDDDDD);
        return p;
    }

    private StandeePdfExporter() {
    }
}
