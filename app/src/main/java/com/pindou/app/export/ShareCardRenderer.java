package com.pindou.app.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.R;

/**
 * 贴纸风分享长图(v2.41):效果图 + 关键数据 + 品牌落款。
 * 奶油底 + 墨描边白卡 + 糖果粉点缀,发小红书/朋友圈用;纯本地渲染。
 * 全图按 1080px 宽的像素坐标直接排版,不跟屏幕密度走。
 */
public final class ShareCardRenderer {

    private static final int INK = 0xFF40354E;
    private static final int BG = 0xFFFFF6ED;
    private static final int CARD = 0xFFFEFEFE;
    private static final int PINK = 0xFFFF6E9C;
    private static final int SUB = 0xFF9A8FA6;

    private ShareCardRenderer() {
    }

    public static Bitmap render(Context ctx, BeadPattern p, String paletteName,
                                boolean mini, float hours, String difficulty) {
        int W = 1080;
        int pad = 56;
        float cm = mini ? 0.26f : 0.5f;

        Bitmap effect = EffectRenderer.render(p);
        int effW = W - pad * 2 - 48;          // 效果卡内边距 24*2
        int effH = Math.round(effect.getHeight() * (effW / (float) effect.getWidth()));

        int titleH = 190;
        int effCardH = effH + 48;
        int gap = 36;
        int statRows = 5;
        int statCardH = statRows * 96 + 40;
        int brandH = 150;
        int H = pad + titleH + effCardH + gap + statCardH + gap + brandH;

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(BG);

        // 拼板点阵底纹:淡淡的孔点,呼应 APP 内底
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFFF0E0D2);
        for (int y = 40; y < H; y += 72) {
            for (int x = 40; x < W; x += 72) {
                c.drawCircle(x, y, 5, dot);
            }
        }

        Paint inkFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);

        // 标题:APP 名 + 规格,糖果粉短下划线
        Paint titleP = text(Typeface.BOLD, 62, 0xFF3A3050);
        String title = String.format(Locale_DEFAULT(),
                ctx.getString(R.string.fmt_card_title),
                ctx.getString(R.string.app_name), p.cols, p.rows);
        c.drawText(title, pad, pad + 58, titleP);
        inkFill.setColor(PINK);
        c.drawRoundRect(pad + 6, pad + 84, pad + 250, pad + 96, 6, 6, inkFill);

        // 效果卡:白底 + 墨描边 + 墨色硬投影
        float ex = pad, ey = pad + titleH - 40;
        inkFill.setColor(INK);
        c.drawRoundRect(ex + 10, ey + 12, ex + W - pad * 2 + 10, ey + effCardH + 12,
                44, 44, inkFill);
        inkFill.setColor(CARD);
        c.drawRoundRect(ex, ey, ex + W - pad * 2, ey + effCardH, 44, 44, inkFill);
        stroke.setColor(INK);
        stroke.setStrokeWidth(6);
        c.drawRoundRect(ex + 3, ey + 3, ex + W - pad * 2 - 3, ey + effCardH - 3,
                41, 41, stroke);
        float effPad = 24;
        RectF dst = new RectF(ex + effPad, ey + effPad,
                ex + effPad + effW, ey + effPad + effH);
        c.drawBitmap(effect, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));

        // 数据卡
        float sx = pad, sy = ey + effCardH + gap;
        inkFill.setColor(INK);
        c.drawRoundRect(sx + 10, sy + 12, sx + W - pad * 2 + 10, sy + statCardH + 12,
                44, 44, inkFill);
        inkFill.setColor(CARD);
        c.drawRoundRect(sx, sy, sx + W - pad * 2, sy + statCardH, 44, 44, inkFill);
        stroke.setStrokeWidth(6);
        c.drawRoundRect(sx + 3, sy + 3, sx + W - pad * 2 - 3, sy + statCardH - 3,
                41, 41, stroke);

        Paint labelP = text(Typeface.NORMAL, 34, SUB);
        Paint valueP = text(Typeface.BOLD, 40, 0xFF3A3050);
        String[] labels = {
                ctx.getString(R.string.card_size),
                ctx.getString(R.string.card_beads),
                ctx.getString(R.string.card_colors),
                ctx.getString(R.string.card_boards),
                ctx.getString(R.string.card_time),
        };
        String[] values = {
                String.format(Locale_DEFAULT(), ctx.getString(R.string.fmt_card_cm),
                        p.cols * cm, p.rows * cm),
                String.format(Locale_DEFAULT(), ctx.getString(R.string.fmt_card_beads),
                        p.totalBeads),
                String.format(Locale_DEFAULT(), ctx.getString(R.string.fmt_card_colors),
                        p.usedColors.size()),
                String.format(Locale_DEFAULT(), ctx.getString(R.string.fmt_card_boards),
                        p.boardsNeeded()),
                String.format(Locale_DEFAULT(), ctx.getString(R.string.fmt_card_time),
                        hours, difficulty),
        };
        float rowY = sy + 72;
        for (int i = 0; i < labels.length; i++) {
            c.drawText(labels[i], sx + 40, rowY, labelP);
            c.drawText(values[i], sx + W - pad * 2 - 40
                    - valueP.measureText(values[i]), rowY, valueP);
            rowY += 96;
        }

        // 品牌落款
        Paint brandP = text(Typeface.NORMAL, 36, SUB);
        String brand = ctx.getString(R.string.card_brand);
        c.drawText(brand, (W - brandP.measureText(brand)) / 2f, H - pad + 34, brandP);
        Paint cand = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] candy = {PINK, 0xFF35C98E, 0xFFFFCF56, 0xFFA78BFA, 0xFF56C2F7};
        for (int i = 0; i < candy.length; i++) {
            cand.setColor(candy[i]);
            c.drawCircle(W / 2f - (candy.length - 1) * 18f + i * 36f, H - pad - 40, 9, cand);
        }

        effect.recycle();
        return bmp;
    }

    /** 分享卡文字没有多语言数字差异,统一用默认 Locale */
    private static java.util.Locale Locale_DEFAULT() {
        return java.util.Locale.getDefault();
    }

    private static Paint text(int style, int px, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, style));
        p.setTextSize(px);
        p.setColor(color);
        return p;
    }
}
