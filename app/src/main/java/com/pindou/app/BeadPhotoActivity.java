package com.pindou.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadPalettes;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.PatternEngine;
import com.pindou.app.provider.AppFileProvider;
import com.pindou.app.util.ImageLoader;
import com.pindou.app.util.L10n;
import com.pindou.app.util.PatternShare;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

/**
 * 成品照片转图纸(v2.64):拍/选拼好的成品照片 → 检测豆子晶格
 * (自相关豆距 + 相位)→ 手势微调网格 → 逐豆采样(环带中位数,剔除
 * 中心孔与高光)→ 就近配豆 → 走分享格式进编辑器(色号/豆单/PDF 全复用)。
 * 纯本地:无网络、无新权限(相机沿用主入口的声明)。
 */
public class BeadPhotoActivity extends Activity {

    private static final int REQ_PICK = 21;
    private static final int REQ_TAKE = 22;
    private static final int REQ_CAMERA_PERM = 23;

    private LatticeView lattice;
    private TextView tvPitch;
    private TextView btnGen;
    private Spinner palSpinner;
    private Bitmap bmp;
    private File cameraFile;
    private volatile boolean working;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.pindou.app.util.Insets.padRoot(this);
        L10n.apply(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFEF7FF);
        int pad = dp(16);
        root.setPadding(pad, dp(8), pad, pad);
        setContentView(root);

