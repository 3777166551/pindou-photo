package com.pindou.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.util.GifEncoder;
import com.pindou.app.util.PatternShare;
import com.pindou.app.view.Play3DView;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 3D 把玩:全屏旋转/缩放自己的拼豆成品;进入自动播放「生长动画」
 * (豆豆按颜色分批从空中落成整幅),彩蛋是虚拟熨斗,还能把生长动画
 * 离线渲成 GIF 分享(v2.58,纯 Java GIF89a 编码器,零权限零网络)。
 * 纯代码构建 UI(DEV-NOTES 23);图纸经 pendingPlay3DJson 传入。
 */
public class Play3DActivity extends Activity implements Play3DView.Listener {

    private static final int REQ_EXPORT_GIF = 10;

    private Play3DView playView;
    private TextView modeChip;
    private TextView progressText;
    private boolean ironMode = false;
    private boolean exporting = false;
    private Uri exportTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playView = new Play3DView(this);
        playView.setListener(this);

        String json = EditorActivity.pendingPlay3DJson;
        EditorActivity.pendingPlay3DJson = null;
        BeadPattern pattern = null;
        try {
            if (json != null) {
                pattern = PatternShare.parse(new JSONObject(json));
            }
        } catch (Exception ignored) {
        }
        if (pattern == null) {
            Toast.makeText(this, getString(R.string.play_err_load),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        playView.setPattern(pattern);

        // 顶栏:退出 | 模式 | 重播 | GIF | 提示/进度
        float dm = getResources().getDisplayMetrics().density;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Math.round(8 * dm), Math.round(8 * dm),
                Math.round(8 * dm), Math.round(8 * dm));

        TextView exit = barChip(getString(R.string.play_exit), 0x33000000 | (getResources().getColor(R.color.line) & 0x00FFFFFF));
        exit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(exit);

        modeChip = barChip(modeLabel(), 0x33000000 | (getResources().getColor(R.color.line) & 0x00FFFFFF));
        modeChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (exporting) return;
                ironMode = !ironMode;
                modeChip.setText(modeLabel());
                GradientDrawable bg = (GradientDrawable) modeChip.getBackground();
                bg.setColor(ironMode ? 0xFFE85D75 : 0x33000000 | (getResources().getColor(R.color.line) & 0x00FFFFFF));
                playView.setIronMode(ironMode);
                progressText.setText(getString(ironMode
                        ? R.string.play_hint_iron : R.string.play_hint_hand));
            }
        });
        bar.addView(chipWithMargin(modeChip, dm));
        TextView replay = barChip(getString(R.string.play_replay), 0x33000000 | (getResources().getColor(R.color.line) & 0x00FFFFFF));
        replay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (exporting) return;
                playView.startBuildAnimation();
                progressText.setText(getString(R.string.play_hint_hand));
            }
        });
        bar.addView(chipWithMargin(replay, dm));

        TextView gif = barChip(getString(R.string.play_export_gif), 0x33000000 | (getResources().getColor(R.color.line) & 0x00FFFFFF));
        gif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (exporting) return;
                exportGif();
            }
        });
        bar.addView(chipWithMargin(gif, dm));

        progressText = new TextView(this);
        progressText.setText(getString(R.string.play_hint_hand));
        progressText.setTextColor(getColor(R.color.textSub));
        progressText.setTextSize(11);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = Math.round(8 * dm);
        progressText.setGravity(Gravity.END);
        progressText.setLayoutParams(tp);
        bar.addView(progressText);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.bg));
        root.addView(playView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));
        setContentView(root);
        com.pindou.app.util.Skin.apply(root);

        // 进门即高潮:生长动画自动来一遍
        playView.startBuildAnimation();
    }

    private TextView chipWithMargin(TextView chip, float dm) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.round(6 * dm);
        chip.setLayoutParams(lp);
        return chip;
    }

    private String modeLabel() {
        return getString(ironMode ? R.string.play_mode_hand : R.string.play_mode_iron);
    }

    private TextView barChip(String text, int bgColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF2A2735);
        tv.setGravity(Gravity.CENTER);
        int p = Math.round(10 * getResources().getDisplayMetrics().density);
        tv.setPadding(p, Math.round(6 * getResources().getDisplayMetrics().density),
                p, Math.round(6 * getResources().getDisplayMetrics().density));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(20 * getResources().getDisplayMetrics().density);
        tv.setBackground(bg);
        tv.setClickable(true);
        return tv;
    }

    // ---------------- GIF 导出 ----------------

    private void exportGif() {
        String name = "pindou_build_"
                + new java.text.SimpleDateFormat("yyyyMMdd_HHmm",
                        java.util.Locale.CHINA).format(new java.util.Date())
                + ".gif";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/gif");
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, REQ_EXPORT_GIF);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.play_gif_err) + "no picker",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT_GIF && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            exportTarget = data.getData();
            startGifExport();
        }
    }

    /**
     * 渲染在 UI 线程(view.draw 只能主线程),LZW 编码在后台线程,
     * 用一个小队列衔接:UI 每渲一帧就丢给后台,进度条同步走。
     */
    private void startGifExport() {
        int vw = playView.getWidth(), vh = playView.getHeight();
        if (vw <= 0 || vh <= 0) return;
        if (ironMode) {   // GIF 帧里不能有熨斗
            ironMode = false;
            modeChip.setText(modeLabel());
            GradientDrawable bg = (GradientDrawable) modeChip.getBackground();
            bg.setColor(0x33000000 | (getResources().getColor(R.color.line) & 0x00FFFFFF));
            playView.setIronMode(false);
        }
        exporting = true;
        playView.setFrozen(true);
        playView.setEnabled(false);
        playView.skipAnimation();

        // 长边压到 720,短边按屏幕比例
        int tw, th;
        if (vw >= vh) {
            tw = Math.min(720, vw);
            th = Math.round(vh * (tw / (float) vw));
        } else {
            th = Math.min(720, vh);
            tw = Math.round(vw * (th / (float) vh));
        }
        final long dur = playView.getAnimDuration();
        final int frames = Math.max(48, Math.min(160, (int) (dur / 90)));
        final int delayCs = (int) Math.max(2, Math.round(dur / (double) frames / 10));
        final int fw = tw, fh = th;

        final Bitmap bmp = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        canvas.scale(fw / (float) vw, fh / (float) vh);

        final BlockingQueue<int[]> queue = new ArrayBlockingQueue<int[]>(3);
        final int[] sentinel = new int[0];
        final OutputStream[] outHolder = new OutputStream[1];
        final java.util.concurrent.atomic.AtomicInteger done =
                new java.util.concurrent.atomic.AtomicInteger();

        // 后台:编码线程
        Thread enc = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream out = getContentResolver().openOutputStream(
                            exportTarget, "w");
                    outHolder[0] = out;
                    if (out == null) throw new Exception("cannot open target");
                    GifEncoder gif = new GifEncoder(fw, fh, delayCs, 0);
                    gif.start(out);
                    while (true) {
                        int[] buf = queue.take();
                        if (buf == sentinel) break;
                        gif.addFrame(buf);
                        int n = done.incrementAndGet();
                        if (n % 8 == 0) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressText.setText(getString(
                                            R.string.play_gif_progress,
                                            done.get() * 100 / frames));
                                }
                            });
                        }
                    }
                    gif.finish();
                    out.close();
                    if (!isFinishing()) {
                        Toast.makeText(Play3DActivity.this,
                                getString(R.string.play_gif_ok), Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    if (!isFinishing()) {
                        Toast.makeText(Play3DActivity.this,
                                getString(R.string.play_gif_err) + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                } finally {
                    try {
                        if (outHolder[0] != null) outHolder[0].close();
                    } catch (Exception ignored) {
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            exporting = false;
                            playView.setFrozen(false);
                            playView.setEnabled(true);
                        }
                    });
                }
            }
        });
        enc.start();

        // UI 线程:逐帧离屏渲染(view.draw 只能主线程)。
        // 每帧独立缓冲:队列里的帧还在编码时,UI 已经在渲下一帧,不能共用
        renderFrames(0, frames, dur, bmp, canvas, queue, sentinel, fw, fh);
    }

    private void renderFrames(final int i, final int frames, final long dur,
                              final Bitmap bmp, final Canvas canvas,
                              final BlockingQueue<int[]> queue,
                              final int[] sentinel, final int fw, final int fh) {
        if (isFinishing() || i >= frames) {
            try {
                queue.put(sentinel);
            } catch (InterruptedException ignored) {
            }
            return;
        }
        try {
            playView.setAnimTime(dur * i / (frames - 1L));
            bmp.eraseColor(getColor(R.color.bg));
            playView.draw(canvas);
            int[] buf = new int[fw * fh];
            bmp.getPixels(buf, 0, fw, 0, 0, fw, fh);
            queue.put(buf);   // 编码快于渲染,通常不阻塞
        } catch (Exception e) {
            try {
                queue.put(sentinel);
            } catch (InterruptedException ignored) {
            }
            return;
        }
        if (i == frames - 1) {
            try {
                queue.put(sentinel);
            } catch (InterruptedException ignored) {
            }
            return;
        }
        playView.postDelayed(new Runnable() {
            @Override
            public void run() {
                renderFrames(i + 1, frames, dur, bmp, canvas, queue, sentinel,
                        fw, fh);
            }
        }, 0);
    }

    // ---------------- 熨烫进度回调 ----------------

    @Override
    public void onIronProgress(int done, int total) {
        final String msg = getString(R.string.play_iron_progress_fmt, done, total);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressText.setText(msg);
            }
        });
    }

    @Override
    public void onIronComplete() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressText.setText(getString(R.string.play_iron_done));
                Toast.makeText(Play3DActivity.this,
                        getString(R.string.play_iron_done), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.enter_undim, R.anim.exit_down);
    }
}
