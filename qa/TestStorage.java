import com.pindou.app.util.BeadCalendar;
import com.pindou.app.util.DraftStore;
import com.pindou.app.util.ProjectStore;
import com.pindou.app.util.Jsons;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 本地存储三件套测试:打卡日历(BeadCalendar)/自动草稿(DraftStore)/
 * 项目存档列表(ProjectStore)。三者共用 useTestFile/useTestDir 桌面
 * 钩子;org.json 走 qa 迷你实现(遮蔽 android.jar stub)。
 * 覆盖:计数钳制/多天独立/持久化往返/损坏兜底/列表排序/坏文件跳过/
 * 非 .json 忽略/删除;另验证"缩略图损坏不影响项目入列"的生产修复。
 */
public class TestStorage {

    private static int passed = 0, failed = 0;
    private static File dir;

    public static void main(String[] args) throws Exception {
        dir = new File("qaout_storage");
        wipe(dir);          // 递归清理(含 projects 子目录残留)
        dir.mkdirs();

        calendarTests();
        draftTests();
        projectTests();

        System.out.println("TestStorage: " + passed + " passed, " + failed + " failed");
        wipe(dir);
        if (failed > 0) System.exit(1);
    }

    private static void wipe(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) wipe(k);
        }
        f.delete();
    }

    // ---------------- 打卡日历 ----------------

    private static void calendarTests() throws Exception {
        File f = new File(dir, "calendar.json");
        BeadCalendar.useTestFile(f);

        check("未记录的日 = 0", BeadCalendar.get(null, "2026-09-29") == 0);
        BeadCalendar.add(null, 10);
        check("add 累计", BeadCalendar.get(null, BeadCalendar.today()) == 10);
        BeadCalendar.add(null, -3);
        check("负 delta 扣减", BeadCalendar.get(null, BeadCalendar.today()) == 7);
        BeadCalendar.add(null, -99);
        check("扣到负数钳 0", BeadCalendar.get(null, BeadCalendar.today()) == 0);
        BeadCalendar.add(null, 5);
        // 任意历史日键(日历月视图回看)
        BeadCalendar.add(null, 0);   // 零变化也写键(行为:optInt+0 仍 put)
        check("历史日独立累计", BeadCalendar.get(null, "2026-09-01") == 0);

        BeadCalendar.resetForRestore();
        check("持久化往返", BeadCalendar.get(null, BeadCalendar.today()) == 5);

        // 损坏文件当空
        write(f, "{broken json!!");
        BeadCalendar.resetForRestore();
        check("损坏日历读作 0", BeadCalendar.get(null, BeadCalendar.today()) == 0);
        BeadCalendar.add(null, 2);
        BeadCalendar.resetForRestore();
        check("损坏后写入自愈", BeadCalendar.get(null, BeadCalendar.today()) == 2);
    }

    // ---------------- 自动草稿 ----------------

    private static void draftTests() throws Exception {
        File f = new File(dir, "draft.json");
        DraftStore.useTestFile(f);

        check("初始不存在", !DraftStore.exists(null));
        check("读不存返回 null", DraftStore.read(null) == null);

        JSONObject o = new JSONObject();
        o.put("name", "草稿测试");
        o.put("cols", 29);
        o.put("edits", new org.json.JSONArray());
        DraftStore.save(null, o);
        check("保存后存在", DraftStore.exists(null));
        String back = DraftStore.read(null);
        check("读回含中文键值", back != null && back.contains("草稿测试")
                && back.contains("\"cols\":29"));

        // 再写覆盖(单槽语义)
        JSONObject o2 = new JSONObject();
        o2.put("name", "v2");
        DraftStore.save(null, o2);
        check("单槽覆盖写", DraftStore.read(null).contains("\"v2\"")
                && !DraftStore.read(null).contains("草稿测试"));

        DraftStore.delete(null);
        check("删除后不存在", !DraftStore.exists(null));

        // 空文件(0 字节)不算存在
        write(f, "");
        check("0 字节文件不算存在", !DraftStore.exists(null));
    }

    // ---------------- 项目存档列表 ----------------

    private static void projectTests() throws Exception {
        File d = new File(dir, "projects");
        ProjectStore.useTestDir(d);

        check("空目录列表为空", ProjectStore.list(null).isEmpty());

        // 两个项目:手写 JSON(create 只建文件名,内容由调用方写)
        File f1 = ProjectStore.create(null, "旧项目", 1000L);
        write(f1, "{\"name\":\"旧项目\",\"savedAt\":1000}");
        File f2 = ProjectStore.create(null, "新项目", 2000L);
        write(f2, "{\"name\":\"新项目\",\"savedAt\":2000,\"thumb\":\"\"}");

        List<ProjectStore.Entry> list = ProjectStore.list(null);
        check("两个项目都入列", list.size() == 2);
        check("按 savedAt 降序", list.get(0).name.equals("新项目")
                && list.get(1).name.equals("旧项目"));
        check("thumb 缺失 → null 且不吞 entry",
                list.get(0).thumb == null);

        // thumb 损坏(base64 乱码):生产修复验证——entry 保留,thumb null
        File f3 = ProjectStore.create(null, "坏缩略图", 3000L);
        write(f3, "{\"name\":\"坏缩略图\",\"savedAt\":3000,"
                + "\"thumb\":\"!!!not-base64!!!\"}");
        List<ProjectStore.Entry> list2 = ProjectStore.list(null);
        boolean kept = false;
        for (ProjectStore.Entry e : list2) {
            if (e.name.equals("坏缩略图")) kept = e.thumb == null;
        }
        check("缩略图损坏不吞项目(生产修复)", kept && list2.size() == 3);

        // 坏 JSON 文件被跳过
        File f4 = ProjectStore.create(null, "坏json", 4000L);
        write(f4, "{not json");
        check("坏 JSON 文件跳过", ProjectStore.list(null).size() == 3);

        // 非 .json 忽略
        write(new File(d, "notes.txt"), "hello");
        check("非 .json 文件忽略", ProjectStore.list(null).size() == 3);

        // 删除
        ProjectStore.delete(f2);
        check("删除生效", ProjectStore.list(null).size() == 2);
        ProjectStore.delete(null);
        check("delete(null) 不崩", ProjectStore.list(null).size() == 2);
    }

    // ---------------- utils ----------------

    private static void write(File f, String s) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(s.getBytes(StandardCharsets.UTF_8));
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
