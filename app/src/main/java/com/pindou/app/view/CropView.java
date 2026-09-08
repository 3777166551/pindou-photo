package com.pindou.app.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * 取景裁剪控件(v2.39 糖果贴纸风重绘):
 *  - 选区外圆角挖孔压暗,选区 = 白细框 + 黄油色 L 角标(SelectionPainter);
 *  - 角点拖拽改大小(锁画幅比例、对角锚定),框内拖动移位置,双指缩放,双击复位;
 *  - 交互时显示三分构图线;顶部提示药丸首次触摸后淡出;
 *  - 右下角"清晰度"徽章 = 选区像素占整图百分比,提醒别裁太小。
 * 确定后用 apply() 取出裁剪后的 Bitmap。
 */
public class CropView extends View {

    private Bitmap bmp;
    /** 裁剪框(图像坐标) */
    private final RectF crop = new RectF();
    private float aspect = 1f;
    private float fitScale = 1f;
    private float imgLeft, imgTop;
    private boolean ready;

    private static final float MIN_CROP_FRACTION = 0.15f;

    private final Paint dimPaint = new Paint();
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SelectionPainter sel;
    private final RectF viewRect = new RectF();
    private final Path dimPath = new Path();
    private final RectF tmpR = new RectF();

    /** 提示药丸 */
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String hint;
    private float hintAlpha = 1f;
    private boolean touched;
    private boolean interacting;

    private static final int CORNER_NONE = -1;
    /** 正在拖拽的角标索引:0 左上 1 右上 2 左下 3 右下,-1 = 无 */
    private int activeCorner = CORNER_NONE;

    private final ScaleGestureDetector scaleDetector;
    private float lastX, lastY;
    /** 手写双击判定:上一次 ACTION_DOWN 的时间与位置 */
    private long lastDownTime;
    private float lastDownX, lastDownY;

