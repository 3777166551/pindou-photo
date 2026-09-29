package com.pindou.app.bead;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 立牌方案:把当前图纸变成"可站立/可挂墙的摆件套件"——
 *   主图(原图不动) + 底座(带 1 格宽插槽的支撑板) + 挂绳杆(穿线孔,挂墙用)。
 * 三件都是自动算好的,**用户零选择**:PDF/豆单是完整套件,用不上挂绳杆
 * 就无视它(几颗豆的小件)。
 *
 * 底座几何(经典 sprite stand 结构,v2.62 加深:按宽高比自适应防倾):
 *   - 深(行) = 2 + 高/5,钳制 [4,10]——越高的件底座越深,瘦高件不再头重脚轻;
 *   - 宽 = max(主图宽+2, 高×0.4) 向上取奇数,最小 5——瘦高件加宽脚距;
 *   - 插槽 = 正中 1 列,从前往后留空到倒数第 2 行,最后一行(背排)实心
 *     ——主图从开口滑入顶到背排,左右导轨夹住,整块底座连通可一次熨成。
 * 挂绳杆 = 宽 max(5, 主图宽×0.6) 取奇 × 3 行,正中格只在中行留空成穿线孔
 *   (上下两行实心保证连通);缝/粘在背面顶端,鱼线穿孔挂钉。
 * 底座/挂绳杆配色 = 主图最低非空行(圆/六边形板取最低有豆行)的众数色。
 * 纯 Java 无 Android 依赖(qa 可单测);渲染/导出在 export 包。
 */
public final class StandeeKit {

    /** 主图(原样保留,生成过程不修改) */
    public final BeadPattern sprite;
    /** 底座图纸(复用主图色板,矩形小画幅) */
    public final BeadPattern base;
    /** 挂绳杆图纸(3 行高,中行正中 1 格穿线孔) */
    public final BeadPattern hanger;
    /** 插槽所在列(底座坐标) */
    public final int slotCol;
    /** 底座深度(行数) */
    public final int baseDepth;
    /** 底座用色(主图色板下标;挂绳杆同色) */
    public final int baseColorIndex;
    /** 底座用豆数 */
    public final int baseBeads;
    /** 挂绳杆用豆数 */
    public final int hangerBeads;
    /** 主图+底座+挂绳杆按色板下标合并的用量 */
    public final int[] mergedCounts;
    /** 合并用豆总数 */
    public final int mergedTotal;

    private StandeeKit(BeadPattern sprite, BeadPattern base, BeadPattern hanger,
                       int slotCol, int baseDepth, int baseColorIndex,
                       int baseBeads, int hangerBeads,
                       int[] mergedCounts, int mergedTotal) {
        this.sprite = sprite;
        this.base = base;
        this.hanger = hanger;
        this.slotCol = slotCol;
        this.baseDepth = baseDepth;
        this.baseColorIndex = baseColorIndex;
        this.baseBeads = baseBeads;
        this.hangerBeads = hangerBeads;
        this.mergedCounts = mergedCounts;
        this.mergedTotal = mergedTotal;
    }

