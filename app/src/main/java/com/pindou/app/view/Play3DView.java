package com.pindou.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 3D 把玩:可旋转的拼豆成品台(纯 Canvas 正交投影,零权限零网络)。
 * 拖动 = 旋转(yaw/tilt),双指 = 缩放,双击 = 复位;熨烫模式下按住拖动
 * 熨斗把豆豆"熔连"起来(致敬熨烫定型,全部烫完自动进入展示自转)。
 * 手势全部自己算,不依赖 GestureDetector(DEV-NOTES 20)。
 * 投影数学在 {@link Play3DProjector}(纯 Java,qa 单测)。
 */
public final class Play3DView extends View {

    /** 熨烫进度回调(Activity 更新顶栏文字/完成提示) */
    public interface Listener {
        void onIronProgress(int done, int total);

        void onIronComplete();
    }

    private static final float BEAD_R = 0.46f;      // 豆半径(格)
    private static final float BEAD_H = 0.34f;      // 豆高(格)
    private static final float MELT_H = 0.10f;      // 熔化后豆高
    private static final float PLATE_T = 0.22f;     // 底板厚(格)
    private static final float IRON_R = 1.9f;       // 熨斗底板半径(格)

    private BeadPattern pattern;
    private boolean[] melted;
    private int meltedCount;
    private int beadTotal = -1;

    // 生长动画:豆豆按颜色分批从空中落下(v2.58,GIF 导出的时间轴也是它)
    private boolean animPlaying;
    private long animStartReal, animMs, animDur;
    private int[] spawnSeq;          // 落豆顺序(只含有豆的格)
    private float[] spawnAt;         // 每格起落时刻占整个动画的比例 0..1
    private boolean frozen;          // GIF 导出时冻结自转/自动重绘

    private double yaw = Math.toRadians(32);
    private double tiltDeg = 58;
    private float zoom = 1f;
    private boolean autoSpin = true;
    private boolean ironMode = false;

    // 手势状态
    private float lastX, lastY;
    private boolean pinching;
    private float pinchStartSpan, pinchStartZoom;
    private long lastDownAt;
    private float lastDownX, lastDownY;
    private boolean ironDown;
    private double ironU = -999, ironV = -999;
    private final List<float[]> steam = new ArrayList<>();   // {x,y,age}
    private long steamAt;

    // 深度排序缓存(yaw 变化才重建);比较器是匿名内部类(裸管线不支持 lambda)
    private Integer[] order;
    private double[] depthKeys;
    private final Comparator<Integer> depthAsc = new Comparator<Integer>() {
        @Override
        public int compare(Integer a, Integer b) {
            return Double.compare(depthKeys[a], depthKeys[b]);
        }
    };
    private double sortedYaw = Double.NaN;

    private final Paint platePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint plateEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pegPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint steamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ironBody = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ironSole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tmpPath = new Path();
    private final RectF tmpRect = new RectF();

    private Listener listener;

    public Play3DView(Context context) {
        super(context);
    }

    public Play3DView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Play3DView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setPattern(BeadPattern p) {
        pattern = p;
        melted = p == null ? null : new boolean[p.cols * p.rows];
        meltedCount = 0;
        beadTotal = p == null ? 0 : p.totalBeads;
        order = null;
        depthKeys = null;
        sortedYaw = Double.NaN;
        animPlaying = false;
        animMs = 0;
        animDur = 0;
        frozen = false;
        resetView();
    }

    public void setIronMode(boolean on) {
        ironMode = on;
        if (on && pattern != null) {
            // 熨斗存板面索引坐标(0..W / 0..H),初始在板中心
            autoSpin = false;
            ironU = pattern.cols / 2.0;
            ironV = pattern.rows / 2.0;
        }
        invalidate();
    }

    public void resetView() {
        yaw = Math.toRadians(32);
        tiltDeg = 58;
        zoom = 1f;
        autoSpin = true;
        invalidate();
    }

    // ---------------- 生长动画 ----------------

    private static final long DROP_MS = 430;   // 单颗豆下落耗时

