package com.pindou.app.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 拼完庆祝(v2.41):深色纱罩 -> 熨斗扫过(拖出高光) -> 贴纸"完成"弹跳
 * + 糖果纸屑。纯 Canvas + ValueAnimator 零依赖,点一下跳过。
 * 时序总长 ~2.1s;t = 归一化进度。
 */
public class CelebrationView extends View {

    public interface OnFinished {
        void onFinished();
    }

    private static final int[] CANDY = {
            0xFFFF6E9C, 0xFF35C98E, 0xFFFFCF56, 0xFFA78BFA, 0xFF56C2F7, 0xFFFF9F6E
    };

    private ValueAnimator anim;
    private String stickerText = "";
    private OnFinished finishedListener;
    private final List<float[]> confetti = new ArrayList<>();
    private final List<Integer> confettiColor = new ArrayList<>();
    private final Random rand = new Random(42);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    public CelebrationView(Context c) {
        this(c, null);
    }

    public CelebrationView(Context c, AttributeSet as) {
        super(c, as);
        textP.setTextAlign(Paint.Align.CENTER);
    }

    public void setOnFinishedListener(OnFinished l) {
        finishedListener = l;
    }

    /** 开始播放;text 是贴纸上的文案(如"拼完啦!🎉") */
    public void start(String text) {
        stickerText = text;
        confetti.clear();
        confettiColor.clear();
        for (int i = 0; i < 46; i++) {
            confetti.add(new float[]{
                    rand.nextFloat(), -0.1f - rand.nextFloat() * 0.5f,
                    10f + rand.nextFloat() * 16f, rand.nextFloat() * 360f,
                    0.55f + rand.nextFloat() * 0.4f});
            confettiColor.add(CANDY[rand.nextInt(CANDY.length)]);
        }
        setVisibility(VISIBLE);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2100);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                invalidate();
                if ((Float) a.getAnimatedValue() >= 1f) finish();
            }
        });
        anim.start();
    }

    private void finish() {
        setVisibility(GONE);
        if (finishedListener != null) finishedListener.onFinished();
    }

    @Override
    public boolean performClick() {
        finish();
        return super.performClick();
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (anim == null || !anim.isRunning()) return;
        float t = (Float) anim.getAnimatedValue();
        int W = getWidth();
        int H = getHeight();

        // 纱罩:0~0.2 淡入,0.9~1 淡出
        float scrim = t < 0.2f ? t / 0.2f : (t > 0.9f ? (1f - t) / 0.1f : 1f);
        paint.setColor(Color.argb((int) (120 * scrim), 0x26, 0x20, 0x33));
        c.drawRect(0, 0, W, H, paint);

        // 熨斗:0.12~0.58 从左扫到右,中高度;尾部拖白色高光带(熔合感)
        if (t >= 0.12f && t <= 0.62f) {
            float k = (t - 0.12f) / 0.5f;
            float iy = H * 0.44f;
            float ix = -W * 0.18f + (W * 1.36f) * k;
            paint.setColor(0x55FFFFFF);
            float tail = W * 0.28f * Math.min(1f, k * 1.6f);
            c.drawRect(Math.max(0, ix - tail), iy - H * 0.055f, ix, iy + H * 0.055f, paint);
            drawIron(c, ix, iy, Math.min(W, H) * 0.09f, 1f);
        }

        // 贴纸:0.55 弹出(Overshoot),之后轻浮动
        if (t >= 0.55f) {
            float k = Math.min(1f, (t - 0.55f) / 0.22f);
            float pop = new OvershootInterpolator(2.4f).getInterpolation(k);
            float bob = (float) Math.sin((t - 0.55f) * 22f) * H * 0.004f;
            drawSticker(c, W / 2f, H * 0.42f + bob, Math.min(W, H) * 0.34f, pop);
        }

        // 纸屑:0.6 起,从贴纸中心迸开下坠
        if (t >= 0.6f) {
            float k = (t - 0.6f) / 0.4f;
            float cx = W / 2f, cy = H * 0.42f;
            for (int i = 0; i < confetti.size(); i++) {
                float[] f = confetti.get(i);
                float ang = f[3] * (float) Math.PI / 180f;
                float dist = f[4] * Math.min(W, H) * (0.35f + k * 0.55f);
                float x = cx + (float) Math.cos(ang) * dist;
                float y = cy + (float) Math.sin(ang) * dist * 0.8f
                        + k * k * H * 0.5f;
                paint.setColor(confettiColor.get(i));
                paint.setAlpha((int) (255 * (1f - k * k)));
                c.save();
                c.translate(x, y);
                c.rotate(f[3] + k * 220f);
                c.drawRect(-f[2] / 2f, -f[2] / 3f, f[2] / 2f, f[2] / 3f, paint);
                c.restore();
            }
            paint.setAlpha(255);
        }
    }

    /** 贴纸风熨斗:糖果粉机身 + 墨描边 + 深色底板 */
    private void drawIron(Canvas c, float cx, float cy, float s, float alpha) {
        paint.setAlpha((int) (255 * alpha));
        // 底板
        paint.setColor(0xFF56C2F7);
        rf.set(cx - s * 1.05f, cy + s * 0.15f, cx + s * 1.05f, cy + s * 0.6f);
        c.drawRoundRect(rf, s * 0.22f, s * 0.22f, paint);
        paint.setColor(0xFF40354E);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.1f);
        c.drawRoundRect(rf, s * 0.22f, s * 0.22f, paint);
        paint.setStyle(Paint.Style.FILL);
        // 机身(梯形圆角)
        paint.setColor(0xFFFF8DB8);
        rf.set(cx - s * 0.85f, cy - s * 0.35f, cx + s * 0.7f, cy + s * 0.28f);
        c.drawRoundRect(rf, s * 0.3f, s * 0.3f, paint);
        paint.setColor(0xFF40354E);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(s * 0.1f);
        c.drawRoundRect(rf, s * 0.3f, s * 0.3f, paint);
        // 手柄
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF40354E);
        paint.setStrokeWidth(s * 0.16f);
        c.drawLine(cx - s * 0.55f, cy - s * 0.35f, cx - s * 0.2f, cy - s * 0.75f, paint);
        c.drawLine(cx - s * 0.2f, cy - s * 0.75f, cx + s * 0.45f, cy - s * 0.75f, paint);
        paint.setStyle(Paint.Style.FILL);
        // 高光点
        paint.setColor(0x66FFFFFF);
        c.drawCircle(cx - s * 0.45f, cy - s * 0.05f, s * 0.1f, paint);
        paint.setAlpha(255);
    }

    /** 白底墨描边贴纸卡 + 文案,按 scale 缩放(带 overshoot 弹跳) */
    private void drawSticker(Canvas c, float cx, float cy, float size, float scale) {
        if (scale <= 0f) return;
        c.save();
        c.translate(cx, cy);
        c.scale(scale, scale);
        float w = size * 1.9f, h = size;
        paint.setColor(0xFF40354E);
        rf.set(-w / 2f + 10, -h / 2f + 12, w / 2f + 10, h / 2f + 12);
        c.drawRoundRect(rf, size * 0.24f, size * 0.24f, paint);
        paint.setColor(Color.WHITE);
        rf.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
        c.drawRoundRect(rf, size * 0.24f, size * 0.24f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFF40354E);
        paint.setStrokeWidth(size * 0.05f);
        c.drawRoundRect(rf, size * 0.24f, size * 0.24f, paint);
        paint.setStyle(Paint.Style.FILL);
        textP.setColor(0xFF3A3050);
        textP.setTextSize(size * 0.3f);
        textP.setFakeBoldText(true);
        Paint.FontMetrics fm = textP.getFontMetrics();
        float dy = -(fm.ascent + fm.descent) / 2f;
        c.drawText(stickerText, 0, dy, textP);
        c.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
