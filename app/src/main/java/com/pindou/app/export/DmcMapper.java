package com.pindou.app.export;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 拼豆色 -> DMC 绣线色的就近映射(CIEDE2000,纯 Java 可单测)。
 * 数据表见 DmcTable(MIT,来源与声明见 THIRD_PARTY.md)。
 */
public final class DmcMapper {

    /** 一个用到的豆色 -> DMC 线色的映射结果 */
    public static final class DmcMatch {
        public final int paletteIndex;   // BeadPattern palette 下标
        public final int dmc;            // DmcTable 下标
        public final int count;          // 格数(=针数)

        DmcMatch(int paletteIndex, int dmc, int count) {
            this.paletteIndex = paletteIndex;
            this.dmc = dmc;
            this.count = count;
        }
    }

    /** 把图纸用到的每个颜色映射到最近 DMC;按针数从多到少排序 */
    public static DmcMatch[] mapColors(BeadPattern p) {
        double[][] dmcLab = new double[DmcTable.COUNT][];
        for (int i = 0; i < DmcTable.COUNT; i++) {
            dmcLab[i] = ColorMath.rgbToLab(DmcTable.RGBS[i]);
        }
        DmcMatch[] out = new DmcMatch[p.usedColors.size()];
        for (int u = 0; u < p.usedColors.size(); u++) {
            BeadPattern.UsedColor uc = p.usedColors.get(u);
            double[] lab = ColorMath.rgbToLab(uc.color.rgb);
            int best = 0;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < DmcTable.COUNT; i++) {
                double d = ColorMath.deltaE2000(lab, dmcLab[i]);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            out[u] = new DmcMatch(uc.index, best, uc.count);
        }
        Arrays.sort(out, new Comparator<DmcMatch>() {
            @Override
            public int compare(DmcMatch a, DmcMatch b) {
                return b.count - a.count;
            }
        });
        return out;
    }

    private DmcMapper() {
    }
}
