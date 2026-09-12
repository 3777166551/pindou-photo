import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.export.DmcMapper;
import com.pindou.app.export.DmcTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v2.50 十字绣导出数据测试(桌面 JVM,需要 android.jar 在 classpath):
 *  - DmcTable:平行数组长度一致、色号唯一、RGB 合法
 *  - DmcMapper.mapColors:每个用到的颜色都有映射、
 *    针数守恒、映射色与原色的 Lab 距离有界(就近映射不跑飞)
 * 失败时 exit 1。
 */
public class TestCrossStitch {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        // 1. 表完整性
        check("count matches arrays", DmcTable.CODES.length == DmcTable.COUNT
                && DmcTable.NAMES.length == DmcTable.COUNT
                && DmcTable.RGBS.length == DmcTable.COUNT);
        Set<String> codes = new HashSet<>();
        boolean uniq = true, rgbOk = true;
        for (int i = 0; i < DmcTable.COUNT; i++) {
            if (!codes.add(DmcTable.CODES[i])) uniq = false;
            int rgb = DmcTable.RGBS[i];
            if (((rgb >>> 24) & 0xFF) != 0xFF) rgbOk = false;
        }
        check("codes unique", uniq);
        check("rgb opaque", rgbOk);
        check("table is reasonably large", DmcTable.COUNT >= 440);

        // 2. 构造 4×4 测试图纸:两种颜色
        List<BeadColor> palette = new ArrayList<>();
        palette.add(new BeadColor(1, "白", 0xFFFFFF));
        palette.add(new BeadColor(2, "黑", 0x000000));
        int[] cells = new int[16];
        int[] counts = new int[2];
        for (int i = 0; i < 16; i++) {
            cells[i] = (i % 3 == 0) ? 1 : 0;
            counts[cells[i]]++;
        }
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        used.add(new BeadPattern.UsedColor(0, palette.get(0), "A", counts[0]));
        used.add(new BeadPattern.UsedColor(1, palette.get(1), "B", counts[1]));
        BeadPattern p = new BeadPattern(4, 4, palette, cells, counts, used,
                counts[0] + counts[1], 0);

        // 3. 映射:全覆盖 + 针数守恒 + 距离有界
        DmcMapper.DmcMatch[] m = DmcMapper.mapColors(p);
        check("all used colors mapped", m.length == 2);
        int total = 0;
        for (DmcMapper.DmcMatch mm : m) {
            total += mm.count;
            int srcRgb = p.palette.get(mm.paletteIndex).rgb;
            double[] a = com.pindou.app.bead.ColorMath.rgbToLab(srcRgb);
            double[] b = com.pindou.app.bead.ColorMath.rgbToLab(DmcTable.RGBS[mm.dmc]);
            double dl = a[0] - b[0], da = a[1] - b[1], db = a[2] - b[2];
            double dist = Math.sqrt(dl * dl + da * da + db * db);
            check("mapping sane (" + DmcTable.CODES[mm.dmc] + ")", dist < 60.0);
        }
        check("stitch count conserved", total == 16);
        check("sorted by count desc", m[0].count >= m[1].count);
        // 白色应映射到浅色线(L* 高),黑色应映射到深色线(L* 低)
        double lWhite = com.pindou.app.bead.ColorMath
                .rgbToLab(DmcTable.RGBS[dmcOf(m, 0)])[0];
        double lBlack = com.pindou.app.bead.ColorMath
                .rgbToLab(DmcTable.RGBS[dmcOf(m, 1)])[0];
        check("white -> light floss", lWhite > 80);
        check("black -> dark floss", lBlack < 20);

        System.out.println("TestCrossStitch: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static int dmcOf(DmcMapper.DmcMatch[] m, int paletteIndex) {
        for (DmcMapper.DmcMatch mm : m) {
            if (mm.paletteIndex == paletteIndex) return mm.dmc;
        }
        return -1;
    }
}