    /** 生成套件;空图抛 IllegalArgumentException(调用方先判 pattern 就绪) */
    public static StandeeKit build(BeadPattern sprite) {
        if (sprite == null || sprite.totalBeads <= 0 || sprite.usedColors.isEmpty()) {
            throw new IllegalArgumentException("sprite pattern is empty");
        }

        int colorIdx = pickBaseColor(sprite);

        // 底座:越高的件越深越宽(防倾),4~10 行封顶
        int depth = 2 + Math.round(sprite.rows / 5f);
        if (depth < 4) depth = 4;
        if (depth > 10) depth = 10;
        int w = oddMax(sprite.cols + 2, Math.round(sprite.rows * 0.4f));
        int slot = w / 2;
        int beads = w * depth - (depth - 1);   // 插槽空 (depth-1) 格

        int[] baseCells = new int[w * depth];
        for (int y = 0; y < depth; y++) {
            for (int x = 0; x < w; x++) {
                baseCells[y * w + x] = (x == slot && y < depth - 1) ? -1 : colorIdx;
            }
        }
        BeadPattern base = assemble(w, depth, sprite, baseCells);

        // 挂绳杆:3 行高,正中格只在中行留空(上下行实心保连通),穿线挂钉
        int hw = oddMax(5, Math.round(sprite.cols * 0.6f));
        int[] hangCells = new int[hw * 3];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < hw; x++) {
                hangCells[y * hw + x] = (x == hw / 2 && y == 1) ? -1 : colorIdx;
            }
        }
        BeadPattern hanger = assemble(hw, 3, sprite, hangCells);

        // 合并用量(主图 + 底座 + 挂绳杆,同色板下标直接相加)
        int[] merged = new int[sprite.palette.size()];
        for (int i = 0; i < merged.length; i++) {
            merged[i] = sprite.counts[i] + base.counts[i] + hanger.counts[i];
        }
        int mergedTotal = sprite.totalBeads + base.totalBeads + hanger.totalBeads;
        return new StandeeKit(sprite, base, hanger, slot, depth, colorIdx,
                base.totalBeads, hanger.totalBeads, merged, mergedTotal);
    }

    /** 上取奇数:base 与 want 取大后向上取奇,最小 5 */
    private static int oddMax(int base, int want) {
        int v = Math.max(base, want);
        if (v % 2 == 0) v++;
        if (v < 5) v = 5;
        return v;
    }

    /** 由 cells 统计用量组装 BeadPattern(复用主图色板,与分享格式同语义) */
    private static BeadPattern assemble(int cols, int rows, BeadPattern sprite,
                                        int[] cells) {
        int[] counts = new int[sprite.palette.size()];
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] >= 0) counts[cells[i]]++;
        }
        int total = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] <= 0) continue;
            total += counts[i];
            used.add(new BeadPattern.UsedColor(i, sprite.palette.get(i),
                    symbolFor(i), counts[i]));
        }
        BeadPattern.sortByCountDesc(used);
        return new BeadPattern(cols, rows, sprite.palette, cells,
                counts, used, total, 0);
    }

    /**
     * 底座配色:主图最低的非空行(圆/六边形板底行多在板外,取最低有豆的
     * 那行)的众数色;整行理论不可能空(totalBeads>0),防御性退回用量第一色。
     */
    private static int pickBaseColor(BeadPattern sprite) {
        for (int y = sprite.rows - 1; y >= 0; y--) {
            Map<Integer, Integer> tally = new HashMap<>();
            int bestIdx = -1, bestCnt = 0;
            for (int x = 0; x < sprite.cols; x++) {
                if (sprite.outsideShape(x, y)) continue;
                int idx = sprite.cellAt(x, y);
                if (idx < 0) continue;
                int c = tally.containsKey(idx) ? tally.get(idx) + 1 : 1;
                tally.put(idx, c);
                if (c > bestCnt) {
                    bestCnt = c;
                    bestIdx = idx;
                }
            }
            if (bestIdx >= 0) return bestIdx;
        }
        return sprite.usedColors.get(0).index;
    }

    /** 合并后的逐色清单(主图+底座+挂绳杆,按合并用量降序),PDF 材料清单页用 */
    public List<BeadPattern.UsedColor> mergedUsed() {
        List<BeadPattern.UsedColor> out = new ArrayList<>();
        for (int i = 0; i < mergedCounts.length; i++) {
            if (mergedCounts[i] <= 0) continue;
            out.add(new BeadPattern.UsedColor(i, sprite.palette.get(i),
                    symbolFor(i), mergedCounts[i]));
        }
        BeadPattern.sortByCountDesc(out);
        return out;
    }

    /** 高件判断:深度封顶(10 行)都兜不住的细高件,建议靠墙/改挂墙 */
    public boolean tallAdvice() {
        return sprite.rows > baseDepth * 5;
    }

    /** 符号系统与图纸页一致(委托 PatternEngine,bead 包内可直达) */
    private static String symbolFor(int idx) {
        return PatternEngine.symbolFor(idx);
    }
}
