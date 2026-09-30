import com.pindou.app.bead.BeadInventory;
import com.pindou.app.util.BeadCalendar;
import com.pindou.app.util.DraftStore;
import com.pindou.app.util.ProjectStore;
import com.pindou.app.util.Jsons;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 损坏注入 chaos 测试(2026-09-29,用户点名"测功能稳定性"):
 * 对四类持久化文件(calendar/draft/projects/inventory)系统性注入破坏
 * ——随机字节/截断/空文件/超大/类型错乱/编码错位/位翻转——注入后调用
 * 模块全部读写路径,断言不抛未捕获异常,且一次合法写入后能自愈。
 * 真实用户最常见的稳定性事故就是文件损坏(杀进程/存储异常),此前只有
 * 零散覆盖,本套件系统化。全部确定性(seed 90210),7 算子 x 4 模块 x 8 变体。
 */
public class TestChaos {

    private static int passed = 0, failed = 0;
    private static File dir;
    private static final Random RND = new Random(90210);

    public static void main(String[] args) throws Exception {
        dir = new File("qaout_chaos");
        wipe(dir);
        dir.mkdirs();

        chaosBeadInventory();
        chaosCalendar();
        chaosDraft();
        chaosProjects();

        System.out.println("TestChaos: " + passed + " passed, " + failed + " failed");
        wipe(dir);
        if (failed > 0) System.exit(1);
    }

    // ---------------- 破坏算子 ----------------

    private static final String VALID_JSON =
            "{\"v\":1,\"counts\":{\"336699\":42},\"name\":\"草稿\",\"savedAt\":1000,\"edits\":[[1,2]],\"day\":\"2026-09-29\"}";

    private static void operator(File f, int op, Random rnd) throws Exception {
        switch (op % 7) {
            case 0:                                   // 随机字节
                byte[] b = new byte[1 + rnd.nextInt(500)];
                rnd.nextBytes(b);
                write(f, b);
                break;
            case 1:                                   // 合法 JSON 截断
                byte[] full = VALID_JSON.getBytes(StandardCharsets.UTF_8);
                write(f, Arrays.copyOf(full,
                        Math.max(1, full.length * (1 + rnd.nextInt(80)) / 100)));
                break;
            case 2:                                   // 空文件
                write(f, new byte[0]);
                break;
            case 3:                                   // 超大重复内容
                byte[] big = new byte[1024 * 1024];
                Arrays.fill(big, (byte) 'x');
                write(f, big);
                break;
            case 4:                                   // 合法 JSON 但值类型错乱
                write(f, ("{\"v\":\"str\",\"counts\":\"abc\",\"edits\":\"x\","
                        + "\"name\":[1,2],\"savedAt\":-999999999999}").getBytes(
                        StandardCharsets.UTF_8));
                break;
            case 5:                                   // UTF-16 编码错位
                write(f, VALID_JSON.getBytes(StandardCharsets.UTF_16));
                break;
            default: {                                // 对合法内容随机位翻转
                byte[] base = VALID_JSON.getBytes(StandardCharsets.UTF_8);
                byte[] flipped = base.clone();
                for (int k = 0; k < 12; k++) {
                    int pos = rnd.nextInt(flipped.length);
                    flipped[pos] = (byte) (flipped[pos] ^ (1 << rnd.nextInt(8)));
                }
                write(f, flipped);
            }
        }
    }

    private static void chaosBeadInventory() throws Exception {
        File f = new File(dir, "inv.json");
        for (int v = 0; v < 8; v++) {
            for (int op = 0; op < 7; op++) {
                BeadInventory.useTestFile(f);
                operator(f, op, RND);
                BeadInventory.resetForRestore();
                int r1 = BeadInventory.get(null, 0x336699);           // 不崩
                List<Integer> own = BeadInventory.ownedColors(null);
                List<Integer> all = BeadInventory.allColors(null);
                BeadInventory.set(null, 0x336699, 7);                 // 自愈写
                BeadInventory.resetForRestore();
                boolean healed = BeadInventory.get(null, 0x336699) == 7;
                if (r1 < -1 || own == null || all == null || !healed) {
                    failed++;
                    System.out.println("[FAIL] inventory op=" + op + " v=" + v);
                    return;
                }
            }
        }
        passed++;
        check("豆仓:56 组破坏全部不崩且自愈", true);
    }

    private static void chaosCalendar() throws Exception {
        File f = new File(dir, "cal.json");
        for (int v = 0; v < 8; v++) {
            for (int op = 0; op < 7; op++) {
                BeadCalendar.useTestFile(f);
                operator(f, op, RND);
                BeadCalendar.resetForRestore();
                int g1 = BeadCalendar.get(null, "2026-09-29");
                BeadCalendar.add(null, 4);
                BeadCalendar.resetForRestore();
                boolean healed = BeadCalendar.get(null, BeadCalendar.today()) >= 4;
                if (g1 < 0 || !healed) {
                    failed++;
                    System.out.println("[FAIL] calendar op=" + op + " v=" + v);
                    return;
                }
            }
        }
        passed++;
        check("打卡日历:56 组破坏全部不崩且自愈", true);
    }

    private static void chaosDraft() throws Exception {
        File f = new File(dir, "draft.json");
        for (int v = 0; v < 8; v++) {
            for (int op = 0; op < 7; op++) {
                DraftStore.useTestFile(f);
                operator(f, op, RND);
                DraftStore.read(null);                    // 不崩(损坏返回 null/原文)
                JSONObject o = new JSONObject();
                o.put("name", "recovered");
                DraftStore.save(null, o);                 // 自愈写
                if (!DraftStore.exists(null)
                        || !DraftStore.read(null).contains("recovered")) {
                    failed++;
                    System.out.println("[FAIL] draft op=" + op + " v=" + v);
                    return;
                }
            }
        }
        passed++;
        check("草稿:56 组破坏全部不崩且自愈", true);
    }

    private static void chaosProjects() throws Exception {
        File d = new File(dir, "projects");
        for (int v = 0; v < 8; v++) {
            for (int op = 0; op < 7; op++) {
                wipeTree(d);
                ProjectStore.useTestDir(d);
                File f1 = ProjectStore.create(null, "p1", 1000L);
                operator(f1, op, RND);                     // 破坏项目文件本体
                File f2 = ProjectStore.create(null, "p2", 2000L);
                write(f2, ("{\"name\":\"p2\",\"savedAt\":2000}").getBytes(
                        StandardCharsets.UTF_8));
                List<ProjectStore.Entry> list = ProjectStore.list(null);   // 不崩
                boolean ok = false;
                for (ProjectStore.Entry e : list) {
                    if ("p2".equals(e.name)) ok = true;    // 好项目仍入列
                }
                ProjectStore.delete(f1);
                ProjectStore.delete(f2);
                if (!ok) {
                    failed++;
                    System.out.println("[FAIL] projects op=" + op + " v=" + v);
                    return;
                }
            }
        }
        passed++;
        check("项目存档:56 组破坏全部不崩且好项目不丢", true);
    }

    // ---------------- utils ----------------

    private static void wipe(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) wipe(k);
        f.delete();
    }

    private static void wipeTree(File f) {
        wipe(f);
    }

    private static void write(File f, byte[] b) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(b);
        out.close();
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
