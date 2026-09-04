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

/** 把渲染好的图片存进系统相册 Pictures/(本地化子目录),并返回可用于分享的 content Uri */
public final class GallerySaver {

    public static String dirName(Context ctx) {
        return ctx.getString(com.pindou.app.R.string.gallery_dir);
    }

    public static Uri save(Context ctx, android.graphics.Bitmap bmp, String fileName)
            throws Exception {
        String dir = dirName(ctx);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + dir);
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = ctx.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception(ctx.getString(
                    com.pindou.app.R.string.err_gallery_write));
            OutputStream os = ctx.getContentResolver().openOutputStream(uri);
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os);
            if (os != null) os.close();
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, done, null, null);
            return uri;
        } else {
            File d = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    dir);
            if (!d.exists() && !d.mkdirs()) throw new Exception(ctx.getString(
                    com.pindou.app.R.string.err_mkdir));
            File f = new File(d, fileName);
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
