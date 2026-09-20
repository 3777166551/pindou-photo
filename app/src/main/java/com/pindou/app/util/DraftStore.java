package com.pindou.app.util;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 自动草稿的文件仓(v2.60):编辑器每步修改防抖落盘的单一草稿槽。
 * 只管文件读写;调度(防抖/切后台兜底)在 EditorActivity。
 * 单槽覆盖写(约 100~300KB),不进「我的项目」列表。
 */
public final class DraftStore {

    private static final String FILE_NAME = "autosave_draft.json";

    private DraftStore() {
    }

    public static File file(Context c) {
        return new File(c.getFilesDir(), FILE_NAME);
    }

    public static boolean exists(Context c) {
        File f = file(c);
        return f.exists() && f.length() > 0;
    }

    public static void save(Context c, JSONObject o) throws Exception {
        Jsons.write(file(c), o);
    }

    /** 读出草稿 JSON 原文;读不到返回 null */
    public static String read(Context c) {
        try {
            File f = file(c);
            byte[] raw = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int read = in.read(raw);
            in.close();
            return read > 0 ? new String(raw, "UTF-8") : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void delete(Context c) {
        File f = file(c);
        if (f.exists()) f.delete();
    }
}
