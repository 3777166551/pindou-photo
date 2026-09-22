package com.pindou.app.view;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.Switch;

import com.pindou.app.R;

/**
 * Material 3 Switch(v2.61):纯手绘轨道+拇指,替换 framework M2 观感。
 * 规格值(M3 spec):轨道 52×32dp 全圆角;拇指 开=24dp/关=16dp,按下再放大
 * 至多 +4dp;轨道 开=colorPrimary/关=surface 灰;拇指 开=onPrimary/关=描边灰。
 * 动效:M3 emphasized/弹簧感插值,位移 200ms、按压生长 150ms。
 * 继承 Switch:监听器/checked 状态/无障碍(读屏报开关)全部免费继承,
 * 布局里把 <Switch> 换成 <com.pindou.app.view.M3Switch> 即可,Java 零改动。
 */
public class M3Switch extends Switch {

    private static final Interpolator SPRING =
            new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);   // M3 expressive spring

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** 拇指位移 0(关)..1(开),动画驱动 */
    private float thumbPos;
    /** 按压生长 0..1 */
    private float pressGrow;
    private ObjectAnimator posAnim, growAnim;
    private boolean pressing;

    /** 主题色缓存(init 解析一次;uiMode 变化会重建 Activity) */
    private int colOnTrack, colOffTrack, colOnThumb, colOffThumb;

    public M3Switch(Context context) {
        super(context);
        init();
    }

    public M3Switch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public M3Switch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setButtonDrawable(null);          // 关掉 framework 的轨道/拇指图
        setBackground(null);
        int[] attrs = {R.attr.colorPrimary, R.attr.onPrimary,
                R.attr.subsurface, R.attr.line};
        android.content.res.TypedArray ta =
                getContext().obtainStyledAttributes(attrs);
        colOnTrack = ta.getColor(0, 0xFF6750A4);
        colOnThumb = ta.getColor(1, 0xFFFFFFFF);
        colOffTrack = ta.getColor(2, 0xFFE2D9F0);
        colOffThumb = ta.getColor(3, 0xFF79747E);
        ta.recycle();
        thumbPos = isChecked() ? 1f : 0f;
        setPressed(isPressed());
    }

    @Override
    public void setChecked(boolean checked) {
        boolean was = isChecked();
        super.setChecked(checked);
        if (was == checked && thumbPos == (checked ? 1f : 0f)) {
            return;
        }
        animateTo(checked ? 1f : 0f);
    }

    private void animateTo(float target) {
        if (posAnim != null) posAnim.cancel();
        posAnim = ObjectAnimator.ofFloat(this, "thumbPos", target);
        posAnim.setDuration(200L);
        posAnim.setInterpolator(SPRING);
        posAnim.start();
    }

    @SuppressWarnings("unused")
    public float getThumbPos() {
        return thumbPos;
    }

    public void setThumbPos(float v) {
        thumbPos = v;
        invalidate();
    }

    private void setPressing(boolean down) {
        if (pressing == down) return;
        pressing = down;
        if (growAnim != null) growAnim.cancel();
        growAnim = ObjectAnimator.ofFloat(this, "pressGrow", down ? 1f : 0f);
        growAnim.setDuration(150L);
        growAnim.setInterpolator(SPRING);
        growAnim.start();
    }

    @SuppressWarnings("unused")
    public float getPressGrow() {
        return pressGrow;
    }

    public void setPressGrow(float v) {
        pressGrow = v;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isEnabled()) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    setPressing(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    setPressing(false);
                    break;
                default:
                    break;
            }
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (posAnim != null) posAnim.cancel();
        if (growAnim != null) growAnim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public synchronized void onMeasure(int widthMeasureSpec,
                                       int heightMeasureSpec) {
        int dp = (int) (getResources().getDisplayMetrics().density + 0.5f);
        setMeasuredDimension(resolveSize(52 * dp + getPaddingLeft()
                + getPaddingRight(), widthMeasureSpec),
                resolveSize(32 * dp + getPaddingTop() + getPaddingBottom(),
                        heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 不调 super:framework Switch 的轨道/拇指是 M2 观感,整幅自绘
        float dp = getResources().getDisplayMetrics().density;
        int padL = getPaddingLeft(), padT = getPaddingTop();
        int w = getWidth() - padL - getPaddingRight();
        int h = getHeight() - padT - getPaddingBottom();
        if (w <= 0 || h <= 0) {
            return;
        }
        // 轨道:固定 52×32dp 观感,按视图实际尺寸缩放(布局给的是 wrap_content=52×32)
        float tw = Math.min(w, Math.round(52 * dp));
        float th = Math.min(h, Math.round(32 * dp));
        float left = padL + (w - tw) / 2f;
        float top = padT + (h - th) / 2f;
        float r = th / 2f;

        boolean on = isChecked();
        int alphaMul = isEnabled() ? 255 : Math.round(255 * 0.38f);
        trackPaint.setColor(on ? colOnTrack : colOffTrack);
        trackPaint.setAlpha(alphaMul);
        rect.set(left, top, left + tw, top + th);
        canvas.drawRoundRect(rect, r, r, trackPaint);

        // 拇指:开 24dp/关 16dp,按压再放大 4dp;左右贴边留 4dp
        float diaOn = 24 * dp;
        float diaOff = 16 * dp;
        float grow = pressGrow * 4 * dp;
        float dia = (on ? diaOn : diaOff) + grow;
        float margin = 4 * dp;
        float x0 = left + margin + r;
        float x1 = left + tw - margin - r;
        float cx = x0 + (x1 - x0) * thumbPos;
        float cy = top + th / 2f;

        thumbPaint.setColor(on ? colOnThumb : colOffThumb);
        thumbPaint.setAlpha(alphaMul);
        canvas.drawCircle(cx, cy, dia / 2f, thumbPaint);
        if (on && isEnabled()) {
            // M3:选中拇指上的一颗小点(轨道色半透明)
            thumbPaint.setColor((colOnTrack & 0x00FFFFFF) | 0x66000000);
            canvas.drawCircle(cx, cy, Math.max(1.5f, dia * 0.08f), thumbPaint);
        }
    }
}
