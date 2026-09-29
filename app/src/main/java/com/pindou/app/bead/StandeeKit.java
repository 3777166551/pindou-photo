package com.pindou.app.bead;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 立牌方案:把当前图纸变成"可站立的摆件套件"——
 *   主图(原图不动) + 底座(带 1 格宽插槽的支撑板)。
 * 底座几何(经典 sprite stand 结构):
 *   - 宽 = 主图宽 + 2,向上取奇数(保证有正中列),最小 5;
 *   - 深(行) = 3 + 主图高/8,钳制在 [4,8](≥4 兼容分享格式 cols/rows≥4,
 *     8 封顶免得底座比主图还费豆);
 *   - 插槽 = 正中 1 列,从前往后留空到倒数第 2 行,最后一行(背排)实心
 *     ——主图从开口滑入顶到背排,左右导轨夹住,整块底座连通可一次熨成。
 * 底座配色 = 主图最下一行(圆形/六边形板取最低的非空行)的主色,
 *   让作品落地处颜色自然衔接;没有则退回全图用量第一的颜色。
 * 纯 Java 无 Android 依赖(qa 可单测);渲染/导出在 export 包。
 */
public final class StandeeKit {

    /** 主图(原样保留,生成过程不修改) */
    public final BeadPattern sprite;
    /** 底座图纸(复用主图色板,矩形小画幅) */
    public final BeadPattern base;
    /** 插槽所在列(底座坐标) */
    public final int slotCol;
    /** 底座深度(行数) */
    public final int baseDepth;
    /** 底座用色(主图色板下标) */
    public final int baseColorIndex;
    /** 底座用豆数 */
    public final int baseBeads;
    /** 主图+底座按色板下标合并的用量 */
    public final int[] mergedCounts;
    /** 合并用豆总数 */
    public final int mergedTotal;

    private StandeeKit(BeadPattern sprite, BeadPattern base, int slotCol,
                       int baseDepth, int baseColorIndex, int baseBeads,
                       int[] mergedCounts, int mergedTotal) {
        this.sprite = sprite;
        this.base = base;
        this.slotCol = slotCol;
        this.baseDepth = baseDepth;
        this.baseColorIndex = baseColorIndex;
        this.baseBeads = baseBeads;
        this.mergedCounts = mergedCounts;
        this.mergedTotal = mergedTotal;
    }

    /** 生成套件;空图抛 IllegalArgumentException(调用方先判 pattern 就绪) */
    public static StandeeKit build(BeadPattern sprite) {
        if (sprite == null || sprite.totalBeads <= 0 || sprite.usedColors.isEmpty()) {
            throw new IllegalArgumentException("sprite pattern is empty");
        }

        // 底座宽:主图宽 + 2,向上取奇数(奇数才有正中列),最小 5
        int w = sprite.cols + 2;
        if (w % 2 == 0) w++;
        if (w < 5) w = 5;
        // 底座深:越高越深,4~8(≥4 兼容分享格式,8 封顶)
        int depth = 3 + sprite.rows / 8;
        if (depth < 4) depth = 4;
        if (depth > 8) depth = 8;
        int slot = w / 2;

        int colorIdx = pickBaseColor(sprite);
        int beads = w * depth - (depth - 1);   // 插槽空 (depth-1) 格

        int[] cells = new int[w * depth];
        for (int y = 0; y < depth; y++) {
            for (int x = 0; x < w; x++) {
                cells[y * w + x] = (x == slot && y < depth - 1) ? -1 : colorIdx;
            }
        }

        // 复用主图色板;逐色统计 + 按用量排序,与 PatternEngine 的 UsedColor 语义一致
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
        BeadPattern base = new BeadPattern(w, depth, sprite.palette, cells,
                counts, used, total, 0);

        // 合并用量(主图 + 底座,同色板下标直接相加)
        int[] merged = new int[sprite.palette.size()];
        for (int i = 0; i < merged.length; i++) {
            merged[i] = sprite.counts[i] + counts[i];
        }
        int mergedTotal = sprite.totalBeads + total;
        return new StandeeKit(sprite, base, slot, depth, colorIdx,
                total, merged, mergedTotal);
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

    /** 合并后的逐色清单(主图+底座,按合并用量降序),PDF 材料清单页用 */
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

    /** 符号系统与图纸页一致(委托 PatternEngine,bead 包内可直达) */
    private static String symbolFor(int idx) {
        return PatternEngine.symbolFor(idx);
    }
}