    /** 落豆计划:按颜色分批(用量多的先落),批内按行序波浪推进 */
    private void buildSpawnPlan() {
        int n = pattern.cols * pattern.rows;
        spawnAt = new float[n];
        // 颜色 -> 批次(按用量从多到少,与 usedColors 顺序一致)
        java.util.Map<Integer, Integer> rank = new java.util.HashMap<>();
        for (int i = 0; i < pattern.usedColors.size(); i++) {
            rank.put(pattern.usedColors.get(i).index, i);
        }
        int groups = Math.max(1, pattern.usedColors.size());
        List<Integer>[] byGroup = new List[groups];
        for (int y = 0; y < pattern.rows; y++) {
            for (int x = 0; x < pattern.cols; x++) {
                int idx = pattern.cellAt(x, y);
                if (idx < 0) continue;
                int g = rank.containsKey(idx) ? rank.get(idx) : groups - 1;
                if (byGroup[g] == null) byGroup[g] = new ArrayList<>();
                byGroup[g].add(y * pattern.cols + x);
            }
        }
        int beadCells = 0;
        for (List<Integer> l : byGroup) {
            if (l != null) beadCells += l.size();
        }
        spawnSeq = new int[beadCells];
        int at = 0;
        int nonEmpty = 0;
        for (List<Integer> l : byGroup) {
            if (l == null) continue;
            nonEmpty++;
        }
        int gDone = 0;
        for (List<Integer> l : byGroup) {
            if (l == null) continue;
            float slice = nonEmpty > 0 ? 1f / nonEmpty : 1f;
            float base = (nonEmpty - 1 > 0 ? Math.min(gDone, nonEmpty - 1) : 0) * slice;
            for (int i = 0; i < l.size(); i++) {
                int cell = l.get(i);
                spawnSeq[at++] = cell;
                float within = l.size() > 1 ? (float) i / (l.size() - 1) : 0f;
                spawnAt[cell] = Math.min(1f, base + within * slice * 0.96f);
            }
            gDone++;
        }
        animDur = Math.max(6000, Math.min(20000, 4500 + beadTotal * 7L));
    }

    /** 播放生长动画(进入页面自动来一遍,「▶ 重播」再触发) */
    public void startBuildAnimation() {
        if (pattern == null) return;
        buildSpawnPlan();
        animPlaying = true;
        animStartReal = android.os.SystemClock.uptimeMillis();
        autoSpin = false;
        invalidate();
    }

    /** GIF 导出:把动画钉到某个时刻后用 view.draw 离屏渲染 */
    public void setAnimTime(long ms) {
        animPlaying = false;
        animMs = Math.max(0, Math.min(animDur, ms));
        invalidate();
    }

    public long getAnimDuration() {
        return animDur;
    }

    public void setFrozen(boolean on) {
        frozen = on;
    }

    /** 动画没播完时点熨斗/导出:直接把所有豆落位 */
    public void skipAnimation() {
        animPlaying = false;
        animMs = animDur;
    }

    public boolean isAnimPlaying() {
        return animPlaying;
    }

    // ---------------- 测量与绘制 ----------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pattern == null || melted == null) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 生长动画时间轴:播放中按真实时钟,导出(setAnimTime)按钉住的时刻
        long t;
        if (animPlaying) {
            t = android.os.SystemClock.uptimeMillis() - animStartReal;
            if (t >= animDur) {
                animPlaying = false;
                animMs = animDur;
                autoSpin = true;
                t = animDur;
            } else if (!frozen) {
                postInvalidateOnAnimation();
            }
        } else {
            t = animMs;
        }
        boolean animOn = t < animDur;   // 豆还没全部落位

        if (!animPlaying && !frozen && autoSpin && !ironDown) {
            yaw += 0.006;
            postInvalidateOnAnimation();
        }
        int W = pattern.cols, H = pattern.rows;
        float maxDim = Math.max(W, H);
        float s = Math.min(w, h) / (maxDim + 6f) * zoom;
        float cx = w / 2f;
        float cy = h / 2f;
        double tiltRad = Play3DProjector.clampTiltRad(tiltDeg);
        double sinT = Math.sin(tiltRad), cosT = Math.cos(tiltRad);
        boolean lod = W * H > 6000;      // 大图省略高光与豆孔
        boolean lod2 = W * H > 20000;    // 特大图再省略定位点

