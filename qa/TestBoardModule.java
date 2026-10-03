import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;

import java.util.ArrayList;
import java.util.List;

/**
 * 拼板模块按豆规格区分的测试(纯 JVM):
 *  - 标准豆 5mm:一块板 = 29×29 孔(29/58/87/116 → 1/4/9/16 块)
 *  - 迷你豆 2.6mm:一块板 = 50×50 孔(50/100/150/200 → 1/4/9/16 块)
 *  - 旧图纸(未带 mini 标志)一律按标准豆 29 处理
 * 失败时 exit 1。
 */
public class TestBoardModule {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    private static BeadPattern make(int cols, int rows) {
        List<BeadColor> pal = new ArrayList<>();
        pal.add(new BeadColor(1, "白", 0xFFFFFF));
        int[] cells = new int[cols * rows];
        int[] counts = new int[]{cols * rows};
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        used.add(new BeadPattern.UsedColor(0, pal.get(0), "A", counts[0]));
        return new BeadPattern(cols, rows, pal, cells, counts, used, counts[0], 0);
    }

    public static void main(String[] args) {
        check("boardSize std = 29", BeadPattern.boardSize(false) == 29);
        check("boardSize mini = 50", BeadPattern.boardSize(true) == 50);

        // 标准豆:29 孔板 ×1/4/9/16
        int[][] std = {{29, 29, 1}, {30, 30, 4}, {58, 58, 4}, {87, 87, 9},
                {116, 116, 16}, {8, 8, 1}, {42, 31, 4}};
        for (int[] c : std) {
            BeadPattern p = make(c[0], c[1]);
            check("std " + c[0] + "x" + c[1] + " = " + c[2] + " boards",
                    !p.miniBead && p.boardsNeeded() == c[2]);
        }

        // 迷你豆:50 孔板 ×1/4/9/16(现场小板 50×50、大板 100×100 即 2×2)
        int[][] mini = {{50, 50, 1}, {51, 51, 4}, {100, 100, 4}, {150, 150, 9},
                {200, 200, 16}, {29, 29, 1}, {64, 47, 2}};
        for (int[] c : mini) {
            BeadPattern p = make(c[0], c[1]);
            p.miniBead = true;
            check("mini " + c[0] + "x" + c[1] + " = " + c[2] + " boards",
                    p.miniBead && p.boardsNeeded() == c[2]);
        }

        // 旧分享/导入图纸不带标志,默认按标准豆
        BeadPattern legacy = make(58, 58);
        check("legacy default not mini", !legacy.miniBead && legacy.boardsNeeded() == 4);

        System.out.println("TestBoardModule: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
