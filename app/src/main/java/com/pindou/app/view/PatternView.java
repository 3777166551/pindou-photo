package com.pindou.app.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;
import com.pindou.app.bead.PatternEngine;

/**
 * 图纸/效果图预览控件,支持双指缩放、单指拖动、双击复位。
 * 图纸模式:网格 + 符号 + 坐标;效果图模式:仿真拼豆圆豆。
 */
public class PatternView extends View {

    public static final int MODE_EFFECT = 0;
    public static final int MODE_PATTERN = 1;

    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 12f;

    private BeadPattern pattern;
    private int mode = MODE_EFFECT;
    private boolean showSymbols = true;
    private boolean showGrid = true;

    private float zoom = 1f;
    private float offX = 0f;
    private float offY = 0f;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private final Paint beadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pegPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ---- 照片变拼豆变形动画(M3 motion)----
    // 源照片先铺满板幅,拼豆沿对角线逐格弹入覆盖照片;
    // 缓动 = M3 emphasized decelerate(0.05,0.7,0.1,1),单珠弹出时长占比 30%,
    // 整体 950ms(M3 大型过渡量级),同一张照片重新生成不重播。
    private static final long REVEAL_MS = 950L;
    private final android.view.animation.Interpolator revealEase =
            new android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private final Paint photoPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private Bitmap revealPhoto;      // 降采样+按板幅比中心裁剪的源照片
    private long revealStart;
    private boolean revealing;

    /** 新照片首次生成完成:播放"照片→拼豆"变形(同照片重生成不重播由调用方判) */
    public void startPhotoReveal(Bitmap src) {
        endReveal();
        if (src == null || src.isRecycled() || effect3d
                || pattern == null || pattern.cols == 0) {
            return;
        }
        int pw = src.getWidth();
        int ph = src.getHeight();
        // 中心裁剪到板幅宽高比,再降采样到 ~640px,保证逐帧铺图的绘制成本
        float boardAspect = pattern.cols / (float) pattern.rows;
        int cw, ch;
        if (pw / (float) ph > boardAspect) {
            ch = ph;
            cw = (int) (ph * boardAspect);
        } else {
            cw = pw;
            ch = (int) (pw / boardAspect);
        }
        Bitmap crop = Bitmap.createBitmap(src,
                (pw - cw) / 2, (ph - ch) / 2, cw, ch);
        int target = Math.max(cw, ch);
        float sc = target > 640 ? 640f / target : 1f;
        revealPhoto = sc < 1f
                ? Bitmap.createScaledBitmap(crop,
                        Math.max(1, Math.round(cw * sc)),
                        Math.max(1, Math.round(ch * sc)), true)
                : crop;
        if (revealPhoto != crop) crop.recycle();
        revealStart = SystemClock.uptimeMillis();
        revealing = true;
        postInvalidateOnAnimation();
    }

    private void endReveal() {
        if (revealPhoto != null) {
            revealPhoto.recycle();
            revealPhoto = null;
        }
        revealing = false;
    }

    /** 单格变形进度 0..1:0=还是照片,1=豆已就位;对角线 stagger */
    private float revealK(int gx, int gy) {
        if (!revealing) {
            return 1f;
        }
        float p = (SystemClock.uptimeMillis() - revealStart) / (float) REVEAL_MS;
        if (p >= 1f) {
            endReveal();
            return 1f;
        }
        int span = pattern.cols + pattern.rows - 2;
        float d = span > 0 ? (gx + gy) / (float) span : 0f;
        float local = (p - d * 0.70f) / 0.30f;
        if (local <= 0f) {
            return 0f;
        }
        if (local >= 1f) {
            return 1f;
        }
        return revealEase.getInterpolation(local);
    }


    public PatternView(Context context) {
        this(context, null);
    }

    public PatternView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ringPaint.setStyle(Paint.Style.STROKE);
        boardLinePaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStyle(Paint.Style.STROKE);
        emptyPaint.setStyle(Paint.Style.STROKE);
        symbolPaint.setTypeface(Typeface.DEFAULT_BOLD);
        symbolPaint.setTextAlign(Paint.Align.CENTER);

