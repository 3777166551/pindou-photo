package com.pindou.app.bead;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 生成结果:像素化 + 颜色匹配后的拼豆图纸数据 */
public final class BeadPattern {

    public final int cols;
    public final int rows;
    /** 当前使用的色板(完整列表,含未用到的颜色) */
    public final List<BeadColor> palette;
    /** 每格对应 palette 的下标,-1 表示空格(不放置;圆形/六边形板板外也是 -1) */
    public final int[] cells;
    /** 每种颜色(按 palette 下标)的使用数量 */
    public final int[] counts;
    /** 实际用到的颜色,按用量从多到少排序 */
    public final List<UsedColor> usedColors;
    public final int totalBeads;
    public final int emptyCount;
    /** 圆形拼板:内切圆以外的格子全部视为板外,不存在 */
    public final boolean round;
    /** 六边形拼板:尖顶正六边形以外的格子全部视为板外,不存在 */
    public final boolean hex;

    public BeadPattern(int cols, int rows, List<BeadColor> palette,
                       int[] cells, int[] counts, List<UsedColor> usedColors,
                       int totalBeads, int emptyCount) {
        this(cols, rows, palette, cells, counts, usedColors, totalBeads, emptyCount, false, false);
    }

    public BeadPattern(int cols, int rows, List<BeadColor> palette,
                       int[] cells, int[] counts, List<UsedColor> usedColors,
                       int totalBeads, int emptyCount, boolean round) {
        this(cols, rows, palette, cells, counts, usedColors, totalBeads, emptyCount, round, false);
    }

    public BeadPattern(int cols, int rows, List<BeadColor> palette,
                       int[] cells, int[] counts, List<UsedColor> usedColors,
                       int totalBeads, int emptyCount, boolean round, boolean hex) {
        this.cols = cols;
        this.rows = rows;
        this.palette = palette;
        this.cells = cells;
        this.counts = counts;
        this.usedColors = usedColors;
        this.totalBeads = totalBeads;
        this.emptyCount = emptyCount;
        this.round = round;
        this.hex = hex;
    }

    public int cellAt(int x, int y) {
        return cells[y * cols + x];
    }

    /** 该格是否在有效拼豆区域外(圆形板的内切圆以外 / 六边形板的轮廓以外) */
    public boolean outsideShape(int x, int y) {
        if (round) return isOutsideRound(cols, rows, x, y);
        if (hex) return isOutsideHex(cols, rows, x, y);
        return false;
    }

    /** 圆形板判定:格中心到画布中心距离 > 内切圆半径即板外 */
    public static boolean isOutsideRound(int cols, int rows, int x, int y) {
        double r = Math.min(cols, rows) / 2.0;
        double dx = x + 0.5 - cols / 2.0;
        double dy = y + 0.5 - rows / 2.0;
        return dx * dx + dy * dy > r * r;
    }

    private static final double SQRT3 = Math.sqrt(3.0);

    /**
     * 六边形板判定:尖顶正六边形(顶点朝上/下,平边朝左右),
     * 对顶高 = min(cols, rows)(上下顶点触画布上下缘),左右按 60° 边收窄。
     * 格中心到中心的偏移 dx/dy 按格为单位;中带(|dy| ≤ R/2)全宽,
     * 顶/底冠部按 (R-|dy|)·√3 收窄。
     */
    public static boolean isOutsideHex(int cols, int rows, int x, int y) {
        double r = Math.min(cols, rows) / 2.0;
        double dx = Math.abs(x + 0.5 - cols / 2.0);
        double dy = Math.abs(y + 0.5 - rows / 2.0);
        if (dy > r) return true;
        double halfW = dy <= r / 2.0 ? r * SQRT3 / 2.0 : (r - dy) * SQRT3;
        return dx > halfW;
    }

    /**
     * 六边形板:竖直网格线 x(网格像素坐标,0~w)在板内的纵向区间。
     * out[0] = 上端,y0;out[1] = 下端;返回 false 表示整条线在板外。
     */
    public static boolean hexChordV(float w, float h, float x, float[] out) {
        double r = Math.min(w, h) / 2.0;
        double dx = Math.abs(x - w / 2.0);
        if (dx > r * SQRT3 / 2.0) return false;
        double dy = dx / SQRT3;
        out[0] = (float) (h / 2.0 - r + dy);
        out[1] = (float) (h / 2.0 + r - dy);
        return true;
    }

    /**
     * 六边形板:水平网格线 y 在板内的横向区间(同上,横向版)。
     */
    public static boolean hexChordH(float w, float h, float y, float[] out) {
        double r = Math.min(w, h) / 2.0;
        double dy = Math.abs(y - h / 2.0);
        if (dy > r) return false;
        double halfW = dy <= r / 2.0 ? r * SQRT3 / 2.0 : (r - dy) * SQRT3;
        out[0] = (float) (w / 2.0 - halfW);
        out[1] = (float) (w / 2.0 + halfW);
        return true;
    }

    /**
     * 六边形板顶点(网格像素坐标,尖顶朝上,顺时针:上→右上→右下→下→左下→左上)。
     * out 长度 12(x,y 交错);供渲染器构建 Path,避免 bead 包依赖 android.graphics。
     */
    public static void hexVertices(float w, float h, float[] out) {
        double r = Math.min(w, h) / 2.0;
        double cx = w / 2.0, cy = h / 2.0;
        double halfW = r * SQRT3 / 2.0;
        out[0] = (float) cx;          out[1] = (float) (cy - r);
        out[2] = (float) (cx + halfW); out[3] = (float) (cy - r / 2.0);
        out[4] = (float) (cx + halfW); out[5] = (float) (cy + r / 2.0);
        out[6] = (float) cx;          out[7] = (float) (cy + r);
        out[8] = (float) (cx - halfW); out[9] = (float) (cy + r / 2.0);
        out[10] = (float) (cx - halfW); out[11] = (float) (cy - r / 2.0);
    }

    public int boardsNeeded() {
        return (int) (Math.ceil(cols / 29.0) * Math.ceil(rows / 29.0));
    }

    /** 按用量从多到少排序后的已用颜色 */
    public static final class UsedColor {
        public final int index;
        public final BeadColor color;
        public final String symbol;
        public final int count;

        public UsedColor(int index, BeadColor color, String symbol, int count) {
            this.index = index;
            this.color = color;
            this.symbol = symbol;
            this.count = count;
        }
    }

    public static Comparator<UsedColor> COUNT_DESC = new Comparator<UsedColor>() {
        @Override
        public int compare(UsedColor a, UsedColor b) {
            return b.count - a.count;
        }
    };

    @SuppressWarnings("unchecked")
    public static void sortByCountDesc(List<UsedColor> list) {
        Collections.sort(list, COUNT_DESC);
    }
}
