package com.pindou.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Random;

/**
 * 开屏拼豆动画(纯 Canvas,零依赖):
 * 1. 一颗拼豆从天上弹跳落下(3D 感:高光 + 渐变厚度描边),砸在拼板上;
 * 2. 弹起的一瞬把周围的豆一颗颗"弹"进各自的孔(依次翻滚落位,类瀑布流);
 * 3. 豆全部落位后整板轻微果冻抖动,logo 以 pop 贴纸感登场;
 * 4. 停留半秒进主界面。全程约 1.6 秒,可点任意处跳过。
 * 二次元/3D 感来自:overshoot 弹性曲线、豆子的厚度描边+镜面高光、落位时的
 * squash & stretch(压扁回弹),这是动画十二法则里最出"3D 感"的三件套。
 */
public class SplashActivity extends Activity {

    /** 落下的拼豆颜色(粉彩系:薄荷/薰衣草/蜜桃/天蓝/柠黄/樱粉) */
    private static final int[] BEAD_COLORS = {
            0xFF34C08B, 0xFF9B8CF2, 0xFFFF9A62, 0xFF4FA8F5, 0xFFFFD166, 0xFFFF7B9C
    };

    private static final int COLS = 7;
    private static final int ROWS = 5;

    private PegBoardView board;
    private boolean skipped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        board = new PegBoardView();
        setContentView(board);

