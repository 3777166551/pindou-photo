package com.pindou.app.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * M3 Expressive 风格加载指示器(2026-10,v2.63 批次一):
 * 超椭圆(squircle)↔ 圆形连续变形 + 匀速旋转的描边环 + 中心呼吸点;
 * 颜色取主题 ?attr/colorPrimary(动态取色下跟随壁纸)。
 * 纯代码零依赖;进 XML 必须有 (Context,AttributeSet) 构造器(DEV-NOTES 23)。
 * 循环 1.6s:旋转一圈,变形往复一次,中心点呼吸两个来回。
 */
public class LoadingIndicatorView extends View {

    private static final long CYCLE_MS = 1600;

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private ValueAnimator anim;
    private float phase;   // 0..1 循环相位
    private int color = 0xFF6750A4;

    public LoadingIndicatorView(Context c) {
        this(c, null);
    }

    public LoadingIndicatorView(Context c, AttributeSet attrs) {
        this(c, attrs, 0);
    }

    public LoadingIndicatorView(Context c, AttributeSet attrs, int style) {
        super(c, attrs, style);
        android.content.res.TypedArray ta = c.getTheme().obtainStyledAttributes(
                new int[]{com.pindou.app.R.attr.colorPrimary});
        try {
            color = ta.getColor(0, color);
        } finally {
            ta.recycle();
        }
        float den = getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.4f * den);
        ring.setColor(color);
        ring.setStrokeCap(Paint.Cap.ROUND);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(color);
        setContentDescription(c.getString(com.pindou.app.R.string.generating));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnim();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnim();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && anim == null && isAttachedToWindow()) {
            startAnim();
        } else if (visibility != VISIBLE) {
            stopAnim();
        }
    }

    private void startAnim() {
        if (anim != null) return;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(CYCLE_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                phase = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    private void stopAnim() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float rBase = Math.min(w, h) / 2f - ring.getStrokeWidth();
        if (rBase <= 0) return;

        c.save();
        c.rotate(phase * 360f, cx, cy);
        // 变形指数 n:2.2(近圆)↔ 4.6(squircle 偏方),往复一次
        float n = 2.2f + 2.4f * (0.5f - 0.5f * (float) Math.cos(phase * 2.0 * Math.PI));
        path.reset();
        int steps = 72;
        for (int i = 0; i <= steps; i++) {
            double th = i * 2.0 * Math.PI / steps;
            double ct = Math.cos(th), st = Math.sin(th);
            double rr = Math.pow(Math.pow(Math.abs(ct), n)
                    + Math.pow(Math.abs(st), n), -1.0 / n);
            float x = (float) (cx + rr * ct * rBase);
            float y = (float) (cy + rr * st * rBase);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        c.drawPath(path, ring);
        c.restore();

        // 中心呼吸点(两个来回/圈)
        float breathe = 0.5f + 0.5f * (float) Math.sin(phase * 2.0 * Math.PI * 2);
        c.drawCircle(cx, cy, rBase * (0.10f + 0.10f * breathe), dot);
    }
}