        ensureOrder(sinT, cosT);

        // 底板:先画加厚暗层(下移 PLATE_T·cosT·s),再画顶面
        drawPlate(canvas, W, H, cx, cy, s, sinT, cosT);

        // 豆与定位点:按 depth 升序(远→近)
        pegPaint.setColor(0x559A938C);
        for (int i = 0; i < order.length; i++) {
            int idx = order[i];
            int x = idx % W, y = idx / W;
            double u = x + 0.5 - W / 2.0;
            double v = y + 0.5 - H / 2.0;
            boolean melt = melted[idx];
            int pIdx = pattern.cellAt(x, y);
            if (pIdx < 0) {
                if (!lod2 && !melt) {
                    drawPeg(canvas, u, v, cx, cy, s, sinT, cosT);
                }
                continue;
            }
            float drop = 0f;
            if (animOn && spawnAt != null) {
                float startMs = spawnAt[idx] * (animDur - DROP_MS);
                float local = (t - startMs) / (float) DROP_MS;
                if (local <= 0f) continue;            // 还没轮到它落
                if (local < 1f) drop = 4.5f * (1f - local) * (1f - local);
            }
            int rgb = pattern.palette.get(pIdx).rgb;
            drawBead(canvas, u, v, rgb, melt, drop, cx, cy, s, sinT, cosT, lod);
        }

