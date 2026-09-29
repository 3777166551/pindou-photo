import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.StandeeKit;

import java.util.ArrayList;
import java.util.List;

/**
 * 立牌方案(StandeeKit)纯数据测试:底座几何/插槽结构/连通性/配色/用量。
 * 无 Android 依赖,桌面 JVM 直跑(与 TestGifEncoder 同款骨架)。
 */
public class TestStandee {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        // ---- 几何:矩形主图 ----
        BeadPattern rect = pattern(16, 12, false, false, 3);
        StandeeKit k1 = StandeeKit.build(rect);
        check("底座宽 = 主图宽+2 取奇", k1.base.cols == 19);
        check("底座宽为奇数", k1.base.cols % 2 == 1);
        check("插槽列 = 正中", k1.slotCol == k1.base.cols / 2);
        check("底座深在 [4,8]", k1.baseDepth >= 4 && k1.baseDepth <= 8);
        check("底座深 = 3+高/8 钳制", k1.baseDepth == clampD(12));

        // ---- 插槽结构:开口在前,背排实心 ----
        boolean slotOk = true;
        for (int y = 0; y < k1.baseDepth; y++) {
            for (int x = 0; x < k1.base.cols; x++) {
                int v = k1.base.cellAt(x, y);
                if (x == k1.slotCol && y < k1.baseDepth - 1 && v != -1) slotOk = false;
                if (x == k1.slotCol && y == k1.baseDepth - 1 && v < 0) slotOk = false;
                if (x != k1.slotCol && v < 0) slotOk = false;
            }
        }
        check("插槽:前 D-1 行空,背排实心,其余满铺", slotOk);
        check("底座是矩形板(非圆非六)", !k1.base.round && !k1.base.hex);

        // ---- 连通性:底座非空格四向连通(一次熨成一块) ----
        check("底座四向连通", connected(k1.base));

        // ---- 用量 ----
        check("底座豆数 = 宽×深-(深-1)",
                k1.baseBeads == k1.base.cols * k1.baseDepth - (k1.baseDepth - 1));
        check("合并总数 = 主图+底座", k1.mergedTotal == rect.totalBeads + k1.baseBeads);
        long sum = 0;
        for (int c : k1.mergedCounts) sum += c;
        check("合并逐色求和 = 合并总数", sum == k1.mergedTotal);

        // ---- 底座配色:主图最下行众数色 ----
        check("底座色 = 最下行众数色", k1.baseColorIndex == bottomRowDominant(rect));

        // ---- 宽主图/偶数宽边界 ----
        BeadPattern wide = pattern(30, 8, false, false, 4);
        StandeeKit k2 = StandeeKit.build(wide);
        check("偶数宽主图 -> 底座仍奇数", k2.base.cols % 2 == 1 && k2.base.cols >= 31);
        check("插槽不越界", k2.slotCol > 0 && k2.slotCol < k2.base.cols);

        // ---- 小主图:底座最小 5 宽、最深钳制 ----
        BeadPattern tiny = pattern(3, 3, false, false, 2);
        StandeeKit k3 = StandeeKit.build(tiny);
        check("3 宽主图 -> 底座 5 宽", k3.base.cols == 5);
        check("3 高主图 -> 底座深钳到 4", k3.baseDepth == 4);
        check("底座行数 ≥ 4(分享格式 cols/rows≥4 兼容)", k3.base.rows >= 4);

        // ---- 高主图:深度封顶 8 ----
        BeadPattern tall = pattern(10, 80, false, false, 5);
        StandeeKit k4 = StandeeKit.build(tall);
        check("80 高主图 -> 底座深封顶 8", k4.baseDepth == 8);

        // ---- 圆形板:最低非空行取色,不崩 ----
        BeadPattern round = pattern(15, 15, true, false, 4);
        StandeeKit k5 = StandeeKit.build(round);
        check("圆形板生成成功", k5.base.totalBeads > 0);
        check("圆板底座色 = 最低非空行众数", k5.baseColorIndex == lowestRowDominant(round));

        // ---- 六边形板 ----
        BeadPattern hex = pattern(15, 15, false, true, 4);
        StandeeKit k6 = StandeeKit.build(hex);
        check("六边形板生成成功", k6.base.totalBeads > 0);

        // ---- 单色图 ----
        BeadPattern mono = pattern(8, 8, false, false, 1);
        StandeeKit k7 = StandeeKit.build(mono);
        check("单色图底座沿用该色", k7.baseColorIndex == mono.cellAt(0, 0));
        check("单色图合并清单只有一色", k7.mergedUsed().size() == 1);

