package com.pindou.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/**
 * 上墙预览(非 AR,零新权限,2026-09 竞品差距清单收尾项):
 * 从相册选一张房间/墙面/桌面照片,把当前图纸的效果图按四点透视
 * (Matrix.setPolyToPoly)贴合上去,拖四个角点对位,看上墙比例与配色。
 * 与相机三页同款恒定深底(设计合同 §10.3),不随主题。
 * 入口 = 编辑页效果图 FAB 菜单「上墙」;效果图经缓存文件传入(同 AR 页)。
 */
public class WallPreviewActivity extends Activity {

    private static final int REQ_PICK_PHOTO = 20;

    private WallView wall;
    private TextView emptyHint;
    private float wallW, wallH;   // 成品物理尺寸(米),尺寸 chip 用

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wallW = getIntent().getFloatExtra("wm", 0f);
        wallH = getIntent().getFloatExtra("hm", 0f);

        String path = getIntent().getStringExtra("path");
        Bitmap effect = null;
        try {
            effect = BitmapFactory.decodeFile(path);
        } catch (Throwable ignored) {
        }
        if (effect == null) {
            Toast.makeText(this, getString(R.string.ar_load_failed),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        float dm = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF141218);   // 相机族页面恒定深底

        wall = new WallView(this, effect);
        root.addView(wall, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        emptyHint = new TextView(this);
        emptyHint.setText(getString(R.string.wall_pick_hint));
        emptyHint.setTextSize(15);
        emptyHint.setTextColor(0xFFE6E1E9);
        emptyHint.setGravity(Gravity.CENTER);
        root.addView(emptyHint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        // 顶栏:成品实际尺寸提示(帮助判断这面墙放不放得下)
        TextView size = barChip(String.format(java.util.Locale.CHINA,
                getString(R.string.wall_size_fmt), wallW * 100f, wallH * 100f));
        size.setOnClickListener(null);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Math.round(10 * dm), Math.round(8 * dm),
                Math.round(10 * dm), Math.round(8 * dm));
        bar.addView(size);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(bar, topLp);

        // 底栏:照片 / 透明度 / 保存 / 完成
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.BOTTOM);

        SeekBar op = new SeekBar(this);
        op.setMax(100);
        op.setProgress(90);
        op.getProgressDrawable().setAlpha(220);
        op.setContentDescription(getString(R.string.wall_opacity));
        op.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                wall.overlayAlpha = Math.max(0.25f, p / 100f);
                wall.invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout.LayoutParams opLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        opLp.setMargins(Math.round(16 * dm), 0, Math.round(16 * dm), 0);
        bottom.addView(op, opLp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(10 * dm), Math.round(4 * dm),
                Math.round(10 * dm), Math.round(10 * dm));
        TextView pick = barChip(getString(R.string.wall_btn_photo));
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickPhoto();
            }
        });
        row.addView(pick);
        TextView spacer = new TextView(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(spacer);
        TextView save = barChip(getString(R.string.wall_btn_save));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSnapshot();
            }
        });
        row.addView(save);
        TextView done = barChip(getString(R.string.wall_btn_done));
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneLp.leftMargin = Math.round(8 * dm);
        row.addView(done, doneLp);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.enter_undim, R.anim.exit_down);
            }
        });
        bottom.addView(row);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bottom, bottomLp);

        setContentView(root);
        pickPhoto();   // 进门直接拉相册(取消也不影响,页面有"照片"键可再来)
    }

    /** 顶栏/底栏 chip(相机族深底白字,与 AR 家族一致) */
    private TextView barChip(String text) {
        float dm = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFFE6E1E9);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Math.round(12 * dm), Math.round(6 * dm),
                Math.round(12 * dm), Math.round(6 * dm));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(20 * dm);
        tv.setBackground(bg);
        return tv;
    }

    private void pickPhoto() {
        android.content.Intent i = new android.content.Intent(
                android.content.Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    android.content.Intent.createChooser(i,
                            getString(R.string.wall_pick_hint)), REQ_PICK_PHOTO);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_photo_read),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_PHOTO || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        try {
            Bitmap photo = decodeSampled(data.getData(), 2048);
            if (photo == null) throw new Exception("decode");
            emptyHint.setVisibility(View.GONE);
            wall.setPhoto(photo);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_photo_read),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** 采样解码到长边 ≤ max(控内存),并按 EXIF 方向摆正 */
    private Bitmap decodeSampled(Uri uri, int max) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, bounds);
        if (in != null) in.close();
        int sample = 1;
        while (bounds.outWidth / (sample + 1) >= max
                || bounds.outHeight / (sample + 1) >= max) {
            sample *= 2;
        }
        in = getContentResolver().openInputStream(uri);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
        if (in != null) in.close();
        if (bmp == null) return null;

        int deg = exifDegrees(uri);
        if (deg != 0) {
            Matrix m = new Matrix();
            m.postRotate(deg);
            Bitmap fixed = Bitmap.createBitmap(bmp, 0, 0,
                    bmp.getWidth(), bmp.getHeight(), m, true);
            if (fixed != bmp) bmp.recycle();
            bmp = fixed;
        }
        return bmp;
    }

    private int exifDegrees(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            android.media.ExifInterface ex = new android.media.ExifInterface(in);
            if (in != null) in.close();
            switch (ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL)) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 保存预览图(不带角点手柄,2x 超采样)到系统相册 */
    private void saveSnapshot() {
        try {
            int w = wall.getWidth(), h = wall.getHeight();
            if (w <= 0 || h <= 0) return;
            float scale = Math.min(2f, 3000f / Math.max(w, h));
            Bitmap out = Bitmap.createBitmap(Math.round(w * scale),
                    Math.round(h * scale), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.scale(scale, scale);
            wall.handlesVisible = false;
            wall.draw(c);
            wall.handlesVisible = true;
            String name = getString(R.string.file_wall_prefix)
                    + new java.text.SimpleDateFormat("yyyyMMdd_HHmm",
                    java.util.Locale.CHINA).format(new java.util.Date()) + ".png";
            Uri uri = com.pindou.app.util.GallerySaver.save(this, out, name);
            out.recycle();
            Toast.makeText(this, getString(R.string.saved_gallery_fmt,
                    com.pindou.app.util.GallerySaver.dirName(this)),
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_prefix_save) + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.enter_undim, R.anim.exit_down);
    }

    // ---------------- 预览画布 ----------------

    /**
     * 照片 centerCrop 铺底,效果图按四点透视贴合(M.setPolyToPoly),
     * 四个角点可拖。交互沿用对位投屏的"就近吸附"手写手势(DEV-NOTES 20)。
     */
    private static final class WallView extends View {

        private final Bitmap effect;
        private Bitmap photo;
        private final float[] quad = new float[8];   // TL,TR,BR,BL(视图坐标)
        private boolean quadReady;
        private float overlayAlpha = 0.9f;
        private boolean handlesVisible = true;
        private int drag = -1;

        private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG
                | Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WallView(android.content.Context context, Bitmap effect) {
            super(context);
            this.effect = effect;
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(2f);
            outlinePaint.setColor(0x8CFFFFFF);
            handlePaint.setColor(0xFFFFFFFF);
            handleEdge.setColor(0xFF2A2735);
            handleEdge.setStyle(Paint.Style.STROKE);
            handleEdge.setStrokeWidth(3f);
            shadePaint.setColor(0x66000000);
        }

        void setPhoto(Bitmap b) {
            if (photo != null && photo != b) photo.recycle();
            photo = b;
            quadReady = false;   // 新照片重新给一个居中的默认贴合框
            invalidate();
        }

        /** 初始贴合框:照片中央 72% 宽、按效果图宽高比、默认带一点透视 */
        private void initQuad() {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float fw = effect.getWidth(), fh = effect.getHeight();
            float qw = w * 0.72f;
            float qh = qw * fh / fw;
            if (qh > h * 0.62f) {
                qh = h * 0.62f;
                qw = qh * fw / fh;
            }
            float cx = w / 2f, cy = h * 0.46f;
            float half = qw / 2f;
            // 左右高度略收,给一个"斜着看墙"的初始透视,提示可拖
            float taper = qh * 0.06f;
            quad[0] = cx - half;
            quad[1] = cy - qh / 2f + taper;
            quad[2] = cx + half;
            quad[3] = cy - qh / 2f;
            quad[4] = cx + half;
            quad[5] = cy + qh / 2f;
            quad[6] = cx - half;
            quad[7] = cy + qh / 2f - taper * 0.4f;
            quadReady = true;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xFF141218);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (photo != null && !photo.isRecycled()) {
                // centerCrop 铺满
                float s = Math.max(w / (float) photo.getWidth(),
                        h / (float) photo.getHeight());
                float dw = photo.getWidth() * s, dh = photo.getHeight() * s;
                canvas.drawBitmap(photo, (w - dw) / 2f, (h - dh) / 2f, bitmapPaint);
            }
            if (!quadReady) initQuad();
            if (photo == null) return;   // 没照片不贴图(框也不画,等选照片)

            // 效果图四点透视贴合
            float fw = effect.getWidth(), fh = effect.getHeight();
            Matrix m = new Matrix();
            m.setPolyToPoly(new float[]{0, 0, fw, 0, fw, fh, 0, fh}, 0,
                    quad, 0, 4);
            bitmapPaint.setAlpha((int) (255 * overlayAlpha));
            canvas.drawBitmap(effect, m, bitmapPaint);
            bitmapPaint.setAlpha(255);

            // 贴合区外轻微压暗,视线聚焦
            Path outside = new Path();
            outside.addRect(0, 0, w, h, Path.Direction.CW);
            Path q = quadPath();
            outside.op(q, Path.Op.DIFFERENCE);
            canvas.drawPath(outside, shadePaint);

            if (handlesVisible) {
                float r = Math.max(18f, w * 0.03f);
                for (int i = 0; i < 4; i++) {
                    canvas.drawCircle(quad[i * 2], quad[i * 2 + 1], r, handlePaint);
                    canvas.drawCircle(quad[i * 2], quad[i * 2 + 1], r, handleEdge);
                }
                canvas.drawPath(q, outlinePaint);
            }
        }

        private Path quadPath() {
            Path p = new Path();
            p.moveTo(quad[0], quad[1]);
            p.lineTo(quad[2], quad[3]);
            p.lineTo(quad[4], quad[5]);
            p.lineTo(quad[6], quad[7]);
            p.close();
            return p;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float hit = Math.max(64f, getWidth() * 0.09f);
                    drag = -1;
                    float best = hit * hit;
                    for (int i = 0; i < 4; i++) {
                        float dx = x - quad[i * 2], dy = y - quad[i * 2 + 1];
                        float d = dx * dx + dy * dy;
                        if (d < best) {
                            best = d;
                            drag = i;
                        }
                    }
                    return drag >= 0;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (drag < 0) return false;
                    quad[drag * 2] = Math.max(4, Math.min(getWidth() - 4, x));
                    quad[drag * 2 + 1] = Math.max(4, Math.min(getHeight() - 4, y));
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    drag = -1;
                    return true;
            }
            return false;
        }
    }
}
