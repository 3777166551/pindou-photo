package com.pindou.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadBrandCharts;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.CustomPalettes;
import com.pindou.app.provider.AppFileProvider;
import com.pindou.app.util.Anim;
import com.pindou.app.util.PaletteShare;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 我的色板管理页:多套自定义色板的增删改、单色编辑(RGB 取色器 + 色号/名称)、
 * 导入导出(开放色板格式 pindou-palette,规范见 docs/SHARE-FORMAT.md)、
 * 以及从豆仓库存一键重建"我的豆板"。
 * 改动即时写盘(CustomPalettes),返回编辑器后由 revision 机制刷新下拉框。
 */
public class PaletteActivity extends Activity {

    /** 取色器预设色(常见拼豆色系,点击直接填入) */
    private static final int[] PRESETS = {
            0xFFFFFF, 0x141414, 0xA8A8A8, 0x4A4A4A, 0xE3242B, 0x9C1C1C,
            0xF48FB1, 0xE4007C, 0xF57C00, 0xF7E01E, 0x43A047, 0x1B5E20,
            0x26A69A, 0x42A5F5, 0x1E5AA8, 0x7B3FA0, 0x795548, 0xC8A17B,
            0x0D2C6B, 0x90CAF9
    };

    private static final int REQ_IMPORT = 201;

