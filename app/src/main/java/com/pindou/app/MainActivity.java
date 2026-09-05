package com.pindou.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.Templates;
import com.pindou.app.provider.AppFileProvider;
import com.pindou.app.util.Jsons;
import com.pindou.app.util.PatternShare;
import com.pindou.app.util.ProjectStore;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.pindou.app.bead.TemplateAssets;

public class MainActivity extends Activity {

    private static final int REQ_PICK_GALLERY = 1;
    private static final int REQ_TAKE_PHOTO = 2;
    private static final int REQ_SCAN_PATTERN = 3;
    private static final int REQ_ACTION_PICK = 4;
    /** 工具卡片(二次元/去水印)选图后要自动执行的动作 */
    private int nextAction = com.pindou.app.EditorActivity.PENDING_NONE;

    private File cameraFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        com.pindou.app.util.Skin.apply(getWindow().getDecorView());
        com.pindou.app.util.L10n.apply(this);

        // 自定义色板仓库:「合并采购单」等后台重算会用到,提前惰性载入
        com.pindou.app.bead.CustomPalettes.loadIfNeeded(this);

        // 按压缩放反馈:主页大按钮都是贴纸,按下去陷一下再弹回
        int[] pressIds = {R.id.btnGallery, R.id.btnCamera, R.id.btnText,
                R.id.btnBlank, R.id.btnTemplates, R.id.btnProjects,
                R.id.btnScanPattern, R.id.cardWatermark, R.id.btnKnowledge,
                R.id.btnInventoryHome};
        for (int id : pressIds) {
            com.pindou.app.util.Anim.pressScale(findViewById(id));
        }
        // 模板数量按实际打包数据实时显示(不再写死宣传数)
        int tplTotal = 0;
        for (Templates.Cat c : com.pindou.app.bead.TemplateAssets.allCategories()) {
            tplTotal += c.items.length;
        }
        ((TextView) findViewById(R.id.toolTemplatesDesc))
                .setText(getString(R.string.tool_templates_desc_n, tplTotal));
        findViewById(R.id.btnInventoryHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(MainActivity.this,
                        InventoryActivity.class));
            }
        });
        findViewById(R.id.btnScanPattern).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickForScan();
            }
        });
        findViewById(R.id.cardWatermark).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickForAction(com.pindou.app.EditorActivity.PENDING_WATERMARK,
                        getString(R.string.pick_wm_photo));
            }
        });

        findViewById(R.id.btnGallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFromGallery();
            }
        });
        findViewById(R.id.btnCamera).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
        findViewById(R.id.btnText).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTextDialog();
            }
        });
        findViewById(R.id.btnBlank).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, EditorActivity.class);
                i.putExtra(EditorActivity.EXTRA_BLANK, true);
                startActivity(i);
            }
        });
        findViewById(R.id.btnTemplates).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTemplateGallery();
            }
        });
        findViewById(R.id.btnProjects).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMyProjects();
            }
        });
        findViewById(R.id.btnKnowledge).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, KnowledgeActivity.class));
                overridePendingTransition(R.anim.enter_up, R.anim.exit_dim);
            }
        });
    }

    /** 文字生成:把名字/词语渲染成黑字透明底位图,交给编辑器变成拼豆图纸 */
    private void showTextDialog() {
        final EditText input = new EditText(this);
        input.setHint(getString(R.string.textgen_hint));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.textgen_title))
                .setMessage(getString(R.string.textgen_msg))
                .setView(input)
                .setPositiveButton(getString(R.string.btn_generate), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String text = input.getText().toString().trim();
                        if (text.isEmpty()) {
                            Toast.makeText(MainActivity.this, getString(R.string.err_empty_text),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        EditorActivity.pendingSource = renderText(text);
                        startActivity(new Intent(MainActivity.this, EditorActivity.class));
                        overridePendingTransition(R.anim.enter_up, R.anim.exit_dim);
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    /** 渲染文字:自适应字号 + 自动换行(最多 4 行),居中画在透明画布上 */
    static Bitmap renderText(String text) {
        int size = 640;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);

        float fs = 240f;
        List<String> lines = new ArrayList<>();
        while (true) {
            p.setTextSize(fs);
            lines.clear();
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                String ch = text.substring(i, i + 1);
                if (cur.length() > 0 && p.measureText(cur.toString() + ch) > 560f) {
                    lines.add(cur.toString());
                    cur = new StringBuilder();
                }
                cur.append(ch);
            }
            if (cur.length() > 0) lines.add(cur.toString());

            boolean widthOk = true;
            for (String ln : lines) {
                if (p.measureText(ln) > 560f) {
                    widthOk = false;
                    break;
                }
            }
            float totalH = lines.size() * fs * 1.25f;
            if (fs <= 24f || (widthOk && totalH <= 560f && lines.size() <= 4)) break;
            fs -= 8f;
        }

        float lineH = fs * 1.25f;
        float y = size / 2f - (lines.size() - 1) * lineH / 2f;
        Paint.FontMetrics fm = p.getFontMetrics();
        float baselineFix = -(fm.ascent + fm.descent) / 2f;
        for (String ln : lines) {
            c.drawText(ln, size / 2f, y + baselineFix, p);
            y += lineH;
        }
        return bmp;
    }

    // ---------------- 图案模板库 ----------------

    /**
     * 模板库:单屏完成"选分类 + 选图案"(旧的二级小弹窗层级深、格子小,
     * 已替换为顶部分类标签 + 大缩略图网格)。
     */
    private void showTemplateGallery() {
        final List<Templates.Cat> cats = TemplateAssets.allCategories();
        int total = 0;
        for (Templates.Cat c : cats) total += c.items.length;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        // 顶部分类标签
        final HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        final LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(10), dp(8), dp(10), dp(4));
        hs.addView(chips);
        box.addView(hs, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final GridView gv = new GridView(this);
        gv.setNumColumns(4);
        gv.setVerticalSpacing(dp(12));
        gv.setHorizontalSpacing(dp(6));
        gv.setPadding(dp(10), dp(10), dp(10), dp(14));
        final ThumbAdapter adapter = new ThumbAdapter(cats.get(0));
        gv.setAdapter(adapter);
        gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                openTemplate(adapter.cat.items[position]);
            }
        });
        box.addView(gv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        for (int i = 0; i < cats.size(); i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(cats.get(i).name);
            chip.setTextSize(13);
            chip.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            chip.setTextColor(0xFF22B57F);
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setElevation(dp(2));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);
            chip.setAlpha(i == 0 ? 1f : 0.55f);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    adapter.setCat(cats.get(idx));
                    for (int j = 0; j < chips.getChildCount(); j++) {
                        chips.getChildAt(j).setAlpha(j == idx ? 1f : 0.55f);
                    }
                }
            });
            chips.addView(chip);
        }

        android.app.AlertDialog d = new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.templates_title)
                        + " · " + getString(R.string.tool_templates_desc_n, total))
                .setView(box)
                .setPositiveButton(getString(R.string.btn_close), null)
                .create();
        d.show();
        d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.8f));
    }

    /** 缩略图网格适配器(64dp 大图,随分类标签切换) */
    private class ThumbAdapter extends BaseAdapter {
        Templates.Cat cat;

        ThumbAdapter(Templates.Cat cat) {
            this.cat = cat;
        }

        void setCat(Templates.Cat cat) {
            this.cat = cat;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return cat.items.length;
        }

        @Override
        public Object getItem(int position) {
            return cat.items[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell = new LinearLayout(MainActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            ImageView iv = new ImageView(MainActivity.this);
            iv.setImageBitmap(Templates.buildThumb(cat.items[position]));
            int side = dp(64);
            cell.addView(iv, new LinearLayout.LayoutParams(side, side));

            TextView t = new TextView(MainActivity.this);
            t.setText(cat.items[position].name);
            t.setTextColor(0xFF444444);
            t.setTextSize(11);
            t.setMaxLines(1);
            cell.addView(t);
            return cell;
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void openTemplate(Templates.Tpl tpl) {
        EditorActivity.pendingSource = Templates.build(tpl, 24);
        EditorActivity.pendingSuggestedSize = tpl.suggestedSize;
        startActivity(new Intent(MainActivity.this, EditorActivity.class));
        overridePendingTransition(R.anim.enter_up, R.anim.exit_dim);
    }

    // ---------------- 我的项目 ----------------

    private void showMyProjects() {
        List<ProjectStore.Entry> items;
        try {
            items = ProjectStore.list(this);
        } catch (Exception e) {
            items = new ArrayList<>();
        }
        if (items.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_projects),
                    Toast.LENGTH_LONG).show();
            return;
        }
        buildProjectsDialog(items);
    }

    private void buildProjectsDialog(final List<ProjectStore.Entry> items) {
        int pad = Math.round(10 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView mergeBtn = new TextView(this);
        mergeBtn.setText(getString(R.string.btn_merge_bom));
        mergeBtn.setTextColor(0xFF1E6BB8);
        mergeBtn.setTextSize(13);
        mergeBtn.setPadding(pad, pad, pad, pad);
        mergeBtn.setBackgroundResource(android.R.drawable.list_selector_background);
        mergeBtn.setClickable(true);
        mergeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMergePicker(items);
            }
        });
        box.addView(mergeBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA);
        for (final ProjectStore.Entry e : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad / 2, pad, pad / 2);
            row.setBackgroundResource(android.R.drawable.list_selector_background);

            ImageView iv = new ImageView(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFEFEAE3);
            bg.setCornerRadius(8 * getResources().getDisplayMetrics().density);
            iv.setBackground(bg);
            iv.setImageBitmap(e.thumb);
            iv.setClipToOutline(true);
            int side = Math.round(46 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(side, side);
            ip.rightMargin = pad * 3 / 2;
            row.addView(iv, ip);

            LinearLayout mid = new LinearLayout(this);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setText(e.name);
            name.setTextColor(0xFF333333);
            name.setTextSize(15);
            mid.addView(name);
            TextView meta = new TextView(this);
            meta.setText(fmt.format(new Date(e.savedAt)));
            meta.setTextColor(0xFF9A9086);
            meta.setTextSize(11);
            mid.addView(meta);
            row.addView(mid, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = new TextView(this);
            del.setText(getString(R.string.btn_delete));
            del.setTextColor(0xFFD32F2F);
            del.setTextSize(13);
            del.setPadding(pad, pad, pad, pad);
            del.setClickable(true);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDeleteProject(e, items);
                }
            });
            row.addView(del, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openProject(e);
                }
            });
            box.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        android.widget.ScrollView sc = new android.widget.ScrollView(this);
        sc.addView(box);
        com.pindou.app.util.Skin.apply(sc);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.projects_title_fmt, items.size()))
                .setView(sc)
                .setPositiveButton(getString(R.string.btn_close), null)
                .show();
    }

    // ---------------- 合并采购单 ----------------

    /** 勾选若干项目,把它们的颜色用量汇总成一张跨项目采购单 */
    private void showMergePicker(final List<ProjectStore.Entry> items) {
        final String[] names = new String[items.size()];
        final boolean[] checked = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = items.get(i).name;
            checked[i] = i == 0;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.merge_pick_title))
                .setMultiChoiceItems(names, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which,
                                                boolean isChecked) {
                                checked[which] = isChecked;
                            }
                        })
                .setPositiveButton(getString(R.string.btn_generate), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        buildMergedBom(items, checked);
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void buildMergedBom(final List<ProjectStore.Entry> items,
                                final boolean[] checked) {
        final AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.merge_running))
                .setMessage(getString(R.string.merge_wait))
                .show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final java.util.LinkedHashMap<String, long[]> agg =
                        new java.util.LinkedHashMap<>();
                final java.util.LinkedHashMap<String, String> labels =
                        new java.util.LinkedHashMap<>();
                final java.util.LinkedHashMap<String, Integer> rgbs =
                        new java.util.LinkedHashMap<>();
                int ok = 0;
                for (int i = 0; i < items.size(); i++) {
                    if (!checked[i]) continue;
                    org.json.JSONObject o = null;
                    try {
                        o = Jsons.read(items.get(i).file);
                    } catch (Exception ignored) {
                    }
                    if (o == null) continue;
                    BeadPattern p = PatternShare.fromProject(o);
                    if (p == null) continue;
                    ok++;
                    for (BeadPattern.UsedColor uc : p.usedColors) {
                        String key = String.format(Locale.CHINA, "%06X",
                                0xFFFFFF & uc.color.rgb);
                        long[] c = agg.get(key);
                        if (c == null) {
                            c = new long[1];
                            agg.put(key, c);
                            labels.put(key, uc.color.fullLabel());
                            rgbs.put(key, uc.color.rgb);
                        }
                        c[0] += uc.count;
                    }
                }
                final java.util.ArrayList<String> keys =
                        new java.util.ArrayList<>(agg.keySet());
                java.util.Collections.sort(keys, new java.util.Comparator<String>() {
                    @Override
                    public int compare(String a, String b) {
                        return Long.compare(agg.get(b)[0], agg.get(a)[0]);
                    }
                });
                final int okCount = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading.dismiss();
                        showMergedBomResult(keys, agg, labels, rgbs, okCount);
                    }
                });
            }
        }).start();
    }

    private void showMergedBomResult(final java.util.ArrayList<String> keys,
                                     final java.util.LinkedHashMap<String, long[]> agg,
                                     final java.util.LinkedHashMap<String, String> labels,
                                     final java.util.LinkedHashMap<String, Integer> rgbs,
                                     final int okProjects) {
        if (keys.isEmpty() || okProjects == 0) {
            Toast.makeText(this, getString(R.string.merge_none),
                    Toast.LENGTH_LONG).show();
            return;
        }
        int pad = Math.round(10 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        long total = 0;
        for (String k : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, pad / 2, 0, pad / 2);
            ImageView iv = new ImageView(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(0xFF000000 | rgbs.get(k));
            iv.setBackground(gd);
            int side = Math.round(18 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(side, side);
            ip.rightMargin = pad;
            row.addView(iv, ip);
            TextView name = new TextView(this);
            name.setText(labels.get(k));
            name.setTextColor(0xFF333333);
            name.setTextSize(13);
            name.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(name);
            TextView cnt = new TextView(this);
            cnt.setText(String.format(Locale.CHINA, "%,d", agg.get(k)[0]));
            cnt.setTextColor(0xFF333333);
            cnt.setTextSize(13);
            row.addView(cnt, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            total += agg.get(k)[0];
            box.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        TextView sum = new TextView(this);
        sum.setText(String.format(Locale.CHINA,
                "%d 个项目 · %d 种颜色 · 合计 %,d 颗", okProjects, keys.size(), total));
        sum.setTextColor(0xFF8A8F98);
        sum.setTextSize(12);
        sum.setPadding(0, pad, 0, 0);
        box.addView(sum);
        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        com.pindou.app.util.Skin.apply(sc);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.merge_title))
                .setView(sc)
                .setNeutralButton(getString(R.string.btn_csv), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        exportCsv(keys, agg, labels);
                    }
                })
                .setPositiveButton(getString(R.string.btn_close), null)
                .show();
    }

    private void exportCsv(java.util.ArrayList<String> keys,
                           java.util.LinkedHashMap<String, long[]> agg,
                           java.util.LinkedHashMap<String, String> labels) {
        try {
            StringBuilder sb = new StringBuilder("\uFEFF");   // UTF-8 BOM,Excel 直接打开不乱码
            sb.append("RGB,色号/名称,数量\r\n");
            for (String k : keys) {
                sb.append(String.format(Locale.CHINA, "#%s,%s,%d\r\n",
                        k, labels.get(k).replace(",", "，"), agg.get(k)[0]));
            }
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA)
                    .format(new Date());
            File out = new File(getCacheDir(), getString(R.string.merge_file_prefix)
                    + stamp + ".csv");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();
            Uri uri = AppFileProvider.forCacheShare(out);
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("text/csv");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, getString(R.string.share_csv)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_prefix_export) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteProject(final ProjectStore.Entry e,
                                      final List<ProjectStore.Entry> all) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.fmt_delete_confirm, e.name))
                .setPositiveButton(getString(R.string.btn_delete), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        ProjectStore.delete(e.file);
                        Toast.makeText(MainActivity.this, getString(R.string.deleted),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void openProject(ProjectStore.Entry e) {
        try {
            byte[] raw = Jsons.readBytes(e.file);
            EditorActivity.pendingProjectJson = new String(raw, "UTF-8");
            startActivity(new Intent(MainActivity.this, EditorActivity.class));
            overridePendingTransition(R.anim.enter_up, R.anim.exit_dim);
        } catch (Exception ex) {
            Toast.makeText(this, getString(R.string.proj_unreadable_del), Toast.LENGTH_LONG).show();
        }
    }

    private void pickFromGallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, getString(R.string.pick_photo)), REQ_PICK_GALLERY);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.err_no_gallery), Toast.LENGTH_SHORT).show();
        }
    }

    /** 识别现成图纸:先选一张图纸照片 */
    private void pickForScan() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, getString(R.string.pick_pattern_photo)), REQ_SCAN_PATTERN);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.err_no_gallery), Toast.LENGTH_SHORT).show();
        }
    }

    /** 工具卡片:选图 → 带着动作进编辑器(图纸就绪后自动执行) */
    private void pickForAction(int action, String title) {
        nextAction = action;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(Intent.createChooser(i, title), REQ_ACTION_PICK);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.err_no_gallery), Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        cameraFile = new File(getCacheDir(), "camera_" + System.currentTimeMillis() + ".jpg");
        Uri uri = AppFileProvider.forCameraFile(cameraFile);
        Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_TAKE_PHOTO);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.err_no_camera), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        if (requestCode == REQ_PICK_GALLERY) {
            if (data != null && data.getData() != null) {
                openEditor(data.getData());
            }
        } else if (requestCode == REQ_TAKE_PHOTO) {
            if (cameraFile != null && cameraFile.exists() && cameraFile.length() > 0) {
                openEditor(Uri.fromFile(cameraFile));
            } else {
                Toast.makeText(this, getString(R.string.err_camera), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQ_SCAN_PATTERN) {
            if (data != null && data.getData() != null) {
                showScanDialog(data.getData());
            }
        } else if (requestCode == REQ_ACTION_PICK) {
            if (data != null && data.getData() != null) {
                com.pindou.app.EditorActivity.pendingAction = nextAction;
                nextAction = com.pindou.app.EditorActivity.PENDING_NONE;
                openEditor(data.getData());
            }
        }
    }

    private void openEditor(Uri photoUri) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_PHOTO_URI, photoUri.toString());
        startActivity(i);
        overridePendingTransition(R.anim.enter_up, R.anim.exit_dim);
    }

    // ---------------- 识别现成图纸 ----------------

    /**
     * 框选图纸区域 → 自动检测网格 → 按格采样 → 生成低分辨率位图交给编辑器。
     * 适用:其他 APP 的图纸截图、网格清晰的印刷图纸/板照。
     */
    private void showScanDialog(final Uri uri) {
        // 解码并限制到工作分辨率(检测/采样在缩图上做,足够精确且快)
        android.graphics.Bitmap bmp;
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > 900) sample *= 2;
            android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
            o2.inSampleSize = sample;
            java.io.InputStream in2 = getContentResolver().openInputStream(uri);
            bmp = android.graphics.BitmapFactory.decodeStream(in2, null, o2);
            if (in2 != null) in2.close();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.err_image), Toast.LENGTH_SHORT).show();
            return;
        }
        if (bmp == null) {
            Toast.makeText(this, getString(R.string.err_image), Toast.LENGTH_SHORT).show();
            return;
        }
        // 相机照的图基本都带 EXIF 旋转标记,不修正的话照片是横/歪的
        bmp = com.pindou.app.util.ImageLoader.fixExif(getContentResolver(), uri, bmp);
        final android.graphics.Bitmap src = bmp;
        float dm = getResources().getDisplayMetrics().density;
        int maxW = Math.round(330 * dm);
        int maxH = Math.round(400 * dm);
        float scale = Math.min(maxW / (float) src.getWidth(),
                maxH / (float) src.getHeight());
        final int dw = Math.max(1, Math.round(src.getWidth() * scale));
        final int dh = Math.max(1, Math.round(src.getHeight() * scale));

        android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setImageBitmap(src);
        iv.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        final ScanRectView overlay = new ScanRectView(this);
        // 默认框:居中 80% 区域
        overlay.rect.set(Math.round(dw * 0.1f), Math.round(dh * 0.1f),
                Math.round(dw * 0.9f), Math.round(dh * 0.9f));

        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.addView(iv, new android.widget.FrameLayout.LayoutParams(dw, dh));
        wrap.addView(overlay, new android.widget.FrameLayout.LayoutParams(dw, dh));
        int pad = Math.round(12 * dm);
        wrap.setPadding(pad, 0, pad, 0);
        com.pindou.app.util.Skin.apply(wrap);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.tool_scan))
                .setMessage(getString(R.string.scan_msg))
                .setView(wrap)
                .setPositiveButton(getString(R.string.btn_detect), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        android.graphics.Rect r = overlay.rect;
                        if (r.width() < 40 || r.height() < 40) {
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.scan_need_box), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        runScan(src, r, dw, dh);
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    /** 后台检测网格 + 采样,产物走 pendingSource 进编辑器 */
    private void runScan(final android.graphics.Bitmap src, final android.graphics.Rect sel,
                         final int dw, final int dh) {
        final android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setMessage(getString(R.string.working_scan));
        pd.setCanceledOnTouchOutside(false);
        pd.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int w = src.getWidth(), h = src.getHeight();
                    int[] px = new int[w * h];
                    src.getPixels(px, 0, w, 0, 0, w, h);
                    // 视口坐标 → 像素坐标
                    int x0 = Math.max(0, Math.round(sel.left * w / (float) dw));
                    int y0 = Math.max(0, Math.round(sel.top * h / (float) dh));
                    int x1 = Math.min(w - 1, Math.round(sel.right * w / (float) dw));
                    int y1 = Math.min(h - 1, Math.round(sel.bottom * h / (float) dh));

                    com.pindou.app.util.GridScanner.Grid g =
                            com.pindou.app.util.GridScanner.detect(px, w, h, x0, y0, x1, y1);
                    if (g == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pd.dismiss();
                                Toast.makeText(MainActivity.this,
                                        getString(R.string.scan_fail_nogrid),
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                    final int[] dims = new int[2];
                    final int[] cells =
                            com.pindou.app.util.GridScanner.sample(px, w, h, g, dims);
                    final int cols = dims[0], rows = dims[1];
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pd.dismiss();
                            android.graphics.Bitmap out =
                                    android.graphics.Bitmap.createBitmap(cols, rows,
                                            android.graphics.Bitmap.Config.ARGB_8888);
                            out.setPixels(cells, 0, cols, 0, 0, cols, rows);
                            EditorActivity.pendingSource = out;
                            EditorActivity.pendingSuggestedSize = Math.max(cols, rows);
                            startActivity(new Intent(MainActivity.this, EditorActivity.class));
                            overridePendingTransition(R.anim.enter_up, R.anim.exit_dim);
                        }
                    });
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pd.dismiss();
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.err_prefix_scan) + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** 框选覆盖层(与编辑页去水印的框选交互一致) */
    private class ScanRectView extends android.view.View {
        final android.graphics.Rect rect = new android.graphics.Rect();
        boolean dragging = false;
        final android.graphics.Paint fill = new android.graphics.Paint();
        final android.graphics.Paint stroke = new android.graphics.Paint();

        ScanRectView(android.content.Context c) {
            super(c);
            fill.setStyle(android.graphics.Paint.Style.FILL);
            fill.setColor(0x3322B57F);
            stroke.setStyle(android.graphics.Paint.Style.STROKE);
            stroke.setStrokeWidth(3);
            stroke.setColor(0xFF22B57F);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (!rect.isEmpty()) {
                canvas.drawRect(rect, fill);
                canvas.drawRect(rect, stroke);
            }
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent event) {
            int x = Math.round(event.getX());
            int y = Math.round(event.getY());
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    rect.set(x, y, x, y);
                    dragging = true;
                    invalidate();
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        rect.right = x;
                        rect.bottom = y;
                        invalidate();
                        return true;
                    }
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
