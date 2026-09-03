package com.pindou.app.util;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.InputStream;

/** 读取相册/相机照片:采样解码(省内存)+ EXIF 方向修正 + 上限缩放 */
public final class ImageLoader {

    public static Bitmap load(ContentResolver cr, Uri uri, int maxDim) throws Exception {
        if (uri == null) throw new Exception("没有选择照片");

        Bitmap bmp;
        int orientation;
        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            o.inSampleSize = sampleSize(o, maxDim);
            o.inJustDecodeBounds = false;
            bmp = BitmapFactory.decodeFile(path, o);
            orientation = exifOrientationFromFile(path);
        } else {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream is = cr.openInputStream(uri);
            BitmapFactory.decodeStream(is, null, o);
            if (is != null) is.close();
            o.inSampleSize = sampleSize(o, maxDim);
            o.inJustDecodeBounds = false;
            InputStream is2 = cr.openInputStream(uri);
            bmp = BitmapFactory.decodeStream(is2, null, o);
            if (is2 != null) is2.close();
            orientation = exifOrientationFromStream(cr, uri);
        }
        if (bmp == null) throw new Exception("这张图片无法读取");

        bmp = rotate(bmp, orientation);

        int m = Math.max(bmp.getWidth(), bmp.getHeight());
        if (m > maxDim) {
            float s = maxDim / (float) m;
            Bitmap nb = Bitmap.createScaledBitmap(bmp,
                    Math.round(bmp.getWidth() * s),
                    Math.round(bmp.getHeight() * s), true);
            if (nb != bmp) bmp.recycle();
            bmp = nb;
        }
        return bmp;
    }

    private static int sampleSize(BitmapFactory.Options o, int maxDim) {
        int w = o.outWidth, h = o.outHeight;
        int s = 1;
        while (Math.max(w, h) / s > maxDim * 2) s *= 2;
        return Math.max(1, s);
    }

    private static int exifOrientationFromFile(String path) {
        try {
            ExifInterface e = new ExifInterface(path);
            return e.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static int exifOrientationFromStream(ContentResolver cr, Uri uri) {
        InputStream is = null;
        try {
            is = cr.openInputStream(uri);
            if (is == null) return ExifInterface.ORIENTATION_NORMAL;
            ExifInterface e = new ExifInterface(is);
            return e.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static Bitmap rotate(Bitmap bmp, int orientation) {
        int deg;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                deg = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                deg = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                deg = 270;
                break;
            default:
                return bmp;
        }
        Matrix m = new Matrix();
        m.postRotate(deg);
        Bitmap nb = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        if (nb != bmp) bmp.recycle();
        return nb;
    }

    private ImageLoader() {
    }
}