        // 熨斗与蒸汽
        if (ironMode) {
            drawIron(canvas, W, H, cx, cy, s, sinT, cosT);
        }
    }

    /** yaw 变化超过阈值才重排(排序是唯一的大开销,拖动时最多 25 次/秒) */
    private void ensureOrder(double sinT, double cosT) {
        if (order != null && !Double.isNaN(sortedYaw)
                && Math.abs(yaw - sortedYaw) < 0.02) {
            return;
        }
        int n = pattern.cols * pattern.rows;
        if (order == null || order.length != n) {
            order = new Integer[n];
            depthKeys = new double[n];
        }
        double cy = Math.cos(yaw), sy = Math.sin(yaw);
        for (int i = 0; i < n; i++) {
            order[i] = i;
            int x = i % pattern.cols, y = i / pattern.cols;
            double u = x + 0.5 - pattern.cols / 2.0;
            double v = y + 0.5 - pattern.rows / 2.0;
            depthKeys[i] = Play3DProjector.depthKey(u, v, yaw);
        }
        Arrays.sort(order, depthAsc);
        sortedYaw = yaw;
    }

    private void drawPlate(Canvas c, int W, int H, float cx, float cy,
                           float s, double sinT, double cosT) {
        float m = 0.55f;
        double[] pts = {-m, -m, W + m, -m, W + m, H + m, -m, H + m};
        // 暗层(加厚)
        tmpPath.reset();
        for (int k = 0; k < 4; k++) {
            double[] p = Play3DProjector.project(pts[k * 2], pts[k * 2 + 1],
                    -PLATE_T, yaw, sinT, cosT);
            float px = cx + (float) p[0] * s, py = cy + (float) p[1] * s;
            if (k == 0) tmpPath.moveTo(px, py);
            else tmpPath.lineTo(px, py);
        }
        tmpPath.close();
        plateEdgePaint.setColor(0xFFC9C2DC);
        c.drawPath(tmpPath, plateEdgePaint);
        // 顶面
        tmpPath.reset();
        for (int k = 0; k < 4; k++) {
            double[] p = Play3DProjector.project(pts[k * 2], pts[k * 2 + 1],
                    0, yaw, sinT, cosT);
            float px = cx + (float) p[0] * s, py = cy + (float) p[1] * s;
            if (k == 0) tmpPath.moveTo(px, py);
            else tmpPath.lineTo(px, py);
        }
        tmpPath.close();
        platePaint.setColor(0xFFF3EFF9);
        c.drawPath(tmpPath, platePaint);
    }

    private void drawPeg(Canvas c, double u, double v, float cx, float cy,
                         float s, double sinT, double cosT) {
        double[] p = Play3DProjector.project(u, v, 0.06, yaw, sinT, cosT);
        float px = cx + (float) p[0] * s, py = cy + (float) p[1] * s;
        float r = 0.12f * s;
        tmpRect.set(px - r, py - r * (float) sinT, px + r, py + r * (float) sinT);
        c.drawOval(tmpRect, pegPaint);
    }

    private void drawBead(Canvas c, double u, double v, int rgb, boolean melt,
                          float drop, float cx, float cy, float s,
                          double sinT, double cosT, boolean lod) {
        float bh = melt ? MELT_H : BEAD_H;
        float br = melt ? BEAD_R * 1.06f : BEAD_R;
        double[] top = Play3DProjector.project(u, v, bh + drop, yaw, sinT, cosT);
        double[] base = Play3DProjector.project(u, v, drop, yaw, sinT, cosT);
        float sx = cx + (float) top[0] * s;
        float syTop = cy + (float) top[1] * s;
        float syBase = cy + (float) base[1] * s;
        float rx = br * s;
        float ry = (float) (br * sinT) * s;
        float wallH = syBase - syTop;                 // >= 0:base 比 top 低(屏幕)

        // 熔连片:和右侧/下侧已熔邻格补一块连接面(先画,再盖豆顶);
        // 下落途中(drop > 0)先不连,落定才焊上
        if (melt && drop <= 0f) {
            int x = (int) Math.round(u + pattern.cols / 2.0 - 0.5);
            int y = (int) Math.round(v + pattern.rows / 2.0 - 0.5);
            int rgbA = 0xFF000000 | ColorMath.lighten(rgb, 1.12f);
            wallPaint.setColor(rgbA);
            drawMeltLink(c, x, y, x + 1, y, cx, cy, s, sinT, cosT);
            drawMeltLink(c, x, y, x, y + 1, cx, cy, s, sinT, cosT);
        }

        // 侧壁
        int dark = 0xFF000000 | ColorMath.darken(rgb, 0.55f);
        wallPaint.setColor(dark);
        tmpRect.set(sx - rx, syTop, sx + rx, syTop + Math.max(0, wallH));
        c.drawRect(tmpRect, wallPaint);
        tmpRect.set(sx - rx, syBase - ry, sx + rx, syBase + ry);
        c.drawOval(tmpRect, wallPaint);
        // 顶面
        int topRgb = 0xFF000000 | (melt ? ColorMath.lighten(rgb, 1.08f) : rgb);
        topPaint.setColor(topRgb);
        tmpRect.set(sx - rx, syTop - ry, sx + rx, syTop + ry);
        c.drawOval(tmpRect, topPaint);
        if (!melt && !lod) {
            holePaint.setColor(0xFF000000 | ColorMath.darken(rgb, 0.55f));
            float hr = BEAD_R * 0.13f;
            tmpRect.set(sx - hr * s, syTop - (float) (hr * sinT) * s,
                    sx + hr * s, syTop + (float) (hr * sinT) * s);
            c.drawOval(tmpRect, holePaint);
            glossPaint.setColor(0x55FFFFFF);
            float gr = BEAD_R * 0.16f * s;
            c.drawCircle(sx - rx * 0.32f, syTop - ry * 0.34f, gr, glossPaint);
        } else if (melt) {
            glossPaint.setColor(0x66FFFFFF);
            float gr = BEAD_R * 0.3f * s;
            c.drawCircle(sx - rx * 0.3f, syTop - ry * 0.3f, gr, glossPaint);
        }
    }

    /** 两颗已熔豆之间的连接面:板面上 (x0,y0)-(x1,y1) 的细带(同色熔蜡) */
    private void drawMeltLink(Canvas c, int x0, int y0, int x1, int y1,
                              float cx, float cy, float s, double sinT, double cosT) {
        if (x1 < 0 || y1 < 0 || x1 >= pattern.cols || y1 >= pattern.rows) return;
        if (!melted[y1 * pattern.cols + x1]) return;
        double u0 = Math.min(x0, x1) + 0.28 - pattern.cols / 2.0;
        double v0 = Math.min(y0, y1) + 0.28 - pattern.rows / 2.0;
        double u1 = Math.max(x0, x1) + 0.72 - pattern.cols / 2.0;
        double v1 = Math.max(y0, y1) + 0.72 - pattern.rows / 2.0;
        tmpPath.reset();
        double[][] corners = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int k = 0; k < 4; k++) {
            double[] p = Play3DProjector.project(corners[k][0], corners[k][1],
                    MELT_H, yaw, sinT, cosT);
            float px = cx + (float) p[0] * s, py = cy + (float) p[1] * s;
            if (k == 0) tmpPath.moveTo(px, py);
            else tmpPath.lineTo(px, py);
        }
        tmpPath.close();
        c.drawPath(tmpPath, wallPaint);
    }

    private void drawIron(Canvas c, int W, int H, float cx, float cy, float s,
                          double sinT, double cosT) {
        if (ironU < -900) return;
        double uC = ironU - W / 2.0;      // 转回投影用的居中坐标
        double vC = ironV - H / 2.0;
        // 投影影子
        double[] sh = Play3DProjector.project(uC, vC, 0.02, yaw, sinT, cosT);
        float shx = cx + (float) sh[0] * s, shy = cy + (float) sh[1] * s;
        shadowPaint.setColor(0x3C40354E);
        float rI = IRON_R * s;
        tmpRect.set(shx - rI, shy - (float) (IRON_R * sinT) * s,
                shx + rI, shy + (float) (IRON_R * sinT) * s);
        c.drawOval(tmpRect, shadowPaint);

        // 蒸汽(熨着的时候冒)
        long now = android.os.SystemClock.uptimeMillis();
        if (ironDown && now - steamAt > 90) {
            steamAt = now;
            steam.add(new float[]{shx + rI * 0.5f, shy - rI * 0.4f, 0});
        }
        steamPaint.setColor(0x8CFFFFFF);
        for (int i = steam.size() - 1; i >= 0; i--) {
            float[] st = steam.get(i);
            st[2] += 1f;
            st[1] -= s * 0.045f;
            if (st[2] > 26) {
                steam.remove(i);
                continue;
            }
            float r = s * (0.10f + st[2] * 0.012f);
            steamPaint.setAlpha((int) (140 * (1f - st[2] / 26f)));
            c.drawCircle(st[0], st[1], r, steamPaint);
        }
        if (ironDown && !steam.isEmpty()) postInvalidateOnAnimation();

        // 熨斗本体:底板椭圆 + 尾座圆角矩形 + 手柄,玫瑰色按钮点
        float bodyLift = (float) (0.55 * cosT) * s;
        ironSole.setColor(0xFF8A93A6);
        tmpRect.set(shx - rI, shy - rI * (float) sinT - bodyLift,
                shx + rI, shy + rI * (float) sinT - bodyLift);
        c.drawOval(tmpRect, ironSole);
        ironBody.setColor(0xFF5E6B80);
        float bw = rI * 1.05f, bh = rI * 0.95f;
        tmpRect.set(shx - bw * 0.35f, shy - bodyLift - bh, shx + bw * 0.75f,
                shy - bodyLift + bh * 0.25f);
        c.drawRoundRect(tmpRect, rI * 0.35f, rI * 0.35f, ironBody);
        ironBody.setColor(0xFF465062);
        float hx = shx - bw * 0.2f, hy = shy - bodyLift - bh * 0.55f;
        tmpRect.set(hx - bw * 0.55f, hy - bh * 0.3f,
                hx + bw * 0.55f, hy + bh * 0.3f);
        c.drawRoundRect(tmpRect, bh * 0.3f, bh * 0.3f, ironBody);
        ironSole.setColor(0xFFFF6E9C);
        c.drawCircle(shx + bw * 0.1f, shy - bodyLift - bh * 0.55f,
                rI * 0.14f, ironSole);
    }

    // ---------------- 手势(全手写,DEV-NOTES 20) ----------------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (pattern == null) return false;
        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                getParent().requestDisallowInterceptTouchEvent(true);
                autoSpin = false;
                long now = android.os.SystemClock.uptimeMillis();
                double dm = 28 * getResources().getDisplayMetrics().density;
                if (now - lastDownAt < 280
                        && Math.hypot(ev.getX() - lastDownX, ev.getY() - lastDownY) < dm) {
                    resetView();
                    lastDownAt = 0;
                    return true;
                }
                lastDownAt = now;
                lastDownX = ev.getX();
                lastDownY = ev.getY();
                lastX = ev.getX();
                lastY = ev.getY();
                if (ironMode) {
                    ironDown = true;
                    moveIron(ev.getX(), ev.getY());
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (!ironMode && ev.getPointerCount() >= 2) {
                    pinching = true;
                    pinchStartSpan = span(ev);
                    pinchStartZoom = zoom;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (ironMode) {
                    moveIron(ev.getX(), ev.getY());
                    invalidate();
                    return true;
                }
                if (pinching && ev.getPointerCount() >= 2) {
                    float span = span(ev);
                    if (pinchStartSpan > 0) {
                        zoom = Math.max(0.35f, Math.min(3f,
                                pinchStartZoom * span / pinchStartSpan));
                        invalidate();
                    }
                    return true;
                }
                float dx = ev.getX() - lastX;
                float dy = ev.getY() - lastY;
                lastX = ev.getX();
                lastY = ev.getY();
                yaw += dx * 0.011;
                tiltDeg = Math.toDegrees(Play3DProjector.clampTiltRad(
                        tiltDeg + dy * 0.28));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                if (ev.getPointerCount() <= 2) pinching = false;
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                pinching = false;
                ironDown = false;
                invalidate();
                return true;
            }
            default:
                return super.onTouchEvent(ev);
        }
    }

    private float span(MotionEvent ev) {
        float dx = ev.getX(0) - ev.getX(1);
        float dy = ev.getY(0) - ev.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    /** 屏幕点 → 板面坐标,并熔化熨斗范围内的豆(生长动画没播完时不允许烫) */
    private void moveIron(float x, float y) {
        if (animPlaying || animMs < animDur) return;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float maxDim = Math.max(pattern.cols, pattern.rows);
        float s = Math.min(w, h) / (maxDim + 6f) * zoom;
        double tiltRad = Play3DProjector.clampTiltRad(tiltDeg);
        double sinT = Math.sin(tiltRad);
        double[] uv = Play3DProjector.surfaceFromScreen(
                x - w / 2f, y - h / 2f, s, yaw, sinT);
        if (uv == null) return;
        // surfaceFromScreen 返回居中坐标,熨斗存索引坐标
        ironU = Math.max(-0.5, Math.min(pattern.cols + 0.5, uv[0] + pattern.cols / 2.0));
        ironV = Math.max(-0.5, Math.min(pattern.rows + 0.5, uv[1] + pattern.rows / 2.0));

        // 熔化范围内的豆(正交投影下直接在板面坐标量距离)
        int x0 = Math.max(0, (int) Math.floor(ironU - IRON_R - 0.5));
        int x1 = Math.min(pattern.cols - 1, (int) Math.ceil(ironU + IRON_R + 0.5));
        int y0 = Math.max(0, (int) Math.floor(ironV - IRON_R - 0.5));
        int y1 = Math.min(pattern.rows - 1, (int) Math.ceil(ironV + IRON_R + 0.5));
        boolean changed = false;
        for (int cy = y0; cy <= y1; cy++) {
            for (int cxI = x0; cxI <= x1; cxI++) {
                int idx = cy * pattern.cols + cxI;
                if (melted[idx] || pattern.cellAt(cxI, cy) < 0) continue;
                double du = cxI + 0.5 - ironU;
                double dv = cy + 0.5 - ironV;
                if (du * du + dv * dv <= IRON_R * IRON_R) {
                    melted[idx] = true;
                    meltedCount++;
                    changed = true;
                }
            }
        }
        if (changed && listener != null) {
            listener.onIronProgress(meltedCount, beadTotal);
            if (beadTotal > 0 && meltedCount >= beadTotal) {
                autoSpin = true;
                listener.onIronComplete();
            }
        }
    }
}
