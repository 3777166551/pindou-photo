package com.pindou.app.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 全量备份/恢复:把 App 私有数据(项目存档 + 豆仓库存 + 打卡日历 + 自定义色板)
 * 打包成一个 zip,经系统文件选择器(SAF)导出/导入,零新权限。
 * 打包/校验/解包只依赖 java.io/java.util.zip,桌面 JVM 可直接单测(TestBackup);
 * Android 侧只负责拿 filesDir、SAF 流和恢复后失效内存缓存。
 *
 * zip 结构(条目名固定,恢复按白名单落盘,防 zip-slip):
 *   manifest.json        {"format":"pindou-backup","version":1,"savedAt":毫秒,
 *                         "app":"PindouPhoto","counts":{"projects":N,
 *                         "inventory":true/false,"calendar":..,"palettes":..}}
 *   projects/*.json      项目存档(ProjectStore)
 *   inventory.json       豆仓(BeadInventory,旧备份可缺)
 *   calendar.json        打卡日历(BeadCalendar,可缺)
 *   custom_palettes.json 自定义色板(CustomPalettes,可缺)
 *
 * 恢复语义:项目存档整目录替换;三个库文件有则覆盖、无则保留现状
 * (旧版本备份缺某个文件时不清用户现有数据)。
 */
public final class BackupManager {

    public static final String FORMAT = "pindou-backup";
    public static final int VERSION = 1;
    public static final String MANIFEST = "manifest.json";

    private static final String DIR_PROJECTS = "projects/";
    private static final String F_INVENTORY = "inventory.json";
    private static final String F_CALENDAR = "calendar.json";
    private static final String F_PALETTES = "custom_palettes.json";

    private BackupManager() {
    }

    /** 备份内容摘要:多少项目、含哪些库(导出 toast / 恢复确认共用) */
    public static final class Result {
        public int projects;
        public boolean inventory;
        public boolean calendar;
        public boolean palettes;
        public long savedAt;

        /** 人类可读摘要(不含当前数据);空内容返回 "空备份" 由调用方兜底 */
        public boolean hasAnything() {
            return projects > 0 || inventory || calendar || palettes;
        }
    }

    /** filesDir 下要打包的相对路径清单(存在的才收,projects 按名排序保证稳定) */
    private static List<String> collect(File filesDir) {
        List<String> out = new ArrayList<>();
        File[] pros = new File(filesDir, "projects").listFiles();
        if (pros != null) {
            List<String> names = new ArrayList<>();
            for (File f : pros) {
                if (f.isFile() && f.getName().endsWith(".json")) names.add(f.getName());
            }
            Collections.sort(names);
            for (String n : names) out.add(DIR_PROJECTS + n);
        }
        if (new File(filesDir, F_INVENTORY).isFile()) out.add(F_INVENTORY);
        if (new File(filesDir, F_CALENDAR).isFile()) out.add(F_CALENDAR);
        if (new File(filesDir, F_PALETTES).isFile()) out.add(F_PALETTES);
        return out;
    }

    private static File target(File filesDir, String relPath) {
        if (relPath.startsWith(DIR_PROJECTS)) {
            String name = relPath.substring(DIR_PROJECTS.length());
            if (name.isEmpty() || name.contains("/") || name.contains("\\")
                    || !name.endsWith(".json")) {
                return null;
            }
            return new File(filesDir, DIR_PROJECTS + name);
        }
        if (relPath.equals(F_INVENTORY)) return new File(filesDir, F_INVENTORY);
        if (relPath.equals(F_CALENDAR)) return new File(filesDir, F_CALENDAR);
        if (relPath.equals(F_PALETTES)) return new File(filesDir, F_PALETTES);
        return null;
    }

