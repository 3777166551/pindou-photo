package com.pindou.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.export.EffectRenderer;
import com.pindou.app.util.GallerySaver;
import com.pindou.app.util.Jsons;
import com.pindou.app.util.PatternShare;
import com.pindou.app.util.ProjectStore;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 立体组合(v2.50):把 2~4 个项目存档堆成多层立体件。
 * 选层 -> 拖动调整每层相对位置/层高 -> 3D 堆叠预览(上小下大透视,
 * 带侧壁与投影) -> 导出预览 PNG + 合并豆单(按颜色汇总各层用量)。
 * 层图纸即各项目自己的图纸;拼法与豆数以逐层图纸为准。
 */
public class LayeredComposeActivity extends Activity {

    /** 一层:项目名 + 图纸 + 预渲染效果图 + 相对偏移/层高 */
    private static final class Layer {
        String name;
        BeadPattern pattern;
        Bitmap effect;
        float dx, dy;      // 相对基准层的平移(比例,基于视口宽)
        float lift = 0.6f; // 层高(视觉抬升,0~1)
        float lastX = -1, lastY = -1;   // 拖动上一点
    }

    private final List<Layer> layers = new ArrayList<>();
    private LinearLayout pickBox;
    private LinearLayout editBar;
    private LayerView layerView;
    private TextView layerInfo;
    private SeekBar liftBar;
    private int selected = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    // ---------------- 选层 ----------------

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFFFF6ED);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(getString(R.string.layered_title));
        title.setTextColor(0xFF3A3050);
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText(getString(R.string.layered_tip));
        tip.setTextColor(0xFF9A8FA6);
        tip.setTextSize(13);
        tip.setPadding(0, pad / 2, 0, pad);
        root.addView(tip);

        pickBox = new LinearLayout(this);
        pickBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(pickBox);

        List<ProjectStore.Entry> items;
        try {
            items = ProjectStore.list(this);
        } catch (Exception e) {
            items = new ArrayList<>();
        }
        if (items.size() < 2) {
            Toast.makeText(this, getString(R.string.layered_need_projects),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        for (final ProjectStore.Entry e : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, pad / 3, 0, pad / 3);

            CheckBox cb = new CheckBox(this);
            cb.setTextSize(14);
            cb.setTextColor(0xFF3A3050);
            cb.setText(e.name);
            row.addView(cb, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (e.thumb != null) {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageBitmap(e.thumb);
                int side = Math.round(42 * getResources().getDisplayMetrics().density);
                row.addView(iv, new LinearLayout.LayoutParams(side, side));
            }
            cb.setTag(e);
            pickBox.addView(row);
        }

        TextView go = new TextView(this);
        go.setText(getString(R.string.layered_start));
        go.setTextColor(0xFFFFFFFF);
        go.setTextSize(16);
        go.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        go.setGravity(Gravity.CENTER);
        go.setBackgroundResource(R.drawable.bg_btn_primary);
        go.setClickable(true);
        int top = Math.round(14 * getResources().getDisplayMetrics().density);
        go.setPadding(0, top / 2, 0, top / 2);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCompose();
            }
        });
        root.addView(go, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        scroll.addView(root);
        setContentView(scroll);
    }

    private void startCompose() {
        List<ProjectStore.Entry> picked = new ArrayList<>();
        for (int i = 0; i < pickBox.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) pickBox.getChildAt(i);
            CheckBox cb = (CheckBox) row.getChildAt(0);
            if (cb.isChecked()) {
                picked.add((ProjectStore.Entry) cb.getTag());
            }
        }
        if (picked.size() < 2 || picked.size() > 4) {
            Toast.makeText(this, getString(R.string.layered_pick_2_4),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        for (ProjectStore.Entry e : picked) {
            try {
                JSONObject o = Jsons.read(e.file);
                BeadPattern p = PatternShare.fromProject(o);
                if (p == null || p.cols == 0) continue;
                Layer l = new Layer();
                l.name = e.name;
                l.pattern = p;
                l.effect = EffectRenderer.render(p, 1024);
                layers.add(l);
            } catch (Exception ex) {
                Toast.makeText(this, getString(R.string.layered_load_failed)
                        + " " + e.name, Toast.LENGTH_SHORT).show();
            }
        }
        if (layers.size() < 2) {
            Toast.makeText(this, getString(R.string.layered_load_failed2),
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // 底层在前(layers[0] = 底),上面层依次抬升
        showEditor();
    }

    // ---------------- 合成编辑 ----------------

    private void showEditor() {
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF2B2436);

        layerView = new LayerView(this);
        root.addView(layerView, new android.widget.FrameLayout.LayoutParams(
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

        // 底部控制条:层切换 / 层高 / 合并豆单 / 导出
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xE62B2436);
        bar.setPadding(pad, pad / 2, pad, pad);

        layerInfo = new TextView(this);
        layerInfo.setTextColor(0xFFFFFFFF);
        layerInfo.setTextSize(13);
        layerInfo.setGravity(Gravity.CENTER);
        layerInfo.setPadding(0, 0, 0, pad / 2);
        bar.addView(layerInfo);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView prev = chip("◀");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selected = (selected - 1 + layers.size()) % layers.size();
                syncLayerUi();
            }
        });
        row.addView(prev, chipLp());

        liftBar = new SeekBar(this);
        liftBar.setMax(100);
        liftBar.setProgress(Math.round(layers.get(selected).lift * 100));
        liftBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                layers.get(selected).lift = v / 100f;
                layerView.invalidate();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(liftBar, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView next = chip("▶");
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selected = (selected + 1) % layers.size();
                syncLayerUi();
            }
        });
        row.addView(next, chipLp());
        bar.addView(row);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        row2.setPadding(0, pad / 2, 0, 0);

        TextView bom = chip(getString(R.string.layered_bom));
        bom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMergedBom();
            }
        });
        row2.addView(bom, chipLp());

        TextView save = chip(getString(R.string.layered_export));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportPreview();
            }
        });
        android.widget.LinearLayout.LayoutParams saveLp = chipLp();
        saveLp.leftMargin = pad;
        row2.addView(save, saveLp);
        bar.addView(row2);

        root.addView(bar, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        setContentView(root);
        syncLayerUi();
    }

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFF3A3050);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_chip);
        t.setPadding(Math.round(12 * getResources().getDisplayMetrics().density), 0,
                Math.round(12 * getResources().getDisplayMetrics().density), 0);
        t.setClickable(true);
        return t;
    }

    private LinearLayout.LayoutParams chipLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Math.round(34 * getResources().getDisplayMetrics().density));
    }

    private void syncLayerUi() {
        Layer l = layers.get(selected);
        layerInfo.setText(getString(R.string.fmt_layered_layer,
                selected + 1, layers.size(), l.name));
        liftBar.setProgress(Math.round(l.lift * 100));
        layerView.invalidate();
    }

    // ---------------- 合并豆单 ----------------

    /** key = 色号 code + rgb,值 = [色名, rgb, 总数] */
    private List<Object[]> mergedBom() {
        Map<String, Object[]> m = new LinkedHashMap<>();
        for (Layer l : layers) {
            for (BeadPattern.UsedColor uc : l.pattern.usedColors) {
                String key = uc.color.code + "|" + uc.color.rgb;
                Object[] v = m.get(key);
                if (v == null) {
                    m.put(key, new Object[]{uc.color.name, uc.color.rgb, uc.count});
                } else {
                    v[2] = (Integer) v[2] + uc.count;
                }
            }
        }
        List<Object[]> out = new ArrayList<>(m.values());
        java.util.Collections.sort(out, new java.util.Comparator<Object[]>() {
            @Override
            public int compare(Object[] a, Object[] b) {
                return (Integer) b[2] - (Integer) a[2];
            }
        });
        return out;
    }

    private void showMergedBom() {
        List<Object[]> bom = mergedBom();
        int total = 0;
        for (Object[] r : bom) total += (Integer) r[2];

        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(10 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad * 2);

        TextView head = new TextView(this);
        head.setText(getString(R.string.fmt_layered_bom_head, layers.size(), total));
        head.setTextColor(0xFF3A3050);
        head.setTextSize(15);
        head.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.setPadding(0, 0, 0, pad);
        box.addView(head);

        for (Object[] r : bom) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View sw = new View(this);
            sw.setBackgroundColor(0xFF000000 | (Integer) r[1]);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                    pad * 2, pad * 2);
            swLp.rightMargin = pad;
            row.addView(sw, swLp);
            TextView t = new TextView(this);
            t.setText(getString(R.string.fmt_layered_bom_row,
                    r[0], (Integer) r[2]));
            t.setTextColor(0xFF3A3050);
            t.setTextSize(13);
            row.addView(t, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            box.addView(row);
        }
        sc.addView(box);

        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.layered_bom))
                .setView(sc)
                .setPositiveButton(getString(R.string.btn_ok), null)
                .create()
                .show();
    }

    // ---------------- 导出预览 ----------------

    private void exportPreview() {
        Bitmap out = layerView.renderOffscreen(1600);
        if (out == null) {
            Toast.makeText(this, getString(R.string.err_infer), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmm",
                    java.util.Locale.CHINA).format(new java.util.Date());
            Uri uri = GallerySaver.save(this, out,
                    getString(R.string.file_layered_prefix) + stamp + ".png");
            if (uri != null) {
                Toast.makeText(this, getString(R.string.saved_gallery_fmt,
                        GallerySaver.dirName(this)), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, getString(R.string.err_prefix_save),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_prefix_save),
                    Toast.LENGTH_SHORT).show();
        } finally {
            out.recycle();
        }
    }

    // ---------------- 堆叠视图 ----------------

    private class LayerView extends View {

        private final Paint bmpP = new Paint(Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final Paint shadowP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frameP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tmp = new Path();
        private final android.graphics.RectF tmpR = new android.graphics.RectF();

        LayerView(android.content.Context ctx) {
            super(ctx);
            frameP.setStyle(Paint.Style.STROKE);
            // 投影的 BlurMaskFilter 在硬件画布上被忽略,本视图按需重绘,软件层即可
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        /** 第 i 层的绘制矩形(上小下大 + 各层 lift 抬升与偏移) */
        private android.graphics.RectF rectOf(int i, int w, int h, float[] outLiftPx) {
            Layer l = layers.get(i);
            float baseW = w * 0.78f;
            float t = layers.size() <= 1 ? 0 : i / (float) (layers.size() - 1);
            float scale = 1f - t * 0.16f;
            float sw = baseW * scale;
            float sh = sw * l.effect.getHeight() / l.effect.getWidth();
            if (sh > h * 0.62f) {
                sh = h * 0.62f;
                sw = sh * l.effect.getWidth() / l.effect.getHeight();
            }
            float cx = w * 0.5f + l.dx * w;
            float liftPx = l.lift * h * 0.16f;
            float cy = h * 0.5f - t * h * 0.10f - liftPx + l.dy * w;
            outLiftPx[0] = liftPx;
            return new android.graphics.RectF(cx - sw / 2, cy - sh / 2, cx + sw / 2, cy + sh / 2);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            if (w == 0 || layers.isEmpty()) return;
            float[] lift = new float[1];
            // 侧壁厚 + 投影随绘制层从底往上
            for (int i = 0; i < layers.size(); i++) {
                android.graphics.RectF r = rectOf(i, w, h, lift);
                Layer l = layers.get(i);
                float thick = Math.max(4f, r.width() * 0.035f);
                // 投影
                shadowP.setColor(0x4D000000);
                shadowP.setMaskFilter(new android.graphics.BlurMaskFilter(
                        Math.max(4f, thick * 0.9f), android.graphics.BlurMaskFilter.Blur.NORMAL));
                tmpR.set(r.left + thick, r.bottom - thick * 0.4f,
                        r.right + thick, r.bottom + thick * 0.8f);
                c.drawOval(tmpR, shadowP);
                // 侧壁(下移的深色底)
                tmp.reset();
                tmp.moveTo(r.left, r.bottom);
                tmp.lineTo(r.right, r.bottom);
                tmp.lineTo(r.right, r.bottom + thick);
                tmp.lineTo(r.left, r.bottom + thick);
                tmp.close();
                bmpP.setColor(0xFFB9AE9C);
                c.drawPath(tmp, bmpP);
                // 层面
                bmpP.setColor(0xFFFFFFFF);
                c.drawBitmap(l.effect, null, r, bmpP);
                // 选中层描边
                if (i == selected) {
                    frameP.setColor(0xFFFFCF56);
                    frameP.setStrokeWidth(Math.max(3f, r.width() * 0.012f));
                    c.drawRect(r, frameP);
                    frameP.setColor(0xFF40354E);
                    frameP.setStrokeWidth(frameP.getStrokeWidth() * 2.2f);
                    tmpR.set(r);
                    tmpR.inset(-frameP.getStrokeWidth() * 0.6f, -frameP.getStrokeWidth() * 0.6f);
                    c.drawRect(tmpR, frameP);
                }
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int w = getWidth(), h = getHeight();
            float[] lift = new float[1];
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    // 点中最上层的含点矩形来选层(从顶层往下找)
                    for (int i = layers.size() - 1; i >= 0; i--) {
                        android.graphics.RectF r = rectOf(i, w, h, lift);
                        if (r.contains(e.getX(), e.getY())) {
                            if (selected != i) {
                                selected = i;
                                syncLayerUi();
                            }
                            return true;
                        }
                    }
                    return false;
                }
                case MotionEvent.ACTION_MOVE: {
                    Layer l = layers.get(selected);
                    l.dx += e.getX() - (l.lastX < 0 ? e.getX() : l.lastX);
                    l.dy += e.getY() - (l.lastY < 0 ? e.getY() : l.lastY);
                    l.lastX = e.getX();
                    l.lastY = e.getY();
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    for (Layer l : layers) {
                        l.lastX = -1;
                        l.lastY = -1;
                    }
                    return true;
                }
            }
            return false;
        }

        Bitmap renderOffscreen(int maxDim) {
            int w = maxDim, h = Math.round(maxDim * 1.15f);
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.drawColor(0xFFF0EAE2);
            bmpP.setShadowLayer(0, 0, 0, 0);
            float[] lift = new float[1];
            for (int i = 0; i < layers.size(); i++) {
                android.graphics.RectF r = rectOf(i, w, h, lift);
                Layer l = layers.get(i);
                float thick = Math.max(4f, r.width() * 0.035f);
                tmp.reset();
                tmp.moveTo(r.left, r.bottom);
                tmp.lineTo(r.right, r.bottom);
                tmp.lineTo(r.right, r.bottom + thick);
                tmp.lineTo(r.left, r.bottom + thick);
                tmp.close();
                bmpP.setColor(0xFFB9AE9C);
                c.drawPath(tmp, bmpP);
                bmpP.setColor(0xFFFFFFFF);
                c.drawBitmap(l.effect, null, r, bmpP);
                if (i == selected) {
                    frameP.setColor(0xFF40354E);
                    frameP.setStrokeWidth(Math.max(3f, r.width() * 0.008f));
                    c.drawRect(r, frameP);
                }
            }
            return bmp;
        }
    }
}
