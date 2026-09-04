import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.PatternPatch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PatternPatch.apply(手动修格覆盖层)测试:
 *  - 覆盖生效 / -1 挖空 / 非法值当空格
 *  - counts / usedColors / emptyCount / total 重算与排序
 *  - 空覆盖表原样返回
 * 纯 Java,无 Android 依赖。失败时 exit 1。
 */
public class TestPatternPatch {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    /** 构造 3x3 图纸:palette 3 色(红绿蓝),格子全部为色板下标 0(红) */
    static BeadPattern make3x3() {
        List<BeadColor> palette = new java.util.ArrayList<>();
        palette.add(new BeadColor(1, "红", 0xFF0000));
        palette.add(new BeadColor(2, "绿", 0x00FF00));
        palette.add(new BeadColor(3, "蓝", 0x0000FF));
        int[] cells = new int[9];
        java.util.Arrays.fill(cells, 0);
        int[] counts = {9, 0, 0};
        List<BeadPattern.UsedColor> used = new java.util.ArrayList<>();
        used.add(new BeadPattern.UsedColor(0, palette.get(0), "A", 9));
        return new BeadPattern(3, 3, palette, cells, counts, used, 9, 0, false);
    }

    public static void main(String[] args) {
        // ---- 空覆盖表:原样返回同一实例 ----
        BeadPattern raw = make3x3();
        check("empty overrides returns same instance",
                PatternPatch.apply(raw, new HashMap<>()) == raw);

        // ---- 覆盖生效 + 挖空 + 非法值 + 统计重算 ----
        Map<Integer, Integer> edits = new HashMap<>();
        edits.put(0, 1);    // 红改绿
        edits.put(4, -1);   // 挖空
        edits.put(8, 99);   // 非法值 -> 当空格
        BeadPattern p = PatternPatch.apply(raw, edits);

        check("override applied", p.cells[0] == 1);
        check("-1 clears cell", p.cells[4] == -1);
        check("invalid override treated as empty", p.cells[8] == -1);
        check("untouched cells keep raw color", p.cells[1] == 0 && p.cells[3] == 0);
        check("counts recalculated",
                p.counts[0] == 6 && p.counts[1] == 1 && p.counts[2] == 0);
        check("emptyCount = 2", p.emptyCount == 2);
        check("total = 7", p.totalBeads == 7);
        check("usedColors sorted by count desc",
                p.usedColors.size() == 2
                        && p.usedColors.get(0).index == 0
                        && p.usedColors.get(0).count == 6
                        && p.usedColors.get(1).index == 1
                        && p.usedColors.get(1).count == 1);
        check("usedColor symbol assigned",
                p.usedColors.get(0).symbol.equals("A")
                        && p.usedColors.get(1).symbol.equals("B"));
        check("raw pattern untouched (pure function)",
                raw.cells[0] == 0 && raw.counts[0] == 9 && raw.totalBeads == 9);

        // ---- 覆盖清零:全部挖空 ----
        Map<Integer, Integer> allClear = new HashMap<>();
        for (int i = 0; i < 9; i++) allClear.put(i, -1);
        BeadPattern empty = PatternPatch.apply(raw, allClear);
        check("all cleared: total=0 empty=9 no used colors",
                empty.totalBeads == 0 && empty.emptyCount == 9
                        && empty.usedColors.isEmpty());

        // ---- 换成第三色:usedColors 顺序随用量变化 ----
        Map<Integer, Integer> toBlue = new HashMap<>();
        for (int i = 0; i < 9; i++) toBlue.put(i, 2);
        BeadPattern blue = PatternPatch.apply(raw, toBlue);
        check("all blue: single used color",
                blue.usedColors.size() == 1
                        && blue.usedColors.get(0).index == 2
                        && blue.counts[0] == 0 && blue.counts[2] == 9);

        System.out.println("TestPatternPatch: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