    /** 打包成 zip 写到 out;顺便返回内容摘要(给导出 toast 用) */
    public static Result writeZip(File filesDir, OutputStream out) throws IOException {
        List<String> rels = collect(filesDir);
        Result r = new Result();
        ZipOutputStream zip = new ZipOutputStream(out);
        // manifest 放第一个条目,导入校验只认它
        int projects = 0;
        boolean inv = false, cal = false, pal = false;
        for (String rel : rels) {
            if (rel.startsWith(DIR_PROJECTS)) projects++;
            else if (rel.equals(F_INVENTORY)) inv = true;
            else if (rel.equals(F_CALENDAR)) cal = true;
            else if (rel.equals(F_PALETTES)) pal = true;
        }
        r.projects = projects;
        r.inventory = inv;
        r.calendar = cal;
        r.palettes = pal;
        r.savedAt = System.currentTimeMillis();
        byte[] manifest = ("{\"format\":\"" + FORMAT + "\",\"version\":" + VERSION
                + ",\"savedAt\":" + r.savedAt + ",\"app\":\"PindouPhoto\""
                + ",\"counts\":{\"projects\":" + projects
                + ",\"inventory\":" + inv + ",\"calendar\":" + cal
                + ",\"palettes\":" + pal + "}}")
                .getBytes(StandardCharsets.UTF_8);
        zip.putNextEntry(new ZipEntry(MANIFEST));
        zip.write(manifest);
        zip.closeEntry();
        for (String rel : rels) {
            File f = target(filesDir, rel);
            if (f == null) continue;
            zip.putNextEntry(new ZipEntry(rel));
            copy(new FileInputStream(f), zip);
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
        return r;
    }

    /**
     * 校验并处理一个备份流。extract=false 只做校验与统计(恢复确认前预览);
     * true 时把白名单条目写入 filesDir(项目整目录替换)。
     * 格式不对抛 IOException,消息可直接给用户看。
     */
    public static Result readZip(InputStream in, File filesDir, boolean extract)
            throws IOException {
        // 先落到临时文件再用 ZipFile 双遍读:manifest 可能不在流首,
        // 校验必须先于任何落盘,而项目文件可能很大不宜整包进内存
        File tmp = File.createTempFile("pindou_bk", ".zip", filesDir);
        try {
            OutputStream tf = new FileOutputStream(tmp);
            try {
                copy(in, tf);
            } finally {
                tf.close();
            }
            ZipFile zip = new ZipFile(tmp);
            try {
                ZipEntry mf = zip.getEntry(MANIFEST);
                if (mf == null) {
                    throw new IOException("not_pindou_backup");
                }
                ByteArrayOutputStream mo = new ByteArrayOutputStream();
                copy(zip.getInputStream(mf), mo);
                String manifest = new String(mo.toByteArray(), StandardCharsets.UTF_8);
                if (!FORMAT.equals(manifestField(manifest, "format"))) {
                    throw new IOException("not_pindou_backup");
                }
                int ver = parseSafeInt(manifestField(manifest, "version"), -1);
                if (ver < 1 || ver > VERSION) {
                    throw new IOException("unsupported_version:" + ver);
                }

                Result r = new Result();
                r.savedAt = parseSafeLong(manifestField(manifest, "savedAt"), 0L);

                // 第一遍:统计(以实际条目为准,不信任 manifest 的 counts)
                java.util.Enumeration<? extends ZipEntry> en = zip.entries();
                List<String> dataEntries = new ArrayList<>();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    String name = e.getName().replace('\\', '/');
                    if (name.startsWith("/")) name = name.substring(1);
                    if (target(filesDir, name) == null) continue;   // 白名单外直接忽略
                    dataEntries.add(name);
                    if (name.startsWith(DIR_PROJECTS)) r.projects++;
                    else if (name.equals(F_INVENTORY)) r.inventory = true;
                    else if (name.equals(F_CALENDAR)) r.calendar = true;
                    else if (name.equals(F_PALETTES)) r.palettes = true;
                }
                if (!extract) {
                    return r;   // inspect 模式:只校验与统计,不落盘
                }

                // 恢复:项目整目录替换(旧文件先删,避免残留孤儿);
                // 空备份 = 恢复成"没有项目"的状态,三个库文件缺了就不动现状
                File projectsDir = new File(filesDir, "projects");
                File[] old = projectsDir.listFiles();
                if (old != null) {
                    for (File f : old) {
                        if (f.isFile() && f.getName().endsWith(".json")) f.delete();
                    }
                }
                if (!projectsDir.exists()) projectsDir.mkdirs();

                for (String name : dataEntries) {
                    ZipEntry e = zip.getEntry(name);
                    if (e == null) continue;
                    File dst = target(filesDir, name);
                    // 双保险:落盘路径必须在 filesDir 内(防 zip-slip 变体)
                    if (!dst.getCanonicalPath().startsWith(
                            filesDir.getCanonicalPath() + File.separator)) {
                        continue;
                    }
                    File parent = dst.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    OutputStream fo = new FileOutputStream(dst);
                    try {
                        copy(zip.getInputStream(e), fo);
                    } finally {
                        fo.close();
                    }
                }
                return r;
            } finally {
                zip.close();
            }
        } finally {
            tmp.delete();
        }
    }

    /** 从 manifest 文本里抠一个顶层字段(format/version/savedAt 都是简单标量) */
    public static String manifestField(String manifest, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"([^\"]*)\"|[-0-9.]+)")
                .matcher(manifest);
        if (!m.find()) return "";
        String g = m.group(1);
        if (g.startsWith("\"")) return m.group(2);
        return g;
    }

    private static int parseSafeInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseSafeLong(String s, long def) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
    }
}
