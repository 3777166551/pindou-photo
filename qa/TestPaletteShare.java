import com.pindou.app.bead.BeadBrandCharts;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.util.PaletteShare;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义色板分享格式(pindou-palette)测试:build/parse 往返、
 * pindou-pattern 兼容导入、RGB 去重、防御边界(空表/超上限/坏颜色)、
 * hex 工具函数。
 */
public class TestPaletteShare {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        // ---- 往返:3 色(含 tag) ----
        List<BeadColor> colors = new ArrayList<>();
        colors.add(new BeadColor(7, "樱桃", 0xE0334A, "sweet"));
        colors.add(new BeadColor(8, "奶白", 0xFFF6E8, ""));
        colors.add(new BeadColor(9, "薄荷", 0x7FD8C4, "cool"));
        BeadBrandCharts.Chart chart = PaletteShare.parse(
                PaletteShare.build("春日甜品", colors));
        check("往返:名字", "春日甜品".equals(chart.name));
        check("往返:颜色数", chart.colors.size() == 3);
        check("往返:首色 rgb", chart.colors.get(0).rgb == 0xE0334A);
        check("往返:tag", "sweet".equals(chart.colors.get(0).tag));
        check("往返:无 tag 为空串", "".equals(chart.colors.get(1).tag));

        // ---- pindou-pattern 兼容:取其 colors 当色板 ----
        JSONObject patternFile = new JSONObject(
                "{\"format\":\"pindou-pattern\",\"version\":1,"
                        + "\"cols\":6,\"rows\":5,\"name\":\"xx\","
                        + "\"colors\":[{\"code\":1,\"name\":\"红\",\"rgb\":13378082}]}");
        BeadBrandCharts.Chart fromPattern = PaletteShare.parse(patternFile);
        check("接受 pattern 格式导入", fromPattern.colors.size() == 1
                && fromPattern.colors.get(0).rgb == 0xCC2222);

        // ---- 去重:同 RGB 保留先出现 ----
        List<BeadColor> dup = new ArrayList<>();
        dup.add(new BeadColor(1, "甲", 0x112233, ""));
        dup.add(new BeadColor(2, "乙", 0x112233, ""));
        dup.add(new BeadColor(3, "丙", 0x445566, ""));
        List<BeadColor> clean = PaletteShare.dedupeByRgb(dup);
        check("去重保先出现", clean.size() == 2
                && "甲".equals(clean.get(0).name));

        // ---- 解析时自动去重 + 空名兜底 + code 负数钳制 ----
        JSONObject dupFile = new JSONObject(
                "{\"format\":\"pindou-palette\",\"version\":1,\"name\":\"d\","
                        + "\"colors\":["
                        + "{\"code\":1,\"name\":\"甲\",\"rgb\":1122867},"
                        + "{\"code\":2,\"name\":\"乙\",\"rgb\":1122867},"
                        + "{\"code\":-5,\"name\":\"\",\"rgb\":4489254}]}");
        BeadBrandCharts.Chart d = PaletteShare.parse(dupFile);
        check("解析去重", d.colors.size() == 2);
        check("空名兜底按原始下标 色3", "色3".equals(d.colors.get(1).name));
        check("负 code 钳 0", d.colors.get(1).code == 0);

        // ---- 坏文件拒收 ----
        reject("非本格式拒收", "{\"format\":\"png\",\"version\":1,"
                + "\"colors\":[{\"code\":1,\"name\":\"a\",\"rgb\":255}]}");
        reject("缺颜色表拒收", "{\"format\":\"pindou-palette\",\"version\":1}");
        reject("空颜色表拒收", "{\"format\":\"pindou-palette\",\"version\":1,"
                + "\"colors\":[]}");
        reject("超上限拒收", hugeColors());
        reject("颜色表损坏拒收(非对象元素)",
                "{\"format\":\"pindou-palette\",\"version\":1,"
                        + "\"colors\":[\"oops\"]}");

        // ---- hex 工具 ----
        check("hex:#RRGGBB", PaletteShare.parseHexColor("#AABBCC") == 0xFFAABBCC);
        check("hex:无井号", PaletteShare.parseHexColor("123456") == 0xFF123456);
        check("hex:#RGB 展开", PaletteShare.parseHexColor("#1A2") == 0xFF11AA22);
        check("hex:非法返回 -1", PaletteShare.parseHexColor("#12G45Z") == -1);
        check("hex:长度不对 -1", PaletteShare.parseHexColor("#12345") == -1);
        check("hex:null -1", PaletteShare.parseHexColor(null) == -1);
        check("toHex 大写", "#AABBCC".equals(PaletteShare.toHex(0xFFAABBCC)));

        System.out.println("TestPaletteShare: " + passed + " passed, "
                + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static String hugeColors() {
        StringBuilder sb = new StringBuilder(
                "{\"format\":\"pindou-palette\",\"version\":1,\"colors\":[");
        for (int i = 0; i < 501; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"code\":").append(i)
                    .append(",\"name\":\"c").append(i)
                    .append("\",\"rgb\":").append(0x010203 + i).append('}');
        }
        return sb.append("]}").toString();
    }

    private static void reject(String name, String json) {
        try {
            PaletteShare.parse(new JSONObject(json));
            check(name, false);
        } catch (Exception e) {
            check(name, true);
        }
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
