package com.pindou.app.bead;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 豆仓库存:记录用户手头各种颜色豆子的数量。
 * 以颜色 RGB 为键(跨色板通用——同一个 RGB 在哪个色板里都是同一种豆),
 * 持久化到 files/inventory.json。
 * get 返回 -1 表示"从未登记过",0 表示"登记过但已用完",以此区分两态。
 */
public final class BeadInventory {

    private static final Map<Integer, Integer> COUNTS = new HashMap<>();
    private static boolean loaded = false;

    private static File file(Context c) {
        return new File(c.getFilesDir(), "inventory.json");
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
            JSONObject o = new JSONObject(
                    new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8));
            JSONObject d = o.optJSONObject("counts");
            if (d != null) {
                Iterator<String> it = d.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    COUNTS.put((int) Long.parseLong(k, 16), d.optInt(k, 0));
                }
            }
        } catch (Throwable ignored) {
            // 损坏就当空库存,用户重新登记即可
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

    private static synchronized void save(Context c) {
        try {
            JSONObject d = new JSONObject();
            for (Map.Entry<Integer, Integer> e : COUNTS.entrySet()) {
                d.put(String.format("%06x", e.getKey()), e.getValue().intValue());
            }
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("counts", d);
            FileOutputStream out = new FileOutputStream(file(c));
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    private BeadInventory() {
    }
}
