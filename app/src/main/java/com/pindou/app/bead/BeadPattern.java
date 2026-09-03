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
    /** 每格对应 palette 的下标,-1 表示空格(不放置;圆形板板外也是 -1) */
    public final int[] cells;
    /** 每种颜色(按 palette 下标)的使用数量 */
    public final int[] counts;
    /** 实际用到的颜色,按用量从多到少排序 */
    public final List<UsedColor> usedColors;
    public final int totalBeads;
    public final int emptyCount;
    /** 圆形拼板:内切圆以外的格子全部视为板外,不存在 */
    public final boolean round;

    public BeadPattern(int cols, int rows, List<BeadColor> palette,
                       int[] cells, int[] counts, List<UsedColor> usedColors,
                       int totalBeads, int emptyCount) {
        this(cols, rows, palette, cells, counts, usedColors, totalBeads, emptyCount, false);
    }

    public BeadPattern(int cols, int rows, List<BeadColor> palette,
                       int[] cells, int[] counts, List<UsedColor> usedColors,
                       int totalBeads, int emptyCount, boolean round) {
        this.cols = cols;
        this.rows = rows;
        this.palette = palette;
        this.cells = cells;
        this.counts = counts;
        this.usedColors = usedColors;
        this.totalBeads = totalBeads;
        this.emptyCount = emptyCount;
        this.round = round;
    }

    public int cellAt(int x, int y) {
        return cells[y * cols + x];
    }

    /** 该格是否在有效拼豆区域外(圆形板的内切圆以外) */
    public boolean outsideShape(int x, int y) {
        return round && isOutsideRound(cols, rows, x, y);
    }

    /** 圆形板判定:格中心到画布中心距离 > 内切圆半径即板外 */
    public static boolean isOutsideRound(int cols, int rows, int x, int y) {
        double r = Math.min(cols, rows) / 2.0;
        double dx = x + 0.5 - cols / 2.0;
        double dy = y + 0.5 - rows / 2.0;
        return dx * dx + dy * dy > r * r;
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
