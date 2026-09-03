package com.pindou.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** 项目存档用的小工具:JSON 文件读写、Bitmap 的 Base64(JPEG)编解码 */
public final class Jsons {

    public static JSONObject read(File f) throws Exception {
        byte[] raw = readBytes(f);
        return new JSONObject(new String(raw, "UTF-8"));
    }

    public static void write(File f, JSONObject o) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(o.toString().getBytes("UTF-8"));
        fos.close();
    }

    public static byte[] readBytes(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        fis.close();
        return bos.toByteArray();
    }

    /** 缩到 maxDim 以内再压缩成 Base64 JPEG,用于项目里的原图备份和缩略图 */
    public static String encodeBitmap(Bitmap src, int maxDim, int quality) {
        if (src == null || src.isRecycled()) return "";
        try {
            Bitmap cur = src;
            boolean scaled = false;
            int m = Math.max(cur.getWidth(), cur.getHeight());
            if (m > maxDim) {
                float s = maxDim / (float) m;
                Bitmap nb = Bitmap.createScaledBitmap(src,
                        Math.max(1, Math.round(src.getWidth() * s)),
                        Math.max(1, Math.round(src.getHeight() * s)), true);
                if (nb != src) scaled = true;
                cur = nb;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            cur.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            if (scaled) cur.recycle();
            return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public static Bitmap decodeBitmap(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            byte[] raw = Base64.decode(b64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(raw, 0, raw.length);
        } catch (Exception e) {
            return null;
        }
    }

    private Jsons() {
    }
}
