package com.pindou.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.util.Jsons;
import com.pindou.app.util.PatternShare;

import org.json.JSONObject;

import java.io.File;

/**
 * 对位投屏(投影对位模式):手机架在拼豆板上方,后置相机取景,
 * 把图纸按四点校准"钉"到取景里的真实拼豆板上,高亮当前要拼的颜色;
 * 拖动四个角手柄对齐真实板子,透明度可调,上一色/下一色切换,
 * 支持整图虚影对照。纯展示叠加,标记仍在编辑器里做。
 * 零新权限(相机运行时权限与 AR 试摆共用),画面仅本地使用。
 */
public class ProjectAlignActivity extends Activity {

    private static final int REQ_CAMERA = 42;

    private TextureView preview;
    private AlignOverlayView overlay;

    private CameraDevice camera;
    private CameraCaptureSession session;
    private CaptureRequest.Builder previewBuilder;
    private HandlerThread camThread;
    private Handler camHandler;
    private int sensorOrientation = 90;
    private Size previewSize;
    private boolean resumed;

    private BeadPattern pattern;
    private int focusPos = 0;   // 当前高亮色在 usedColors 里的位置
    private boolean wholeMode = false;
    private int alphaPct = 62;

    private TextView swatch;
    private TextView colorName;
    private TextView modeChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();

        String path = getIntent().getStringExtra("path");
        focusPos = getIntent().getIntExtra("focusPos", 0);
        if (path != null) {
            try {
                JSONObject o = Jsons.read(new File(path));
                pattern = PatternShare.parse(o);
            } catch (Exception e) {
                pattern = null;
            }
        }
        if (pattern == null || pattern.usedColors.isEmpty()) {
            Toast.makeText(this, getString(R.string.ar_load_failed), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (focusPos < 0 || focusPos >= pattern.usedColors.size()) focusPos = 0;

        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            hookPreview();
        }
        refreshColorUi();
    }

    // ---------------- 程序化 UI(v2.49 教训:不走 XML 膨胀) ----------------

    private void buildUi() {
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        preview = new TextureView(this);
        root.addView(preview, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new AlignOverlayView(this);
        root.addView(overlay, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        int pad = Math.round(12 * getResources().getDisplayMetrics().density);

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

        // 底部控制面板:换色 / 模式 / 透明度 / 重置
        android.widget.LinearLayout panel = new android.widget.LinearLayout(this);
        panel.setOrientation(android.widget.LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xCC2B2436);
        panel.setPadding(pad, pad / 2, pad, pad);

        android.widget.LinearLayout row1 = new android.widget.LinearLayout(this);
        row1.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView prev = chip("◀");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepColor(-1);
            }
        });
        row1.addView(prev, rowChipLp());

