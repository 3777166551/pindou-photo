package com.pindou.app.bead;

import java.util.ArrayList;
import java.util.Map;

/**
 * 手动修格:把"某格 -> 某色板下标"的覆盖关系套到图纸上,
 * 并重新统计用量。纯函数,不改原图纸对象。
 */
public final class PatternPatch {

    public static BeadPattern apply(BeadPattern p, Map<Integer, Integer> overrides) {
        if (p == null || overrides == null || overrides.isEmpty()) return p;

        int[] cells = p.cells.clone();
        int n = p.palette.size();
        int[] counts = new int[Math.max(1, n)];
        int empty = 0;
        int total = 0;

        for (int i = 0; i < cells.length; i++) {
            Integer ov = overrides.get(i);
            int c = (ov != null) ? ov : cells[i];
            cells[i] = c;
            if (c < 0) {
                empty++;
            } else if (c < counts.length && c >= 0) {
                counts[c]++;
                total++;
            } else {
                // 非法覆盖值,当作空格处理
                cells[i] = -1;
                empty++;
            }
        }

        ArrayList<BeadPattern.UsedColor> used = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (counts[i] > 0) {
                used.add(new BeadPattern.UsedColor(
                        i, p.palette.get(i), PatternEngine.symbolFor(i), counts[i]));
            }
        }
        BeadPattern.sortByCountDesc(used);

        return new BeadPattern(p.cols, p.rows, p.palette, cells, counts,
                used, total, empty, p.round);
    }

    private PatternPatch() {
    }
}
