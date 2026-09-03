package com.pindou.app.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.pindou.app.provider.AppFileProvider;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 导出可打印的 PDF 图纸:把渲染好的大图按宽度缩放到 A4 竖版,
 * 垂直方向自动分页。生成临时文件后用 FileProvider 分享
 * (可直接选"保存到文件"、微信、WPS 等,由用户决定去向)。
 */
public final class PdfExporter {

    /** A4 竖版,单位 pt(72dpi 标准) */
    private static final int PAGE_W = 595;
    private static final int PAGE_H = 842;
    private static final int MARGIN = 22;

    /**
     * @param sheet    PatternSheetRenderer 渲染的大图
     * @param fileName 形如 拼豆图纸_58x58_202608271030.pdf
     * @return 可用于 ACTION_SEND 的 content:// Uri
     */
    public static Uri export(Context ctx, Bitmap sheet, String fileName) throws Exception {
        if (sheet == null || sheet.getWidth() <= 0 || sheet.getHeight() <= 0) {
            throw new Exception("图纸还没有生成");
        }
        PdfDocument doc = new PdfDocument();
        try {
            int drawW = PAGE_W - 2 * MARGIN;
            int drawH = PAGE_H - 2 * MARGIN;
            float scale = drawW / (float) sheet.getWidth();
            int stripH = Math.max(1, Math.round(drawH / scale));

            int pageNo = 1;
            for (int top = 0; top < sheet.getHeight(); top += stripH) {
                int bottom = Math.min(sheet.getHeight(), top + stripH);
                PdfDocument.PageInfo info =
                        new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas c = page.getCanvas();
                c.drawColor(Color.WHITE);
                Rect src = new Rect(0, top, sheet.getWidth(), bottom);
                RectF dst = new RectF(MARGIN, MARGIN,
                        MARGIN + drawW, MARGIN + (bottom - top) * scale);
                c.drawBitmap(sheet, src, dst, null);
                doc.finishPage(page);
                pageNo++;
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

    private PdfExporter() {
    }
}
