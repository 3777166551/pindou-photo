import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.util.PatternShare;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 图纸分享格式(pindou-pattern)测试:build/parse 往返一致性 + 坏文件
 * 拒收边界。这是微信/QQ 直开图纸、外部导入、存档 share 字段共用的
 * 核心格式层,此前 0 桌面覆盖(org.json stub 挡路,现已解锁)。
 */
public class TestPatternShare {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        // ---- 往返:矩形 3 色(含空格) ----
        BeadPattern p1 = pattern(6, 5, false, false, 3, true);
        BeadPattern r1 = roundTrip(p1);
        sameCells("矩形 3 色往返", p1, r1);
        check("往返:尺寸", r1.cols == 6 && r1.rows == 5);
        check("往返:非圆非六", !r1.round && !r1.hex);
        check("往返:总豆数", r1.totalBeads == p1.totalBeads);

        // ---- 往返:圆形板(板外 -1) ----
        BeadPattern p2 = pattern(9, 9, true, false, 4, false);
        BeadPattern r2 = roundTrip(p2);
        sameCells("圆形板往返(板外空格)", p2, r2);
        check("往返:圆标志", r2.round && !r2.hex);

        // ---- 往返:六边形板 ----
        BeadPattern p3 = pattern(9, 9, false, true, 4, false);
        BeadPattern r3 = roundTrip(p3);
        sameCells("六边形板往返", p3, r3);
        check("往返:六标志", r3.hex);

        // ---- 往返:单色满铺(RLE 一条长 run) ----
        BeadPattern p4 = pattern(20, 20, false, false, 1, false);
        BeadPattern r4 = roundTrip(p4);
        sameCells("单色满铺往返(RLE)", p4, r4);

        // ---- 往返:交替棋盘(RLE 最碎) ----
        int[] cells = new int[8 * 8];
        for (int i = 0; i < cells.length; i++) cells[i] = i % 2;
        BeadPattern p5 = assemble(8, 8, palette(2), cells, false, false);
        BeadPattern r5 = roundTrip(p5);
        sameCells("棋盘往返(RLE 最碎)", p5, r5);

        // ---- 往返:名字保留(空名给默认) ----
        JSONObject named = PatternShare.build(p1, "我的小马");
        check("名字写入", "我的小马".equals(named.optString("name")));
        check("格式名写入", "pindou-pattern".equals(named.optString("format")));
        check("空名给默认", "未命名图纸".equals(
                PatternShare.build(p1, "").optString("name")));

        // ---- 坏文件拒收 ----
        reject("非本格式拒收", mk("format", "png"));
        reject("缺版本当 0 拒收", mk());
        reject("尺寸过小拒收", new JSONObject(
                "{\"format\":\"pindou-pattern\",\"version\":1,\"cols\":3,\"rows\":5,"
                        + "\"colors\":[{\"code\":1,\"name\":\"a\",\"rgb\":255}],"
                        + "\"cells\":[0,15]}"));
        reject("缺颜色表拒收", new JSONObject(
                "{\"format\":\"pindou-pattern\",\"version\":1,\"cols\":6,\"rows\":5,"
                        + "\"cells\":[0,30]}"));
        reject("格子数量不符拒收", new JSONObject(
                "{\"format\":\"pindou-pattern\",\"version\":1,\"cols\":6,\"rows\":5,"
                        + "\"colors\":[{\"code\":1,\"name\":\"a\",\"rgb\":255}],"
                        + "\"cells\":[0,10]}"));
        reject("RLE 长度 0 拒收", new JSONObject(
                "{\"format\":\"pindou-pattern\",\"version\":1,\"cols\":6,\"rows\":5,"
                        + "\"colors\":[{\"code\":1,\"name\":\"a\",\"rgb\":255}],"
                        + "\"cells\":[0,0]}"));
        reject("颜色下标越界拒收", new JSONObject(
                "{\"format\":\"pindou-pattern\",\"version\":1,\"cols\":6,\"rows\":5,"
                        + "\"colors\":[{\"code\":1,\"name\":\"a\",\"rgb\":255}],"
                        + "\"cells\":[5,30]}"));
        reject("RLE 截断拒收(奇数长度)", new JSONObject(
                "{\"format\":\"pindou-pattern\",\"version\":1,\"cols\":6,\"rows\":5,"
                        + "\"colors\":[{\"code\":1,\"name\":\"a\",\"rgb\":255}],"
                        + "\"cells\":[0,15,1]}"));

        System.out.println("TestPatternShare: " + passed + " passed, "
                + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ---------------- helpers ----------------

    private static BeadPattern roundTrip(BeadPattern p) throws Exception {
        return PatternShare.parse(PatternShare.build(p, "t"));
    }

    /** 往返后逐格颜色一致(cells 会重映射下标,按 rgb 对齐比较) */
    private static void sameCells(String name, BeadPattern a, BeadPattern b) {
        boolean ok = a.cols == b.cols && a.rows == b.rows;
        if (ok) {
            for (int y = 0; y < a.rows && ok; y++) {
                for (int x = 0; x < a.cols && ok; x++) {
                    int ia = a.cellAt(x, y);
                    int ib = b.cellAt(x, y);
                    if (ia < 0 && ib < 0) continue;
                    if (ia < 0 || ib < 0) {
                        ok = false;
                        break;
                    }
                    if (a.palette.get(ia).rgb != b.palette.get(ib).rgb) ok = false;
                }
            }
        }
        check(name, ok);
    }

    private static void reject(String name, JSONObject o) {
        try {
            PatternShare.parse(o);
            check(name, false);
        } catch (Exception e) {
            check(name, true);
        }
    }

    private static JSONObject mk(String key, String value) throws Exception {
        JSONObject o = new JSONObject();
        o.put("format", value);
        o.put("version", 1);
        return o;
    }

    private static JSONObject mk() throws Exception {
        JSONObject o = new JSONObject();
        o.put("format", "pindou-pattern");
        return o;
    }

    private static List<BeadColor> palette(int n) {
        List<BeadColor> pal = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pal.add(new BeadColor(100 + i, "C" + i, 0x224466 + i * 0x112233));
        }
        return pal;
    }

    /** 造图:伪随机铺色;emptyPockets=true 时中心挖几个 -1 格 */
    private static BeadPattern pattern(int cols, int rows, boolean round,
                                       boolean hex, int colors, boolean emptyPockets) {
        int[] cells = new int[cols * rows];
        int seed = 90210;
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
                if (emptyPockets && (idx == 7 || idx == 14 || idx == 22)) {
                    cells[idx] = -1;
                    continue;
                }
                seed = seed * 1103515245 + 12345;
                cells[idx] = Math.abs(seed) % colors;
            }
        }
        return assemble(cols, rows, palette(colors), cells, round, hex);
    }

    private static BeadPattern assemble(int cols, int rows, List<BeadColor> pal,
                                        int[] cells, boolean round, boolean hex) {
        int[] counts = new int[pal.size()];
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        for (int v : cells) {
            if (v >= 0) counts[v]++;
        }
        int total = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] <= 0) continue;
            total += counts[i];
            used.add(new BeadPattern.UsedColor(i, pal.get(i),
                    String.valueOf((char) ('A' + i)), counts[i]));
        }
        BeadPattern.sortByCountDesc(used);
        return new BeadPattern(cols, rows, pal, cells, counts, used,
                total, cols * rows - total, round, hex);
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
