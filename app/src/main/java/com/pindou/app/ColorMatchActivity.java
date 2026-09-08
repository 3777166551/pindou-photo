package com.pindou.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadBrandCharts;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadInventory;
import com.pindou.app.bead.ColorMath;
import com.pindou.app.util.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 拍照对色(v2.42):拍一张手头豆子的照片,点一下豆子,
 * 用 CIEDE2000 在全部品牌色号表里找最接近的色号,可一键登记进豆仓。
 * 纯本地计算,无网络。
 */
public class ColorMatchActivity extends Activity {

    private static final int REQ_PHOTO = 100;
    /** 采样半径:点按位置的 1/60 短边,至少 3px */
    private static final int ROWS = 5;

    private Bitmap photo;
    private ImageView iv;
    private LinearLayout llResults;
    private TextView tvHint;

    /** 一个品牌的最接近色 */
    private static final class Match {
        final String brand;
        final BeadColor color;
        final double deltaE;

        Match(String brand, BeadColor color, double deltaE) {
            this.brand = brand;
            this.color = color;
            this.deltaE = deltaE;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_match);
        iv = findViewById(R.id.ivMatch);
        llResults = findViewById(R.id.llMatchResults);
        tvHint = findViewById(R.id.tvMatchHint);
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btnMatchPick).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickPhoto();
            }
        });
        iv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getAction() != android.view.MotionEvent.ACTION_UP) return true;
                if (photo == null) {
                    pickPhoto();
                    return true;
                }
                toBitmapPoint(e.getX(), e.getY());
                return true;
            }
        });
    }

    private void pickPhoto() {
        try {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("image/*");
            startActivityForResult(it, REQ_PHOTO);
        } catch (Throwable t) {
            Toast.makeText(this, getString(R.string.err_no_picker), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PHOTO || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        Toast.makeText(this, getString(R.string.match_loading), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    bmp = ImageLoader.load(getContentResolver(), uri, 1200);
                    bmp = ImageLoader.fixExif(getContentResolver(), uri, bmp);
                } catch (Exception ignored) {
                }
                final Bitmap out = bmp;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (out == null) {
                            Toast.makeText(ColorMatchActivity.this,
                                    getString(R.string.err_photo_read), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        photo = out;
                        iv.setImageBitmap(out);
                        tvHint.setText(R.string.match_hint_tap);
                        llResults.removeAllViews();
                    }
                });
            }
        }).start();
    }

    /** 视图坐标 -> 位图像素坐标(ImageView fitCenter 的逆矩阵),命中后立即匹配 */
    private void toBitmapPoint(float vx, float vy) {
        float[] pt = {vx, vy};
        android.graphics.Matrix inv = new android.graphics.Matrix();
        iv.getImageMatrix().invert(inv);
        inv.mapPoints(pt);
        int x = Math.round(pt[0]);
        int y = Math.round(pt[1]);
        if (x < 0 || y < 0 || x >= photo.getWidth() || y >= photo.getHeight()) return;
        matchAt(x, y);
    }

    /** 取点周围小邻域的平均色,再做全色号 CIEDE2000 匹配 */
    private void matchAt(int px, int py) {
        int r = Math.max(3, Math.min(photo.getWidth(), photo.getHeight()) / 60);
        long rs = 0, gs = 0, bs = 0, cnt = 0;
        for (int y = Math.max(0, py - r); y <= Math.min(photo.getHeight() - 1, py + r); y++) {
            for (int x = Math.max(0, px - r); x <= Math.min(photo.getWidth() - 1, px + r); x++) {
                int c = photo.getPixel(x, y);
                if (Color.alpha(c) < 200) continue;
                rs += Color.red(c);
                gs += Color.green(c);
                bs += Color.blue(c);
                cnt++;
            }
        }
        if (cnt == 0) {
            Toast.makeText(this, getString(R.string.match_retry), Toast.LENGTH_SHORT).show();
            return;
        }
        int rgb = Color.rgb((int) (rs / cnt), (int) (gs / cnt), (int) (bs / cnt));
        showMatches(rgb);
    }

    /** 每个品牌色号表取最近的一色,按 ΔE2000 从小到大排 */
    private List<Match> match(int rgb) {
        double[] lab = ColorMath.rgbToLab(0xFF000000 | rgb);
        List<Match> out = new ArrayList<>();
        for (BeadBrandCharts.Chart chart : BeadBrandCharts.ALL) {
            BeadColor best = null;
            double bestDe = Double.MAX_VALUE;
            for (BeadColor bc : chart.colors) {
                double de = ColorMath.deltaE2000(lab,
                        ColorMath.rgbToLab(0xFF000000 | bc.rgb));
                if (de < bestDe) {
                    bestDe = de;
                    best = bc;
                }
            }
            if (best != null) out.add(new Match(chart.name, best, bestDe));
        }
        Collections.sort(out, new java.util.Comparator<Match>() {
            @Override
            public int compare(Match a, Match b) {
                return Double.compare(a.deltaE, b.deltaE);
            }
        });
        return out.size() > ROWS ? out.subList(0, ROWS) : out;
    }

    private void showMatches(int rgb) {
        llResults.removeAllViews();
        // 你点的颜色
        TextView picked = resultLabel(String.format(Locale.getDefault(),
                getString(R.string.match_picked_fmt), colorHex(rgb)));
        llResults.addView(picked);
        List<Match> matches = match(rgb);
        float dm = getResources().getDisplayMetrics().density;
        for (final Match m : matches) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setElevation(dp(3));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(8);
            row.setLayoutParams(rlp);

            View sw = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(0xFF000000 | m.color.rgb);
            gd.setStroke(dp(2), 0xFF40354E);
            sw.setBackground(gd);
            row.addView(sw, new LinearLayout.LayoutParams(dp(30), dp(30)));

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(dp(12), 0, 0, 0);
            TextView l1 = new TextView(this);
            l1.setText(String.format(Locale.getDefault(), "%s %s%s",
                    m.brand, String.valueOf(m.color.code),
                    BeadColor.codeSuffix) + " · " + m.color.name);
            l1.setTextColor(0xFF3A3050);
            l1.setTextSize(14);
            l1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            TextView l2 = new TextView(this);
            l2.setText(String.format(Locale.getDefault(),
                    getString(R.string.match_de_fmt), m.deltaE));
            l2.setTextColor(0xFF9A8FA6);
            l2.setTextSize(12);
            textCol.addView(l1);
            textCol.addView(l2);
            row.addView(textCol, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView reg = new TextView(this);
            reg.setText(R.string.match_register);
            reg.setTextSize(12);
            reg.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            reg.setBackgroundResource(R.drawable.bg_chip);
            reg.setElevation(dp(2));
            reg.setPadding(dp(12), dp(8), dp(12), dp(8));
            reg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askRegister(m);
                }
            });
            row.addView(reg, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            llResults.addView(row);
        }
    }

    /** 登记进豆仓:弹数量输入,默认 50 颗 */
    private void askRegister(final Match m) {
        final EditText et = new EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText("50");
        et.setSelection(et.getText().length());
        new android.app.AlertDialog.Builder(this)
                .setTitle(String.format(Locale.getDefault(),
                        getString(R.string.match_register_title), m.brand,
                        String.valueOf(m.color.code), BeadColor.codeSuffix))
                .setView(et)
                .setPositiveButton(R.string.btn_ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        int count = 50;
                        try {
                            count = Math.max(0, Integer.parseInt(
                                    et.getText().toString().trim()));
                        } catch (Exception ignored) {
                        }
                        BeadInventory.set(ColorMatchActivity.this, m.color.rgb, count);
                        Toast.makeText(ColorMatchActivity.this,
                                getString(R.string.match_registered), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private TextView resultLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF3A3050);
        tv.setTextSize(13);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(4), dp(2), dp(4), dp(2));
        return tv;
    }

    private static String colorHex(int rgb) {
        return String.format(Locale.getDefault(), "#%02X%02X%02X",
                Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
