import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadInventory;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.SubstituteSolver;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 豆仓库(BeadInventory + SubstituteSolver)桌面全量测试。
 * 通过 useTestFile 注入临时文件,覆盖:增删改/未登记与 0 两态/RGB 掩码/
 * 持久化往返/损坏文件/旧版 org.json 格式兼容/规模;替代算法的推荐与
 * 拒推边界(够/未登记/富余不足/色差过远/恰好够用)。
 */
public class TestInventory {

    private static int passed = 0, failed = 0;
    private static File dir;

    public static void main(String[] args) throws Exception {
        dir = new File("qaout_inventory");
        dir.mkdirs();
        for (File f : dir.listFiles()) f.delete();

        storageTests();
        substituteTests();

        System.out.println("TestInventory: " + passed + " passed, " + failed + " failed");
        for (File f : dir.listFiles()) f.delete();
        dir.delete();
        if (failed > 0) System.exit(1);
    }

    // ---------------- 存储层 ----------------

    private static void storageTests() throws Exception {
        File f = new File(dir, "inv_main.json");
        BeadInventory.useTestFile(f);

        // 两态语义
        check("未登记 = -1", BeadInventory.get(null, 0x336699) == -1);
        BeadInventory.set(null, 0x336699, 0);
        check("登记为 0 ≠ 未登记", BeadInventory.get(null, 0x336699) == 0);
        BeadInventory.set(null, 0x336699, 42);
        check("set 后 get", BeadInventory.get(null, 0x336699) == 42);
        BeadInventory.set(null, 0x336699, -7);
        check("负数钳制为 0", BeadInventory.get(null, 0x336699) == 0);
        BeadInventory.set(null, 0x336699, 42);

        // RGB 掩码:带 alpha/不带 alpha/纯 rgb 同一粒豆(用独立色,别碰 336699)
        BeadInventory.set(null, 0xFF445566, 10);
        check("0xRRGGBB 与 0xFFRRGGBB 同键", BeadInventory.get(null, 0x445566) == 10);
        BeadInventory.set(null, 0x00556677, 5);
        check("低位写入,get 带 alpha 读出", BeadInventory.get(null, 0xFF556677) == 5);

        // remove 回到未登记
        BeadInventory.set(null, 0xAABBCC, 3);
        BeadInventory.remove(null, 0xAABBCC);
        check("remove 后恢复未登记", BeadInventory.get(null, 0xAABBCC) == -1);

        // ownedColors/allColors
        BeadInventory.set(null, 0xFF0001, 1);
        BeadInventory.set(null, 0xFF0002, 0);
        List<Integer> owned = BeadInventory.ownedColors(null);
        boolean ownedOk = containsRgb(owned, 0x336699) && containsRgb(owned, 0xFF0001)
                && !containsRgb(owned, 0xFF0002);
        check("ownedColors 只含有货色", ownedOk);
        List<Integer> all = BeadInventory.allColors(null);
        check("allColors 含 0 数量色", containsRgb(all, 0xFF0002));

        // 持久化往返(resetForRestore = 丢弃内存,从磁盘重读)
        BeadInventory.set(null, 0x112233, Integer.MAX_VALUE);
        BeadInventory.resetForRestore();
        check("往返:普通值", BeadInventory.get(null, 0x336699) == 42);
        check("往返:Integer.MAX_VALUE", BeadInventory.get(null, 0x112233) == Integer.MAX_VALUE);
        check("往返:登记 0 态保持", BeadInventory.get(null, 0xFF0002) == 0);

        // remove 也持久化
        BeadInventory.remove(null, 0xFF0001);
        BeadInventory.resetForRestore();
        check("remove 持久化", BeadInventory.get(null, 0xFF0001) == -1);

        // 规模:300 色往返
        for (int i = 0; i < 300; i++) {
            BeadInventory.set(null, 0x100000 + i, i + 1);
        }
        BeadInventory.resetForRestore();
        boolean scaleOk = true;
        for (int i = 0; i < 300; i++) {
            if (BeadInventory.get(null, 0x100000 + i) != i + 1) scaleOk = false;
        }
        check("300 色持久化往返", scaleOk);

        // 损坏文件:当空库存不崩,且下次 set 重写为合法文件
        File bad = new File(dir, "inv_bad.json");
        write(bad, "{this is not json at all~~~");
        BeadInventory.useTestFile(bad);
        check("损坏文件读作空库存", BeadInventory.get(null, 0x336699) == -1);
        BeadInventory.set(null, 0x336699, 7);
        BeadInventory.resetForRestore();
        check("损坏后写入自愈", BeadInventory.get(null, 0x336699) == 7);

        // 旧版 org.json 格式兼容(与旧 BeadInventory 写出的形状一致)
        File legacy = new File(dir, "inv_legacy.json");
        write(legacy, "{\"v\":1,\"counts\":{\"ff0000\":5,\"00ff00\":0}}");
        BeadInventory.useTestFile(legacy);
        check("旧格式:非零值", BeadInventory.get(null, 0xFF0000) == 5);
        check("旧格式:0 值", BeadInventory.get(null, 0x00FF00) == 0);

        // 大写 hex 键容错
        File upper = new File(dir, "inv_upper.json");
        write(upper, "{\"counts\":{ \"AB12CD\" : 9 }}");
        BeadInventory.useTestFile(upper);
        check("大写 hex 键可读", BeadInventory.get(null, 0xAB12CD) == 9);

        // 新写出的文件是合法形状(供备份/人眼检查)
        File fresh = new File(dir, "inv_fresh.json");
        BeadInventory.useTestFile(fresh);
        BeadInventory.set(null, 0x123456, 8);
        String content = new String(read(fresh), StandardCharsets.UTF_8);
        check("写出文件含 counts 体", content.contains("\"counts\"")
                && content.contains("\"123456\":8"));

        BeadInventory.useTestFile(null);
    }

