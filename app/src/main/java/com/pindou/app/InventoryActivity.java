package com.pindou.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import com.pindou.app.bead.BeadInventory;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPalettes;
import com.pindou.app.util.Anim;
import com.pindou.app.util.L10n;
import com.pindou.app.util.PaletteShare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 豆仓库存独立管理页(首页即可进入,不必先进编辑器):
 * 逐色登记/修改手头数量、长按删除、添加新颜色(RGB 取色器)。
 * 数据仍存 BeadInventory(files/inventory.json),编辑器内的
 * 豆仓缺口/替代建议/「我的豆板」直接共享。
 */
public class InventoryActivity extends Activity {

    /** 取色器预设色(与 PaletteActivity 一致) */
    private static final int[] PRESETS = {
            0xFFFFFF, 0x141414, 0xA8A8A8, 0x4A4A4A, 0xE3242B, 0x9C1C1C,
            0xF48FB1, 0xE4007C, 0xF57C00, 0xF7E01E, 0x43A047, 0x1B5E20,
            0x26A69A, 0x42A5F5, 0x1E5AA8, 0x7B3FA0, 0x795548, 0xC8A17B,
            0x0D2C6B, 0x90CAF9
    };

    private ListView lv;
    private TextView tvCount;
    private InvAdapter adapter;

    /** 当前展示的颜色(ARGB,按色相排序) */
    private final List<Integer> colors = new ArrayList<>();
    /** 每种颜色的数量草稿(rgb -> 文本),点「保存」统一写盘 */
    private final Map<Integer, String> drafts = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        L10n.apply(this);