    private ListView lv;
    private TextView tvCount;
    private PaletteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_palette);
        CustomPalettes.loadIfNeeded(this);

        tvCount = findViewById(R.id.tvCount);
        lv = findViewById(R.id.lvPalettes);
        adapter = new PaletteAdapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent,
                                    View view, int position, long id) {
                if (position < BeadBrandCharts.customCount()) {
                    showActions(position);
                }
            }
        });
        lv.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent,
                                           View view, int position, long id) {
                if (position < BeadBrandCharts.customCount()) {
                    confirmDelete(position);
                    return true;
                }
                return false;
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        View btnNew = findViewById(R.id.btnNew);
        btnNew.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditDialog(-1);
            }
        });
        View btnInv = findViewById(R.id.btnFromInventory);
        btnInv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                regenerateFromInventory();
            }
        });
        View btnImport = findViewById(R.id.btnImport);
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importFile();
            }
        });
        Anim.pressScale(btnNew);
        Anim.pressScale(btnInv);
        Anim.pressScale(btnImport);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.notifyDataSetChanged();
        int n = BeadBrandCharts.customCount();
        tvCount.setText(n == 0 ? "空" : n + " 套");
    }

    // ---------------- 色板列表 ----------------

    private class PaletteAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return Math.max(1, BeadBrandCharts.customCount());
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(PaletteActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setElevation(dp(3));
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            row.setLayoutParams(lp);

            if (BeadBrandCharts.customCount() == 0) {
                TextView empty = new TextView(PaletteActivity.this);
                empty.setText("还没有自定义色板。\n\n点下面「➕ 新建」从零开始,「🎒 从豆仓生成」"
                        + "用手头有的豆子一键生成,或「📥 导入」别人分享的色板文件。");
                empty.setTextColor(0xFF4E4A46);
                empty.setTextSize(14);
                empty.setLineSpacing(dp(3), 1f);
                row.setPadding(dp(18), dp(24), dp(18), dp(24));
                row.addView(empty, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return row;
            }

            BeadBrandCharts.Chart c = BeadBrandCharts.customAt(position);

            // 前 12 色的小色块条,一眼看清配色
            LinearLayout strip = new LinearLayout(PaletteActivity.this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            int shown = Math.min(12, c.colors.size());
            for (int i = 0; i < shown; i++) {
                View sw = new View(PaletteActivity.this);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(0xFF000000 | c.colors.get(i).rgb);
                gd.setCornerRadius(dp(3));
                sw.setBackground(gd);
                LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(dp(13), dp(26));
                swp.rightMargin = dp(2);
                strip.addView(sw, swp);
            }
            if (c.colors.isEmpty()) {
                View sw = new View(PaletteActivity.this);
                sw.setBackgroundColor(0xFFDDDDDD);
                strip.addView(sw, new LinearLayout.LayoutParams(dp(13), dp(26)));
            }
            row.addView(strip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout texts = new LinearLayout(PaletteActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(12);
            texts.setLayoutParams(tlp);

            TextView name = new TextView(PaletteActivity.this);
            name.setText(c.name);
            name.setTextColor(0xFF1F2430);
            name.setTextSize(15);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setMaxLines(1);
            texts.addView(name);

            TextView sub = new TextView(PaletteActivity.this);
            String extra = CustomPalettes.inventoryIndex() == position ? " · 豆仓自动" : "";
            sub.setText(c.colors.size() + " 色" + extra + " · 点按管理");
            sub.setTextColor(0xFF8A857F);
            sub.setTextSize(12);
            texts.addView(sub);
            row.addView(texts);

            TextView chev = new TextView(PaletteActivity.this);
            chev.setText("›");
            chev.setTextColor(0xFF8A857F);
            chev.setTextSize(22);
            row.addView(chev);
            return row;
        }
    }

    private void showActions(final int idx) {
        final BeadBrandCharts.Chart c = BeadBrandCharts.customAt(idx);
        new AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(new String[]{
                        "✏️ 编辑颜色", "✍️ 重命名", "📤 导出 JSON", "🗑 删除"
                }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) showEditDialog(idx);
                        else if (which == 1) showRenameDialog(idx);
                        else if (which == 2) export(idx);
                        else confirmDelete(idx);
                    }
                })
                .show();
    }

    private void confirmDelete(final int idx) {
        final BeadBrandCharts.Chart c = BeadBrandCharts.customAt(idx);
        new AlertDialog.Builder(this)
                .setTitle("删除色板")
                .setMessage("确定删除「" + c.name + "」?" + (CustomPalettes.inventoryIndex() == idx
                        ? "\n这是豆仓生成的\"我的豆板\",删除后可再次生成。"
                        : ""))
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        CustomPalettes.remove(PaletteActivity.this, idx);
                        refresh();
                        Toast.makeText(PaletteActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRenameDialog(final int idx) {
        final BeadBrandCharts.Chart c = BeadBrandCharts.customAt(idx);
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(c.name);
        et.setSelection(et.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("重命名色板")
                .setView(et)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String name = et.getText().toString().trim();
                        if (name.isEmpty()) {
                            Toast.makeText(PaletteActivity.this,
                                    "名称不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        CustomPalettes.update(PaletteActivity.this, idx, name, c.colors);
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 色板编辑(增删改单色) ----------------

    /** 编辑一套色板;idx = -1 表示新建 */
    private void showEditDialog(final int idx) {
        final boolean isNew = idx < 0;
        final BeadBrandCharts.Chart src =
                isNew ? null : BeadBrandCharts.customAt(idx);
        final List<BeadColor> work = new ArrayList<>(
                src == null ? new ArrayList<BeadColor>() : src.colors);

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(8), p, 0);

        final EditText etName = new EditText(this);
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        etName.setHint("色板名称");
        etName.setText(src == null ? "我的色板" : src.name);
        etName.setSelection(etName.getText().length());
        box.addView(etName);

        final TextView hint = new TextView(this);
        hint.setText("点按颜色可改 RGB/名称/色号,长按删除:");
        hint.setTextColor(0xFF8A857F);
        hint.setTextSize(12);
        hint.setPadding(0, dp(8), 0, dp(4));
        box.addView(hint);

        final ColorRowAdapter colorAdapter = new ColorRowAdapter(work);
        final ListView lvColors = new ListView(this);
        lvColors.setDivider(null);
        lvColors.setDividerHeight(0);
        lvColors.setAdapter(colorAdapter);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(248));
        clp.topMargin = dp(2);
        box.addView(lvColors, clp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, dp(10), 0, 0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, dp(38), 1f);
        blp.rightMargin = dp(8);
        TextView btnAdd = chipButton("➕ 添加颜色");
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showColorDialog(work, -1, null, colorAdapter);
            }
        });
        btnRow.addView(btnAdd, blp);
        TextView btnSort = chipButton("🌈 按色相排序");
        btnSort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (work.size() < 2) return;
                com.pindou.app.bead.BeadPalettes.sortByHue(work);
                colorAdapter.notifyDataSetChanged();
            }
        });
        btnRow.addView(btnSort, new LinearLayout.LayoutParams(0, dp(38), 1f));
        box.addView(btnRow);

        new AlertDialog.Builder(this)
                .setTitle(isNew ? "新建色板" : "编辑色板")
                .setView(box)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (work.isEmpty()) {
                            Toast.makeText(PaletteActivity.this,
                                    "色板至少要有一种颜色", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String name = etName.getText().toString().trim();
                        if (name.isEmpty()) name = "我的色板";
                        List<BeadColor> fixed = new ArrayList<>(work.size());
                        for (int i = 0; i < work.size(); i++) {
                            BeadColor c = work.get(i);
                            fixed.add(new BeadColor(i + 1, c.name, c.rgb, c.tag));
                        }
                        if (isNew) {
                            CustomPalettes.add(PaletteActivity.this, name, fixed);
                        } else {
                            CustomPalettes.update(PaletteActivity.this, idx, name, fixed);
                        }
                        refresh();
                        Toast.makeText(PaletteActivity.this,
                                "已保存:" + name + "(" + fixed.size() + "色)",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class ColorRowAdapter extends BaseAdapter {

        private final List<BeadColor> colors;

        ColorRowAdapter(List<BeadColor> colors) {
            this.colors = colors;
        }

        @Override
        public int getCount() {
            return colors.size();
        }

        @Override
        public Object getItem(int position) {
            return colors.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final BeadColor c = colors.get(position);
            LinearLayout row = new LinearLayout(PaletteActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(5), dp(4), dp(5));

            View sw = new View(PaletteActivity.this);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(0xFF000000 | c.rgb);
            gd.setCornerRadius(dp(6));
            sw.setBackground(gd);
            row.addView(sw, new LinearLayout.LayoutParams(dp(30), dp(30)));

            TextView label = new TextView(PaletteActivity.this);
            String tagPart = c.tag.isEmpty() ? "" : " · " + c.tag;
            label.setText(c.name + tagPart + "  " + PaletteShare.toHex(c.rgb));
            label.setTextColor(0xFF1F2430);
            label.setTextSize(14);
            label.setMaxLines(1);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            llp.leftMargin = dp(10);
            row.addView(label, llp);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showColorDialog(colors, position, c, ColorRowAdapter.this);
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new AlertDialog.Builder(PaletteActivity.this)
                            .setTitle("删除颜色")
                            .setMessage("从色板移除「" + c.name + "」?")
                            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    colors.remove(position);
                                    notifyDataSetChanged();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    return true;
                }
            });
            return row;
        }
    }

    /**
     * 单色编辑:名称 + 色号 + RGB 滑杆/十六进制取色器。
     * rowIdx = -1 表示新增(追加到列表末尾)。
     */
    private void showColorDialog(final List<BeadColor> work, final int rowIdx,
                                 final BeadColor cur, final BaseAdapter notifier) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(8), p, 0);

        final EditText etName = new EditText(this);
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        etName.setHint("颜色名称");
        etName.setText(cur == null ? "" : cur.name);
        box.addView(etName);

        final EditText etTag = new EditText(this);
        etTag.setInputType(InputType.TYPE_CLASS_TEXT);
        etTag.setHint("色号(选填,如 S-47)");
        etTag.setText(cur == null ? "" : cur.tag);
        box.addView(etTag);

        final GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(dp(8));
        previewBg.setColor(cur == null ? 0xFF888888 : (0xFF000000 | cur.rgb));
        View preview = new View(this);
        preview.setBackground(previewBg);
        box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        final EditText etHex = new EditText(this);
        etHex.setInputType(InputType.TYPE_CLASS_TEXT);
        etHex.setMaxLines(1);
        etHex.setHint("#RRGGBB");
        etHex.setText(cur == null ? "#E3242B" : PaletteShare.toHex(cur.rgb));
        etHex.setTextSize(13);
        box.addView(etHex);

        final SeekBar[] bars = new SeekBar[3];
        final TextView[] vals = new TextView[3];
        final char[] ch = {'R', 'G', 'B'};
        int[] init = new int[3];
        int seed = cur == null ? 0xE3242B : cur.rgb;
        init[0] = (seed >> 16) & 0xFF;
        init[1] = (seed >> 8) & 0xFF;
        init[2] = seed & 0xFF;
        for (int i = 0; i < 3; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            TextView lab = new TextView(this);
            lab.setText(String.valueOf(ch[i]));
            lab.setTextColor(0xFF4E4A46);
            lab.setTextSize(13);
            lab.setTypeface(Typeface.DEFAULT_BOLD);
            r.addView(lab, new LinearLayout.LayoutParams(dp(24),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            bars[i] = new SeekBar(this);
            bars[i].setMax(255);
            bars[i].setProgress(init[i]);
            r.addView(bars[i], new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            vals[i] = new TextView(this);
            vals[i].setText(String.valueOf(init[i]));
            vals[i].setTextColor(0xFF8A857F);
            vals[i].setTextSize(12);
            vals[i].setGravity(Gravity.CENTER);
            r.addView(vals[i], new LinearLayout.LayoutParams(dp(34),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(6);
            box.addView(r, rlp);
        }

        // 预设色:点击直接填入
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout pre = new LinearLayout(this);
        pre.setOrientation(LinearLayout.HORIZONTAL);
        pre.setPadding(0, dp(10), 0, dp(4));
        for (int pc : PRESETS) {
            View sw = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(0xFF000000 | pc);
            gd.setCornerRadius(dp(6));
            sw.setBackground(gd);
            LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(dp(30), dp(30));
            swp.rightMargin = dp(8);
            sw.setLayoutParams(swp);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyRgb(bars, vals, etHex, previewBg, pc, true);
                }
            });
            pre.addView(sw);
        }
        hs.addView(pre);
        box.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 滑杆 -> 十六进制 + 预览;十六进制 -> 滑杆 + 预览
        SeekBar.OnSeekBarChangeListener seek = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                int rgb = 0xFF000000 | (bars[0].getProgress() << 16)
                        | (bars[1].getProgress() << 8) | bars[2].getProgress();
                applyRgb(bars, vals, etHex, previewBg, rgb & 0xFFFFFF, true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        };
        for (int i = 0; i < 3; i++) bars[i].setOnSeekBarChangeListener(seek);

        etHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                int rgb = PaletteShare.parseHexColor(s.toString());
                if (rgb >= 0) {
                    // 不回写 etHex,避免 setText 重入;只同步滑杆和预览
                    applyRgb(bars, vals, etHex, previewBg, rgb & 0xFFFFFF, false);
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(rowIdx < 0 ? "添加颜色" : "编辑颜色")
                .setView(box)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int rgb = PaletteShare.parseHexColor(etHex.getText().toString());
                        if (rgb < 0) {
                            Toast.makeText(PaletteActivity.this,
                                    "颜色值不合法,请填 #RRGGBB", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String name = etName.getText().toString().trim();
                        if (name.isEmpty()) {
                            name = rowIdx < 0
                                    ? "色" + (work.size() + 1)
                                    : "色" + (rowIdx + 1);
                        }
                        String tag = etTag.getText().toString().trim();
                        BeadColor c = new BeadColor(rowIdx < 0 ? work.size() + 1
                                : rowIdx + 1, name, rgb & 0xFFFFFF, tag);
                        if (rowIdx < 0) {
                            work.add(c);
                        } else {
                            work.set(rowIdx, c);
                        }
                        notifier.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 同步取色器各控件;updateHex 时把十六进制框也刷成该颜色 */
    private void applyRgb(SeekBar[] bars, TextView[] vals, EditText etHex,
                          GradientDrawable previewBg, int rgb, boolean updateHex) {
        bars[0].setProgress((rgb >> 16) & 0xFF);
        bars[1].setProgress((rgb >> 8) & 0xFF);
        bars[2].setProgress(rgb & 0xFF);
        vals[0].setText(String.valueOf((rgb >> 16) & 0xFF));
        vals[1].setText(String.valueOf((rgb >> 8) & 0xFF));
        vals[2].setText(String.valueOf(rgb & 0xFF));
        previewBg.setColor(0xFF000000 | rgb);
        if (updateHex) etHex.setText(PaletteShare.toHex(rgb));
    }

    private TextView chipButton(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setBackgroundResource(R.drawable.bg_chip);
        b.setClickable(true);
        b.setFocusable(true);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(getResources().getColor(R.color.text_chip));
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setElevation(dp(2));
        return b;
    }

    // ---------------- 豆仓重建 / 导入 / 导出 ----------------

    private void regenerateFromInventory() {
        int idx = CustomPalettes.regenerateInventory(this);
        if (idx < 0) {
            Toast.makeText(this, "豆仓还没有有货的颜色,先在编辑器的豆仓里登记数量",
                    Toast.LENGTH_LONG).show();
            return;
        }
        refresh();
        lv.smoothScrollToPosition(idx);
        Toast.makeText(this, "🎒 我的豆板已按豆仓库存重建:"
                + BeadBrandCharts.customAt(idx).colors.size() + " 色", Toast.LENGTH_SHORT).show();
    }

    private void export(final int idx) {
        try {
            BeadBrandCharts.Chart c = BeadBrandCharts.customAt(idx);
            String safe = c.name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
            if (safe.isEmpty()) safe = "palette";
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA)
                    .format(new Date());
            File out = new File(getCacheDir(), "拼豆色板_" + safe + "_" + stamp + ".json");
            JSONObject o = PaletteShare.build(c.name, c.colors);
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(o.toString().getBytes("UTF-8"));
            fos.close();
            share(AppFileProvider.forCacheShare(out));
        } catch (Exception e) {
            Toast.makeText(this, "导出失败:" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void share(Uri uri) {
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("application/json");
        send.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        send.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(android.content.Intent.createChooser(send, "分享色板文件"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void importFile() {
        try {
            android.content.Intent it =
                    new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(android.content.Intent.CATEGORY_OPENABLE);
            it.setType("*/*");
            startActivityForResult(it, REQ_IMPORT);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        try {
            java.io.InputStream is = getContentResolver().openInputStream(data.getData());
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            JSONObject o = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            BeadBrandCharts.Chart c = PaletteShare.parse(o);
            int idx = CustomPalettes.add(this, c.name, c.colors);
            refresh();
            lv.smoothScrollToPosition(idx);
            Toast.makeText(this, "已导入:" + c.name + "(" + c.colors.size() + "色)",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败:" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