    // ---------------- 替代算法 ----------------

    private static void substituteTests() {
        // 色板颜色间距经 ColorMath 实测(ΔE²):0xCC2222↔0xCC3322=28(近,
        // 可推荐);↔0x2233AA=12932(远,拒推)。推荐阈值 ΔE<18(ΔE²<324)。
        List<BeadColor> pal = new ArrayList<>();
        pal.add(new BeadColor(1, "红", 0xCC2222));
        pal.add(new BeadColor(2, "近橙红", 0xCC3322));
        pal.add(new BeadColor(3, "蓝", 0x2233AA));
        pal.add(new BeadColor(4, "白", 0xEEEEEE));

        // 图纸:红用 50,近橙红自身用 10
        int[] counts = {50, 10, 5, 0};
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        used.add(new BeadPattern.UsedColor(0, pal.get(0), "A", 50));
        used.add(new BeadPattern.UsedColor(1, pal.get(1), "B", 10));

        // 库存足够 → 不推荐
        Map<Integer, Integer> inv = new HashMap<>();
        inv.put(0, 60);
        inv.put(1, 30);
        inv.put(2, 30);
        check("库存足够不推荐", solve(pal, counts, used, inv).isEmpty());

        // 未登记的需求色 → 不管
        Map<Integer, Integer> inv2 = new HashMap<>();
        inv2.put(1, 30);
        check("需求色未登记不推荐", solve(pal, counts, used, inv2).isEmpty());

        // 缺豆 → 推荐色差最近的富余色(近橙红,而非更远的蓝)
        Map<Integer, Integer> inv3 = new HashMap<>();
        inv3.put(0, 20);   // 红总需求 50,缺 30
        inv3.put(1, 70);   // 富余 70-10=60 ≥ 总需求 50
        inv3.put(2, 100);  // 富余 95 ≥ 50 但 ΔE≈114 拒推
        Map<Integer, Integer> r3 = solve(pal, counts, used, inv3);
        check("缺豆推荐最近色(近橙红)", r3.get(0) != null && r3.get(0) == 1);

        // 富余不足(拆东墙防护):近橙红富余只有 5
        Map<Integer, Integer> inv4 = new HashMap<>();
        inv4.put(0, 20);
        inv4.put(1, 15);   // 15-10=5 < 50
        inv4.put(2, 15);   // 且蓝色 ΔE 过远
        check("富余都不足不推荐(拆东墙防护)", solve(pal, counts, used, inv4).isEmpty());

        // 原语义文档化:富余 ≥ 缺口但 < 总需求 → 不推荐(推"整套替掉",
        // 不推"混着用";与线上历史行为一致,曾在此写过错的期望)
        Map<Integer, Integer> inv5b = new HashMap<>();
        inv5b.put(0, 20);
        inv5b.put(1, 40);   // 富余 30 ≥ 缺口 30,但 < 总需求 50
        check("富余≥缺口但<总需求不推荐(原语义)", solve(pal, counts, used, inv5b).isEmpty());

        // 富余恰好等于总需求 → 可推荐
        Map<Integer, Integer> inv5 = new HashMap<>();
        inv5.put(0, 20);
        inv5.put(1, 60);   // 60-10=50 == 总需求 50
        Map<Integer, Integer> r5 = solve(pal, counts, used, inv5);
        check("富余恰好等于总需求可推荐", r5.get(0) != null && r5.get(0) == 1);

        // 登记为 0 的色当不了替代
        Map<Integer, Integer> inv6 = new HashMap<>();
        inv6.put(0, 20);
        inv6.put(1, 0);
        check("0 库存色不能当替代", solve(pal, counts, used, inv6).isEmpty());

        // ΔE 过远不推荐:只有蓝色富余
        Map<Integer, Integer> inv7 = new HashMap<>();
        inv7.put(0, 10);
        inv7.put(2, 100);
        check("色差过远不推荐", solve(pal, counts, used, inv7).isEmpty());

        // 替代色自身用量高的富余被扣除后仍够 → 正常推荐
        Map<Integer, Integer> inv8 = new HashMap<>();
        inv8.put(0, 10);
        inv8.put(1, 60);   // 60-10=50 ≥ 50
        Map<Integer, Integer> r8 = solve(pal, counts, used, inv8);
        check("扣除自身用量后仍够则推荐", r8.get(0) != null && r8.get(0) == 1);

        // 多个缺色各自独立求解(近红近蓝找不到都不硬推)
        List<BeadPattern.UsedColor> used9 = new ArrayList<>();
        used9.add(new BeadPattern.UsedColor(0, pal.get(0), "A", 50));
        used9.add(new BeadPattern.UsedColor(2, pal.get(2), "C", 20));
        int[] counts9 = {50, 10, 20, 0};
        Map<Integer, Integer> inv9 = new HashMap<>();
        inv9.put(0, 10);   // 红缺 40
        inv9.put(2, 5);    // 蓝缺 15
        inv9.put(1, 60);   // 近橙红富余 50 → 补红(ΔE≈5.3)
        inv9.put(3, 100);  // 白对红/蓝都过远
        Map<Integer, Integer> r9 = solve(pal, counts9, used9, inv9);
        check("红缺→推荐近橙红", r9.get(0) != null && r9.get(0) == 1);
        check("蓝缺找不到近色→不硬推", r9.get(2) == null);

        // 空用量列表
        check("空用量返回空", solve(pal, counts, new ArrayList<BeadPattern.UsedColor>(), inv).isEmpty());
    }

    private static Map<Integer, Integer> solve(List<BeadColor> pal, int[] counts,
                                               List<BeadPattern.UsedColor> used,
                                               Map<Integer, Integer> inv) {
        return SubstituteSolver.solve(pal, counts, used, inv);
    }

    // ---------------- utils ----------------

    private static boolean containsRgb(List<Integer> list, int rgb) {
        for (Integer v : list) {
            if (v != null && (v.intValue() & 0xFFFFFF) == (rgb & 0xFFFFFF)) return true;
        }
        return false;
    }

    private static void write(File f, String s) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    private static byte[] read(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        int n = in.read(buf);
        in.close();
        byte[] out = new byte[Math.max(0, n)];
        System.arraycopy(buf, 0, out, 0, out.length);
        return out;
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
