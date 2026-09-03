package com.pindou.app.util;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.pindou.app.provider.AppFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** 把渲染好的图片存进系统相册 Pictures/拼豆图纸,并返回可用于分享的 content Uri */
public final class GallerySaver {

    public static final String DIR_NAME = "拼豆图纸";

    public static Uri save(Context ctx, android.graphics.Bitmap bmp, String fileName)
            throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + DIR_NAME);
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = ctx.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("相册写入失败");
            OutputStream os = ctx.getContentResolver().openOutputStream(uri);
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os);
            if (os != null) os.close();
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, done, null, null);
            return uri;
        } else {
            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    DIR_NAME);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("创建目录失败");
            File f = new File(dir, fileName);
            FileOutputStream fos = new FileOutputStream(f);
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            // 让相册立刻可见
            Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f));
            ctx.sendBroadcast(scan);
            return AppFileProvider.forSavedFile(f);
        }
    }

    private GallerySaver() {
    }
}