        // 点任意处跳过
        board.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skipped = true;
                goMain();
            }
        });

        startShow();
    }

    private void startShow() {
        // 阶段1:领头豆落下(bounce)
        ValueAnimator drop = ValueAnimator.ofFloat(0f, 1f);
        drop.setDuration(520);
        drop.setInterpolator(new BounceInterpolator());
        drop.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                board.leaderT = (Float) a.getAnimatedValue();
                board.invalidate();
            }
        });

        // 阶段2:其余豆依次落位(交错启动 + overshoot)
        ValueAnimator fill = ValueAnimator.ofFloat(0f, 1f);
        fill.setDuration(760);
        fill.setInterpolator(new DecelerateInterpolator(1.6f));
        fill.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                board.fillT = (Float) a.getAnimatedValue();
                board.invalidate();
            }
        });

        // 阶段3:果冻抖动 + logo 弹出
        ValueAnimator jelly = ValueAnimator.ofFloat(0f, 1f);
        jelly.setDuration(360);
        jelly.setInterpolator(new OvershootInterpolator(2.2f));
        jelly.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                board.jellyT = (Float) a.getAnimatedValue();
                board.invalidate();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(drop, fill, jelly);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                board.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!skipped) goMain();
                    }
                }, 520);
            }
        });
        set.start();
    }

    private void goMain() {
        if (isFinishing()) return;
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** 拼板动画画布 */
    private class PegBoardView extends View {

        float leaderT;   // 领头豆下落进度 0..1
        float fillT;     // 其余豆落位进度 0..1
        float jellyT;    // 果冻抖动进度 0..1

        final Paint pegPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint beadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random rnd = new Random(42);
        /** 每格的固定随机延迟与颜色(保证每次进 APP 摆设一致) */
        final float[] delays = new float[COLS * ROWS];
        final int[] colors = new int[COLS * ROWS];

        PegBoardView() {
            super(SplashActivity.this);
            setBackgroundColor(0xFFF6F3EE);
            setClickable(true);
            for (int i = 0; i < delays.length; i++) {
                delays[i] = rnd.nextFloat();
                colors[i] = BEAD_COLORS[rnd.nextInt(BEAD_COLORS.length)];
            }
            // 领头豆固定是主题珊瑚橘
            colors[(ROWS / 2) * COLS + COLS / 2] = BEAD_COLORS[0];
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cell = Math.min(w, h * 1.4f) / (COLS + 2);
            float boardW = COLS * cell;
            float boardH = ROWS * cell;
            float ox = (w - boardW) / 2f;
            float oy = (h - boardH) / 2f - h * 0.04f;

            // 拼板底
            boardPaint.setColor(0xFFFFFFFF);
            float r = cell * 0.5f;
            canvas.drawRoundRect(ox - r, oy - r, ox + boardW + r, oy + boardH + r,
                    r * 1.6f, r * 1.6f, boardPaint);
            // 孔
            pegPaint.setColor(0xFFE8E6E1);
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) {
                    canvas.drawCircle(ox + (x + 0.5f) * cell, oy + (y + 0.5f) * cell,
                            cell * 0.09f, pegPaint);
                }
            }

            // 果冻抖动:整体轻微 squash & stretch
            float sx = 1f + jellyT * 0.05f * (float) Math.sin(jellyT * Math.PI * 3);
            float sy = 1f - jellyT * 0.05f * (float) Math.sin(jellyT * Math.PI * 3);
            canvas.save();
            canvas.translate(ox + boardW / 2f, oy + boardH / 2f);
            canvas.scale(sx, sy);
            canvas.translate(-(ox + boardW / 2f), -(oy + boardH / 2f));

            boolean leader = leaderT < 1f;
            int mid = (ROWS / 2) * COLS + COLS / 2;
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) {
                    int i = y * COLS + x;
                    float cx = ox + (x + 0.5f) * cell;
                    float cy = oy + (y + 0.5f) * cell;
                    float radius = cell * 0.46f;

                    if (i == mid) {
                        // 领头豆:bounce 下落
                        if (leaderT <= 0f) continue;
                        float bounce = bounceCurve(leaderT);
                        float dy = (1f - bounce) * -h * 0.75f;
                        drawBead(canvas, cx, cy + dy, radius, colors[i],
                                leader ? 1f : 1f, 1f);
                    } else {
                        // 其余豆:按随机延迟依次落位,带压扁回弹
                        float delay = delays[i] * 0.55f;
                        float t = (fillT - delay) / 0.45f;
                        if (t <= 0f) continue;
                        t = Math.min(1f, t);
                        float dy = (1f - t) * -h * 0.5f;
                        // 落位瞬间的 squash & stretch:落地压扁再回弹
                        float squash = (float) Math.sin(t * Math.PI);
                        drawBead(canvas, cx, cy + dy, radius, colors[i],
                                1f + squash * 0.12f, 1f - squash * 0.12f);
                    }
                }
            }
            canvas.restore();

            // 标题贴纸弹出(jelly 阶段)
            if (jellyT > 0f) {
                Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
                tp.setColor(0xFF1F2430);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(w * 0.085f);
                tp.setFakeBoldText(true);
                float pop = overshoot(jellyT);
                canvas.save();
                canvas.translate(w / 2f, oy + boardH + h * 0.1f);
                canvas.scale(pop, pop);
                canvas.drawText("照片变拼豆", 0, 0, tp);
                tp.setTextSize(w * 0.036f);
                tp.setColor(0xFF8A8F98);
                canvas.drawText("· 拼出你的快乐 ·", 0, w * 0.06f, tp);
                canvas.restore();
            }
        }

        /** 3D 感拼豆:底色 + 厚度描边 + 镜面高光 */
        private void drawBead(Canvas c, float cx, float cy, float r,
                              int color, float sx, float sy) {
            c.save();
            c.translate(cx, cy);
            c.scale(sx, sy);
            beadPaint.setColor(color);
            c.drawCircle(0, 0, r, beadPaint);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(r * 0.16f);
            ringPaint.setColor(0x29000000);   // 极淡描边,柔和现代
            c.drawCircle(0, 0, r - ringPaint.getStrokeWidth() / 2f, ringPaint);
            if (r > dp(6)) {
                glossPaint.setColor(0x66FFFFFF);
                c.drawCircle(-r * 0.32f, -r * 0.34f, r * 0.26f, glossPaint);
            }
            c.restore();
        }

        /** 标准弹跳曲线(与 BounceInterpolator 观感一致) */
        private float bounceCurve(float t) {
            t = Math.min(1f, Math.max(0f, t));
            if (t < 0.364f) return 7.5625f * t * t;
            if (t < 0.727f) {
                t -= 0.546f;
                return 7.5625f * t * t + 0.75f;
            }
            t -= 0.8636f;
            return 7.5625f * t * t + 0.9375f;
        }

        /** overshoot 弹出曲线 */
        private float overshoot(float t) {
            t = Math.min(1f, Math.max(0f, t));
            float s = 1.70158f * 1.5f;
            t -= 1f;
            return t * t * ((s + 1) * t + s) + 1f;
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }
    }
}
