package com.pindou.app.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;
import com.pindou.app.provider.AppFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 可打印 PDF 图纸,页面结构:
 *   1. 封面页:标题、尺寸/用量/拼板等统计、整图缩略预览
 *   2. 材料清单页:逐色色块 + 色号 + 用量 + 占比(超过 30 种自动分页)
 *   3. 图纸页:渲染好的大图按宽度缩放到 A4 竖版,垂直自动分页
 * 每页底部都有"第 X 页 / 共 N 页"页码导航。
 * 生成临时文件后用 FileProvider 分享
 * (可直接选"保存到文件"、微信、WPS 等,由用户决定去向)。
 */
public final class PdfExporter {

    /** A4 竖版,单位 pt(72dpi 标准) */
    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 22;
    /** 材料清单页每页行数 */
    private static final int BOM_ROWS_PER_PAGE = 30;

    /**
     * @param sheet       PatternSheetRenderer 渲染的大图
     * @param p           图纸数据(封面统计与材料清单用)
     * @param paletteName 色板名(封面展示)
     * @param fileName    形如 拼豆图纸_58x58_202608271030.pdf
     * @return 可用于 ACTION_SEND 的 content:// Uri
     */
    public static Uri export(Context ctx, Bitmap sheet, BeadPattern p,
                             String paletteName, String fileName) throws Exception {
        if (sheet == null || sheet.getWidth() <= 0 || sheet.getHeight() <= 0) {
            throw new Exception("图纸还没有生成");
        }
        PdfDocument doc = new PdfDocument();
        try {
            int drawW = PAGE_W - 2 * MARGIN;
            int drawH = PAGE_H - 2 * MARGIN;
            float scale = drawW / (float) sheet.getWidth();
            int stripH = Math.max(1, Math.round(drawH / scale));
            int sheetPages = (sheet.getHeight() + stripH - 1) / stripH;
            int bomPages = (p == null || p.usedColors.isEmpty()) ? 0
                    : (p.usedColors.size() + BOM_ROWS_PER_PAGE - 1) / BOM_ROWS_PER_PAGE;
            int total = 1 + bomPages + sheetPages;
            int[] counter = {1};

            coverPage(doc, sheet, p, paletteName, counter, total);
            for (int start = 0; start < bomPages * BOM_ROWS_PER_PAGE;
                 start += BOM_ROWS_PER_PAGE) {
                bomPage(doc, p, start, counter, total);
            }
            for (int top = 0; top < sheet.getHeight(); top += stripH) {
                int bottom = Math.min(sheet.getHeight(), top + stripH);
                PdfDocument.PageInfo info =
                        new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, counter[0]).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas c = page.getCanvas();
                c.drawColor(Color.WHITE);
                Rect src = new Rect(0, top, sheet.getWidth(), bottom);
                RectF dst = new RectF(MARGIN, MARGIN,
                        MARGIN + drawW, MARGIN + (bottom - top) * scale);
                c.drawBitmap(sheet, src, dst, null);
                footer(c, counter[0], total);
                doc.finishPage(page);
                counter[0]++;
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

    /** 封面页:标题 + 统计 + 整图缩略预览 */
    private static void coverPage(PdfDocument doc, Bitmap sheet, BeadPattern p,
                                  String paletteName, int[] counter, int total) {
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, counter[0]).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        c.drawText("照片变拼豆 · 拼豆图纸", MARGIN, MARGIN + 30,
                textPaint(26, 0xFF232323, true));
        c.drawText("PindouPhoto · 完全免费 · 无广告 · AGPL-3.0 开源",
                MARGIN, MARGIN + 48, textPaint(10, 0xFF8A8F98, false));
        c.drawLine(MARGIN, MARGIN + 60, PAGE_W - MARGIN, MARGIN + 60, linePaint());

        Paint label = textPaint(12, 0xFF444444, false);
        float y = MARGIN + 92;
        if (p != null) {
            String[] lines = {
                    String.format(Locale.CHINA, "图纸尺寸:%d × %d 格", p.cols, p.rows),
                    String.format(Locale.CHINA, "总用豆:%,d 颗 · 颜色 %d 种",
                            p.totalBeads, p.usedColors.size()),
                    String.format(Locale.CHINA, "29×29 拼板:%d 块 · 成品约 %.0f × %.0f cm",
                            p.boardsNeeded(), p.cols * 0.5, p.rows * 0.5),
                    String.format(Locale.CHINA, "约重 %,d g(标准 5mm 豆)",
                            Math.round(p.totalBeads * 0.024f)),
                    "色板:" + (paletteName == null || paletteName.isEmpty() ? "-" : paletteName),
                    "生成日期:" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                            .format(new Date()),
            };
            for (String line : lines) {
                c.drawText(line, MARGIN, y, label);
                y += 20;
            }
            if (p.emptyCount > 0) {
                c.drawText(String.format(Locale.CHINA, "空格 %,d 格(不摆放)",
                        p.emptyCount), MARGIN, y, label);
                y += 20;
            }
        }

        float maxW = PAGE_W - 2 * MARGIN;
        float maxH = PAGE_H - y - 90;
        if (maxH > 60) {
            float s = Math.min(maxW / sheet.getWidth(), maxH / sheet.getHeight());
            float w = sheet.getWidth() * s;
            float h = sheet.getHeight() * s;
            RectF dst = new RectF(MARGIN + (maxW - w) / 2, y + 14,
                    MARGIN + (maxW - w) / 2 + w, y + 14 + h);
            c.drawBitmap(sheet, null, dst, null);
            Paint border = linePaint();
            c.drawRect(dst, border);
        }
        footer(c, counter[0], total);
        doc.finishPage(page);
        counter[0]++;
    }

    /** 材料清单页:逐色色块/色号/用量/占比 */
    private static void bomPage(PdfDocument doc, BeadPattern p, int start,
                                int[] counter, int total) {
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, counter[0]).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        c.drawText("材料清单(按用量排序)", MARGIN, MARGIN + 24, textPaint(18, 0xFF232323, true));
        Paint head = textPaint(10, 0xFF8A8F98, false);
        c.drawText("色号 / 名称", MARGIN + 56, MARGIN + 44, head);
        c.drawText("用量", PAGE_W - MARGIN - 130, MARGIN + 44, head);
        c.drawText("占比", PAGE_W - MARGIN - 46, MARGIN + 44, head);
        c.drawLine(MARGIN, MARGIN + 52, PAGE_W - MARGIN, MARGIN + 52, linePaint());

        float y = MARGIN + 74;
        Paint tp = textPaint(11, 0xFF333333, false);
        int end = Math.min(p.usedColors.size(), start + BOM_ROWS_PER_PAGE);
        for (int i = start; i < end; i++) {
            BeadPattern.UsedColor uc = p.usedColors.get(i);
            Paint sw = new Paint(Paint.ANTI_ALIAS_FLAG);
            sw.setColor(0xFF000000 | uc.color.rgb);
            c.drawCircle(MARGIN + 14, y - 4, 9, sw);
            c.drawText(uc.symbol, MARGIN + 14, y - 1,
                    textPaint(9, ColorMath.textColorOn(uc.color.rgb), true));
            c.drawText(uc.color.fullLabel(), MARGIN + 56, y, tp);
            c.drawText(String.format(Locale.CHINA, "%,d 颗", uc.count),
                    PAGE_W - MARGIN - 130, y, tp);
            float pct = p.totalBeads > 0 ? uc.count * 100f / p.totalBeads : 0f;
            c.drawText(String.format(Locale.CHINA, "%.1f%%", pct),
                    PAGE_W - MARGIN - 46, y, tp);
            y += 24;
        }
        footer(c, counter[0], total);
        doc.finishPage(page);
        counter[0]++;
    }

    private static void footer(Canvas c, int pageNo, int total) {
        Paint fp = textPaint(9, 0xFF9AA0A6, false);
        String txt = String.format(Locale.CHINA, "第 %d 页 / 共 %d 页", pageNo, total);
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

    private PdfExporter() {
    }
}