        // 完成 ✓ 印章:白圈衬底 + 黄油圆 + 墨勾(贴纸风,与选框/行框同语言)
        popHaloPaint.setColor(0xFFFFFFFF);
        popDiscPaint.setColor(0xFFFFCF56);
        popRingPaint.setColor(0xFF2A2735);
        popRingPaint.setStyle(Paint.Style.STROKE);
        popCheckPaint.setColor(0xFF2A2735);
        popCheckPaint.setStrokeCap(Paint.Cap.ROUND);
        popCheckPaint.setStrokeJoin(Paint.Join.ROUND);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float fit = fitCell();
                if (fit <= 0) return false;
                float oldCell = fit * zoom;
                float nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * detector.getScaleFactor()));
                float newCell = fit * nz;
                float m = marginRatio() * oldCell;
                float fx = detector.getFocusX();
                float fy = detector.getFocusY();
                float gx = (fx - offX - m) / oldCell;
                float gy = (fy - offY - m) / oldCell;
                float nm = marginRatio() * newCell;
                zoom = nz;
                offX = fx - nm - gx * newCell;
                offY = fy - nm - gy * newCell;
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                offX -= distanceX;
                offY -= distanceY;
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                zoom = 1f;
                offX = 0f;
                offY = 0f;
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (tapListener == null) return false;
                int[] c = cellAt(e.getX(), e.getY());
                if (c == null) return false;
                tapListener.onCellTap(c[0], c[1]);
                return true;
            }
        });
    }

    public void setPattern(BeadPattern p) {
        // 画幅没变时保留当前缩放/平移(调节参数时不打断查看)
        boolean sameSize = pattern != null && p != null
                && pattern.cols == p.cols && pattern.rows == p.rows;
        this.pattern = p;
        if (!sameSize) {
            zoom = 1f;
            offX = 0f;
            offY = 0f;
        }
        invalidate();
    }

    public void setMode(int mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        endReveal();   // 切到图纸页/清单页时变形动画立即结束
        zoom = 1f;
        offX = 0f;
        offY = 0f;
        invalidate();
    }

    public void setShowSymbols(boolean show) {
        this.showSymbols = show;
        invalidate();
    }

    public void setShowGrid(boolean show) {
        this.showGrid = show;
        invalidate();
    }

    /** 点击格子回调(图纸模式下单击某格返回其坐标) */
    public interface OnCellTapListener {
        void onCellTap(int cellX, int cellY);
    }

    /** 涂色回调(画笔模式下单指划过的每一格;一笔开始时先回调 onStrokeStart) */
    public interface OnPaintListener {
        void onStrokeStart();

        void onPaintCell(int cellX, int cellY);
    }

    /** 吸管模式:单点一格 = 取该格颜色当笔色 */
    public interface OnDropListener {
        void onDropCell(int cellX, int cellY);
    }

    private boolean dropper = false;
    private OnDropListener dropListener;

    public void setDropper(boolean on) {
        dropper = on;
    }

    public void setOnDropListener(OnDropListener l) {
        dropListener = l;
    }

    private OnCellTapListener tapListener;

    public void setOnCellTapListener(OnCellTapListener l) {
        this.tapListener = l;
    }

    /** 油漆桶回调:画笔模式下长按一格,把同色连通区域整体填充 */
    public interface OnCellLongPressListener {
        void onCellLongPress(int cellX, int cellY);
    }

    private OnPaintListener paintListener;
    private OnCellLongPressListener longPressListener;
    /** 画笔模式:单指在图纸上滑动 = 连续涂色,双指仍可缩放 */
    private boolean paintEnabled;
    private boolean paintStroke;
    private int lastPaintedX = -1;
    private int lastPaintedY = -1;
    // 长按(油漆桶)判定:按下先不落笔,超时仍按着且未滑动才触发
    private float downX, downY;
    private int[] downCell;
    private boolean strokeMoved, longPressFired;
    private Runnable longPressCheck;

    public void setOnCellLongPressListener(OnCellLongPressListener l) {
        this.longPressListener = l;
    }

    /** 拼豆模式拖动刷选:滑过的格子逐个回调(只做"标记完成",不取消) */
    public interface OnAssistDragListener {
        void onAssistDragCell(int cellX, int cellY);

        void onAssistDragEnd();
    }

    private OnAssistDragListener assistDragListener;

    public void setOnAssistDragListener(OnAssistDragListener l) {
        this.assistDragListener = l;
    }

    // 拼豆模式触摸状态
    private boolean dragging, dragMarking;
    private int lastDragX = -1, lastDragY = -1;
    private Runnable pendingTap;
    private long lastTapUp;
    private float lastTapX, lastTapY;
    // 定位闪烁
    private int[] flashCell;
    private long flashUntil;
    private Paint flashPaint;

    // 完成 ✓ 印章:{cellX, cellY, t0};标记完成的格心弹一枚贴纸,460ms 弹回淡出
    private static final long POP_MS = 460L;
    private static final int MAX_POPS = 24;   // 拖动刷选一扫一串,限制同屏数量
    private final java.util.ArrayList<float[]> popStamps = new java.util.ArrayList<>();
    private final Paint popHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popDiscPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popCheckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 标记完成反馈:格心弹一枚 ✓ 印章(纯视觉,无振动) */
    public void popCell(int gx, int gy) {
        if (popStamps.size() >= MAX_POPS) popStamps.remove(0);
        popStamps.add(new float[]{gx, gy, SystemClock.uptimeMillis()});
        postInvalidateOnAnimation();
    }

    // ---- 拼豆模式(逐色辅助)----
    /** true = 只突出 assistFocus 颜色,已完成的格子画描边 */
    private boolean assistOn;
    /** 当前辅助的颜色(palette 下标),-1 = 全部突出 */
    private int assistFocus = -1;
    /** 已拼好的格子(y*cols+x) */
    private java.util.Set<Integer> assistDone;

    // 拼豆模式:非当前颜色蒙上纸色,已完成的格子描薄荷绿边;
    // 按板引导时只点亮当前 29×29 板,逐行引导时只点亮当前行,其余蒙灰
    private boolean assistBoardMode;
    private int assistBoard;
    private android.graphics.Rect assistBoardRect;
    /** 逐行引导:只点亮 assistRow 这一行 */
    private boolean assistRowMode;
    private int assistRow;
    private final Paint boardFramePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 夜间图纸:纸面转暗、网格线转亮,豆子颜色保持原样 */
    private boolean night;

    /** 夜间图纸模式开关(只影响画布渲染,不改豆子颜色) */
    public void setNight(boolean on) {
        night = on;
        invalidate();
    }
    // 描摹底图:画笔模式下垫在格子下面的半透明照片
    private Bitmap traceBitmap;
    private boolean traceVisible = true;
    private final Paint tracePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF traceDst = new android.graphics.RectF();

    public void setAssist(boolean on, int focusColor, java.util.Set<Integer> done) {
        setAssist(on, focusColor, done, false, 0, false, 0);
    }

    /** 兼容旧签名:按板引导(无逐行) */
    public void setAssist(boolean on, int focusColor, java.util.Set<Integer> done,
                          boolean boardMode, int boardIndex) {
        setAssist(on, focusColor, done, boardMode, boardIndex, false, 0);
    }

    /** 按板/逐行引导:boardIndex 指定当前板,rowIndex 指定当前行(focusColor<0 = 带内全色可见) */
    public void setAssist(boolean on, int focusColor, java.util.Set<Integer> done,
                          boolean boardMode, int boardIndex,
                          boolean rowMode, int rowIndex) {
        assistOn = on;
        assistFocus = focusColor;
        assistDone = done;
        assistBoardMode = on && boardMode && !rowMode && pattern != null;
        assistBoard = boardIndex;
        assistBoardRect = assistBoardMode ? boardRect(pattern, boardIndex) : null;
        assistRowMode = on && rowMode && pattern != null;
        assistRow = Math.max(0, Math.min(rowIndex, pattern == null ? 0 : pattern.rows - 1));
        invalidate();
    }

    /** 第 b 块 29×29 板的格子范围(边缘板不足 29 按实际格子裁) */
    public static android.graphics.Rect boardRect(BeadPattern p, int b) {
        int bc = (int) Math.ceil(p.cols / 29.0);
        int br = (int) Math.ceil(p.rows / 29.0);
        int idx = Math.max(0, Math.min(b, bc * br - 1));
        int x0 = (idx % bc) * 29;
        int y0 = (idx / bc) * 29;
        return new android.graphics.Rect(x0, y0,
                Math.min(x0 + 29, p.cols), Math.min(y0 + 29, p.rows));
    }

    /** 设置描摹底图(传 null 清除);显示开关用 setTraceVisible */
    public void setTraceBitmap(Bitmap b) {
        traceBitmap = b;
        invalidate();
    }

    public void setTraceVisible(boolean v) {
        traceVisible = v;
        invalidate();
    }

    public void setPaintEnabled(boolean enabled) {
        this.paintEnabled = enabled;
        paintStroke = false;
    }

    public void setOnPaintListener(OnPaintListener l) {
        this.paintListener = l;
    }

    /**
     * 屏幕坐标 -> 图纸格坐标;不在范围内或非图纸模式返回 null。
     * 复用 onDraw 相同的缩放/平移参数保证指哪是哪。
     */
    public int[] cellAt(float vx, float vy) {
        if (pattern == null || pattern.cols == 0 || mode != MODE_PATTERN) return null;
        float fit = fitCell();
        float cell = fit * zoom;
        if (cell <= 0) return null;
        int cols = pattern.cols;
        int rows = pattern.rows;
        float cw = cols * cell;
        float ch = rows * cell;
        int w = getWidth();
        int h = getHeight();
        // 与 onDraw 一致的位置计算(clamp 后的偏移)
        float ox = offX;
        float oy = offY;
        if (cw <= w) ox = (w - cw) / 2f;
        else ox = Math.max(Math.min(offX, 0), w - cw);
        if (ch <= h) oy = (h - ch) / 2f;
        else oy = Math.max(Math.min(offY, 0), h - ch);
        int gx = (int) Math.floor((vx - ox) / cell);
        int gy = (int) Math.floor((vy - oy) / cell);
        if (gx < 0 || gy < 0 || gx >= cols || gy >= rows) return null;
        return new int[]{gx, gy};
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (assistOn && mode == MODE_PATTERN && handleAssistTouch(event)) {
            return true;
        }
        if (paintEnabled && mode == MODE_PATTERN && handlePaintTouch(event)) {
            return true;
        }
        if (!scaleDetector.isInProgress()) {
            gestureDetector.onTouchEvent(event);
        }
        scaleDetector.onTouchEvent(event);
        return true;
    }

    /**
     * 拼豆模式的触摸处理:单击 = 切换完成标记(双击仍复位缩放),
     * 按住滑动 = 连续刷选(只标记完成,不取消)。
     */
    private boolean handleAssistTouch(MotionEvent event) {
        if (pattern == null || fitCell() <= 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                int[] c = cellAt(event.getX(), event.getY());
                if (c == null) return false;
                downX = event.getX();
                downY = event.getY();
                downCell = c;
                dragging = true;
                dragMarking = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!dragging || event.getPointerCount() != 1) {
                    // 第二根手指落下:中断标记(取消待定单击),放行给缩放手势
                    // ——与画笔模式同款约定,否则辅助模式下双指缩放永远失效
                    if (dragging && event.getPointerCount() > 1) {
                        dragging = false;
                        dragMarking = false;
                        if (pendingTap != null) {
                            removeCallbacks(pendingTap);
                            pendingTap = null;
                        }
                        return false;
                    }
                    return dragging;
                }
                if (!dragMarking) {
                    if (!isBeyondSlop(event)) return true;
                    dragMarking = true;
                    if (pendingTap != null) {
                        removeCallbacks(pendingTap);
                        pendingTap = null;
                    }
                    lastDragX = -1;
                    lastDragY = -1;
                    markAt(downX, downY);
                }
                markLine(event.getX(), event.getY());
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging) return false;
                dragging = false;
                if (dragMarking) {
                    dragMarking = false;
                    if (assistDragListener != null) assistDragListener.onAssistDragEnd();
                    return true;
                }
                int[] c = cellAt(event.getX(), event.getY());
                if (c == null) return true;
                long now = SystemClock.uptimeMillis();
                if (now - lastTapUp <= 280
                        && Math.abs(event.getX() - lastTapX) < dp(24)
                        && Math.abs(event.getY() - lastTapY) < dp(24)) {
                    // 双击复位缩放
                    lastTapUp = 0;
                    if (pendingTap != null) {
                        removeCallbacks(pendingTap);
                        pendingTap = null;
                    }
                    zoom = 1f;
                    offX = 0f;
                    offY = 0f;
                    invalidate();
                    return true;
                }
                lastTapUp = now;
                lastTapX = event.getX();
                lastTapY = event.getY();
                final int fx = c[0];
                final int fy = c[1];
                if (pendingTap != null) {
                    // 上一个待定标记与本次不同格,不可能构成双击:
                    // 立即落账而不是吞掉——快速点相邻格时每一格都必须生效
                    // (CI 冒烟 8×8 标 52 格只登记最后一格的根因,DEV-NOTES 25)
                    Runnable prev = pendingTap;
                    pendingTap = null;
                    removeCallbacks(prev);
                    prev.run();
                }
                pendingTap = new Runnable() {
                    @Override
                    public void run() {
                        pendingTap = null;
                        if (tapListener != null) tapListener.onCellTap(fx, fy);
                    }
                };
                postDelayed(pendingTap, 260);
                return true;
            }
        }
        return false;
    }

    private void markAt(float vx, float vy) {
        int[] c = cellAt(vx, vy);
        if (c != null) {
            lastDragX = c[0];
            lastDragY = c[1];
            if (assistDragListener != null) assistDragListener.onAssistDragCell(c[0], c[1]);
        }
    }

    private void markLine(float vx, float vy) {
        int[] c = cellAt(vx, vy);
        if (c == null) {
            lastDragX = -1;
            lastDragY = -1;
            return;
        }
        if (lastDragX < 0) {
            markAt(vx, vy);
        } else {
            int dx = Math.abs(c[0] - lastDragX);
            int dy = Math.abs(c[1] - lastDragY);
            int steps = Math.max(dx, dy);
            for (int s = 0; s <= steps; s++) {
                int ix = lastDragX + (int) Math.round(
                        (c[0] - lastDragX) * (steps == 0 ? 0 : s / (float) steps));
                int iy = lastDragY + (int) Math.round(
                        (c[1] - lastDragY) * (steps == 0 ? 0 : s / (float) steps));
                if (assistDragListener != null) assistDragListener.onAssistDragCell(ix, iy);
            }
        }
        lastDragX = c[0];
        lastDragY = c[1];
    }

    /** 把指定格居中显示(拼豆模式"定位未拼"用) */
    public void centerOn(int gx, int gy) {
        if (pattern == null || pattern.cols == 0 || fitCell() <= 0) return;
        float cell = fitCell() * zoom;
        float w = getWidth();
        float h = getHeight();
        float cx = (gx + 0.5f) * cell;
        float cy = (gy + 0.5f) * cell;
        float cw = pattern.cols * cell;
        float ch = pattern.rows * cell;
        if (cw <= w) offX = (w - cw) / 2f;
        else offX = Math.max(Math.min(w / 2f - cx, 0), w - cw);
        if (ch <= h) offY = (h - ch) / 2f;
        else offY = Math.max(Math.min(h / 2f - cy, 0), h - ch);
        invalidate();
    }

    /** 在指定格上画一个渐隐的橙色高亮框(定位反馈) */
    public void flashCell(int gx, int gy) {
        flashCell = new int[]{gx, gy};
        flashUntil = SystemClock.uptimeMillis() + 1400;
        invalidate();
        postInvalidateDelayed(1500);
    }

    /**
     * 画笔模式的触摸处理:单指按下开始涂,移动跟随(两点间线性插值补格,
     * 快速滑动不留缝),第二根手指落下则中断笔画并放行给缩放手势。
     */
    private boolean handlePaintTouch(MotionEvent event) {
        if (paintListener == null || pattern == null || fitCell() <= 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                paintStroke = true;
                strokeMoved = false;
                longPressFired = false;
                lastPaintedX = -1;
                lastPaintedY = -1;
                paintListener.onStrokeStart();
                // 落笔延迟到长按判定之后:长按 = 油漆桶,移动/抬起 = 普通涂色
                downX = event.getX();
                downY = event.getY();
                downCell = cellAt(downX, downY);
                if (!dropper) armLongPress();   // 吸管模式不需要长按填充
                return true;
            case MotionEvent.ACTION_MOVE:
                if (paintStroke && event.getPointerCount() == 1) {
                    if (dropper) return true;   // 吸管只取色不涂色
                    if (longPressFired) return true;   // 填充后吞掉剩余滑动
                    if (!strokeMoved) {
                        if (!isBeyondSlop(event)) return true;   // 未出阈值,继续等长按
                        cancelLongPressCheck();
                        strokeMoved = true;
                        paintAt(downX, downY);   // 补上起笔那一格
                    }
                    paintLine(event.getX(), event.getY());
                    return true;
                }
                if (event.getPointerCount() > 1) paintStroke = false;
                cancelLongPressCheck();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelLongPressCheck();
                if (!longPressFired && !strokeMoved && downCell != null) {
                    if (dropper) {
                        if (dropListener != null) {
                            dropListener.onDropCell(downCell[0], downCell[1]);
                        }
                    } else {
                        paintAt(downX, downY);   // 单点即涂一格
                    }
                }
                longPressFired = false;
                strokeMoved = false;
                downCell = null;
                paintStroke = false;
                lastPaintedX = -1;
                lastPaintedY = -1;
                return true;
        }
        return false;
    }

    /** 启动长按计时:超时仍按着且未滑动就触发油漆桶 */
    private void armLongPress() {
        cancelLongPressCheck();
        longPressCheck = new Runnable() {
            @Override
            public void run() {
                if (!paintStroke || strokeMoved || longPressFired) return;
                longPressFired = true;
                if (downCell != null && longPressListener != null) {
                    longPressListener.onCellLongPress(downCell[0], downCell[1]);
                }
            }
        };
        postDelayed(longPressCheck, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelLongPressCheck() {
        if (longPressCheck != null) {
            removeCallbacks(longPressCheck);
            longPressCheck = null;
        }
    }

    private boolean isBeyondSlop(MotionEvent event) {
        int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        return Math.abs(event.getX() - downX) > slop
                || Math.abs(event.getY() - downY) > slop;
    }

    /** 从上一触点到当前点按半格步长插值,逐格触发涂色;滑出画布即截断笔画 */
    private void paintLine(float vx, float vy) {
        int[] c = cellAt(vx, vy);
        if (c == null) {
            // 出界一次就当作笔画断开,避免绕边回来时误拉一条长线
            lastPaintedX = -1;
            lastPaintedY = -1;
            return;
        }
        if (lastPaintedX < 0) {
            firePaint(c[0], c[1]);
        } else {
            int dx = Math.abs(c[0] - lastPaintedX);
            int dy = Math.abs(c[1] - lastPaintedY);
            int steps = Math.max(dx, dy);
            for (int s = 0; s <= steps; s++) {
                int ix = lastPaintedX + (int) Math.round((c[0] - lastPaintedX) * (steps == 0 ? 0 : s / (float) steps));
                int iy = lastPaintedY + (int) Math.round((c[1] - lastPaintedY) * (steps == 0 ? 0 : s / (float) steps));
                firePaint(ix, iy);
            }
        }
        lastPaintedX = c[0];
        lastPaintedY = c[1];
    }

    private void paintAt(float vx, float vy) {
        int[] c = cellAt(vx, vy);
        if (c != null) {
            lastPaintedX = c[0];
            lastPaintedY = c[1];
            firePaint(c[0], c[1]);
        }
    }

    private void firePaint(int x, int y) {
        if (x < 0 || y < 0 || x >= pattern.cols || y >= pattern.rows) return;
        paintListener.onPaintCell(x, y);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    /** 画幅正好放进控件时每格的像素(效果图模式额外预留板底边距,避免 zoom=1 两侧被裁) */
    private float fitCell() {
        if (pattern == null || pattern.cols == 0 || pattern.rows == 0) return 0f;
        float pad = dp(10);
        float m = marginRatio();
        return Math.min(
                (getWidth() - 2 * pad) / (pattern.cols + 2 * m),
                (getHeight() - 2 * pad) / (pattern.rows + 2 * m));
    }

    private float marginRatio() {
        return mode == MODE_EFFECT ? 0.7f : 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (pattern == null || pattern.cols == 0) {
            hintPaint.setColor(0xFFB3A99F);
            hintPaint.setTextSize(dp(15));
            hintPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(getContext().getString(com.pindou.app.R.string.preview_hint),
                    w / 2f, h / 2f, hintPaint);
            return;
        }

        int cols = pattern.cols;
        int rows = pattern.rows;
        float cell = fitCell() * zoom;
        float m = marginRatio() * cell;
        float cw = cols * cell + 2 * m;
        float ch = rows * cell + 2 * m;

        // 居中 / 限制拖动范围
        if (cw <= w) offX = (w - cw) / 2f;
        else offX = Math.max(Math.min(offX, 0), w - cw);
        if (ch <= h) offY = (h - ch) / 2f;
        else offY = Math.max(Math.min(offY, 0), h - ch);

        canvas.save();
        canvas.translate(offX + m, offY + m);
        if (mode == MODE_EFFECT) {
            if (effect3d) {
                endReveal();   // 3D 预览不播变形,避免动画残留
                drawEffect3D(canvas, cell);
            } else {
                drawEffect(canvas, cell);
            }
        } else {
            drawPatternGrid(canvas, cell);
            if (flashCell != null && SystemClock.uptimeMillis() < flashUntil) {
                float k = 1f - (flashUntil - SystemClock.uptimeMillis()) / 1400f;
                if (flashPaint == null) {
                    flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    flashPaint.setStyle(Paint.Style.STROKE);
                }
                flashPaint.setColor(Color.argb(
                        (int) (200 * (1 - k * k)), 0xFF, 0x8C, 0x00));
                flashPaint.setStrokeWidth(Math.max(2f, cell * 0.12f));
                canvas.drawRect(flashCell[0] * cell, flashCell[1] * cell,
                        (flashCell[0] + 1) * cell, (flashCell[1] + 1) * cell, flashPaint);
            }
            // 完成 ✓ 印章:前 40% 从 1.55 倍压到 0.92(盖章),回弹到 1,末段整体淡出
            if (!popStamps.isEmpty()) {
                long now = SystemClock.uptimeMillis();
                for (int i = popStamps.size() - 1; i >= 0; i--) {
                    float[] s = popStamps.get(i);
                    float t = (now - s[2]) / (float) POP_MS;
                    if (t >= 1f) {
                        popStamps.remove(i);
                        continue;
                    }
                    float cx = (s[0] + 0.5f) * cell;
                    float cy = (s[1] + 0.5f) * cell;
                    float scale = t < 0.4f ? 1.55f - 1.57f * (t / 0.4f)
                            : t < 0.75f ? 0.92f + 0.08f * ((t - 0.4f) / 0.35f) : 1f;
                    float alpha = t < 0.75f ? 1f : 1f - (t - 0.75f) / 0.25f;
                    float r = cell * 0.62f * scale;
                    popHaloPaint.setAlpha((int) (236 * alpha));
                    popDiscPaint.setAlpha((int) (255 * alpha));
                    popRingPaint.setAlpha((int) (255 * alpha));
                    popCheckPaint.setAlpha((int) (255 * alpha));
                    canvas.drawCircle(cx, cy, r, popHaloPaint);
                    canvas.drawCircle(cx, cy, r * 0.82f, popDiscPaint);
                    popRingPaint.setStrokeWidth(Math.max(2f, r * 0.1f));
                    canvas.drawCircle(cx, cy, r * 0.82f, popRingPaint);
                    popCheckPaint.setStrokeWidth(r * 0.24f);
                    canvas.drawLine(cx - r * 0.40f, cy + r * 0.02f,
                            cx - r * 0.10f, cy + r * 0.32f, popCheckPaint);
                    canvas.drawLine(cx - r * 0.10f, cy + r * 0.32f,
                            cx + r * 0.44f, cy - r * 0.30f, popCheckPaint);
                }
                if (!popStamps.isEmpty()) postInvalidateOnAnimation();
            }
        }
        canvas.restore();
    }

    /** 圆内竖直线段:过 (x, y0Cell~y1Cell 范围) 画弦;不在圆内就不画 */
    private void chordV(Canvas c, Paint p, float x, float cx, float cy,
                        float r, float min, float max) {
        float dx = x - cx;
        float h2 = r * r - dx * dx;
        if (h2 <= 0) return;
        float half = (float) Math.sqrt(h2);
        c.drawLine(x, Math.max(min, cy - half), x, Math.min(max, cy + half), p);
    }

    /** 圆内水平线段(同上,横向版) */
    private void chordH(Canvas c, Paint p, float y, float cx, float cy,
                        float r, float min, float max) {
        float dy = y - cy;
        float h2 = r * r - dy * dy;
        if (h2 <= 0) return;
        float half = (float) Math.sqrt(h2);
        c.drawLine(Math.max(min, cx - half), y, Math.min(max, cx + half), y, p);
    }

    /** 尖顶正六边形路径(顶点朝上),r = 中心到顶点距离 */
    private static Path hexPath(float cx, float cy, float r) {
        Path path = new Path();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 2 + i * Math.PI / 3;
            float x = (float) (cx + r * Math.cos(a));
            float y = (float) (cy - r * Math.sin(a));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    /** 效果图:仿真的拼豆圆豆 + 拼板底板(圆形板画圆盘,六边形板画六角盘) */
    private void drawEffect(Canvas canvas, float cell) {
        int cols = pattern.cols;
        int rows = pattern.rows;
        float m = marginRatio() * cell;

        boardPaint.setColor(night ? 0xFF3A3346 : 0xFFF3EFF9);
        if (pattern.round) {
            float r = cols * cell / 2f;
            canvas.drawCircle(cols * cell / 2f, rows * cell / 2f, r + m * 0.9f, boardPaint);
        } else if (pattern.hex) {
            canvas.drawPath(hexPath(cols * cell / 2f, rows * cell / 2f,
                    cols * cell / 2f + m * 0.9f), boardPaint);
        } else {
            canvas.drawRoundRect(-m, -m, cols * cell + m, rows * cell + m,
                    Math.max(6f, m * 0.8f), Math.max(6f, m * 0.8f), boardPaint);
        }

        pegPaint.setColor(night ? 0x55FFFFFF : 0xFFE2D9F0);
        beadPaint.setStyle(Paint.Style.FILL);
        float ringW = Math.max(1f, cell * 0.06f);
        ringPaint.setStrokeWidth(ringW);

        // 变形动画:底板上先铺源照片,已扫到的格子逐格弹出豆子盖住照片
        if (revealing && revealPhoto != null) {
            canvas.save();
            canvas.clipPath(platePath(cols, rows, cell, m));
            canvas.drawBitmap(revealPhoto, null,
                    new android.graphics.RectF(-m, -m,
                            cols * cell + m, rows * cell + m), photoPaint);
            canvas.restore();
        }

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (pattern.outsideShape(x, y)) continue;
                float k = revealing ? revealK(x, y) : 1f;
                if (k <= 0f) continue;   // 未扫到:这一格还是照片
                int idx = pattern.cellAt(x, y);
                float cx = (x + 0.5f) * cell;
                float cy = (y + 0.5f) * cell;
                float r = cell * 0.46f * (0.55f + 0.45f * k);
                if (idx < 0) {
                    canvas.drawCircle(cx, cy, cell * 0.15f * k, pegPaint);
                    continue;
                }
                int rgb = pattern.palette.get(idx).rgb;
                beadPaint.setColor(0xFF000000 | rgb);
                canvas.drawCircle(cx, cy, r, beadPaint);
                ringPaint.setColor(0xFF000000 | ColorMath.darken(rgb, 0.72f));
                canvas.drawCircle(cx, cy, r - ringW * 0.5f, ringPaint);
                if (cell > dp(16)) {
                    glossPaint.setColor(0x46FFFFFF);
                    canvas.drawCircle(cx - cell * 0.14f, cy - cell * 0.16f,
                            cell * 0.11f * k, glossPaint);
                }
            }
        }
        if (revealing) {
            postInvalidateOnAnimation();
        }
    }

    /** 底板轮廓(圆形/六角/圆角矩形),变形动画用它在铺照片时裁出板形 */
    private Path platePath(int cols, int rows, float cell, float m) {
        Path p = new Path();
        if (pattern.round) {
            p.addCircle(cols * cell / 2f, rows * cell / 2f,
                    cols * cell / 2f + m * 0.9f, Path.Direction.CW);
        } else if (pattern.hex) {
            p.addPath(hexPath(cols * cell / 2f, rows * cell / 2f,
                    cols * cell / 2f + m * 0.9f));
        } else {
            p.addRoundRect(-m, -m, cols * cell + m, rows * cell + m,
                    Math.max(6f, m * 0.8f), Math.max(6f, m * 0.8f), Path.Direction.CW);
        }
        return p;
    }

    /** 效果图 3D 预览:俯视 3/4 透视 + 圆豆圆柱光影(纯 Canvas 伪 3D,v2.44) */
    private boolean effect3d;

    /** 切换效果图 3D 预览 */
    public void setEffect3D(boolean on) {
        effect3d = on;
        invalidate();
    }

    /**
     * 3D 预览:整面纵向压缩成俯视 3/4 视角;每颗豆画成有厚度的圆柱
     * (底部深色侧壁 + 顶面原色 + 中孔 + 高光),并带右下投影;
     * 底板加厚一块深色边,营造"板立在桌上"的感觉。逐行扫描顺序绘制,
     * 无需深度排序。
     */
    private void drawEffect3D(Canvas canvas, float cell) {
        int cols = pattern.cols;
        int rows = pattern.rows;
        float m = marginRatio() * cell + cell * 0.5f;

        canvas.save();
        canvas.scale(1f, 0.8f);

        // 底板厚度(深色) + 顶面
        float thick = Math.max(3f, cell * 0.22f);
        boardPaint.setColor(night ? 0xFF2A2534 : 0xFFDFD9EC);
        if (pattern.round) {
            float r = cols * cell / 2f;
            canvas.drawCircle(cols * cell / 2f, rows * cell / 2f + thick,
                    r + m * 0.9f, boardPaint);
            boardPaint.setColor(night ? 0xFF3A3346 : 0xFFF3EFF9);
            canvas.drawCircle(cols * cell / 2f, rows * cell / 2f,
                    r + m * 0.9f, boardPaint);
        } else if (pattern.hex) {
            canvas.drawPath(hexPath(cols * cell / 2f, rows * cell / 2f + thick,
                    cols * cell / 2f + m * 0.9f), boardPaint);
            boardPaint.setColor(night ? 0xFF3A3346 : 0xFFF3EFF9);
            canvas.drawPath(hexPath(cols * cell / 2f, rows * cell / 2f,
                    cols * cell / 2f + m * 0.9f), boardPaint);
        } else {
            float rr = Math.max(6f, m * 0.8f);
            canvas.drawRoundRect(-m, -m + thick, cols * cell + m, rows * cell + m + thick,
                    rr, rr, boardPaint);
            boardPaint.setColor(night ? 0xFF3A3346 : 0xFFF3EFF9);
            canvas.drawRoundRect(-m, -m, cols * cell + m, rows * cell + m, rr, rr, boardPaint);
        }

        pegPaint.setColor(night ? 0x55FFFFFF : 0xFFE2D9F0);
        beadPaint.setStyle(Paint.Style.FILL);
        float ringW = Math.max(1f, cell * 0.06f);
        ringPaint.setStrokeWidth(ringW);
        float lift = cell * 0.08f;          // 豆顶面相对格心的抬升
        float shx = cell * 0.10f, shy = cell * 0.14f;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (pattern.outsideShape(x, y)) continue;
                int idx = pattern.cellAt(x, y);
                float cx = (x + 0.5f) * cell;
                float cy = (y + 0.5f) * cell;
                if (idx < 0) {
                    canvas.drawCircle(cx, cy, cell * 0.15f, pegPaint);
                    continue;
                }
                int rgb = pattern.palette.get(idx).rgb;

                // 投影
                shadowPaint.setColor(night ? 0x66000000 : 0x3C40354E);
                canvas.drawCircle(cx + shx, cy + shy, cell * 0.46f, shadowPaint);
                // 侧壁(加深):先画下移的圆柱底
                beadPaint.setColor(0xFF000000 | ColorMath.darken(rgb, 0.62f));
                canvas.drawCircle(cx, cy + lift * 0.9f, cell * 0.46f, beadPaint);
                // 顶面
                beadPaint.setColor(0xFF000000 | rgb);
                canvas.drawCircle(cx, cy - lift, cell * 0.46f, beadPaint);
                // 内孔
                beadPaint.setColor(0xFF000000 | ColorMath.darken(rgb, 0.55f));
                canvas.drawCircle(cx, cy - lift, cell * 0.13f, beadPaint);
                // 高光
                glossPaint.setColor(0x66FFFFFF);
                canvas.drawCircle(cx - cell * 0.15f, cy - lift - cell * 0.17f,
                        cell * 0.12f, glossPaint);
                if (cell > dp(14)) {
                    ringPaint.setColor(0x8CFFFFFF);
                    ringPaint.setStrokeWidth(cell * 0.05f);
                    canvas.drawArc(cx - cell * 0.30f, cy - lift - cell * 0.30f,
                            cx + cell * 0.30f, cy - lift + cell * 0.30f,
                            -160f, 70f, false, ringPaint);
                }
            }
        }
        canvas.restore();
    }

    /** 图纸:格子 + 网格线 + 29 格拼板分隔线 + 符号 + 坐标(圆形板画圆,六边形板画六角) */
    private void drawPatternGrid(Canvas canvas, float cell) {
        int cols = pattern.cols;
        int rows = pattern.rows;
        float w = cols * cell;
        float h = rows * cell;
        boolean round = pattern.round;
        float[] span = new float[2];    // 六边形板弦段区间复用,避免循环里反复分配

        // 白底(圆形板为圆面,六边形板为六角面);夜间图纸纸面转暗
        cellPaint.setColor(night ? 0xFF2E2938 : Color.WHITE);
        if (round) {
            float r = Math.min(w, h) / 2f;
            canvas.drawCircle(w / 2f, h / 2f, r, cellPaint);
        } else if (pattern.hex) {
            canvas.drawPath(hexPath(w / 2f, h / 2f, Math.min(w, h) / 2f), cellPaint);
        } else {
            canvas.drawRect(-1, -1, w + 1, h + 1, cellPaint);
        }

        // 描摹底图:半透明照片垫在格子下面,照着描轮廓用
        if (traceBitmap != null && !traceBitmap.isRecycled() && traceVisible) {
            traceDst.set(0, 0, w, h);
            tracePaint.setAlpha(95);
            if (pattern.hex) {
                canvas.save();
                canvas.clipPath(hexPath(w / 2f, h / 2f, Math.min(w, h) / 2f));
                canvas.drawBitmap(traceBitmap, null, traceDst, tracePaint);
                canvas.restore();
            } else {
                canvas.drawBitmap(traceBitmap, null, traceDst, tracePaint);
            }
        }

        // 颜色格子
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (pattern.outsideShape(x, y)) continue;
                int idx = pattern.cellAt(x, y);
                if (idx < 0) continue;
                cellPaint.setColor(0xFF000000 | pattern.palette.get(idx).rgb);
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, cellPaint);
            }
        }

        // 空格画小叉(板外格不画)
        if (pattern.emptyCount > 0) {
            emptyPaint.setColor(night ? 0x59FFFFFF : 0xFFCFCFCF);
            emptyPaint.setStrokeWidth(Math.max(1f, cell * 0.06f));
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (pattern.outsideShape(x, y)) continue;
                    if (pattern.cellAt(x, y) >= 0) continue;
                    float x0 = x * cell + cell * 0.3f;
                    float y0 = y * cell + cell * 0.3f;
                    float x1 = (x + 1) * cell - cell * 0.3f;
                    float y1 = (y + 1) * cell - cell * 0.3f;
                    canvas.drawLine(x0, y0, x1, y1, emptyPaint);
                    canvas.drawLine(x1, y0, x0, y1, emptyPaint);
                }
            }
        }

        // 细网格线(圆形/六边形板只画板内弦段);开关开就画,不再按缩放自动隐藏
        if (showGrid) {
            gridPaint.setColor(night ? 0x2EFFFFFF : 0x33888888);
            gridPaint.setStrokeWidth(1f);
            for (int x = 1; x < cols; x++) {
                if (round) {
                    chordV(canvas, gridPaint, x * cell, w / 2f, h / 2f,
                            Math.min(w, h) / 2f, 0f, h);
                } else if (pattern.hex) {
                    if (BeadPattern.hexChordV(w, h, x * cell, span)) {
                        canvas.drawLine(x * cell, span[0], x * cell, span[1], gridPaint);
                    }
                } else {
                    canvas.drawLine(x * cell, 0, x * cell, h, gridPaint);
                }
            }
            for (int y = 1; y < rows; y++) {
                if (round) {
                    chordH(canvas, gridPaint, y * cell, w / 2f, h / 2f,
                            Math.min(w, h) / 2f, 0f, w);
                } else if (pattern.hex) {
                    if (BeadPattern.hexChordH(w, h, y * cell, span)) {
                        canvas.drawLine(span[0], y * cell, span[1], y * cell, gridPaint);
                    }
                } else {
                    canvas.drawLine(0, y * cell, w, y * cell, gridPaint);
                }
            }
        }

        // 每 29 格一条拼板分隔线
        float boardW = Math.max(2f, cell * 0.1f);
        boardLinePaint.setColor(night ? 0x8CC9BFD6 : 0xFF9A9086);
        boardLinePaint.setStrokeWidth(boardW);
        for (int x = 29; x < cols; x += 29) {
            if (round) {
                chordV(canvas, boardLinePaint, x * cell, w / 2f, h / 2f,
                        Math.min(w, h) / 2f, 0f, h);
            } else if (pattern.hex) {
                if (BeadPattern.hexChordV(w, h, x * cell, span)) {
                    canvas.drawLine(x * cell, span[0], x * cell, span[1], boardLinePaint);
                }
            } else {
                canvas.drawLine(x * cell, 0, x * cell, h, boardLinePaint);
            }
        }
        for (int y = 29; y < rows; y += 29) {
            if (round) {
                chordH(canvas, boardLinePaint, y * cell, w / 2f, h / 2f,
                        Math.min(w, h) / 2f, 0f, w);
            } else if (pattern.hex) {
                if (BeadPattern.hexChordH(w, h, y * cell, span)) {
                    canvas.drawLine(span[0], y * cell, span[1], y * cell, boardLinePaint);
                }
            } else {
                canvas.drawLine(0, y * cell, w, y * cell, boardLinePaint);
            }
        }

        // 外框
        borderPaint.setColor(night ? 0xFFB9AFC6 : 0xFF6E655C);
        borderPaint.setStrokeWidth(2f);
        if (round) {
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - 1f, borderPaint);
        } else if (pattern.hex) {
            canvas.drawPath(hexPath(w / 2f, h / 2f, Math.min(w, h) / 2f - 1f), borderPaint);
        } else {
            canvas.drawRect(0, 0, w, h, borderPaint);
        }

        // 符号(开关开就画;缩得太小时字会很小,但不至于"开关失灵")
        if (showSymbols) {
            symbolPaint.setTextSize(cell * 0.42f);
            Paint.FontMetrics fm = symbolPaint.getFontMetrics();
            float dy = -(fm.ascent + fm.descent) / 2f;
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    int idx = pattern.cellAt(x, y);
                    if (idx < 0) continue;
                    int rgb = pattern.palette.get(idx).rgb;
                    symbolPaint.setColor(ColorMath.textColorOn(rgb));
                    String sym = PatternEngine.symbolFor(idx);
                    canvas.drawText(sym, (x + 0.5f) * cell, (y + 0.5f) * cell + dy, symbolPaint);
                }
            }
        }

        // 坐标编号
        if (cell >= dp(16)) {
            int step = cell >= dp(22) ? 1 : 5;
            labelPaint.setColor(night ? 0xFF9A93A8 : 0xFF9A938C);
            labelPaint.setTextSize(cell * 0.3f);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            float dy = -(fm.ascent + fm.descent) / 2f;
            for (int x = 0; x < cols; x += step) {
                canvas.drawText(String.valueOf(x + 1), (x + 0.5f) * cell,
                        -cell * 0.45f + dy, labelPaint);
            }
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            for (int y = 0; y < rows; y += step) {
                canvas.drawText(String.valueOf(y + 1), -cell * 0.18f,
                        (y + 0.5f) * cell + dy, labelPaint);
            }
        }

        // 拼豆模式:非当前颜色蒙上纸色,已完成的格子描薄荷绿边;
        // 按板引导时当前板以外的格子整片蒙灰,逐行引导时当前行以外的格子整片蒙灰
        if (assistOn) {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (pattern.outsideShape(x, y)) continue;
                    int idx = pattern.cellAt(x, y);
                    if (idx < 0) continue;
                    boolean outsideBand =
                            (assistBoardMode && assistBoardRect != null
                                    && (x < assistBoardRect.left || x >= assistBoardRect.right
                                    || y < assistBoardRect.top || y >= assistBoardRect.bottom))
                            || (assistRowMode && y != assistRow);
                    if (outsideBand) {
                        cellPaint.setColor(night ? 0xB8332C40 : 0xB8EFE9DC);
                        canvas.drawRect(x * cell, y * cell,
                                (x + 1) * cell, (y + 1) * cell, cellPaint);
                        continue;
                    }
                    if (assistFocus >= 0 && idx != assistFocus) {
                        cellPaint.setColor(night ? 0xE6332C40 : 0xE6FDF8EF);
                        canvas.drawRect(x * cell, y * cell,
                                (x + 1) * cell, (y + 1) * cell, cellPaint);
                    }
                    if (assistDone != null && assistDone.contains(y * cols + x)) {
                        boolean isFocus = assistFocus < 0 || idx == assistFocus;
                        emptyPaint.setColor(isFocus ? 0xFF2EC4B6 : 0x662EC4B6);
                        emptyPaint.setStrokeWidth(Math.max(2f, cell * 0.12f));
                        float inset = cell * 0.12f;
                        canvas.drawRect(x * cell + inset, y * cell + inset,
                                (x + 1) * cell - inset, (y + 1) * cell - inset, emptyPaint);
                    }
                }
            }
            // 当前行高亮框:墨衬底 + 黄油主线(与按板外框同语言)
            if (assistRowMode) {
                boardFramePaint.setStyle(Paint.Style.STROKE);
                float fr = Math.max(3f, cell * 0.14f);
                boardFramePaint.setColor(0xFF2A2735);
                boardFramePaint.setStrokeWidth(fr * 2.2f);
                canvas.drawRect(0, assistRow * cell, cols * cell,
                        (assistRow + 1) * cell, boardFramePaint);
                boardFramePaint.setColor(0xFFFFCF56);
                boardFramePaint.setStrokeWidth(fr);
                canvas.drawRect(0, assistRow * cell, cols * cell,
                        (assistRow + 1) * cell, boardFramePaint);
            }
            // 当前板外框:墨衬底 + 黄油主线(和拼板分隔线区分开)
            if (assistBoardMode && assistBoardRect != null) {
                boardFramePaint.setStyle(Paint.Style.STROKE);
                float fr = Math.max(3f, cell * 0.14f);
                boardFramePaint.setColor(0xFF2A2735);
                boardFramePaint.setStrokeWidth(fr * 2.2f);
                canvas.drawRect(assistBoardRect.left * cell, assistBoardRect.top * cell,
                        assistBoardRect.right * cell, assistBoardRect.bottom * cell,
                        boardFramePaint);
                boardFramePaint.setColor(0xFFFFCF56);
                boardFramePaint.setStrokeWidth(fr);
                canvas.drawRect(assistBoardRect.left * cell, assistBoardRect.top * cell,
                        assistBoardRect.right * cell, assistBoardRect.bottom * cell,
                        boardFramePaint);
            }
        }
    }
}