        // ---- 合并清单与符号/排序 ----
        check("合并清单降序", sortedDesc(k1.mergedUsed()));

        // ---- 空图拒绝 ----
        boolean threw = false;
        try {
            List<BeadColor> pal = new ArrayList<>();
            pal.add(new BeadColor(1, "C0", 0x336699));
            int[] empty = new int[36];
            java.util.Arrays.fill(empty, -1);
            int[] zeros = new int[pal.size()];
            StandeeKit.build(new BeadPattern(6, 6, pal, empty, zeros,
                    new ArrayList<BeadPattern.UsedColor>(), 0, 36));
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("空图抛 IllegalArgumentException", threw);

        System.out.println("TestStandee: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ---------------- helpers ----------------

    private static int clampD(int rows) {
        int d = 3 + rows / 8;
        return Math.max(4, Math.min(8, d));
    }

    /** 造图:colors 色随机抖动铺满(shape 内),保证 totalBeads>0 */
    private static BeadPattern pattern(int cols, int rows, boolean round,
                                       boolean hex, int colors) {
        List<BeadColor> palette = new ArrayList<>();
        for (int i = 0; i < colors + 1; i++) {
            palette.add(new BeadColor(1000 + i, "C" + i, 0x336699 + i * 0x001122));
        }
        int[] cells = new int[cols * rows];
        int seed = 90210;
        int total = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int idx = y * cols + x;
                boolean out = round
                        ? BeadPattern.isOutsideRound(cols, rows, x, y)
                        : hex ? BeadPattern.isOutsideHex(cols, rows, x, y) : false;
                if (out) {
                    cells[idx] = -1;
                    continue;
                }
                seed = seed * 1103515245 + 12345;
                // 底行(最低非空行)集中用色 0,其余散色,便于测底座取色
                boolean bottomish = y >= rows - 2;
                cells[idx] = bottomish ? 0 : (Math.abs(seed) % colors);
                total++;
            }
        }
        int[] counts = new int[palette.size()];
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] >= 0) counts[cells[i]]++;
        }
        int sum = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] <= 0) continue;
            sum += counts[i];
            used.add(new BeadPattern.UsedColor(i, palette.get(i),
                    String.valueOf((char) ('A' + i)), counts[i]));
        }
        BeadPattern.sortByCountDesc(used);
        return new BeadPattern(cols, rows, palette, cells, counts, used,
                sum, cols * rows - sum, round, hex);
    }

    private static int bottomRowDominant(BeadPattern p) {
        // 测试图最两行都是色 0 的聚集区:直接重算众数对照
        return lowestRowDominant(p);
    }

    private static int lowestRowDominant(BeadPattern p) {
        for (int y = p.rows - 1; y >= 0; y--) {
            int[] tally = new int[p.palette.size()];
            int best = -1, bestCnt = 0;
            boolean any = false;
            for (int x = 0; x < p.cols; x++) {
                if (p.outsideShape(x, y)) continue;
                int idx = p.cellAt(x, y);
                if (idx < 0) continue;
                any = true;
                tally[idx]++;
                if (tally[idx] > bestCnt) {
                    bestCnt = tally[idx];
                    best = idx;
                }
            }
            if (any) return best;
        }
        return -1;
    }

    /** 底座非空格四向连通(BFS) */
    private static boolean connected(BeadPattern b) {
        int start = -1;
        for (int i = 0; i < b.cells.length; i++) {
            if (b.cells[i] >= 0) {
                start = i;
                break;
            }
        }
        if (start < 0) return false;
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty()) {
            int cur = q.poll();
            int x = cur % b.cols, y = cur / b.cols;
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dd : d) {
                int nx = x + dd[0], ny = y + dd[1];
                if (nx < 0 || ny < 0 || nx >= b.cols || ny >= b.rows) continue;
                int n = ny * b.cols + nx;
                if (b.cells[n] < 0 || seen.contains(n)) continue;
                seen.add(n);
                q.add(n);
            }
        }
        int total = 0;
        for (int v : b.cells) {
            if (v >= 0) total++;
        }
        return seen.size() == total;
    }

    private static boolean sortedDesc(List<BeadPattern.UsedColor> l) {
        for (int i = 1; i < l.size(); i++) {
            if (l.get(i - 1).count < l.get(i).count) return false;
        }
        return true;
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }
}
