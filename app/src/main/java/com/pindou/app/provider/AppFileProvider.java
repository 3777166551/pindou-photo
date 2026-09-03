package com.pindou.app.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * 极简 ContentProvider,只做两件事:
 *  1. 给相机应用一个可写的临时文件(cache/camera_*.jpg)
 *  2. 给分享意图一个可读的已保存图片(pictures/xxx.png,旧系统用)
 */
public class AppFileProvider extends ContentProvider {

    public static final String AUTHORITY = "com.pindou.app.files";

    public static Uri forCameraFile(File f) {
        return forCacheShare(f);
    }

    /** 分享 cache 目录里的临时文件(图片/PDF 均可) */
    public static Uri forCacheShare(File f) {
        return Uri.parse("content://" + AUTHORITY + "/cache/" + Uri.encode(f.getName()));
    }

    public static Uri forSavedFile(File f) {
        return Uri.parse("content://" + AUTHORITY + "/pictures/" + Uri.encode(f.getName()));
    }

    /** 按扩展名给出 MIME 类型(分享意图用) */
    public static String mimeFor(String name) {
        if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return "application/pdf";
        }
        return "image/png";
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private File resolve(Uri uri) {
        List<String> seg = uri.getPathSegments();
        if (seg == null || seg.size() != 2) return null;
        String root = seg.get(0);
        String name = seg.get(1);
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        File dir;
        if ("cache".equals(root)) {
            dir = ctx.getCacheDir();
        } else if ("pictures".equals(root)) {
            dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "拼豆图纸");
        } else {
            return null;
        }
        try {
            File f = new File(dir, name);
            if (!f.getCanonicalPath().startsWith(dir.getCanonicalPath())) return null;
            return f;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        if (f == null) throw new FileNotFoundException(uri.toString());
        int m;
        if (mode != null && mode.contains("w")) {
            m = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE;
            if (mode.contains("t")) m |= ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            m = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(f, m);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File f = resolve(uri);
        if (f == null) return null;
        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        MatrixCursor cursor = new MatrixCursor(projection);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
                row[i] = f.getName();
            } else if (OpenableColumns.SIZE.equals(projection[i])) {
                row[i] = f.length();
            } else {
                row[i] = null;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        List<String> seg = uri.getPathSegments();
        if (seg != null && seg.size() == 2) {
            return mimeFor(seg.get(1));
        }
        return "image/png";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
