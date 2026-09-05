package com.pindou.app.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * 取景裁剪控件:照片按适配显示,上面浮一个可拖动/双指缩放的裁剪框,
 * 比例锁定为画幅(cols:rows)。确定后用 apply() 取出裁剪后的 Bitmap。
 * 用于解决"居中裁剪不可调 -> 主体被切/不在中心"的问题。
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
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector dragDetector;

    public CropView(Context context) {
        super(context);
        dimPaint.setStyle(Paint.Style.FILL);
        dimPaint.setColor(0x99000000);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setColor(0xFFFF8C00);
        framePaint.setStrokeWidth(3f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setColor(0x66FFFFFF);
        gridPaint.setStrokeWidth(1f);

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        scaleBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                        return true;
                    }
                });
        dragDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                            float dx, float dy) {
                        moveBy(-dx, -dy);
                        return true;
                    }
                });
    }

    /** @param aspect 目标画幅宽高比(cols / rows) */
    public void setup(Bitmap bitmap, float aspectRatio) {
        bmp = bitmap;
        aspect = aspectRatio <= 0 ? 1f : aspectRatio;
        ready = bmp != null && bmp.getWidth() > 0;
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

        RectF v = new RectF(
                imgLeft + crop.left * fitScale,
                imgTop + crop.top * fitScale,
                imgLeft + crop.right * fitScale,
                imgTop + crop.bottom * fitScale);
        // 选区外压暗
        Path p = new Path();
        p.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        p.addRect(v, Path.Direction.CCW);
        canvas.drawPath(p, dimPaint);
        canvas.drawRect(v, framePaint);
        // 三分线
        canvas.drawLine(v.left, v.centerY(), v.right, v.centerY(), gridPaint);
        canvas.drawLine(v.centerX(), v.top, v.centerX(), v.bottom, gridPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!ready) return true;
        scaleDetector.onTouchEvent(event);
        if (!scaleDetector.isInProgress()) {
            dragDetector.onTouchEvent(event);
        }
        return true;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