        tvCount = findViewById(R.id.tvCount);
        lv = findViewById(R.id.lvInventory);
        adapter = new InvAdapter();
        lv.setAdapter(adapter);
        lv.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent,
                                           View view, int position, long id) {
                if (position < colors.size()) confirmDelete(colors.get(position));
                return true;
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        View btnSave = findViewById(R.id.btnInvSave);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAll();
            }
        });
        View btnAdd = findViewById(R.id.btnInvAdd);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddDialog();
            }
        });
        Anim.pressScale(btnSave);
        Anim.pressScale(btnAdd);
        reload();
    }

    private void reload() {
        List<Integer> all = BeadInventory.allColors(this);
        List<BeadColor> wrapped = new ArrayList<>(all.size());
        for (int rgb : all) {
            wrapped.add(new BeadColor(1, PaletteShare.toHex(rgb), rgb & 0xFFFFFF));
        }
        BeadPalettes.sortByHue(wrapped);
        colors.clear();
        for (BeadColor c : wrapped) {
            colors.add(0xFF000000 | c.rgb);
        }
        drafts.clear();
        for (int rgb : colors) {
            drafts.put(rgb, String.valueOf(BeadInventory.get(this, rgb)));
        }
        adapter.notifyDataSetChanged();
        tvCount.setText(colors.size() == 0
                ? getString(R.string.palette_count_none)
                : getString(R.string.palette_count_fmt, colors.size()));
    }

    private void saveAll() {
        for (int rgb : colors) {
            String t = drafts.containsKey(rgb) ? drafts.get(rgb).trim() : "";
            if (t.isEmpty()) continue;
            try {
                BeadInventory.set(this, rgb, Integer.parseInt(t));
            } catch (NumberFormatException ignored) {
            }
        }
        Toast.makeText(this, getString(R.string.inv_saved), Toast.LENGTH_SHORT).show();
    }

    private void confirmDelete(final int rgb) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_delete))
                .setMessage(getString(R.string.fmt_del_color,
                        PaletteShare.toHex(rgb)))
                .setPositiveButton(getString(R.string.btn_delete),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                BeadInventory.remove(InventoryActivity.this, rgb);
                                reload();
                                Toast.makeText(InventoryActivity.this,
                                        getString(R.string.deleted), Toast.LENGTH_SHORT).show();
                            }
                        })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private class InvAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return Math.max(1, colors.size());
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
            if (colors.isEmpty()) {
                TextView empty = new TextView(InventoryActivity.this);
                empty.setText(getString(R.string.inv_empty_home));
                empty.setTextColor(0xFF4E4A46);
                empty.setTextSize(14);
                empty.setLineSpacing(dp(3), 1f);
                LinearLayout row = new LinearLayout(InventoryActivity.this);
                row.setBackgroundResource(R.drawable.bg_card);
                row.setElevation(dp(3));
                row.setPadding(dp(16), dp(20), dp(16), dp(20));
                row.addView(empty, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return row;
            }

            final int rgb = colors.get(position);
            LinearLayout row = new LinearLayout(InventoryActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setElevation(dp(2));
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);

            View sw = new View(InventoryActivity.this);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(0xFF000000 | rgb);
            gd.setCornerRadius(dp(6));
            sw.setBackground(gd);
            row.addView(sw, new LinearLayout.LayoutParams(dp(30), dp(30)));

            TextView label = new TextView(InventoryActivity.this);
            label.setText(PaletteShare.toHex(rgb));
            label.setTextColor(0xFF1F2430);
            label.setTextSize(14);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            llp.leftMargin = dp(10);
            row.addView(label, llp);

            TextView cntLabel = new TextView(InventoryActivity.this);
            cntLabel.setText(getString(R.string.inv_count_hint));
            cntLabel.setTextColor(0xFF8A857F);
            cntLabel.setTextSize(11);
            row.addView(cntLabel);

            final EditText et = new EditText(InventoryActivity.this);
            et.setInputType(InputType.TYPE_CLASS_NUMBER);
            et.setMinLines(1);
            et.setMaxLines(1);
            et.setTextSize(14);
            et.setText(drafts.containsKey(rgb) ? drafts.get(rgb) : "0");
            et.setTag(rgb);
            et.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    Object tag = et.getTag();
                    if (tag instanceof Integer) {
                        drafts.put((Integer) tag, s.toString());
                    }
                }
            });
            et.setLayoutParams(new LinearLayout.LayoutParams(dp(84),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(et);
            return row;
        }
    }

    // ---------------- 添加颜色(RGB 取色器) ----------------

    private void showAddDialog() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, dp(8), p, 0);

        final GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(dp(8));
        previewBg.setColor(0xFFE3242B);
        View preview = new View(this);
        preview.setBackground(previewBg);
        box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

        final EditText etHex = new EditText(this);
        etHex.setInputType(InputType.TYPE_CLASS_TEXT);
        etHex.setMaxLines(1);
        etHex.setHint("#RRGGBB");
        etHex.setText("#E3242B");
        etHex.setTextSize(13);
        box.addView(etHex);

        final EditText etCount = new EditText(this);
        etCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCount.setMaxLines(1);
        etCount.setHint(getString(R.string.inv_count_hint));
        etCount.setText("100");
        box.addView(etCount);

        final SeekBar[] bars = new SeekBar[3];
        final TextView[] vals = new TextView[3];
        final char[] ch = {'R', 'G', 'B'};
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
            bars[i].setProgress(i == 0 ? 0xE3 : (i == 1 ? 0x24 : 0x2B));
            r.addView(bars[i], new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            vals[i] = new TextView(this);
            vals[i].setText(String.valueOf(bars[i].getProgress()));
            vals[i].setTextColor(0xFF8A857F);
            vals[i].setTextSize(12);
            vals[i].setGravity(Gravity.CENTER);
            r.addView(vals[i], new LinearLayout.LayoutParams(dp(34),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(6);
            box.addView(r, rlp);
        }

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout pre = new LinearLayout(this);
        pre.setOrientation(LinearLayout.HORIZONTAL);
        pre.setPadding(0, dp(10), 0, dp(4));
        for (final int pc : PRESETS) {
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

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
        for (int i = 0; i < 3; i++) {
            bars[i].setOnSeekBarChangeListener(seek);
        }

        etHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                int rgb = PaletteShare.parseHexColor(s.toString());
                if (rgb >= 0) {
                    applyRgb(bars, vals, etHex, previewBg, rgb & 0xFFFFFF, false);
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.btn_add_color))
                .setView(box)
                .setPositiveButton(getString(R.string.btn_ok),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                int rgb = PaletteShare.parseHexColor(
                                        etHex.getText().toString());
                                if (rgb < 0) {
                                    Toast.makeText(InventoryActivity.this,
                                            getString(R.string.err_bad_hex),
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                int count = 0;
                                try {
                                    count = Integer.parseInt(etCount.getText().toString().trim());
                                } catch (NumberFormatException ignored) {
                                }
                                BeadInventory.set(InventoryActivity.this,
                                        rgb & 0xFFFFFF, Math.max(0, count));
                                reload();
                                lv.smoothScrollToPosition(0);
                                Toast.makeText(InventoryActivity.this,
                                        getString(R.string.inv_added), Toast.LENGTH_SHORT).show();
                            }
                        })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void applyRgb(SeekBar[] bars, TextView[] vals, EditText etHex,
                          GradientDrawable previewBg, int rgb, boolean updateHex) {
        bars[0].setProgress((rgb >> 16) & 0xFF);
        bars[1].setProgress((rgb >> 8) & 0xFF);
        bars[2].setProgress(rgb & 0xFF);
        vals[0].setText(String.valueOf((rgb >> 16) & 0xFF));
        vals[1].setText(String.valueOf((rgb >> 8) & 0xFF));
        vals[2].setText(String.valueOf(rgb & 0xFF));
        previewBg.setColor(0xFF000000 | rgb);
        if (updateHex) {
            etHex.setText(PaletteShare.toHex(rgb));
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