        // 标题行:返回 + 标题
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this);
        back.setText("←");
        back.setTextSize(22);
        back.setTextColor(0xFF1D1B20);
        back.setPadding(dp(4), dp(8), dp(16), dp(8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        titleRow.addView(back);
        TextView title = new TextView(this);
        title.setText(getString(R.string.tool_bead_photo));
        title.setTextSize(19);
        title.setTypeface(typefaceBold());
        title.setTextColor(0xFF1D1B20);
        titleRow.addView(title);
        root.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 预览区:网格覆盖层(权重占满剩余空间)
        lattice = new LatticeView(this);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0xFFECE6F0);
        frame.addView(lattice, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.bp_hint));
        hint.setTextSize(12);
        hint.setTextColor(0xFF49454F);
        hint.setBackgroundColor(0xB3FFFFFF);
        hint.setPadding(dp(8), dp(4), dp(8), dp(4));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
                | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = dp(10);
        frame.addView(hint, hlp);
        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 操作行:选照片 / 拍一张
        root.addView(buildButtonRow(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 豆距行:自动对格 + [-] 豆距 [+]
        LinearLayout pitchRow = new LinearLayout(this);
        pitchRow.setOrientation(LinearLayout.HORIZONTAL);
        pitchRow.setGravity(Gravity.CENTER_VERTICAL);
        pitchRow.setPadding(0, dp(10), 0, 0);
        pitchRow.addView(stepperButton(getString(R.string.bp_auto),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        autoDetect();
                    }
                }), rowWeight(1f));
        pitchRow.addView(stepperButton("−", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scalePitch(1f / 1.06f);
            }
        }), fixedWidth(52));
        tvPitch = new TextView(this);
        tvPitch.setText(getString(R.string.bp_pitch));
        tvPitch.setTextSize(13);
        tvPitch.setTextColor(0xFF1D1B20);
        tvPitch.setGravity(Gravity.CENTER);
        pitchRow.addView(tvPitch, rowWeight(1.4f));
        pitchRow.addView(stepperButton("+", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scalePitch(1.06f);
            }
        }), fixedWidth(52));
        root.addView(pitchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 色板行
        LinearLayout palRow = new LinearLayout(this);
        palRow.setOrientation(LinearLayout.HORIZONTAL);
        palRow.setGravity(Gravity.CENTER_VERTICAL);
        palRow.setPadding(0, dp(10), 0, 0);
        TextView palLabel = new TextView(this);
        palLabel.setText(getString(R.string.palette_label));
        palLabel.setTextSize(13);
        palLabel.setTextColor(0xFF49454F);
        palRow.addView(palLabel);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        plp.leftMargin = dp(10);
        palSpinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, BeadPalettes.selNames());
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        palSpinner.setAdapter(ad);
        palSpinner.setSelection(defaultTier());
        palSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                refreshPitchLabel();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        palRow.addView(palSpinner, plp);
        root.addView(palRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 生成图纸:主色大按钮
        btnGen = new TextView(this);
        btnGen.setText(getString(R.string.bp_gen));
        btnGen.setTextSize(16);
        btnGen.setTypeface(typefaceBold());
        btnGen.setTextColor(0xFFFFFFFF);
        btnGen.setBackgroundResource(R.drawable.bg_btn_primary);
        btnGen.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        glp.topMargin = dp(14);
        btnGen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                generate();
            }
        });
        root.addView(btnGen, glp);

        // 模拟器 QA 直载通道:am start --es bp_uri file:///...(仅 root/调试可达,
        // 绕开系统选择器在部分镜像上点击无响应的怪癖)
        String dbg = getIntent().getStringExtra("bp_uri");
        if (dbg != null) loadImage(Uri.parse(dbg));
    }

    private LinearLayout buildButtonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, 0);
        TextView pick = pillButton(getString(R.string.bp_pick),
                R.drawable.bg_btn_secondary, 0xFF6750A4);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        row.addView(pick, rowWeight(1f));
        TextView take = pillButton(getString(R.string.bp_take),
                R.drawable.bg_btn_secondary, 0xFF6750A4);
        LinearLayout.LayoutParams tlp = rowWeight(1f);
        tlp.leftMargin = dp(10);
        take.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
        row.addView(take, tlp);
        return row;
    }

    private TextView pillButton(String text, int bgRes, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(typefaceBold());
        t.setTextColor(color);
        t.setBackgroundResource(bgRes);
        t.setGravity(Gravity.CENTER);
        t.setHeight(dp(46));
        return t;
    }

    private TextView stepperButton(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(typefaceBold());
        t.setTextColor(0xFF49454F);
        t.setBackgroundResource(R.drawable.bg_icon_btn);
        t.setGravity(Gravity.CENTER);
        t.setHeight(dp(40));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams rowWeight(float w) {
        return new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    private LinearLayout.LayoutParams fixedWidth(int dp) {
        return new LinearLayout.LayoutParams(this.dp(dp),
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private android.graphics.Typeface typefaceBold() {
        return android.graphics.Typeface.DEFAULT_BOLD;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 默认色板 = 漫德 2.6mm(DIY 店主流),找不到则第一个含 2.6 的,再退 0 */
    private int defaultTier() {
        String[] names = BeadPalettes.selNames();
        int fallback = -1;
        for (int i = 0; i < names.length; i++) {
            if (names[i].contains("漫德") && names[i].contains("2.6")) return i;
            if (fallback < 0 && names[i].contains("2.6")) fallback = i;
        }
        return Math.max(0, fallback);
    }

    // ---- 选图 / 拍照 ----

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i,
                    getString(R.string.pick_photo)), REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_no_gallery),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA},
                    REQ_CAMERA_PERM);
            return;
        }
        takePhotoNow();
    }

    private void takePhotoNow() {
        cameraFile = new File(getCacheDir(),
                "beadphoto_" + System.currentTimeMillis() + ".jpg");
        Uri uri = AppFileProvider.forCameraFile(cameraFile);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TAKE);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_no_camera),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms,
                                           int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == REQ_CAMERA_PERM) {
            if (results.length > 0 && results[0]
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                takePhotoNow();
            } else {
                Toast.makeText(this, getString(R.string.err_need_camera),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        Uri uri = null;
        if (requestCode == REQ_PICK && data != null && data.getData() != null) {
            uri = data.getData();
        } else if (requestCode == REQ_TAKE) {
            if (cameraFile != null && cameraFile.exists()
                    && cameraFile.length() > 0) {
                uri = Uri.fromFile(cameraFile);
            } else {
                Toast.makeText(this, getString(R.string.err_camera),
                        Toast.LENGTH_SHORT).show();
            }
        }
        if (uri != null) loadImage(uri);
    }

    private void loadImage(Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap b = ImageLoader.load(getContentResolver(), uri, 2400);
                    b = ImageLoader.fixExif(getContentResolver(), uri, b);
                    final Bitmap fb = b;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (fb == null) {
                                Toast.makeText(BeadPhotoActivity.this,
                                        getString(R.string.err_camera),
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            bmp = fb;
                            lattice.setImage(fb);
                            refreshPitchLabel();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(BeadPhotoActivity.this,
                                    getString(R.string.err_camera),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ---- 网格调整 ----

    private void scalePitch(double f) {
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.bp_no_img),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        lattice.scalePitch(f);
        refreshPitchLabel();
    }

    private void refreshPitchLabel() {
        if (bmp == null || lattice == null) return;
        int across = (int) Math.round(bmp.getWidth() / lattice.pitchX);
        int down = (int) Math.round(bmp.getHeight() / lattice.pitchY);
        tvPitch.setText(String.format(getString(R.string.bp_beads_fmt),
                Math.max(1, across), Math.max(1, down)));
    }

    private void autoDetect() {
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.bp_no_img),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (working) return;
        working = true;
        Toast.makeText(this, getString(R.string.bp_loading),
                Toast.LENGTH_SHORT).show();
        final Bitmap b = bmp;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int w = b.getWidth(), h = b.getHeight();
                int[] px = new int[w * h];
                b.getPixels(px, 0, w, 0, 0, w, h);
                // 透视校正(v2.71):斜拍必然带梯形畸变+残余旋转,轴对齐网格
                // 模型对不齐整幅(解码白边呈"下宽上窄"楔形)。先找面板四角
                // 拉正,后续对格/生成都用校正后的图;无清晰面板则用原图
                Bitmap work = b;
                int[] wh = new int[2];
                int[] rect = com.pindou.app.util.GridScanner.deskew(px, w, h, wh);
                if (rect != null) {
                    work = Bitmap.createBitmap(rect, wh[0], wh[1],
                            Bitmap.Config.ARGB_8888);
                    px = rect;
                    w = wh[0];
                    h = wh[1];
                }
                final PatternEngine.BeadGrid g =
                        PatternEngine.detectBeadGrid(px, w, h);
                final Bitmap fb = work;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        working = false;
                        if (fb != b) {   // 拉正成功:工作图与叠加层都换成校正图
                            bmp = fb;
                            lattice.setImage(fb);
                        }
                        if (g == null) {
                            Toast.makeText(BeadPhotoActivity.this,
                                    getString(R.string.bp_auto_fail),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        lattice.setGrid(g);
                        refreshPitchLabel();
                    }
                });
            }
        }).start();
    }

    // ---- 生成图纸 ----

    private void generate() {
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.bp_no_img),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (working) return;
        working = true;
        btnGen.setEnabled(false);
        btnGen.setAlpha(0.6f);
        Toast.makeText(this, getString(R.string.bp_loading),
                Toast.LENGTH_SHORT).show();
        final int tier = palSpinner.getSelectedItemPosition();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int w = bmp.getWidth(), h = bmp.getHeight();
                    int[] px = new int[w * h];
                    bmp.getPixels(px, 0, w, 0, 0, w, h);
                    List<com.pindou.app.bead.BeadColor> pal =
                            BeadPalettes.getPalette(tier);
                    final BeadPattern p = PatternEngine.fromBeadPhoto(px, w, h,
                            lattice.lineX, lattice.lineY,
                            lattice.pitchX, lattice.pitchY,
                            pal, true, false, false);
                    if (p == null) throw new IllegalStateException("no cells");
                    String palName = BeadPalettes.selNames()[tier];
                    JSONObject share = PatternShare.build(p,
                            getString(R.string.tool_bead_photo));
                    JSONObject settings = new JSONObject();
                    settings.put("cols", p.cols);
                    settings.put("rows", p.rows);
                    settings.put("tierIdx", tier);
                    settings.put("style", 0);
                    settings.put("dither", false);
                    settings.put("denoise", 0);
                    settings.put("dominant", false);
                    settings.put("precise", true);
                    settings.put("limitIdx", 0);
                    settings.put("mini", palName.contains("2.6")
                            || palName.contains("2.5"));
                    JSONObject o = new JSONObject();
                    o.put("name", getString(R.string.tool_bead_photo));
                    o.put("settings", settings);
                    o.put("share", share);
                    final String json = o.toString();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            working = false;
                            EditorActivity.pendingProjectJson = json;
                            startActivity(new Intent(BeadPhotoActivity.this,
                                    EditorActivity.class));
                            finish();
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            working = false;
                            btnGen.setEnabled(true);
                            btnGen.setAlpha(1f);
                            Toast.makeText(BeadPhotoActivity.this,
                                    getString(R.string.bp_gen_fail),
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ---- 网格预览视图 ----

    /** 照片 + 豆格叠加层:单指拖动 = 平移网格原点;豆距由 ± 步进或自动对格 */
    private static class LatticeView extends View {

        Bitmap bmp;
        double lineX, lineY, pitchX, pitchY;
        private final Paint pShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);

        LatticeView(android.content.Context c) {
            super(c);
            pShadow.setColor(0x66000000);
            pShadow.setStrokeWidth(4);
            pLine.setColor(0xFFFFC846);
            pLine.setStrokeWidth(2);
        }

        void setImage(Bitmap b) {
            bmp = b;
            // 默认网格:按横 24 颗假设,用户随后自动对格或微调
            pitchX = Math.max(8, b.getWidth() / 24.0);
            pitchY = pitchX;
            lineX = 0;
            lineY = 0;
            requestLayout();
            invalidate();
        }

        void setGrid(PatternEngine.BeadGrid g) {
            pitchX = g.pitchX;
            pitchY = g.pitchY;
            lineX = g.lineX;
            lineY = g.lineY;
            invalidate();
        }

        void scalePitch(double f) {
            double maxDim = bmp == null ? 4000
                    : Math.max(bmp.getWidth(), bmp.getHeight());
            pitchX = Math.max(6, Math.min(maxDim / 4.0, pitchX * f));
            pitchY = Math.max(6, Math.min(maxDim / 4.0, pitchY * f));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bmp == null) return;
            int vw = getWidth(), vh = getHeight();
            int bw = bmp.getWidth(), bh = bmp.getHeight();
            float sc = Math.min(vw / (float) bw, vh / (float) bh);
            float offX = (vw - bw * sc) / 2f;
            float offY = (vh - bh * sc) / 2f;
            dstRect.set(offX, offY, offX + bw * sc, offY + bh * sc);
            canvas.drawBitmap(bmp, null, dstRect, null);
            // 网格线:从第一条 ≥ 原点的线画到越界为止
            for (double x = lineX; x <= bw + 0.5; x += pitchX) {
                if (x < -0.5) continue;
                float sx = offX + (float) (x * sc);
                canvas.drawLine(sx, offY, sx, offY + bh * sc, pShadow);
                canvas.drawLine(sx, offY, sx, offY + bh * sc, pLine);
            }
            for (double y = lineY; y <= bh + 0.5; y += pitchY) {
                if (y < -0.5) continue;
                float sy = offY + (float) (y * sc);
                canvas.drawLine(offX, sy, offX + bw * sc, sy, pShadow);
                canvas.drawLine(offX, sy, offX + bw * sc, sy, pLine);
            }
        }

        private final android.graphics.RectF dstRect =
                new android.graphics.RectF();
        private float lastX, lastY;

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (bmp == null) return false;
            int vw = getWidth(), vh = getHeight();
            int bw = bmp.getWidth(), bh = bmp.getHeight();
            float sc = Math.min(vw / (float) bw, vh / (float) bh);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX();
                    lastY = e.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lineX -= (e.getX() - lastX) / sc;
                    lineY -= (e.getY() - lastY) / sc;
                    lastX = e.getX();
                    lastY = e.getY();
                    invalidate();
                    return true;
                default:
                    return true;
            }
        }
    }
}
