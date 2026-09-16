import com.pindou.app.util.BackupManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 全量备份/恢复核心测试(纯 JVM,不依赖 android):
 *  - writeZip/readZip 往返:文件逐字节一致、Result 统计正确、白名单外条目被忽略
 *  - inspect 模式(extract=false)只校验不落盘
 *  - 恢复语义:项目目录整目录替换(旧孤儿删除),库文件缺了不动现状
 *  - 拒绝非本格式:无 manifest / format 不对 / version 超范围
 *  - zip-slip:带 ../ 的恶意条目不落盘
 * 失败时 exit 1。
 */
public class TestBackup {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static File tempDir() throws Exception {
        File d = Files.createTempDirectory("pindou_bk_test").toFile();
        d.deleteOnExit();
        return d;
    }

    static File write(File dir, String rel, byte[] data) throws Exception {
        File f = new File(dir, rel);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        o.write(data);
        o.close();
        return f;
    }

    static boolean sameFile(File a, File b) throws Exception {
        if (!a.isFile() || !b.isFile()) return false;
        FileInputStream ia = new FileInputStream(a);
        FileInputStream ib = new FileInputStream(b);
        int ca, cb;
        do {
            ca = ia.read();
            cb = ib.read();
            if (ca != cb) {
                ia.close();
                ib.close();
                return false;
            }
        } while (ca != -1);
        ia.close();
        ib.close();
        return true;
    }

