package com.pindou.app.bead;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 豆仓库存:记录用户手头各种颜色豆子的数量。
 * 以颜色 RGB 为键(跨色板通用——同一个 RGB 在哪个色板里都是同一种豆),
 * 持久化到 files/inventory.json。
 * get 返回 -1 表示"从未登记过",0 表示"登记过但已用完",以此区分两态。
 * 纯 Java 实现(手写 JSON 序列化/解析,无 org.json——android.jar 里的
 * org.json 是运行时抛异常的 stub,挡住了桌面 JVM 单测;格式与旧版
 * org.json 写出的完全一致,旧文件直接可读)。qa 可用 useTestFile 注入
 * 文件路径在桌面跑全套往返测试(TestInventory)。
 */
public final class BeadInventory {

    private static final Map<Integer, Integer> COUNTS = new HashMap<>();
    private static boolean loaded = false;
    /** qa 专用:注入桌面测试文件路径(null = 正常 Android filesDir 路径) */
    private static File testFile;

    private static File file(Context c) {
        return testFile != null
                ? testFile : new File(c.getFilesDir(), "inventory.json");
    }

    /** qa 专用:切到桌面测试文件并丢弃内存缓存;测完可传 null 还原 */
    public static void useTestFile(File f) {
        testFile = f;
        resetForRestore();
    }

    private static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            File f = file(c);
            if (!f.exists()) return;
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf);
            in.close();
            loadFrom(new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // 损坏就当空库存,用户重新登记即可
        }
    }

    /** 解析 {"v":1,"counts":{"rrggbb":n,...}};只认 counts 体里的 6 位 hex 键 */
    private static void loadFrom(String s) {
        int start = s.indexOf("\"counts\"");
        if (start < 0) return;   // 没有库存字段 = 空库存(与旧版行为一致)
        int bodyStart = s.indexOf('{', start);
        if (bodyStart < 0) return;
        int depth = 0, end = -1;
        for (int i = bodyStart; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') depth++;
            else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (end < 0) return;
        Matcher m = Pattern.compile("\"([0-9a-fA-F]{6})\"\\s*:\\s*(-?\\d+)")
                .matcher(s.substring(bodyStart + 1, end));
        while (m.find()) {
            COUNTS.put((int) Long.parseLong(m.group(1), 16),
                    Integer.parseInt(m.group(2)));
        }
    }

    /** @return 手头数量;-1 = 未登记 */
    public static synchronized int get(Context c, int rgb) {
        load(c);
        Integer v = COUNTS.get(rgb & 0xFFFFFF);
        return v == null ? -1 : v;
    }

    public static synchronized void set(Context c, int rgb, int count) {
        load(c);
        COUNTS.put(rgb & 0xFFFFFF, Math.max(0, count));
        save(c);
    }

    /** 手头有货(数量>0)的全部颜色 RGB(0xFFRRGGBB);HashMap 无序,调用方自行排序 */
    public static synchronized List<Integer> ownedColors(Context c) {
        load(c);
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : COUNTS.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                out.add(0xFF000000 | e.getKey());
            }
        }
        return out;
    }

    /** 登记过的全部颜色 RGB(含数量 0 的);HashMap 无序,调用方自行排序 */
    public static synchronized List<Integer> allColors(Context c) {
        load(c);
        List<Integer> out = new ArrayList<>();
        for (Integer k : COUNTS.keySet()) {
            out.add(0xFF000000 | k);
        }
        return out;
    }

    /** 从豆仓删除一种颜色(下次 get 恢复"未登记"状态) */
    public static synchronized void remove(Context c, int rgb) {
        load(c);
        COUNTS.remove(rgb & 0xFFFFFF);
        save(c);
    }

    /** 全量备份恢复后调用:丢弃内存缓存,下次访问从磁盘重读恢复的数据 */
    public static synchronized void resetForRestore() {
        COUNTS.clear();
        loaded = false;
    }

    private static synchronized void save(Context c) {
        try {
            StringBuilder sb = new StringBuilder("{\"v\":1,\"counts\":{");
            boolean first = true;
            for (Map.Entry<Integer, Integer> e : COUNTS.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(String.format("%06x", e.getKey()))
                        .append("\":").append(e.getValue().intValue());
            }
            sb.append("}}");
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    private BeadInventory() {
    }
}