        swatch = new TextView(this);
        swatch.setTextSize(18);
        swatch.setGravity(Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams swLp = new android.widget.LinearLayout.LayoutParams(
                Math.round(44 * getResources().getDisplayMetrics().density),
                Math.round(34 * getResources().getDisplayMetrics().density));
        swLp.setMargins(pad / 2, 0, pad / 2, 0);
        row1.addView(swatch, swLp);

        colorName = new TextView(this);
        colorName.setTextColor(0xFFFFFFFF);
        colorName.setTextSize(13);
        colorName.setSingleLine(true);
        android.widget.LinearLayout.LayoutParams nameLp = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row1.addView(colorName, nameLp);

        TextView next = chip("▶");
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepColor(1);
            }
        });
        row1.addView(next, rowChipLp());

        modeChip = chip(getString(R.string.align_whole));
        modeChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wholeMode = !wholeMode;
                modeChip.setText(wholeMode
                        ? getString(R.string.align_single) : getString(R.string.align_whole));
                overlay.invalidate();
            }
        });
        android.widget.LinearLayout.LayoutParams modeLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                Math.round(34 * getResources().getDisplayMetrics().density));
        modeLp.setMargins(pad, 0, 0, 0);
        row1.addView(modeChip, modeLp);
        panel.addView(row1);

        android.widget.LinearLayout row2 = new android.widget.LinearLayout(this);
        row2.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.LinearLayout.LayoutParams row2Lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = pad / 2;

        TextView opLabel = new TextView(this);
        opLabel.setText(getString(R.string.align_opacity));
        opLabel.setTextColor(0xFFC9BFD6);
        opLabel.setTextSize(12);
        opLabel.setPadding(0, 0, pad / 2, 0);
        row2.addView(opLabel);

        SeekBar alpha = new SeekBar(this);
        alpha.setMax(90);
        alpha.setProgress(alphaPct);
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                alphaPct = v;
                overlay.invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row2.addView(alpha, new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView reset = chip(getString(R.string.align_reset));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                overlay.resetQuad();
            }
        });
        android.widget.LinearLayout.LayoutParams resetLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Math.round(34 * getResources().getDisplayMetrics().density));
        resetLp.setMargins(pad, 0, 0, 0);
        row2.addView(reset, resetLp);
        panel.addView(row2, row2Lp);

        android.widget.FrameLayout.LayoutParams panelLp =
                new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM);
        root.addView(panel, panelLp);

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.align_hint));
        hint.setTextColor(0xFFFFFFFF);
        hint.setTextSize(12);
        hint.setBackgroundColor(0x66000000);
        hint.setPadding(pad, pad / 3, pad, pad / 3);
        android.widget.FrameLayout.LayoutParams hintLp =
                new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(hint, hintLp);

        setContentView(root);
        // 提示条挂在面板上方:面板加进 root 后再调 hint 的边距
        android.widget.FrameLayout.LayoutParams lp =
                (android.widget.FrameLayout.LayoutParams) hint.getLayoutParams();
        lp.bottomMargin = Math.round(118 * getResources().getDisplayMetrics().density);
        hint.setLayoutParams(lp);
    }

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF3A3050);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_chip);
        t.setPadding(Math.round(10 * getResources().getDisplayMetrics().density), 0,
                Math.round(10 * getResources().getDisplayMetrics().density), 0);
        t.setClickable(true);
        return t;
    }

    private android.widget.LinearLayout.LayoutParams rowChipLp() {
        return new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                Math.round(34 * getResources().getDisplayMetrics().density));
    }

    private void stepColor(int d) {
        if (pattern == null || pattern.usedColors.isEmpty()) return;
        int n = pattern.usedColors.size();
        focusPos = ((focusPos + d) % n + n) % n;
        refreshColorUi();
        overlay.invalidate();
    }

    private void refreshColorUi() {
        if (pattern == null) return;
        BeadColor c = pattern.usedColors.get(focusPos).color;
        swatch.setBackgroundColor(0xFF000000 | c.rgb);
        colorName.setText(getString(R.string.fmt_align_color,
                focusPos + 1, pattern.usedColors.size(), c.name));
    }

    // ---------------- 叠层视图:四点校准 + 格子透视高亮 ----------------

    private class AlignOverlayView extends View {

        // 校准四角:TL,TR,BR,BL(视图坐标)
        final float[] qx = new float[4];
        final float[] qy = new float[4];
        int drag = -1;
        private final Paint cellP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handleEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tmp = new Path();

        AlignOverlayView(Context ctx) {
            super(ctx);
            gridP.setStrokeWidth(1f);
            handleP.setColor(0xFFFFFFFF);
            handleEdge.setColor(0xFF40354E);
            handleEdge.setStyle(Paint.Style.STROKE);
            handleEdge.setStrokeWidth(3f);
        }

        void resetQuad() {
            layoutQuad(getWidth(), getHeight());
            invalidate();
        }

        private void layoutQuad(int w, int h) {
            if (w <= 0 || h <= 0) return;
            float mw = w * 0.12f, mh = h * 0.18f;
            qx[0] = mw;
            qy[0] = mh;
            qx[1] = w - mw;
            qy[1] = mh;
            qx[2] = w - mw;
            qy[2] = h - mh;
            qx[3] = mw;
            qy[3] = h - mh;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            layoutQuad(w, h);
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

        private void cellPath(Path out, int x, int y, float inset) {
            float u0 = (x + inset) / pattern.cols, u1 = (x + 1 - inset) / pattern.cols;
            float v0 = (y + inset) / pattern.rows, v1 = (y + 1 - inset) / pattern.rows;
            out.reset();
            out.moveTo(bx(u0, v0), by(u0, v0));
            out.lineTo(bx(u1, v0), by(u1, v0));
            out.lineTo(bx(u1, v1), by(u1, v1));
            out.lineTo(bx(u0, v1), by(u0, v1));
            out.close();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (pattern == null || qx[0] == 0) return;
            int a = (alphaPct * 255) / 100;
            // 蒙层:四边形外压暗,聚焦校准区域
            c.drawColor(0x33000000);
            cellP.setStyle(Paint.Style.FILL);
            if (wholeMode) {
                for (int y = 0; y < pattern.rows; y++) {
                    for (int x = 0; x < pattern.cols; x++) {
                        int idx = pattern.cellAt(x, y);
                        if (idx < 0) continue;
                        cellP.setColor(0xFF000000 | pattern.palette.get(idx).rgb);
                        cellP.setAlpha(Math.max(40, a * 55 / 100));
                        cellPath(tmp, x, y, 0.06f);
                        c.drawPath(tmp, cellP);
                    }
                }
            } else {
                int focus = pattern.usedColors.get(focusPos).index;
                int rgb = pattern.usedColors.get(focusPos).color.rgb;
                cellP.setColor(0xFF000000 | rgb);
                cellP.setAlpha(a);
                for (int y = 0; y < pattern.rows; y++) {
                    for (int x = 0; x < pattern.cols; x++) {
                        if (pattern.cellAt(x, y) != focus) continue;
                        cellPath(tmp, x, y, 0.08f);
                        c.drawPath(tmp, cellP);
                    }
                }
            }
            // 网格
            gridP.setColor(0x30FFFFFF);
            for (int x = 0; x <= pattern.cols; x++) {
                float u = x / (float) pattern.cols;
                c.drawLine(bx(u, 0), by(u, 0), bx(u, 1), by(u, 1), gridP);
            }
            for (int y = 0; y <= pattern.rows; y++) {
                float v = y / (float) pattern.rows;
                c.drawLine(bx(0, v), by(0, v), bx(1, v), by(1, v), gridP);
            }
            // 角手柄
            float r = Math.max(20f, getWidth() * 0.035f);
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

    // ---------------- 相机(与 FakeArActivity 同款零依赖 Camera2) ----------------

    private void hookPreview() {
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st,
                                                  int w, int h) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st,
                                                    int w, int h) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) {
                closeCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_CAMERA) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                hookPreview();
            } else {
                Toast.makeText(this, getString(R.string.ar_need_camera),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void openCamera() {
        if (!resumed || camHandler == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String camId = pickBackCamera(cm);
            if (camId == null) {
                fail();
                return;
            }
            CameraCharacteristics chars = cm.getCameraCharacteristics(camId);
            sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
            android.graphics.SurfaceTexture st = preview.getSurfaceTexture();
            if (st == null) return;
            previewSize = pickPreviewSize(chars, preview.getWidth(), preview.getHeight());
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            applyPreviewTransform();
            Surface surface = new Surface(st);
            cm.openCamera(camId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice dev) {
                    camera = dev;
                    try {
                        previewBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewBuilder.addTarget(surface);
                        dev.createCaptureSession(java.util.Collections.singletonList(surface),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(CameraCaptureSession s) {
                                        session = s;
                                        try {
                                            s.setRepeatingRequest(previewBuilder.build(),
                                                    null, camHandler);
                                        } catch (Exception e) {
                                            fail();
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(CameraCaptureSession s) {
                                        fail();
                                    }
                                }, camHandler);
                    } catch (Exception e) {
                        fail();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice dev) {
                    dev.close();
                    if (camera == dev) camera = null;
                }

                @Override
                public void onError(CameraDevice dev, int error) {
                    dev.close();
                    if (camera == dev) camera = null;
                    fail();
                }
            }, camHandler);
        } catch (Exception e) {
            fail();
        }
    }

    private String pickBackCamera(CameraManager cm) {
        String first = null;
        try {
            for (String id : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
                if (first == null) first = id;
            }
        } catch (Exception e) {
            return null;
        }
        return first;
    }

    private Size pickPreviewSize(CameraCharacteristics chars, int vw, int vh) {
        try {
            android.hardware.camera2.params.StreamConfigurationMap map =
                    chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return new Size(640, 480);
            Size[] sizes = map.getOutputSizes(android.graphics.SurfaceTexture.class);
            if (sizes == null || sizes.length == 0) return new Size(640, 480);
            float wantAspect = vh > 0 && vw > 0 ? (float) vw / vh : 9f / 16f;
            Size best = null;
            int bestScore = Integer.MAX_VALUE;
            for (Size s : sizes) {
                float sa = (float) s.getWidth() / s.getHeight();
                if (Math.abs(sa - 1f / wantAspect) > 0.25f) continue;
                int score = Math.abs(s.getWidth() * s.getHeight() - 1280 * 720);
                if (score < bestScore) {
                    bestScore = score;
                    best = s;
                }
            }
            return best != null ? best : sizes[0];
        } catch (Exception e) {
            return new Size(640, 480);
        }
    }

    private void applyPreviewTransform() {
        if (previewSize == null) return;
        int vw = preview.getWidth(), vh = preview.getHeight();
        if (vw == 0 || vh == 0) return;
        boolean swap = sensorOrientation == 90 || sensorOrientation == 270;
        int bw = swap ? previewSize.getHeight() : previewSize.getWidth();
        int bh = swap ? previewSize.getWidth() : previewSize.getHeight();
        float scale = Math.max(vw / (float) bw, vh / (float) bh);
        float cx = vw * 0.5f, cy = vh * 0.5f;
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(scale, scale, cx, cy);
        m.postRotate((360 - sensorOrientation) % 360, cx, cy);
        preview.setTransform(m);
    }

    private void closeCamera() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
            if (camera != null) {
                camera.close();
                camera = null;
            }
            previewBuilder = null;
        } catch (Exception e) {
            // 退出路上关闭失败不影响流程
        }
    }

    private void fail() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ProjectAlignActivity.this,
                        getString(R.string.ar_no_camera), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        camThread = new HandlerThread("align_cam");
        camThread.start();
        camHandler = new Handler(camThread.getLooper());
        if (preview.getSurfaceTexture() != null
                && checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        closeCamera();
        if (camThread != null) {
            camThread.quitSafely();
            camThread = null;
        }
        camHandler = null;
        super.onPause();
    }
}
