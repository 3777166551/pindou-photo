package com.pindou.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
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
    private final Paint cellPaint = new Paint();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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

    // ---- 拼豆模式(逐色辅助)----
    /** true = 只突出 assistFocus 颜色,已完成的格子画描边 */
    private boolean assistOn;
    /** 当前辅助的颜色(palette 下标),-1 = 全部突出 */
    private int assistFocus = -1;
    /** 已拼好的格子(y*cols+x) */
    private java.util.Set<Integer> assistDone;

    public void setAssist(boolean on, int focusColor, java.util.Set<Integer> done) {
        assistOn = on;
        assistFocus = focusColor;
        assistDone = done;
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
                armLongPress();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (paintStroke && event.getPointerCount() == 1) {
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
                    paintAt(downX, downY);   // 单点即涂一格
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

    /** 画幅正好放进控件时每格的像素 */
    private float fitCell() {
        if (pattern == null || pattern.cols == 0 || pattern.rows == 0) return 0f;
        float pad = dp(10);
        return Math.min((getWidth() - 2 * pad) / pattern.cols, (getHeight() - 2 * pad) / pattern.rows);
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
            canvas.drawText("选择照片后自动生成预览", w / 2f, h / 2f, hintPaint);
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
            drawEffect(canvas, cell);
        } else {
            drawPatternGrid(canvas, cell);
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

    /** 效果图:仿真的拼豆圆豆 + 拼板底板(圆形板画圆盘) */
    private void drawEffect(Canvas canvas, float cell) {
        int cols = pattern.cols;
        int rows = pattern.rows;
        float m = marginRatio() * cell;

        boardPaint.setColor(0xFFEFEAE3);
        if (pattern.round) {
            float r = cols * cell / 2f;
            canvas.drawCircle(cols * cell / 2f, rows * cell / 2f, r + m * 0.9f, boardPaint);
        } else {
            canvas.drawRoundRect(-m, -m, cols * cell + m, rows * cell + m,
                    Math.max(6f, m * 0.8f), Math.max(6f, m * 0.8f), boardPaint);
        }

        pegPaint.setColor(0xFFD8D2C9);
        beadPaint.setStyle(Paint.Style.FILL);
        float ringW = Math.max(1f, cell * 0.06f);
        ringPaint.setStrokeWidth(ringW);

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
                beadPaint.setColor(0xFF000000 | rgb);
                canvas.drawCircle(cx, cy, cell * 0.46f, beadPaint);
                ringPaint.setColor(0xFF000000 | ColorMath.darken(rgb, 0.72f));
                canvas.drawCircle(cx, cy, cell * 0.46f - ringW * 0.5f, ringPaint);
                if (cell > dp(16)) {
                    glossPaint.setColor(0x46FFFFFF);
                    canvas.drawCircle(cx - cell * 0.14f, cy - cell * 0.16f, cell * 0.11f, glossPaint);
                }
            }
        }
    }

    /** 图纸:格子 + 网格线 + 29 格拼板分隔线 + 符号 + 坐标(圆形板画圆) */
    private void drawPatternGrid(Canvas canvas, float cell) {
        int cols = pattern.cols;
        int rows = pattern.rows;
        float w = cols * cell;
        float h = rows * cell;
        boolean round = pattern.round;

        // 白底(圆形板为圆面)
        cellPaint.setColor(Color.WHITE);
        if (round) {
            float r = Math.min(w, h) / 2f;
            canvas.drawCircle(w / 2f, h / 2f, r, cellPaint);
        } else {
            canvas.drawRect(-1, -1, w + 1, h + 1, cellPaint);
        }

        // 颜色格子
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (round && pattern.outsideShape(x, y)) continue;
                int idx = pattern.cellAt(x, y);
                if (idx < 0) continue;
                cellPaint.setColor(0xFF000000 | pattern.palette.get(idx).rgb);
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, cellPaint);
            }
        }

        // 空格画小叉(板外格不画)
        if (pattern.emptyCount > 0) {
            emptyPaint.setColor(0xFFCFCFCF);
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

        // 细网格线(圆形板只画弦段);开关开就画,不再按缩放自动隐藏
        if (showGrid) {
            gridPaint.setColor(0x33888888);
            gridPaint.setStrokeWidth(1f);
            for (int x = 1; x < cols; x++) {
                if (round) {
                    chordV(canvas, gridPaint, x * cell, w / 2f, h / 2f,
                            Math.min(w, h) / 2f, 0f, h);
                } else {
                    canvas.drawLine(x * cell, 0, x * cell, h, gridPaint);
                }
            }
            for (int y = 1; y < rows; y++) {
                if (round) {
                    chordH(canvas, gridPaint, y * cell, w / 2f, h / 2f,
                            Math.min(w, h) / 2f, 0f, w);
                } else {
                    canvas.drawLine(0, y * cell, w, y * cell, gridPaint);
                }
            }
        }

        // 每 29 格一条拼板分隔线
        float boardW = Math.max(2f, cell * 0.1f);
        boardLinePaint.setColor(0xFF9A9086);
        boardLinePaint.setStrokeWidth(boardW);
        for (int x = 29; x < cols; x += 29) {
            if (round) {
                chordV(canvas, boardLinePaint, x * cell, w / 2f, h / 2f,
                        Math.min(w, h) / 2f, 0f, h);
            } else {
                canvas.drawLine(x * cell, 0, x * cell, h, boardLinePaint);
            }
        }
        for (int y = 29; y < rows; y += 29) {
            if (round) {
                chordH(canvas, boardLinePaint, y * cell, w / 2f, h / 2f,
                        Math.min(w, h) / 2f, 0f, w);
            } else {
                canvas.drawLine(0, y * cell, w, y * cell, boardLinePaint);
            }
        }

        // 外框
        borderPaint.setColor(0xFF6E655C);
        borderPaint.setStrokeWidth(2f);
        if (round) {
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - 1f, borderPaint);
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
            labelPaint.setColor(0xFF9A938C);
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

        // 拼豆模式:非当前颜色蒙上纸色,已完成的格子描薄荷绿边
        if (assistOn) {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (round && pattern.outsideShape(x, y)) continue;
                    int idx = pattern.cellAt(x, y);
                    if (idx < 0) continue;
                    if (assistFocus >= 0 && idx != assistFocus) {
                        cellPaint.setColor(0xE6FDF8EF);
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
        }
    }
}
