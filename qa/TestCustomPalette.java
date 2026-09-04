import com.pindou.app.bead.BeadBrandCharts;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPalettes;
import com.pindou.app.util.PaletteShare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 自定义色板(多套槽位)测试:
 *  - BeadBrandCharts 多套自定义槽位的增删改与 BeadPalettes 选择逻辑
 *    (selCount/selNames/getPalette/越界收拢)
 *  - 按色相排序 sortByHue(红→绿→蓝 + 重新编号)
 *  - PaletteShare 纯函数:十六进制取色解析 / RGB 去重 / 展示文本
 * 纯 Java,无 Android 依赖(org.json 方法不触碰)。失败时 exit 1。
 */
public class TestCustomPalette {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static BeadColor color(int code, String name, int rgb) {
        return new BeadColor(code, name, rgb);
    }

    public static void main(String[] args) {
        // ---- 基线:无自定义色板 ----
        BeadBrandCharts.setCustoms(new ArrayList<BeadBrandCharts.Chart>());
        BeadPalettes.resetCache();
        int baseCount = BeadPalettes.selCount();
        check("baseline selCount = generic + brands(8)",
                baseCount == BeadPalettes.GENERIC_COUNT + BeadBrandCharts.ALL.length
                        && baseCount == 8);
        check("baseline customSlotStart = 8", BeadPalettes.customSlotStart() == 8);

        // ---- 两套自定义色板 ----
        List<BeadColor> p1 = new ArrayList<>(Arrays.asList(
                color(1, "红", 0xFF0000), color(2, "蓝", 0x0000FF),
                color(3, "白", 0xFFFFFF)));
        List<BeadColor> p2 = new ArrayList<>(Arrays.asList(color(1, "绿", 0x00FF00)));
        BeadBrandCharts.setCustoms(Arrays.asList(
                BeadBrandCharts.make("测试A", p1), BeadBrandCharts.make("测试B", p2)));
        BeadPalettes.resetCache();
        check("selCount grows by 2", BeadPalettes.selCount() == baseCount + 2);
        String[] names = BeadPalettes.selNames();
        check("selNames length", names.length == baseCount + 2);
        check("selNames custom A", "测试A".equals(names[baseCount]));
        check("selNames custom B", "测试B".equals(names[baseCount + 1]));

        // ---- 取色板与越界收拢 ----
        List<BeadColor> gotA = BeadPalettes.getPalette(baseCount);
        check("getPalette(custom A) size", gotA.size() == 3);
        check("getPalette returns a list copy", gotA != p1);
        gotA.clear();
        check("copy mutation does not leak",
                BeadPalettes.getPalette(baseCount).size() == 3);
        check("getPalette(custom B)", BeadPalettes.getPalette(baseCount + 1).size() == 1);
        check("out of range clamps to last custom",
                BeadPalettes.getPalette(baseCount + 99).size() == 1);
        check("tier0 still works", BeadPalettes.getPalette(0).size() == 24);
        List<BeadColor> lastBrand = BeadPalettes.getPalette(baseCount - 1);
        check("last brand palette intact",
                lastBrand.size() == BeadBrandCharts.ALL[BeadBrandCharts.ALL.length - 1]
                        .colors.size());

        // ---- 增删改槽位 ----
        int at = BeadBrandCharts.upsertCustom(1, BeadBrandCharts.make(
                "测试C", new ArrayList<>(Arrays.asList(color(1, "黑", 0x000000)))));
        check("upsert replaces at index", at == 1
                && BeadBrandCharts.customAt(1).name.equals("测试C"));
        at = BeadBrandCharts.upsertCustom(-5, BeadBrandCharts.make("测试D",
                new ArrayList<BeadColor>()));
        check("upsert out of range appends", at == 2
                && BeadBrandCharts.customCount() == 3);
        BeadBrandCharts.removeCustom(2);
        check("remove shrinks", BeadBrandCharts.customCount() == 2);
        BeadPalettes.resetCache();
        check("names reflect after resetCache",
                BeadPalettes.selNames().length == baseCount + 2);

        // ---- 按色相排序:蓝/绿/红 -> 红→绿→蓝,编号重排 ----
        List<BeadColor> hues = new ArrayList<>(Arrays.asList(
                color(9, "蓝", 0x0000FF), color(7, "绿", 0x00FF00),
                color(5, "红", 0xFF0000)));
        BeadPalettes.sortByHue(hues);
        check("hue order red->green->blue", hues.get(0).name.equals("红")
                && hues.get(1).name.equals("绿") && hues.get(2).name.equals("蓝"));
        check("renumber 1..n", hues.get(0).code == 1 && hues.get(1).code == 2
                && hues.get(2).code == 3);

        // ---- 十六进制取色 ----
        check("hex with #", PaletteShare.parseHexColor("#AABBCC") == 0xFFAABBCC);
        check("hex without #", PaletteShare.parseHexColor("aabbcc") == 0xFFAABBCC);
        check("hex short form", PaletteShare.parseHexColor("#ABC") == 0xFFAABBCC);
        check("hex invalid letters", PaletteShare.parseHexColor("#GGHHII") == -1);
        check("hex wrong length", PaletteShare.parseHexColor("12345") == -1
                && PaletteShare.parseHexColor("1234567") == -1);
        check("hex null/empty", PaletteShare.parseHexColor(null) == -1
                && PaletteShare.parseHexColor("") == -1);
        check("toHex format", "#AABBCC".equals(PaletteShare.toHex(0xAABBCC)));

        // ---- RGB 去重 ----
        List<BeadColor> dup = PaletteShare.dedupeByRgb(Arrays.asList(
                color(1, "红一", 0xFF0000), color(2, "蓝", 0x0000FF),
                color(3, "红二", 0xFF0000)));
        check("dedupe keeps first", dup.size() == 2 && dup.get(0).name.equals("红一")
                && dup.get(1).name.equals("蓝"));

        // ---- 还原空槽位(避免影响同 JVM 其它测试) ----
        BeadBrandCharts.setCustoms(new ArrayList<BeadBrandCharts.Chart>());
        BeadPalettes.resetCache();
        check("restore baseline", BeadPalettes.selCount() == baseCount);

        System.out.println("TestCustomPalette: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