    static BackupManager.Result roundTrip(File src, File dst, boolean extract)
            throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        BackupManager.writeZip(src, bo);
        return BackupManager.readZip(new ByteArrayInputStream(bo.toByteArray()),
                dst, extract);
    }

    public static void main(String[] args) throws Exception {
        // ---- 往返:两份项目 + 三个库文件 ----
        File src = tempDir();
        write(src, "projects/a.json", "{\"name\":\"A\"}".getBytes("UTF-8"));
        write(src, "projects/b.json", "{\"name\":\"B\"}".getBytes("UTF-8"));
        write(src, "inventory.json", "{\"v\":1}".getBytes("UTF-8"));
        write(src, "calendar.json", "{}".getBytes("UTF-8"));
        write(src, "custom_palettes.json", "{\"v\":1,\"palettes\":[]}".getBytes("UTF-8"));

        File dst = tempDir();
        write(dst, "projects/stale.json", "old".getBytes("UTF-8"));   // 恢复时该被清掉

        BackupManager.Result r = roundTrip(src, dst, true);
        check("roundtrip: counts", r.projects == 2 && r.inventory
                && r.calendar && r.palettes && r.savedAt > 0);
        check("roundtrip: files byte-identical",
                sameFile(new File(src, "projects/a.json"), new File(dst, "projects/a.json"))
                        && sameFile(new File(src, "inventory.json"),
                        new File(dst, "inventory.json"))
                        && sameFile(new File(src, "custom_palettes.json"),
                        new File(dst, "custom_palettes.json")));
        check("roundtrip: stale project replaced", !new File(dst, "projects/stale.json").exists());
        check("roundtrip: no extra entries in projects dir",
                new File(dst, "projects").listFiles().length == 2);
        check("roundtrip: temp zip cleaned",
                dst.listFiles().length == 4);   // projects/ + 三个库文件,无 .zip 残留

        // ---- inspect 模式:只校验统计,不落盘 ----
        File dst2 = tempDir();
        BackupManager.Result ri = roundTrip(src, dst2, false);
        check("inspect: same counts without writing", ri.projects == 2
                && ri.inventory && dst2.listFiles().length == 0);

        // ---- 缺库文件的旧备份:有的覆盖,缺的保留现状 ----
        File src3 = tempDir();
        write(src3, "projects/only.json", "{\"name\":\"O\"}".getBytes("UTF-8"));
        File dst3 = tempDir();
        write(dst3, "calendar.json", "keep".getBytes("UTF-8"));
        BackupManager.Result r3 = roundTrip(src3, dst3, true);
        byte[] kept = Files.readAllBytes(new File(dst3, "calendar.json").toPath());
        check("partial backup: projects replaced, absent stores kept",
                r3.projects == 1 && !r3.inventory && !r3.palettes
                        && new String(kept, "UTF-8").equals("keep"));

        // ---- 空备份:合法,恢复 = 清空项目、库文件不动 ----
        File src4 = tempDir();
        File dst4 = tempDir();
        write(dst4, "projects/x.json", "x".getBytes("UTF-8"));
        BackupManager.Result r4 = roundTrip(src4, dst4, true);
        check("empty backup: valid, clears projects", r4.projects == 0
                && !r4.hasAnything() && !new File(dst4, "projects/x.json").exists());

        // ---- 拒绝非本格式 ----
        boolean rejected = false;
        try {
            File empty = tempDir();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream z = new ZipOutputStream(bo);
            z.putNextEntry(new ZipEntry("whatever.json"));
            z.write("{}".getBytes("UTF-8"));
            z.close();
            BackupManager.readZip(new ByteArrayInputStream(bo.toByteArray()),
                    empty, true);
        } catch (java.io.IOException e) {
            rejected = e.getMessage() != null && e.getMessage().contains("not_pindou_backup");
        }
        check("reject: zip without manifest", rejected);

        rejected = false;
        try {
            File empty = tempDir();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream z = new ZipOutputStream(bo);
            z.putNextEntry(new ZipEntry("manifest.json"));
            z.write("{\"format\":\"other\",\"version\":1}".getBytes("UTF-8"));
            z.close();
            BackupManager.readZip(new ByteArrayInputStream(bo.toByteArray()),
                    empty, true);
        } catch (java.io.IOException e) {
            rejected = e.getMessage() != null && e.getMessage().contains("not_pindou_backup");
        }
        check("reject: wrong format field", rejected);

        rejected = false;
        try {
            File empty = tempDir();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream z = new ZipOutputStream(bo);
            z.putNextEntry(new ZipEntry("manifest.json"));
            z.write("{\"format\":\"pindou-backup\",\"version\":99}".getBytes("UTF-8"));
            z.close();
            BackupManager.readZip(new ByteArrayInputStream(bo.toByteArray()),
                    empty, true);
        } catch (java.io.IOException e) {
            rejected = e.getMessage() != null && e.getMessage().contains("unsupported_version");
        }
        check("reject: version too new", rejected);

        // ---- zip-slip:../ 条目不落盘 ----
        File dst5 = tempDir();
        File outside = dst5.getParentFile();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bo);
        z.putNextEntry(new ZipEntry("manifest.json"));
        z.write("{\"format\":\"pindou-backup\",\"version\":1}".getBytes("UTF-8"));
        z.closeEntry();
        z.putNextEntry(new ZipEntry("projects/../../evil.json"));
        z.write("evil".getBytes("UTF-8"));
        z.closeEntry();
        z.close();
        BackupManager.readZip(new ByteArrayInputStream(bo.toByteArray()), dst5, true);
        check("zip-slip: traversal entry ignored",
                !new File(outside, "evil.json").exists()
                        && !new File(outside, "projects/evil.json").exists()
                        && new File(dst5, "projects").listFiles().length == 0);

        // ---- manifestField:简单标量解析 ----
        String mf = "{\"format\":\"pindou-backup\",\"version\":1,\"savedAt\":1725400000000,"
                + "\"app\":\"PindouPhoto\",\"counts\":{\"projects\":2}}";
        check("manifestField: scalars",
                "pindou-backup".equals(BackupManager.manifestField(mf, "format"))
                        && "1".equals(BackupManager.manifestField(mf, "version"))
                        && "1725400000000".equals(BackupManager.manifestField(mf, "savedAt"))
                        && "".equals(BackupManager.manifestField(mf, "missing")));

        System.out.println("TestBackup: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
