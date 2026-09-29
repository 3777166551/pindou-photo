package com.pindou.app.bead;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缺豆替代求解(纯 Java,qa 单测):对每种"库存不够"的用量色,在色板里找
 * "登记过且富余量 ≥ 该色总需求"的替代色中 Lab 色差最近者(ΔE<18 才推荐)。
 * 富余量 = 替代色库存 - 它自己在图纸里的用量(避免拆东墙补西墙)。
 * 注意语义:比较对象是该色全部需求(uc.count),不是缺口(需求-库存)——
 * 即推荐"整套替掉"的色,不推"混着用"的色;与线上历史行为一致。
 * 从 EditorActivity.computeSubstitutes 提炼,逻辑一字不改,便于桌面
 * 测试;编辑页传登记过的下标→数量映射进来即可。
 */
public final class SubstituteSolver {

    /** 色差超过这个 ΔE 不推荐(肉眼可辨的凑合不如补买) */
    public static final double MAX_DE = 18.0;

    /**
     * @param palette    图纸色板
     * @param counts     每个 palette 下标在图纸里的用量
     * @param usedColors 用量色(按用量降序)
     * @param inventory  palette 下标 → 登记数量(只含登记过的下标)
     * @return 缺豆色的 palette 下标 → 推荐替代的 palette 下标
     */
    public static Map<Integer, Integer> solve(List<BeadColor> palette,
                                              int[] counts,
                                              List<BeadPattern.UsedColor> usedColors,
                                              Map<Integer, Integer> inventory) {
        Map<Integer, Integer> out = new HashMap<>();
        if (palette == null || counts == null
                || usedColors == null || usedColors.isEmpty()) {
            return out;
        }
        double[][] labs = new double[palette.size()][];
        for (int i = 0; i < palette.size(); i++) {
            labs[i] = ColorMath.rgbToLab(0xFF000000 | palette.get(i).rgb);
        }
        for (BeadPattern.UsedColor uc : usedColors) {
            Integer have = inventory.get(uc.index);
            if (have == null || have >= uc.count) continue;   // 未登记或够:不管
            int best = -1;
            double bestDe = Double.MAX_VALUE;
            for (int j = 0; j < palette.size(); j++) {
                if (j == uc.index) continue;
                Integer inv = inventory.get(j);
                if (inv == null) continue;   // 未登记色当不了替代(数量未知)
                int spare = inv - counts[j];   // 排除替代色自身图纸用量
                if (spare < uc.count) continue;
                double de = ColorMath.dist2(labs[uc.index], labs[j]);
                if (de < bestDe) {
                    bestDe = de;
                    best = j;
                }
            }
            if (best >= 0 && bestDe < MAX_DE * MAX_DE) {
                out.put(uc.index, best);
            }
        }
        return out;
    }

    private SubstituteSolver() {
    }
}
