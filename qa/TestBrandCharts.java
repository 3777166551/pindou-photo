import com.pindou.app.bead.BeadBrandCharts;
import com.pindou.app.bead.BeadColor;

import java.util.HashSet;
import java.util.Set;

/**
 * 品牌色号表数据质量测试(纯 JVM):
 *  - 共 9 张品牌表(5 张 5mm + 4 张迷你)
 *  - 每张表至少 30 色、色号唯一、RGB 均为合法 24bit
 *  - 迷你规格表按名字识别(isMiniChart)
 * 失败时 exit 1。
 */
public class TestBrandCharts {

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
        BeadBrandCharts.Chart[] all = BeadBrandCharts.ALL;
        check("9 brand charts", all.length == 9);

        boolean haveArtkalC = false, havePerlerMini = false, haveHamaMini = false,
                haveMard221 = false;
        for (BeadBrandCharts.Chart chart : all) {
            check("non-empty: " + chart.name, chart.colors.size() >= 30);

            Set<String> codes = new HashSet<>();
            Set<Integer> rgbs = new HashSet<>();
            boolean dupCode = false, badRgb = false;
            for (BeadColor bc : chart.colors) {
                if (!codes.add(bc.code + "")) dupCode = true;
                // 同一 rgb 允许出现在不同色号下(品牌多系列常见,如实测出同色),
                // 但越界值一律失败
                if (bc.rgb < 0 || bc.rgb > 0xFFFFFF) {
                    badRgb = true;
                    System.out.println("  bad rgb in " + chart.name + ": "
                            + bc.code + " " + bc.name + " #"
                            + Integer.toHexString(bc.rgb));
                }
            }
            check("unique codes: " + chart.name, !dupCode);
            check("valid rgb: " + chart.name, !badRgb);

            if (chart.name.startsWith("Artkal C")) {
                haveArtkalC = true;
                check("Artkal C is mini", BeadBrandCharts.isMiniChart(chart.name));
            }
            if (chart.name.startsWith("Perler Mini")) {
                havePerlerMini = true;
                check("Perler Mini is mini", BeadBrandCharts.isMiniChart(chart.name));
            }
            if (chart.name.startsWith("Hama Mini")) {
                haveHamaMini = true;
                check("Hama Mini is mini", BeadBrandCharts.isMiniChart(chart.name));
            }
            if (chart.name.startsWith("漫德 Mard·2.6")) {
                haveMard221 = true;
                check("Mard 2.6 is mini", BeadBrandCharts.isMiniChart(chart.name));
                check("Mard 221 color count", chart.colors.size() == 221);
                // 锚定上游数据,防再生成时漂移(alfonse 核对版)
                BeadColor a1 = chart.colors.get(0);
                check("Mard 221 A1 anchor", "A1".equals(a1.tag) && a1.rgb == 0xFAF5CD);
                BeadColor a10 = chart.colors.get(9);
                check("Mard 221 A10 anchor", "A10".equals(a10.tag) && a10.rgb == 0xF47E38);
                BeadColor m15 = chart.colors.get(220);
                check("Mard 221 M15 anchor", "M15".equals(m15.tag) && m15.rgb == 0x747D7A);
            }
        }
        check("three mini charts present", haveArtkalC && havePerlerMini && haveHamaMini);
        check("mard 221 chart present", haveMard221);
        check("midi charts not mini", !BeadBrandCharts.isMiniChart("Artkal S·5mm")
                && !BeadBrandCharts.isMiniChart("Hama Midi·5mm"));
        check("null safe", !BeadBrandCharts.isMiniChart(null));

        System.out.println("TestBrandCharts: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
