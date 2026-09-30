package com.pindou.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import com.pindou.app.R;

/**
 * 萌新引导示意图(v2.62 可发现性修补):手势操作纯文字说不清的部分配一张画。
 * 两张图都是纯 Canvas 程序绘制——零新资产、零新权限,画面里没有文字所以
 * 三语通用;颜色全部来自设计合同 token 表(相机家族页恒定深底,允许页内
 * 固定色,见合同 §10 相机三页注记)。
 *
 * ① VerifyGuideView:拍照验收页用。俯拍照片卡 + 透视四边形 + 四个角点手柄,
 *    右下手柄琥珀高亮并画一个外拖箭头 = "把四个点拖到板的四角"。
 * ② ScanGuideView:识别图纸弹窗用。左右对照——绿 ✓ = 框贴住网格,
 *    红 ✗ = 框把旁边空白也框进去了,一眼看懂 scan_msg 说的"贴住网格"。
 */
public final class GuideDiagrams {

    private GuideDiagrams() {
    }

    /** 拍照验收:四角对齐示意(深底页,配色与 VerifyOverlay 手柄一致) */
    public static class VerifyGuideView extends View {

        private final Paint cardFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cardEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint quadP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint amberP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public VerifyGuideView(Context context) {
            super(context);
            cardFill.setColor(0xFF2E2938);
            cardEdge.setColor(0x33FFFFFF);
            cardEdge.setStyle(Paint.Style.STROKE);
            quadP.setColor(0xD9FFFFFF);
            quadP.setStyle(Paint.Style.STROKE);
            gridP.setColor(0x30FFFFFF);
            gridP.setStrokeWidth(1f);
            dotP.setColor(0xFFFFFFFF);
            dotEdge.setColor(0xFF2A2735);
            dotEdge.setStyle(Paint.Style.STROKE);
            amberP.setColor(0xFFF0B35B);
            amberP.setStyle(Paint.Style.STROKE);
            arrowP.setColor(0xFFF0B35B);
            arrowP.setStyle(Paint.Style.STROKE);
            arrowP.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float dp = getResources().getDisplayMetrics().density;

            // 照片卡(代表拍下来的俯拍照)
            float cardW = Math.min(w * 0.56f, h * 2.1f);
            float cardH = h * 0.84f;
            float cl = (w - cardW) / 2f, ct = (h - cardH) / 2f;
            RectF card = new RectF(cl, ct, cl + cardW, ct + cardH);
            c.drawRoundRect(card, 4 * dp, 4 * dp, cardFill);
            c.drawRoundRect(card, 4 * dp, 4 * dp, cardEdge);

            // 透视四边形:顶边略窄 = 俯拍的轻微透视感
            float inx = cardW * 0.14f, iny = cardH * 0.16f;
            float tlx = cl + inx * 1.45f, trx = cl + cardW - inx * 1.45f;
            float ty = ct + iny, by = ct + cardH - iny;
            float blx = cl + inx * 0.4f, brx = cl + cardW - inx * 0.4f;

            quadP.setStrokeWidth(1.6f * dp);
            path.reset();
            path.moveTo(tlx, ty);
            path.lineTo(trx, ty);
            path.lineTo(brx, by);
            path.lineTo(blx, by);
            path.close();
            c.drawPath(path, quadP);

            // 四边形内网格预览(与真界面的校准网格同语言,纵向线随透视收窄)
            for (int i = 1; i < 5; i++) {
                float u = i / 5f;
                c.drawLine(lerp(tlx, trx, u), ty, lerp(blx, brx, u), by, gridP);
            }
            for (int i = 1; i < 4; i++) {
                float v = i / 4f;
                c.drawLine(lerp(tlx, blx, v), lerp(ty, by, v),
                        lerp(trx, brx, v), lerp(ty, by, v), gridP);
            }

            // 四个角点手柄(与 VerifyOverlay 同款:白底深边)
            float r = 5.5f * dp;
            float[] xs = {tlx, trx, brx, blx};
            float[] ys = {ty, ty, by, by};
            for (int i = 0; i < 4; i++) {
                c.drawCircle(xs[i], ys[i], r, dotP);
                dotEdge.setStrokeWidth(2.2f * dp);
                c.drawCircle(xs[i], ys[i], r, dotEdge);
            }

            // 右下角点:琥珀外圈 + 向外拖的箭头 = "把它拖到板的角上"
            amberP.setStrokeWidth(2f * dp);
            c.drawCircle(brx, by, r + 3.2f * dp, amberP);
            float ax0 = brx + r + 5f * dp, ay0 = by + r + 3.5f * dp;
            float ax1 = ax0 + 9f * dp, ay1 = ay0 + 6f * dp;
            arrowP.setStrokeWidth(2.2f * dp);
            c.drawLine(ax0, ay0, ax1, ay1, arrowP);
            double ang = Math.atan2(ay1 - ay0, ax1 - ax0);
            float head = 3.6f * dp;
            for (int s = -1; s <= 1; s += 2) {
                c.drawLine(ax1, ay1,
                        (float) (ax1 - head * Math.cos(ang + s * 0.45)),
                        (float) (ay1 - head * Math.sin(ang + s * 0.45)), arrowP);
            }
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }

