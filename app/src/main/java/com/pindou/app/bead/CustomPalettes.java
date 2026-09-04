package com.pindou.app.bead;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义色板仓库:多套用户色板("我的豆板"只是其中自动生成的一套),
 * 持久化到 files/custom_palettes.json,启动时 load 注册进 BeadBrandCharts。
 * 每次变更 revision 自增,EditorActivity 靠它判断下拉框要不要刷新。
 * 文件里的 colors 数组结构与开放图纸格式(docs/SHARE-FORMAT.md)一致。
 */
public final class CustomPalettes {

    private static final java.util.concurrent.atomic.AtomicLong REV =
            new java.util.concurrent.atomic.AtomicLong();

    /** "我的豆板"(豆仓自动生成)所在槽位,-1 = 还没生成过 */
    private static int autoIdx = -1;
    private static boolean loaded = false;

    /** 变更序号:UI 对比它决定是否刷新色板下拉框 */
    public static long revision() {
        return REV.get();
    }

    private static void bump() {
        REV.incrementAndGet();
        BeadPalettes.resetCache();
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "custom_palettes.json");
    }

    /** 启动时调用:载入已存的色板并注册;文件缺失/损坏当空(不删除,避免覆盖) */
    public static synchronized void load(Context c) {
        loaded = true;
        List<BeadBrandCharts.Chart> list = new ArrayList<>();
        autoIdx = -1;
        try {
            File f = file(c);
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                byte[] buf = new byte[(int) f.length()];
                int n = in.read(buf);
                in.close();
                JSONObject o = new JSONObject(
                        new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8));
                autoIdx = o.optInt("autoIdx", -1);
                JSONArray arr = o.optJSONArray("palettes");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject p = arr.optJSONObject(i);
                        if (p == null) continue;
                        List<BeadColor> colors = new ArrayList<>();
                        JSONArray cs = p.optJSONArray("colors");
                        if (cs != null) {
                            for (int j = 0; j < cs.length(); j++) {
                                JSONObject cc = cs.optJSONObject(j);
                                if (cc == null) continue;
                                colors.add(new BeadColor(Math.max(1, cc.optInt("code", j + 1)),
                                        cc.optString("name",
                                                c.getString(com.pindou.app.R.string.fmt_color_n, j + 1)),
                                        cc.optInt("rgb", 0) & 0xFFFFFF,
                                        cc.optString("tag", "")));
                            }
                        }
                        if (!colors.isEmpty()) {
                            list.add(BeadBrandCharts.make(p.optString("name",
                                    c.getString(com.pindou.app.R.string.default_palette_name)), colors));
                        } else if (i < autoIdx) {
                            autoIdx--;   // 前面的空套被丢弃,槽位前移
                        } else if (i == autoIdx) {
                            autoIdx = -1;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 损坏就当空仓库;用户重建即可,不给已损坏文件留 写坏->更坏 的机会
        }
        BeadBrandCharts.setCustoms(list);
        bump();
    }

    /** 新建一套色板,返回槽位下标 */
    public static synchronized int add(Context c, String name, List<BeadColor> colors) {
        loadIfNeeded(c);
        int idx = BeadBrandCharts.upsertCustom(-1, BeadBrandCharts.make(name, colors));
        save(c);
        bump();
        return idx;
    }

    /** 更新某一槽位(改名/改色),越界时自动追加 */
    public static synchronized void update(Context c, int idx,
                                           String name, List<BeadColor> colors) {
        loadIfNeeded(c);
        int at = BeadBrandCharts.upsertCustom(idx, BeadBrandCharts.make(name, colors));
        if (autoIdx == idx && at != idx) autoIdx = at;
        save(c);
        bump();
    }

    /** 删除某一槽位;"我的豆板"标记随之失效 */
    public static synchronized void remove(Context c, int idx) {
        loadIfNeeded(c);
        BeadBrandCharts.removeCustom(idx);
        if (idx == autoIdx) {
            autoIdx = -1;
        } else if (idx >= 0 && idx < autoIdx) {
            autoIdx--;
        }
        save(c);
        bump();
    }

    /**
     * 从豆仓库存重建"我的豆板":已有自动槽位就原位替换,否则追加新槽位。
     * 豆仓没有有货颜色时返回 -1(不动现有数据)。
     */
    public static synchronized int regenerateInventory(Context c) {
        loadIfNeeded(c);
        List<BeadColor> mine = BeadPalettes.buildInventoryPalette(c);
        if (mine == null) return -1;
        String name = c.getString(com.pindou.app.R.string.palette_inventory_name_fmt,
                mine.size());
        int idx = (autoIdx >= 0 && autoIdx < BeadBrandCharts.customCount())
                ? autoIdx : -1;
        autoIdx = BeadBrandCharts.upsertCustom(idx, BeadBrandCharts.make(name, mine));
        save(c);
        bump();
        return autoIdx;
    }

    /** 当前是否已存在"我的豆板"自动槽位 */
    public static synchronized boolean hasInventoryPalette() {
        return autoIdx >= 0 && autoIdx < BeadBrandCharts.customCount();
    }

    /** "我的豆板"的槽位下标,没有返回 -1 */
    public static synchronized int inventoryIndex() {
        return hasInventoryPalette() ? autoIdx : -1;
    }

    /** 未载入时才读盘;已载入则忽略(运行时以 BeadBrandCharts 为准) */
    public static synchronized void loadIfNeeded(Context c) {
        if (!loaded) load(c);
    }

    private static synchronized void save(Context c) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < BeadBrandCharts.customCount(); i++) {
                BeadBrandCharts.Chart ch = BeadBrandCharts.customAt(i);
                JSONObject p = new JSONObject();
                p.put("name", ch.name);
                JSONArray cs = new JSONArray();
                for (BeadColor col : ch.colors) {
                    JSONObject cc = new JSONObject();
                    cc.put("code", col.code);
                    cc.put("name", col.name);
                    cc.put("rgb", col.rgb);
                    if (!col.tag.isEmpty()) cc.put("tag", col.tag);
                    cs.put(cc);
                }
                p.put("colors", cs);
                arr.put(p);
            }
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("autoIdx", autoIdx);
            o.put("palettes", arr);
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Throwable ignored) {
            // 存储失败不中断 UI;下次变更会再试
        }
    }

    private CustomPalettes() {
    }
}
