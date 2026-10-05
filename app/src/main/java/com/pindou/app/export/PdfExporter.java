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

import com.pindou.app.R;
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
 *   3. 图纸页:渲染好的大图按宽度缩放到 A4,垂直自动分页
 * 每页底部都有"第 X 页 / 共 N 页"页码导航。
 * 生成临时文件后用 FileProvider 分享
 * (可直接选"保存到文件"、微信、WPS 等,由用户决定去向)。
 * landscape=true 时用 A4 横版(格内色号版用:横放每格更大,色号更清晰)。
 */
public final class PdfExporter {

    /** A4 竖版,单位 pt(72dpi 标准) */
    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 22;
    /** 材料清单页每页行数 */
    private static final int BOM_ROWS_PER_PAGE = 30;

    public static Uri export(Context ctx, Bitmap sheet, BeadPattern p,
                             String paletteName, String fileName) throws Exception {
        return export(ctx, sheet, p, paletteName, fileName, false, false);
    }

    public static Uri export(Context ctx, Bitmap sheet, BeadPattern p,
                             String paletteName, String fileName, boolean mini) throws Exception {
        return export(ctx, sheet, p, paletteName, fileName, mini, false);
    }

    /**
     * @param sheet       PatternSheetRenderer 渲染的大图
     * @param p           图纸数据(封面统计与材料清单用)
     * @param paletteName 色板名(封面展示)
     * @param fileName    形如 拼豆图纸_58x58_202608271030.pdf
     * @param mini        true = 按迷你豆 2.6mm 折算封面尺寸/克重
     * @param landscape   true = A4 横版(格内色号版:横放每格更大)
     * @return 可用于 ACTION_SEND 的 content:// Uri
     */
    public static Uri export(Context ctx, Bitmap sheet, BeadPattern p,
                             String paletteName, String fileName, boolean mini,
                             boolean landscape) throws Exception {
        if (sheet == null || sheet.getWidth() <= 0 || sheet.getHeight() <= 0) {
            throw new Exception(ctx.getString(R.string.err_no_pattern));
        }
        final int pw = landscape ? PAGE_H : PAGE_W;
        final int ph = landscape ? PAGE_W : PAGE_H;
        PdfDocument doc = new PdfDocument();
        try {
            int drawW = pw - 2 * MARGIN;
            int drawH = ph - 2 * MARGIN;
            float scale = drawW / (float) sheet.getWidth();
            int stripH = Math.max(1, Math.round(drawH / scale));
            int sheetPages = (sheet.getHeight() + stripH - 1) / stripH;
            // 材料清单分页按页面高度自适应(横版矮,每页行数变少)
            int bomRowsPerPage = Math.max(1, (ph - 2 * MARGIN - 74) / 24);
            int bomPages = (p == null || p.usedColors.isEmpty()) ? 0
                    : (p.usedColors.size() + bomRowsPerPage - 1) / bomRowsPerPage;
            int total = 1 + bomPages + sheetPages;
            int[] counter = {1};

            coverPage(ctx, doc, sheet, p, paletteName, counter, total, mini, pw, ph);
            for (int start = 0; start < bomPages * bomRowsPerPage;
                 start += bomRowsPerPage) {
                bomPage(ctx, doc, p, start, counter, total, bomRowsPerPage, pw, ph);
            }
            for (int top = 0; top < sheet.getHeight(); top += stripH) {
                int bottom = Math.min(sheet.getHeight(), top + stripH);
                PdfDocument.PageInfo info =
                        new PdfDocument.PageInfo.Builder(pw, ph, counter[0]).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas c = page.getCanvas();
                c.drawColor(Color.WHITE);
                Rect src = new Rect(0, top, sheet.getWidth(), bottom);
                RectF dst = new RectF(MARGIN, MARGIN,
                        MARGIN + drawW, MARGIN + (bottom - top) * scale);
                c.drawBitmap(sheet, src, dst, null);
                footer(ctx, c, counter[0], total, ph, pw);
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
    private static void coverPage(Context ctx, PdfDocument doc, Bitmap sheet, BeadPattern p,
                                  String paletteName, int[] counter, int total, boolean mini,
                                  int pw, int ph) {
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(pw, ph, counter[0]).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        c.drawText(ctx.getString(R.string.pdf_header_fmt,
                        ctx.getString(R.string.app_name)), MARGIN, MARGIN + 30,
                textPaint(26, 0xFF232323, true));
        c.drawText(ctx.getString(R.string.pdf_tagline),
                MARGIN, MARGIN + 48, textPaint(10, 0xFF8A8F98, false));
        c.drawLine(MARGIN, MARGIN + 60, pw - MARGIN, MARGIN + 60, linePaint());

        Paint label = textPaint(12, 0xFF444444, false);
        float cm = mini ? 0.26f : 0.5f;
        float y = MARGIN + 92;
        if (p != null) {
            String[] lines = {
                    String.format(Locale.CHINA, ctx.getString(R.string.fmt_pdf_size),
                            p.cols, p.rows),
                    String.format(Locale.CHINA, ctx.getString(R.string.fmt_pdf_total),
                            p.totalBeads, p.usedColors.size()),
                    String.format(Locale.CHINA, ctx.getString(R.string.fmt_pdf_boards),
                            p.boardsNeeded(), p.cols * cm, p.rows * cm),
                    String.format(Locale.CHINA, ctx.getString(R.string.fmt_pdf_weight),
                            Math.round(p.totalBeads * (mini ? 0.0067f : 0.024f)),
                            ctx.getString(mini ? R.string.bead_mini : R.string.bead_std)),
                    ctx.getString(R.string.pdf_palette_prefix)
                            + (paletteName == null || paletteName.isEmpty() ? "-" : paletteName),
                    ctx.getString(R.string.pdf_date_prefix)
                            + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                            .format(new Date()),
            };
            for (String line : lines) {
                c.drawText(line, MARGIN, y, label);
                y += 20;
            }
            if (p.emptyCount > 0) {
                c.drawText(String.format(Locale.CHINA, ctx.getString(R.string.fmt_pdf_empty),
                        p.emptyCount), MARGIN, y, label);
                y += 20;
            }
        }

        float maxW = pw - 2 * MARGIN;
        float maxH = ph - y - 90;
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
        footer(ctx, c, counter[0], total, ph, pw);
        doc.finishPage(page);
        counter[0]++;
    }

    /** 材料清单页:逐色色块/色号/用量/占比(rowsPerPage 按页面高度自适应) */
    private static void bomPage(Context ctx, PdfDocument doc, BeadPattern p, int start,
                                int[] counter, int total, int rowsPerPage, int pw, int ph) {
        PdfDocument.PageInfo info =
                new PdfDocument.PageInfo.Builder(pw, ph, counter[0]).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas c = page.getCanvas();
        c.drawColor(Color.WHITE);
        c.drawText(ctx.getString(R.string.pdf_bom_title), MARGIN, MARGIN + 24,
                textPaint(18, 0xFF232323, true));
        Paint head = textPaint(10, 0xFF8A8F98, false);
        c.drawText(ctx.getString(R.string.pdf_col_code), MARGIN + 56, MARGIN + 44, head);
        c.drawText(ctx.getString(R.string.pdf_col_qty), pw - MARGIN - 130, MARGIN + 44, head);
        c.drawText(ctx.getString(R.string.pdf_col_pct), pw - MARGIN - 46, MARGIN + 44, head);
        c.drawLine(MARGIN, MARGIN + 52, pw - MARGIN, MARGIN + 52, linePaint());

        float y = MARGIN + 74;
        Paint tp = textPaint(11, 0xFF333333, false);
        int end = Math.min(p.usedColors.size(), start + rowsPerPage);
        for (int i = start; i < end; i++) {
            BeadPattern.UsedColor uc = p.usedColors.get(i);
            Paint sw = new Paint(Paint.ANTI_ALIAS_FLAG);
            sw.setColor(0xFF000000 | uc.color.rgb);
            c.drawCircle(MARGIN + 14, y - 4, 9, sw);
            c.drawText(uc.symbol, MARGIN + 14, y - 1,
                    textPaint(9, ColorMath.textColorOn(uc.color.rgb), true));
            c.drawText(uc.color.fullLabel(), MARGIN + 56, y, tp);
            c.drawText(String.format(Locale.CHINA, ctx.getString(R.string.fmt_qty_beads),
                    uc.count), pw - MARGIN - 130, y, tp);
            float pct = p.totalBeads > 0 ? uc.count * 100f / p.totalBeads : 0f;
            c.drawText(String.format(Locale.CHINA, "%.1f%%", pct),
                    pw - MARGIN - 46, y, tp);
            y += 24;
        }
        footer(ctx, c, counter[0], total, ph, pw);
        doc.finishPage(page);
        counter[0]++;
    }

    private static void footer(Context ctx, Canvas c, int pageNo, int total, int ph, int pw) {
        Paint fp = textPaint(9, 0xFF9AA0A6, false);
        String txt = String.format(Locale.CHINA, ctx.getString(R.string.fmt_pdf_page),
                pageNo, total);
        float w = fp.measureText(txt);
        c.drawText(txt, (pw - w) / 2f, ph - 12, fp);
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