    /** 识别图纸:框选正误对照(浅底弹窗,token 色自适应深浅模式) */
    public static class ScanGuideView extends View {

        private final Paint chartFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint chartEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint boxP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgeP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private int colGood, colBad;

        public ScanGuideView(Context context) {
            super(context);
            chartFill.setColor(getResources().getColor(R.color.colorSurface));
            chartEdge.setColor(getResources().getColor(R.color.colorStroke));
            chartEdge.setStyle(Paint.Style.STROKE);
            gridP.setColor(getResources().getColor(R.color.textSecondary));
            gridP.setAlpha(80);
            gridP.setStrokeWidth(1f);
            boxP.setStyle(Paint.Style.STROKE);
            boxP.setStrokeCap(Paint.Cap.ROUND);
            markP.setColor(0xFFFFFFFF);
            markP.setStyle(Paint.Style.STROKE);
            markP.setStrokeCap(Paint.Cap.ROUND);
            colGood = getResources().getColor(R.color.colorSuccess);
            colBad = getResources().getColor(R.color.colorDanger);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float dp = getResources().getDisplayMetrics().density;

            float marginX = w * 0.06f;
            float gap = 18 * dp;
            float halfW = (w - marginX * 2 - gap) / 2f;
            float padV = h * 0.10f;
            float halfH = h - padV * 2;

            panel(c, marginX, padV, halfW, halfH, true, dp);
            panel(c, marginX + halfW + gap, padV, halfW, halfH, false, dp);
        }

        /** 半边:照片卡 + 网格区 + 对/错选框 + 角标 */
        private void panel(Canvas c, float l, float t, float pw, float ph,
                           boolean good, float dp) {
            // 照片卡(带空白边缘的图纸照片)
            rect.set(l, t, l + pw, t + ph);
            c.drawRoundRect(rect, 3 * dp, 3 * dp, chartFill);
            chartEdge.setStrokeWidth(dp);
            c.drawRoundRect(rect, 3 * dp, 3 * dp, chartEdge);

            // 网格区:占卡的 70%,四周留"空白边"(✗ 示例框的就是这部分)
            float gl = l + pw * 0.15f, gt = t + ph * 0.15f;
            float gr = l + pw * 0.85f, gb = t + ph * 0.85f;
            for (int i = 0; i <= 4; i++) {
                float u = i / 4f;
                c.drawLine(lerp(gl, gr, u), gt, lerp(gl, gr, u), gb, gridP);
                c.drawLine(gl, lerp(gt, gb, u), gr, lerp(gt, gb, u), gridP);
            }

            // 选框:✓ 贴住网格;✗ 连空白一起框进去
            boxP.setColor(good ? colGood : colBad);
            boxP.setStrokeWidth(2.4f * dp);
            rect.set(good ? gl : l + 1.5f * dp,
                    good ? gt : t + 1.5f * dp,
                    good ? gr : l + pw - 1.5f * dp,
                    good ? gb : t + ph - 1.5f * dp);
            c.drawRoundRect(rect, 2.5f * dp, 2.5f * dp, boxP);

            // 角标圆:绿 ✓ / 红 ✗(画面无文字,符号即语言)
            float br = 6.5f * dp;
            float bx = good ? gr : l + pw;
            float by = good ? gt : t;
            badgeP.setColor(good ? colGood : colBad);
            c.drawCircle(bx, by, br, badgeP);
            markP.setStrokeWidth(1.8f * dp);
            if (good) {
                c.drawLine(bx - 3f * dp, by + 0.2f * dp, bx - 0.8f * dp, by + 2.8f * dp, markP);
                c.drawLine(bx - 0.8f * dp, by + 2.8f * dp, bx + 3.2f * dp, by - 2.6f * dp, markP);
            } else {
                c.drawLine(bx - 2.6f * dp, by - 2.6f * dp, bx + 2.6f * dp, by + 2.6f * dp, markP);
                c.drawLine(bx + 2.6f * dp, by - 2.6f * dp, bx - 2.6f * dp, by + 2.6f * dp, markP);
            }
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }
}
