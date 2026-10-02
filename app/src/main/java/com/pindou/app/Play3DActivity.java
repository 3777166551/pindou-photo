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
import java.util.concurrent.TimeUnit;

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
    /** GIF 编码线程引用:onDestroy 打断用(编码中途退出页面时 take 永久阻塞) */
    private volatile Thread gifEncThread;

    // GIF 导出的帧渲染资源(仅导出期间非空,restorePlayLayout 释放):
    // 视口位图(屏幕比例,长边≤1440)→ 缩到成帧位图(长边 720)→ 交编码线程
    private Bitmap gifViewBmp;
    private Canvas gifViewCanvas;
    private Bitmap gifFrameBmp;
    private Canvas gifFrameCanvas;
    private android.graphics.RectF gifDst;
    private android.graphics.Paint gifScalePaint;
    private int gifRw, gifRh, gifFw, gifFh;

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
                && data != null && data.getData() != null && !isFinishing()) {
            exportTarget = data.getData();
            startGifExport();
        }
    }

    /**
     * 渲染在 UI 线程(view.draw 只能主线程),LZW 编码在后台线程,
     * 用一个小队列衔接:UI 每渲一帧就丢给后台,进度条同步走。
     */
    private void startGifExport() {
        if (isFinishing()) return;
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

        // 帧几何不取 playView 的布局宽高——真机上从 SAF 选择器返回时该值
        // 不可信(实测拿到 100×208 且帧全空:view 布局态一错,onDraw 早退/
        // 投影比例全歪)。改两级定尺寸:渲染视口=屏幕尺寸(导出期间屏幕
        // 观感不变),每帧渲完再缩到长边 720 的成帧位图;渲染前后都把 view
        // 显式 measure+layout 钉住(见 renderFrames)。
        android.util.DisplayMetrics dmi = getResources().getDisplayMetrics();
        int rw = Math.max(1, dmi.widthPixels), rh = Math.max(1, dmi.heightPixels);
        int tw, th;
        if (rw >= rh) {
            tw = Math.min(720, rw);
            th = Math.max(1, Math.round(rh * (tw / (float) rw)));
        } else {
            th = Math.min(720, rh);
            tw = Math.max(1, Math.round(rw * (th / (float) rh)));
        }
        final long dur = playView.getAnimDuration();
        final int frames = Math.max(48, Math.min(160, (int) (dur / 90)));
        final int delayCs = (int) Math.max(2, Math.round(dur / (double) frames / 10));
        final int fw = tw, fh = th;

        gifViewBmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888);
        gifViewCanvas = new Canvas(gifViewBmp);
        gifFrameBmp = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
        gifFrameCanvas = new Canvas(gifFrameBmp);
        gifDst = new android.graphics.RectF(0, 0, fw, fh);
        gifScalePaint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
                        | android.graphics.Paint.FILTER_BITMAP_FLAG);
        gifRw = rw;
        gifRh = rh;
        gifFw = fw;
        gifFh = fh;

        final BlockingQueue<int[]> queue = new ArrayBlockingQueue<int[]>(3);
        final int[] sentinel = new int[0];
        final OutputStream[] outHolder = new OutputStream[1];
        final java.util.concurrent.atomic.AtomicInteger done =
                new java.util.concurrent.atomic.AtomicInteger();

        // 后台:编码线程
        Thread enc = new Thread(new Runnable() {
            @Override
            public void run() {
                gifEncThread = Thread.currentThread();
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
                    // 编码线程死亡后 UI 还在往队列投帧:清空队列解堵,
                    // UI 侧的超时 offer 才能探测到"无人消费"并终止
                    queue.clear();
                    queue.offer(sentinel);
                    if (!isFinishing()) {
                        Toast.makeText(Play3DActivity.this,
                                getString(R.string.play_gif_err) + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                } finally {
                    gifEncThread = null;
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
        renderFrames(0, frames, dur, queue, sentinel);
    }

    private void renderFrames(final int i, final int frames, final long dur,
                              final BlockingQueue<int[]> queue,
                              final int[] sentinel) {
        if (isFinishing() || i >= frames) {
            queue.offer(sentinel);
            restorePlayLayout();
            return;
        }
        try {
            // 把 view 显式钉到渲染视口尺寸再画,不依赖它当时的布局态;
            // 导出中进度文本刷新可能触发父布局重排,每帧前重新钉一遍
            playView.measure(
                    View.MeasureSpec.makeMeasureSpec(gifRw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(gifRh, View.MeasureSpec.EXACTLY));
            playView.layout(0, 0, gifRw, gifRh);
            playView.setAnimTime(dur * i / (frames - 1L));
            gifViewBmp.eraseColor(getColor(R.color.bg));
            playView.draw(gifViewCanvas);
            gifFrameCanvas.drawBitmap(gifViewBmp, null, gifDst, gifScalePaint);
            int[] buf = new int[gifFw * gifFh];
            gifFrameBmp.getPixels(buf, 0, gifFw, 0, 0, gifFw, gifFh);
            // 编码线程死亡后队列无人消费,put 会永久阻塞主线程(冻屏):
            // 带超时的 offer,超时即判编码线程已死,抛给下面的 catch 收场
            if (!queue.offer(buf, 5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("gif encoder not consuming");
            }
        } catch (Exception e) {
            queue.offer(sentinel);
            restorePlayLayout();
            return;
        }
        if (i == frames - 1) {
            queue.offer(sentinel);
            restorePlayLayout();
            return;
        }
        playView.postDelayed(new Runnable() {
            @Override
            public void run() {
                renderFrames(i + 1, frames, dur, queue, sentinel);
            }
        }, 0);
    }

    /**
     * 导出把 view 钉在了渲染视口尺寸,结束后让父布局把它排回全屏,
     * 并释放导出期的两张帧位图。
     */
    private void restorePlayLayout() {
        playView.forceLayout();
        View p = (View) playView.getParent();
        if (p != null) p.requestLayout();
        playView.invalidate();
        gifViewBmp = null;
        gifViewCanvas = null;
        gifFrameBmp = null;
        gifFrameCanvas = null;
        gifDst = null;
        gifScalePaint = null;
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
    public void onIronAutoFinish() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressText.setText(getString(R.string.play_iron_auto));
            }
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.enter_undim, R.anim.exit_down);
    }

    @Override
    protected void onDestroy() {
        // 导出中途退出:打断阻塞在队列 take() 上的编码线程,
        // 其 catch/finally 会关输出流,否则线程+文件描述符双泄漏
        Thread t = gifEncThread;
        if (t != null) t.interrupt();
        super.onDestroy();
    }
}