    public CropView(Context context) {
        super(context);
        float dm = getResources().getDisplayMetrics().density;
        dimPaint.setStyle(Paint.Style.FILL);
        dimPaint.setColor(0xA6000000);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setColor(SelectionPainter.WHITE);
        framePaint.setStrokeWidth(1.5f * dm);
        sel = new SelectionPainter(dm);

        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(0xE640354E);
        pillText.setColor(Color.WHITE);
        pillText.setTextSize(12.5f * dm);
        pillText.setFakeBoldText(true);
        badgeText.setColor(Color.WHITE);
        badgeText.setTextSize(11.5f * dm);
        badgeText.setFakeBoldText(true);
        hint = context.getString(com.pindou.app.R.string.crop_hint_touch);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        scaleBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                        return true;
                    }
                });
        // v2.44:拖动不再走 GestureDetector(部分机型/父容器组合下手势死),
        // 全部触摸自己算;双击复位也手写判定
    }

    /** @param aspect 目标画幅宽高比(cols / rows) */
    public void setup(Bitmap bitmap, float aspectRatio) {
        bmp = bitmap;
        aspect = aspectRatio <= 0 ? 1f : aspectRatio;
        ready = bmp != null && bmp.getWidth() > 0;
        touched = false;
        hintAlpha = 1f;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (!ready || w == 0 || h == 0) return;
        float pad = dp(12);
        fitScale = Math.min((w - 2 * pad) / bmp.getWidth(),
                (h - 2 * pad) / bmp.getHeight());
        imgLeft = (w - bmp.getWidth() * fitScale) / 2f;
        imgTop = (h - bmp.getHeight() * fitScale) / 2f;
        resetCrop();
    }

    /** 初始化为图片中央的最大同比例选区 */
    private void resetCrop() {
        float iw = bmp.getWidth(), ih = bmp.getHeight();
        float cw = iw, ch = cw / aspect;
        if (ch > ih) {
            ch = ih;
            cw = ch * aspect;
        }
        crop.set((iw - cw) / 2f, (ih - ch) / 2f, (iw + cw) / 2f, (ih + ch) / 2f);
        invalidate();
    }

    private void moveBy(float dxView, float dyView) {
        float dx = dxView / fitScale;
        float dy = dyView / fitScale;
        if (crop.left + dx < 0) dx = -crop.left;
        if (crop.right + dx > bmp.getWidth()) dx = bmp.getWidth() - crop.right;
        if (crop.top + dy < 0) dy = -crop.top;
        if (crop.bottom + dy > bmp.getHeight()) dy = bmp.getHeight() - crop.bottom;
        crop.offset(dx, dy);
        invalidate();
    }

    private void scaleBy(float factor, float focusVx, float focusVy) {
        // 焦点(屏幕坐标)转图像坐标
        float fx = (focusVx - imgLeft) / fitScale;
        float fy = (focusVy - imgTop) / fitScale;
        float iw = bmp.getWidth(), ih = bmp.getHeight();
        float minSide = Math.min(iw, ih) * MIN_CROP_FRACTION;
        float cw = crop.width() * factor;
        float ch = crop.height() * factor;
        if (cw < minSide || ch < minSide) {
            cw = crop.width();
            ch = crop.height();
            factor = 1f;
        }
        if (cw > iw || ch > ih) {
            cw = Math.min(iw, ch * aspect);
            ch = cw / aspect;
            if (ch > ih) {
                ch = ih;
                cw = ch * aspect;
            }
            factor = cw / crop.width();
        }
        // 以焦点为不动点缩放
        float nl = fx + (crop.left - fx) * factor;
        float nt = fy + (crop.top - fy) * factor;
        crop.set(nl, nt, nl + cw, nt + ch);
        clampCrop();
        invalidate();
    }

    private void clampCrop() {
        float iw = bmp.getWidth(), ih = bmp.getHeight();
        if (crop.left < 0) crop.offset(-crop.left, 0);
        if (crop.top < 0) crop.offset(0, -crop.top);
        if (crop.right > iw) crop.offset(iw - crop.right, 0);
        if (crop.bottom > ih) crop.offset(0, ih - crop.bottom);
    }

    /** 按当前选区裁剪;尺寸不合法返回 null */
    public Bitmap apply() {
        if (!ready) return null;
        int l = Math.max(0, Math.round(crop.left));
        int t = Math.max(0, Math.round(crop.top));
        int wpx = Math.min(bmp.getWidth() - l, Math.round(crop.width()));
        int hpx = Math.min(bmp.getHeight() - t, Math.round(crop.height()));
        if (wpx < 8 || hpx < 8) return null;
        return Bitmap.createBitmap(bmp, l, t, wpx, hpx);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);
        if (!ready) return;
        Matrix m = new Matrix();
        m.postTranslate(imgLeft, imgTop);
        m.preScale(fitScale, fitScale);
        canvas.drawBitmap(bmp, m, null);

        viewRect.set(
                imgLeft + crop.left * fitScale,
                imgTop + crop.top * fitScale,
                imgLeft + crop.right * fitScale,
                imgTop + crop.bottom * fitScale);

        // 选区外圆角挖孔压暗
        dimPath.reset();
        dimPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        tmpR.set(viewRect);
        dimPath.addRoundRect(tmpR, dp(14), dp(14), Path.Direction.CCW);
        canvas.drawPath(dimPath, dimPaint);

        // 细白框 + 三分线 + 角标
        canvas.drawRect(viewRect, framePaint);
        sel.drawThirds(canvas, viewRect);
        sel.drawBrackets(canvas, viewRect);

        // 清晰度徽章(右下,空间不够放选区上方)
        int pct = Math.round(crop.width() * crop.height()
                / ((float) bmp.getWidth() * bmp.getHeight()) * 100f);
        String badge = getContext().getString(com.pindou.app.R.string.crop_keep, pct);
        float tw = badgeText.measureText(badge);
        float padH = dp(9), padV = dp(5);
        float bw = tw + padH * 2, bh = badgeText.getTextSize() + padV * 2;
        float bx = Math.min(viewRect.right - bw, getWidth() - bw - dp(6));
        float by = viewRect.bottom + dp(10);
        if (by + bh > getHeight()) by = viewRect.top - bh - dp(10);
        tmpR.set(bx, by, bx + bw, by + bh);
        canvas.drawRoundRect(tmpR, bh / 2f, bh / 2f, pillPaint);
        canvas.drawText(badge, bx + padH, by + padV - badgeText.ascent(), badgeText);

        // 顶部提示药丸,首次触摸后淡出
        if (hintAlpha > 0.02f && !interacting) {
            float hw = pillText.measureText(hint) + dp(24);
            float hh = pillText.getTextSize() + dp(12);
            float hx = (getWidth() - hw) / 2f;
            float hy = Math.max(dp(10), viewRect.top - hh - dp(14));
            tmpR.set(hx, hy, hx + hw, hy + hh);
            pillPaint.setAlpha(Math.round(230 * hintAlpha));
            canvas.drawRoundRect(tmpR, hh / 2f, hh / 2f, pillPaint);
            pillText.setAlpha(Math.round(255 * hintAlpha));
            canvas.drawText(hint, hx + dp(12),
                    hy + (hh - pillText.getTextSize()) / 2f - pillText.ascent(),
                    pillText);
            pillPaint.setAlpha(230);
            pillText.setAlpha(255);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!ready) return true;
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!touched) {
                    touched = true;
                    animateHintOut();
                }
                // 万一将来被放进可滚动容器,不让父层抢走手势
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                // 手写双击:300ms 内同一位置二次按下 → 复位
                long now = event.getEventTime();
                if (now - lastDownTime < 300
                        && Math.abs(event.getX() - lastDownX) <= dp(40)
                        && Math.abs(event.getY() - lastDownY) <= dp(40)) {
                    resetCrop();
                    lastDownTime = 0;
                    break;
                }
                lastDownTime = now;
                lastDownX = event.getX();
                lastDownY = event.getY();
                lastX = event.getX();
                lastY = event.getY();
                activeCorner = hitCorner(lastX, lastY);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // 第二根手指按下:角点/单指拖拽让位给双指缩放
                activeCorner = CORNER_NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) {
                    lastX = event.getX();
                    lastY = event.getY();
                    break;
                }
                if (event.getPointerCount() > 1) break;
                if (activeCorner != CORNER_NONE) {
                    dragCorner(event.getX(), event.getY());
                } else {
                    // 选框跟着手指走
                    moveBy(event.getX() - lastX, event.getY() - lastY);
                }
                lastX = event.getX();
                lastY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeCorner = CORNER_NONE;
                interacting = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                break;
            default:
                break;
        }
        return true;
    }

    /** 命中测试:触摸点距哪个角标足够近(dp(26) 内) */
    private int hitCorner(float x, float y) {
        float slop = dp(26);
        for (int i = 0; i < 4; i++) {
            float cx = (i & 1) == 0 ? viewRect.left : viewRect.right;
            float cy = (i & 2) == 0 ? viewRect.top : viewRect.bottom;
            if (Math.abs(x - cx) <= slop && Math.abs(y - cy) <= slop) {
                interacting = true;
                return i;
            }
        }
        return CORNER_NONE;
    }

    /**
     * 角点拖拽改大小:对角固定为锚点,按触摸点算新宽高(锁画幅比例),
     * 越界自动收住 —— 与主流图片编辑器的角点手势一致。
     */
    private void dragCorner(float x, float y) {
        boolean right = (activeCorner & 1) != 0;
        boolean bottom = (activeCorner & 2) != 0;
        float iw = bmp.getWidth(), ih = bmp.getHeight();
        float minSide = Math.min(iw, ih) * MIN_CROP_FRACTION;

        // 锚点 = 对角(图像坐标)
        float ax = right ? crop.left : crop.right;
        float ay = bottom ? crop.top : crop.bottom;
        float dirX = right ? 1f : -1f;
        float dirY = bottom ? 1f : -1f;

        // 该方向上锚点到图像边缘的最大可用宽高
        float maxW = right ? iw - ax : ax;
        float maxH = bottom ? ih - ay : ay;

        // 由触摸点得出期望宽高,取长边保证"贴手"
        float fw = Math.max(0, (x - imgLeft) / fitScale - ax) * dirX;
        float fh = Math.max(0, (y - imgTop) / fitScale - ay) * dirY;
        float cw = Math.max(fw, fh * aspect);
        float ch = cw / aspect;
        if (ch > maxH) {
            ch = maxH;
            cw = ch * aspect;
        }
        if (cw > maxW) {
            cw = maxW;
            ch = cw / aspect;
        }
        if (cw < minSide || ch < minSide) {
            // 空间放得下最小选区才起步,否则保持原状
            if (Math.min(maxW, maxH * aspect) < minSide) return;
            cw = minSide;
            ch = cw / aspect;
            if (ch < minSide) {
                ch = minSide;
                cw = ch * aspect;
            }
        }
        if (right) {
            crop.set(ax, bottom ? ay : ay - ch, ax + cw, bottom ? ay + ch : ay);
        } else {
            crop.set(ax - cw, bottom ? ay : ay - ch, ax, bottom ? ay + ch : ay);
        }
        invalidate();
    }

    private void animateHintOut() {
        android.animation.ValueAnimator an =
                android.animation.ValueAnimator.ofFloat(hintAlpha, 0f);
        an.setDuration(260);
        an.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                hintAlpha = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        an.start();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
