package com.pindou.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.provider.AppFileProvider;
import com.pindou.app.util.ImageLoader;
import com.pindou.app.util.Jsons;
import com.pindou.app.util.PatternShare;
import com.pindou.app.util.VerifyEngine;

import org.json.JSONObject;

import java.io.File;

/**
 * 拍照验收:拼到一半或拼完后,给真实拼豆板拍一张俯拍照,
 * 拖四个角点对齐板子四角,APP 按图纸逐格透视采样读色
 * (CIEDE2000 就近匹配),高亮摆错/漏摆/多余的格子——
 * 错一格要到熨烫后才发现的痛点,在熨烫前拦住。
 *
 * 入口:①拼豆辅助工具行「📸 验收」(当前图纸直接进);
 * ②首页「我的项目」每行的 📸 (打开存档生成图纸后自动进)。
 * 比对核心在 VerifyEngine(纯 Java,JVM 量化单测),零新权限。
 */
public class VerifyActivity extends Activity {

    private static final int REQ_TAKE = 51;
    private static final int REQ_PICK = 52;
    private static final int MAX_DIM = 1600;

    private BeadPattern pattern;
    private Bitmap photo;
    private ImageView iv;
    private VerifyOverlay overlay;
    private TextView btnGo, summary, onlyWrongChip;
    private View panelRow2;
    private boolean verified;
    private boolean onlyWrong = false;
    private VerifyEngine.VerifyResult result;
    private Thread worker;
    private File cameraFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();

