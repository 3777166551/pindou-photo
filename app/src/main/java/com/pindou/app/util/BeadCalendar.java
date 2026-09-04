package com.pindou.app.util;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局拼豆打卡日历:按天记录(所有项目合计)完成的颗数,
 * 存在应用私有目录 files/calendar.json,纯本地、无任何网络。
 * 打卡数字用于"📅 打卡日历"月视图,鼓励坚持拼完。
 */
public final class BeadCalendar {

    private static JSONObject data;
    private static boolean loaded;

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "calendar.json");
    }

    private static synchronized void load(Context ctx) {
        if (loaded) return;
        try {
            data = Jsons.read(file(ctx));
        } catch (Exception e) {
            data = new JSONObject();
        }
        loaded = true;
    }

    private static synchronized void save(Context ctx) {
        try {
            Jsons.write(file(ctx), data);
        } catch (Exception ignored) {
        }
    }

    /** 记录今天完成的变化量(标记完成 +1,取消标记 -1,当天最少记 0) */
    public static synchronized void add(Context ctx, int delta) {
        load(ctx);
        String day = today();
        int v = Math.max(0, data.optInt(day, 0) + delta);
        try {
            data.put(day, v);
        } catch (Exception ignored) {
        }
        save(ctx);
    }

    /** 查询某天(yyyy-MM-dd)完成的颗数 */
    public static synchronized int get(Context ctx, String day) {
        load(ctx);
        return data.optInt(day, 0);
    }

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    private BeadCalendar() {
    }
}
