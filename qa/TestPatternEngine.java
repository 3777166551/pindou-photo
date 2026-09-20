import com.pindou.app.bead.PatternEngine;

/**
 * PatternEngine 纯数组函数测试(不依赖 Bitmap):
 *  - boxResample:尺寸映射 / 面积平均 / alpha 加权 / 多数透明格置空
 *  - dominantResample:众数胜平均(灰毛边) / 透明像素不参与 / 全透明输出
 *  - resampleBilinear:常色不变 / 尺寸正确
 *  - expandBricks:粗网格展开覆盖
 *  - symbolFor:符号循环
 * 需要 android.jar 在 classpath(PatternEngine 引用 Bitmap)。
 * 失败时 exit 1。
 */
public class TestPatternEngine {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static int px(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void main(String[] args) {
        // ---- boxResample:纯色缩小 ----
        int[] pure = new int[16];
        java.util.Arrays.fill(pure, 0xFF3366CC);
        int[] r1 = PatternEngine.boxResample(pure, 4, 4, 2, 2);
        check("boxResample keeps pure color", r1.length == 4
                && r1[0] == 0xFF3366CC && r1[3] == 0xFF3366CC);

        // ---- boxResample:alpha 加权(透明黑色不污染) ----
        int[] mix = {
                px(128, 255, 0, 0), px(255, 0, 0, 255),
        };
        int[] r2 = PatternEngine.boxResample(mix, 2, 1, 1, 1);
        // aSum=383, outA=192>=128 -> 不透明;颜色按 alpha 加权,不掺透明像素的 B
        check("boxResample alpha-weighted avg", r2[0] == px(255, 85, 0, 170));

        // ---- boxResample:多数透明 -> 整格透明 ----
        int[] mostlyClear = {
                0x00000000, 0x00000000, 0x00000000, px(255, 255, 0, 0),
        };
        int[] r3 = PatternEngine.boxResample(mostlyClear, 2, 2, 1, 1);
        check("boxResample majority transparent -> empty cell", r3[0] == 0x00000000);

        // ---- dominantResample:众数胜平均(3 黑 1 白 -> 黑,不是灰) ----
        int[] bw = {px(255, 0, 0, 0), px(255, 0, 0, 0),
                px(255, 0, 0, 0), px(255, 255, 255, 255)};
        int[] r4 = PatternEngine.dominantResample(bw, 4, 1, 1, 1);
        check("dominantResample picks mode not average", r4[0] == px(255, 0, 0, 0));

        // ---- dominantResample:平均会得灰,众数得白(反例对照) ----
        int[] r5 = PatternEngine.boxResample(bw, 4, 1, 1, 1);
        check("boxResample average would be gray (sanity)",
                r5[0] != px(255, 0, 0, 0) && r5[0] != 0xFFFFFFFF);

        // ---- dominantResample:透明像素不参与统计 ----
        int[] clearMix = {0x00000000, px(255, 10, 200, 30),
                px(255, 10, 200, 30), px(255, 10, 200, 30)};
        int[] r6 = PatternEngine.dominantResample(clearMix, 4, 1, 1, 1);
        check("dominantResample skips transparent pixels",
                r6[0] == px(255, 10, 200, 30));

        // ---- dominantResample:全透明 -> 输出透明 ----
        int[] allClear = new int[4];
        int[] r7 = PatternEngine.dominantResample(allClear, 2, 2, 1, 1);
        check("dominantResample all transparent -> transparent", r7[0] == 0x00000000);

        // ---- dominantResample:放大场景回退到平均(不越界) ----
        int[] up = PatternEngine.dominantResample(new int[]{0xFFFF0000}, 1, 1, 2, 2);
        check("dominantResample upscale falls back safely",
                up.length == 4 && up[0] == 0xFFFF0000);

        // ---- resampleBilinear:常色任意尺寸不变 ----
        int[] r8 = PatternEngine.resampleBilinear(pure, 4, 4, 7, 5);
        boolean same = r8.length == 35;
        for (int c : r8) if (c != 0xFF3366CC) same = false;
        check("resampleBilinear constant color constant", same);

        // ---- expandBricks:粗网格按块展开 ----
        int[] coarse = {0, 1, 2, 3};
        int[] fine = new int[16];
        java.util.Arrays.fill(fine, -1);
        PatternEngine.expandBricks(coarse, 2, 2, fine, 4, 4, 2);
        boolean ok = true;
        for (int y = 0; y < 4 && ok; y++) {
            for (int x = 0; x < 4 && ok; x++) {
                if (fine[y * 4 + x] != coarse[(y / 2) * 2 + x / 2]) ok = false;
            }
        }
        check("expandBricks fills blocks correctly", ok);

        // ---- symbolFor:符号循环 ----
        check("symbolFor A..Z a..z 0..9 then AA",
                PatternEngine.symbolFor(0).equals("A")
                        && PatternEngine.symbolFor(25).equals("Z")
                        && PatternEngine.symbolFor(26).equals("a")
                        && PatternEngine.symbolFor(51).equals("z")
                        && PatternEngine.symbolFor(52).equals("0")
                        && PatternEngine.symbolFor(61).equals("9")
                        && PatternEngine.symbolFor(62).equals("AA"));

        // ---- generateFromGrid:网格缓存路径(纯数组,不依赖 Bitmap;换色板秒切的基础) ----
        java.util.List<com.pindou.app.bead.BeadColor> pal = new java.util.ArrayList<>();
        pal.add(new com.pindou.app.bead.BeadColor(1, "R", 0xFFE53935));
        pal.add(new com.pindou.app.bead.BeadColor(2, "G", 0xFF43A047));
        pal.add(new com.pindou.app.bead.BeadColor(3, "B", 0xFF1E88E5));

        PatternEngine.WorkGrid g = new PatternEngine.WorkGrid();
        g.gw = 2;
        g.gh = 2;
        g.brick = 1;
        g.px = new int[]{0xFFE53935, 0xFF43A047, 0xFF1E88E5, 0x00000000};

        PatternEngine.Options og = new PatternEngine.Options();
        og.cols = 2;
        og.rows = 2;
        og.dither = false;
        com.pindou.app.bead.BeadPattern p1 = PatternEngine.generateFromGrid(g, pal, og, 2, 2);
        check("generateFromGrid nearest match + transparent cell", p1 != null
                && p1.cols == 2 && p1.rows == 2
                && p1.cellAt(0, 0) == 0 && p1.cellAt(1, 0) == 1
                && p1.cellAt(0, 1) == 2 && p1.cellAt(1, 1) == -1);
        check("generateFromGrid usage stats", p1.usedColors.size() == 3 && p1.totalBeads == 3);

        // 换色板重映射:同一网格像素,新色板秒出
        java.util.List<com.pindou.app.bead.BeadColor> pal2 = new java.util.ArrayList<>();
        pal2.add(new com.pindou.app.bead.BeadColor(11, "R2", 0xFFEF5350));
        pal2.add(new com.pindou.app.bead.BeadColor(12, "P", 0xFFAB47BC));
        com.pindou.app.bead.BeadPattern p2 = PatternEngine.generateFromGrid(g, pal2, og, 2, 2);
        check("generateFromGrid remaps to new palette", p2 != null
                && p2.usedColors.size() == 2
                && p2.cellAt(0, 0) == 0 && p2.cellAt(1, 1) == -1);

        // 圆形蒙版在缓存路径同样生效
        PatternEngine.Options oround = new PatternEngine.Options();
        oround.cols = 4;
        oround.rows = 4;
        oround.roundBoard = true;
        g.gw = 4;
        g.gh = 4;
        g.px = new int[16];
        java.util.Arrays.fill(g.px, 0xFFE53935);
        com.pindou.app.bead.BeadPattern p3 = PatternEngine.generateFromGrid(g, pal, oround, 4, 4);
        check("generateFromGrid round mask applied", p3 != null
                && p3.cellAt(0, 0) == -1 && p3.cellAt(2, 2) >= 0);

        // 线稿模式在缓存路径被明确拒绝(需要全分辨率源像素)
        PatternEngine.Options oline = new PatternEngine.Options();
        oline.cols = 2;
        oline.rows = 2;
        oline.style = PatternEngine.STYLE_LINEART;
        boolean rejected = false;
        try {
            PatternEngine.generateFromGrid(g, pal, oline, 2, 2);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check("generateFromGrid rejects line art", rejected);

        System.out.println("TestPatternEngine: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