        String path = getIntent().getStringExtra("path");
        if (path != null) {
            try {
                pattern = PatternShare.parse(Jsons.read(new File(path)));
            } catch (Exception e) {
                pattern = null;
            }
        }
        if (pattern == null || pattern.usedColors.isEmpty()) {
            Toast.makeText(this, getString(R.string.ar_load_failed), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // 冒烟/调试钩子:直接给一张已拍好的照片文件,跳过相机/相册
        String img = getIntent().getStringExtra("image");
        if (img != null && !img.isEmpty()) {
            try {
                setPhoto(ImageLoader.load(getContentResolver(),
                        android.net.Uri.fromFile(new File(img)), MAX_DIM));
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.err_camera), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------------- 程序化 UI(与对位投屏同款思路,不走 XML) ----------------

    private void buildUi() {
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);

        iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(iv, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new VerifyOverlay(this);
        root.addView(overlay, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(30);
        back.setPadding(pad, pad / 2, pad, pad / 2);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(back, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP));

        TextView title = new TextView(this);
        title.setText(getString(R.string.verify_title));
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setPadding(pad, pad, pad, pad / 2);
        root.addView(title, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        // 底部面板:提示 / 拍照·相册·比对 / 结果与重拍
        android.widget.LinearLayout panel = new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xCC2B2436);
        panel.setPadding(pad, pad / 2, pad, pad);

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.verify_hint));
        hint.setTextColor(0xFFC9BFD6);
        hint.setTextSize(12);
        panel.addView(hint);

        android.widget.LinearLayout row1 = new android.widget.LinearLayout(this);
        row1.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.LinearLayout.LayoutParams row1Lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        row1Lp.topMargin = pad / 2;

        TextView take = chip(getString(R.string.verify_take));
        take.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
        row1.addView(take, new android.widget.LinearLayout.LayoutParams(
                0, Math.round(36 * getResources().getDisplayMetrics().density), 1f));

        TextView pick = chip(getString(R.string.verify_pick));
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickGallery();
            }
        });
        android.widget.LinearLayout.LayoutParams pickLp =
                new android.widget.LinearLayout.LayoutParams(
                        0, Math.round(36 * getResources().getDisplayMetrics().density), 1f);
        pickLp.setMargins(pad / 2, 0, 0, 0);
        row1.addView(pick, pickLp);

        btnGo = chip(getString(R.string.verify_start));
        btnGo.setSelected(true);
        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCompare();
            }
        });
        android.widget.LinearLayout.LayoutParams goLp =
                new android.widget.LinearLayout.LayoutParams(
                        0, Math.round(36 * getResources().getDisplayMetrics().density), 1f);
        goLp.setMargins(pad / 2, 0, 0, 0);
        row1.addView(btnGo, goLp);
        panel.addView(row1, row1Lp);

        android.widget.LinearLayout row2 = new android.widget.LinearLayout(this);
        row2.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setVisibility(View.GONE);
        android.widget.LinearLayout.LayoutParams row2Lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = pad / 2;

        summary = new TextView(this);
        summary.setTextColor(0xFFFFFFFF);
        summary.setTextSize(13);
        row2.addView(summary, new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        onlyWrongChip = chip(getString(R.string.verify_only_wrong));
        onlyWrongChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onlyWrong = !onlyWrong;
                onlyWrongChip.setSelected(onlyWrong);
                overlay.invalidate();
            }
        });
        row2.addView(onlyWrongChip, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Math.round(34 * getResources().getDisplayMetrics().density)));

        TextView retake = chip(getString(R.string.verify_recheck));
        retake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setPhoto(photo);   // 清结果、复位四角,重拖重比
            }
        });
        android.widget.LinearLayout.LayoutParams retakeLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        Math.round(34 * getResources().getDisplayMetrics().density));
        retakeLp.setMargins(pad / 2, 0, 0, 0);
        row2.addView(retake, retakeLp);
        panel.addView(row2, row2Lp);
        panelRow2 = row2;

        root.addView(panel, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        setContentView(root);
    }

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF23212B);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_chip);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    // ---------------- 取照片(系统相机意图 + 相册,零新权限) ----------------

    private void takePhoto() {
        cameraFile = new File(getCacheDir(), "verify_" + System.currentTimeMillis() + ".jpg");
        android.net.Uri uri = AppFileProvider.forCameraFile(cameraFile);
        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TAKE);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.err_no_camera), Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            // 无相机设备直接抛 SecurityException(DEV-NOTES 24)
            Toast.makeText(this, getString(R.string.err_no_camera), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickGallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(
                    Intent.createChooser(i, getString(R.string.verify_pick)), REQ_PICK);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.err_no_gallery), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        try {
            if (requestCode == REQ_TAKE) {
                if (cameraFile != null && cameraFile.exists() && cameraFile.length() > 0) {
                    setPhoto(ImageLoader.load(getContentResolver(),
                            android.net.Uri.fromFile(cameraFile), MAX_DIM));
                } else {
                    Toast.makeText(this, getString(R.string.err_camera), Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQ_PICK && data != null && data.getData() != null) {
                setPhoto(ImageLoader.load(getContentResolver(), data.getData(), MAX_DIM));
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.verify_bad_photo), Toast.LENGTH_SHORT).show();
        }
    }

    private void setPhoto(Bitmap b) {
        if (b == null) return;
        if (photo != null && photo != b) photo.recycle();
        photo = b;
        verified = false;
        result = null;
        iv.setImageBitmap(photo);
        panelRow2.setVisibility(View.GONE);
        iv.post(new Runnable() {
            @Override
            public void run() {
                overlay.resetQuad();
            }
        });
    }

    // ---------------- 比对:四点透视采样 + CIEDE2000 就近匹配 ----------------

    private void startCompare() {
        if (photo == null) {
            Toast.makeText(this, getString(R.string.verify_need_photo), Toast.LENGTH_SHORT).show();
            return;
        }
        if (worker != null && worker.isAlive()) return;
        final float[] viewQuad = overlay.quadSnapshot();
        btnGo.setEnabled(false);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final VerifyEngine.VerifyResult r;
                try {
                    r = compare(viewQuad);
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnGo.setEnabled(true);
                            Toast.makeText(VerifyActivity.this,
                                    getString(R.string.verify_bad_photo), Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnGo.setEnabled(true);
                        verified = true;
                        result = r;
                        if (r.total > 0 && r.wrong + r.missing + r.extra == 0) {
                            summary.setText(getString(R.string.verify_good));
                        } else {
                            summary.setText(String.format(
                                    java.util.Locale.CHINA,
                                    getString(R.string.fmt_verify_result),
                                    r.total, r.wrong, r.missing, r.extra));
                        }
                        panelRow2.setVisibility(View.VISIBLE);
                        overlay.invalidate();
                    }
                });
            }
        });
        worker.start();
    }

    /** 比对:视图四角 → 照片像素四角,核心计算在 VerifyEngine(纯 Java) */
    private VerifyEngine.VerifyResult compare(float[] viewQuad) {
        // 视图坐标 -> 位图像素坐标(ImageView fitCenter 的逆矩阵)
        Matrix inv = new Matrix();
        iv.getImageMatrix().invert(inv);
        float[] dst = new float[8];
        System.arraycopy(viewQuad, 0, dst, 0, 8);
        inv.mapPoints(dst);
        int[] px = new int[photo.getWidth() * photo.getHeight()];
        photo.getPixels(px, 0, photo.getWidth(), 0, 0, photo.getWidth(), photo.getHeight());
        return VerifyEngine.compare(px, photo.getWidth(), photo.getHeight(), pattern, dst);
    }

    // ---------------- 叠层:四角手柄 + 网格 + 结果框 ----------------

    private class VerifyOverlay extends View {

        // 校准四角:TL,TR,BR,BL(视图坐标)
        final float[] qx = new float[4];
        final float[] qy = new float[4];
        int drag = -1;
        private final Paint frameP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scrimP = new Paint();
        private final Path tmp = new Path();

        VerifyOverlay(android.content.Context ctx) {
            super(ctx);
            frameP.setStyle(Paint.Style.STROKE);
            gridP.setColor(0x30FFFFFF);
            gridP.setStrokeWidth(1f);
            handleP.setColor(0xFFFFFFFF);
            handleEdge.setColor(0xFF2A2735);
            handleEdge.setStyle(Paint.Style.STROKE);
            handleEdge.setStrokeWidth(3f);
        }

        void resetQuad() {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0 || photo == null) return;
            // 默认四角 = 照片显示区(fitCenter 后的实际矩形)略内缩,
            // 而不是全屏视图 —— 否则照片有黑边时默认框落在照片外
            float[] pts = {0, 0, photo.getWidth(), photo.getHeight()};
            iv.getImageMatrix().mapPoints(pts);
            float inx = (pts[2] - pts[0]) * 0.03f;
            float iny = (pts[3] - pts[1]) * 0.04f;
            qx[0] = pts[0] + inx;
            qy[0] = pts[1] + iny;
            qx[1] = pts[2] - inx;
            qy[1] = pts[1] + iny;
            qx[2] = pts[2] - inx;
            qy[2] = pts[3] - iny;
            qx[3] = pts[0] + inx;
            qy[3] = pts[3] - iny;
            invalidate();
        }

        float[] quadSnapshot() {
            return new float[]{qx[0], qy[0], qx[1], qy[1], qx[2], qy[2], qx[3], qy[3]};
        }

        /** 双线性插值:格内相对坐标 (u,v) -> 视图坐标 */
        private float bx(float u, float v) {
            return (1 - u) * (1 - v) * qx[0] + u * (1 - v) * qx[1]
                    + u * v * qx[2] + (1 - u) * v * qx[3];
        }

        private float by(float u, float v) {
            return (1 - u) * (1 - v) * qy[0] + u * (1 - v) * qy[1]
                    + u * v * qy[2] + (1 - u) * v * qy[3];
        }

        private void cellFrame(Canvas c, int x, int y, int color) {
            float u0 = (x + 0.12f) / pattern.cols, u1 = (x + 0.88f) / pattern.cols;
            float v0 = (y + 0.12f) / pattern.rows, v1 = (y + 0.88f) / pattern.rows;
            tmp.reset();
            tmp.moveTo(bx(u0, v0), by(u0, v0));
            tmp.lineTo(bx(u1, v0), by(u1, v0));
            tmp.lineTo(bx(u1, v1), by(u1, v1));
            tmp.lineTo(bx(u0, v1), by(u0, v1));
            tmp.close();
            frameP.setColor(color);
            frameP.setStrokeWidth(Math.max(3f, getWidth() * 0.006f));
            c.drawPath(tmp, frameP);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (photo == null || qx[0] == 0) return;
            if (!verified) {
                // 校准模式:四边形外压暗 + 网格预览,帮用户对齐
                scrimP.setColor(0x55000000);
                c.drawColor(0x22000000);
                for (int x = 0; x <= pattern.cols; x++) {
                    float u = x / (float) pattern.cols;
                    c.drawLine(bx(u, 0), by(u, 0), bx(u, 1), by(u, 1), gridP);
                }
                for (int y = 0; y <= pattern.rows; y++) {
                    float v = y / (float) pattern.rows;
                    c.drawLine(bx(0, v), by(0, v), bx(1, v), by(1, v), gridP);
                }
            } else if (result != null) {
                boolean showAll = !onlyWrong;
                for (int y = 0; y < pattern.rows; y++) {
                    for (int x = 0; x < pattern.cols; x++) {
                        int s = result.state[y * pattern.cols + x];
                        if (s == 2) cellFrame(c, x, y, 0xFFFF3B30);       // 错色·红
                        else if (s == 3 && showAll) cellFrame(c, x, y, 0xFFFFCF56); // 漏摆·黄油
                        else if (s == 4 && showAll) cellFrame(c, x, y, 0xFFB388FF); // 多余·紫
                    }
                }
            }
            // 角手柄(校准与结果态都要能重调)
            float r = Math.max(18f, getWidth() * 0.03f);
            for (int i = 0; i < 4; i++) {
                c.drawCircle(qx[i], qy[i], r, handleP);
                c.drawCircle(qx[i], qy[i], r, handleEdge);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float hit = Math.max(60f, getWidth() * 0.09f);
                    drag = -1;
                    float best = hit * hit;
                    for (int i = 0; i < 4; i++) {
                        float dx = x - qx[i], dy = y - qy[i];
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
                    qx[drag] = Math.max(0, Math.min(getWidth() - 2, x));
                    qy[drag] = Math.max(0, Math.min(getHeight() - 2, y));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (worker != null) worker.interrupt();
        if (photo != null) {
            photo.recycle();
            photo = null;
        }
    }
}
