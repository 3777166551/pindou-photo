package com.pindou.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Random;

/**
 * 开屏动画 v2.39「贴纸拍击」(纯 Canvas,零依赖,约 1.6s,点任意处跳过):
 * 1. 豆雨从四面八方飞入拼板(每颗独立起角/延迟/弧线,落位 squash 回弹);
 * 2. 白色墨描边贴纸 logo 卡"啪"地拍下(大→小过冲 + 微旋转,与贴纸卡同语言);
 * 3. 拍击瞬间冲击环扩散 + 糖果纸屑迸开;
 * 4. 高光斜扫贴纸,停留后淡入主界面。
 * 节奏:飞入 620ms → 拍击 340ms → 高光 340ms(与冲击特效并行)→ 停 360ms。
 * 改花样:行列在 COLS/ROWS,节奏在 startShow 的时长,贴纸样式在 drawSticker。
 */
public class SplashActivity extends Activity {

    /** 拼豆颜色(糖果贴纸风点缀色) */
    private static final int[] BEAD_COLORS = {
            0xFF35C98E, 0xFFA78BFA, 0xFFFF9F6E, 0xFF56C2F7, 0xFFFFCF56, 0xFFFF6E9C
    };

    private static final int COLS = 7;
    private static final int ROWS = 5;

    private SplashView board;
    private boolean skipped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        board = new SplashView();
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
        DecelerateInterpolator decel = new DecelerateInterpolator(1.4f);

        // 阶段1:豆从四面八方飞入落位
        ValueAnimator fly = ValueAnimator.ofFloat(0f, 1f);
        fly.setDuration(620);
        fly.setInterpolator(decel);
        fly.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                board.flyT = (Float) a.getAnimatedValue();
                board.invalidate();
            }
        });

        // 阶段2:贴纸 logo 拍下(overshoot = 拍击的"啪"感)
        ValueAnimator slap = ValueAnimator.ofFloat(0f, 1f);
        slap.setDuration(340);
        slap.setInterpolator(new OvershootInterpolator(2.2f));
        slap.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                board.slapT = (Float) a.getAnimatedValue();
                board.invalidate();
            }
        });

        // 阶段3:高光扫过 + 冲击环/纸屑(冲击特效在阶段3开头一次性放出)
        ValueAnimator shine = ValueAnimator.ofFloat(0f, 1f);
        shine.setDuration(340);
        shine.setInterpolator(decel);
        shine.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                board.fxT = (Float) a.getAnimatedValue(); // 与高光同进度,保证走满 0→1
                board.shineT = (Float) a.getAnimatedValue();
                board.invalidate();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(fly, slap, shine);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                board.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!skipped) goMain();
                    }
                }, 360);
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

    /** 开屏画布 */
    private class SplashView extends View {

        float flyT;    // 豆飞入进度 0..1
        float slapT;   // 贴纸拍击进度 0..1
        float fxT;     // 冲击环/纸屑进度 0..1
        float shineT;  // 高光扫过进度 0..1

        final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint pegPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint beadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stickerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint confettiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Random rnd = new Random(42);
        /** 每格固定的飞行起点角/距离/延迟与颜色(保证每次进 APP 摆设一致) */
        final float[] angles = new float[COLS * ROWS];
        final float[] dists = new float[COLS * ROWS];
        final float[] delays = new float[COLS * ROWS];
        final int[] colors = new int[COLS * ROWS];
        /** 纸屑:角度/距离/色 */
        final float[] confAngle = new float[14];
        final float[] confDist = new float[14];
        final int[] confColor = new int[14];

        SplashView() {
            super(SplashActivity.this);
            setBackgroundColor(0xFFFFF6ED);
            setClickable(true);
            for (int i = 0; i < COLS * ROWS; i++) {
                angles[i] = rnd.nextFloat() * 2f * (float) Math.PI;
                dists[i] = 0.55f + rnd.nextFloat() * 0.7f;
                delays[i] = rnd.nextFloat();
                colors[i] = BEAD_COLORS[rnd.nextInt(BEAD_COLORS.length)];
            }
            for (int i = 0; i < confAngle.length; i++) {
                confAngle[i] = rnd.nextFloat() * 2f * (float) Math.PI;
                confDist[i] = 0.5f + rnd.nextFloat();
                confColor[i] = BEAD_COLORS[rnd.nextInt(BEAD_COLORS.length)];
            }
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
            float oy = (h - boardH) / 2f - h * 0.06f;
            float diag = (float) Math.sqrt(w * w + h * h);

            // 拼板底 + 孔
            boardPaint.setColor(0xFFFFFFFF);
            float r = cell * 0.5f;
            canvas.drawRoundRect(ox - r, oy - r, ox + boardW + r, oy + boardH + r,
                    r * 1.6f, r * 1.6f, boardPaint);
            pegPaint.setColor(0xFFF2DFD2);
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) {
                    canvas.drawCircle(ox + (x + 0.5f) * cell, oy + (y + 0.5f) * cell,
                            cell * 0.09f, pegPaint);
                }
            }

            // 豆雨:每颗从自己的随机方向飞入,落位压扁回弹
            float maxDelay = 0.55f;
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) {
                    int i = y * COLS + x;
                    float delay = delays[i] * maxDelay;
                    float t = (flyT - delay) / (1f - maxDelay);
                    if (t <= 0f) continue;
                    t = Math.min(1f, t);
                    float cx = ox + (x + 0.5f) * cell;
                    float cy = oy + (y + 0.5f) * cell;
                    // 起点 = 终点沿随机方向推出去,飞入时收拢 + 微弧线
                    float off = (1f - t) * diag * dists[i] * 0.35f;
                    float sx = cx + (float) Math.cos(angles[i]) * off;
                    float sy = cy + (float) Math.sin(angles[i]) * off
                            - (float) Math.sin(t * Math.PI) * h * 0.04f;
                    // 落位瞬间 squash & stretch
                    float q = t > 0.82f
                            ? (float) Math.sin((t - 0.82f) / 0.18f * Math.PI) : 0f;
                    drawBead(canvas, sx, sy, cell * 0.46f, colors[i],
                            1f + q * 0.16f, 1f - q * 0.16f,
                            Math.min(1f, t * 5f));
                }
            }

            // 贴纸拍击之后才有冲击特效
            if (slapT > 0f) {
                float stickerCx = w / 2f;
                float stickerCy = oy + boardH + h * 0.1f;

                // 冲击环:拍实的一瞬扩散
                if (fxT > 0f && fxT < 1f) {
                    ringPaint.setStyle(Paint.Style.STROKE);
                    ringPaint.setStrokeWidth(dp(3f) * (1f - fxT) + dp(1f));
                    ringPaint.setColor(0x66FFCF56);
                    float rr = stickerW() * (0.5f + fxT * 0.55f);
                    canvas.drawCircle(stickerCx, stickerCy, rr, ringPaint);
                }

                // 糖果纸屑:小方片飞散 + 旋转感(用长短轴模拟)
                if (fxT > 0f) {
                    float ct = Math.min(1f, fxT);
                    for (int i = 0; i < confAngle.length; i++) {
                        float d = stickerW() * (0.55f + confDist[i] * 0.75f)
                                * (float) Math.sqrt(ct);
                        float px = stickerCx + (float) Math.cos(confAngle[i]) * d;
                        float py = stickerCy + (float) Math.sin(confAngle[i]) * d
                                + ct * ct * dp(26f);   // 微下坠
                        confettiPaint.setColor(confColor[i]);
                        confettiPaint.setAlpha(Math.round(255 * (1f - ct)));
                        float cw = dp(4.5f) * (1f - ct * 0.5f);
                        canvas.drawCircle(px, py, cw, confettiPaint);
                    }
                    confettiPaint.setAlpha(255);
                }

                drawSticker(canvas, stickerCx, stickerCy, w);
            }
        }

        /** 贴纸宽度(拍击环/纸屑的尺度基准) */
        float stickerW() {
            float tw = textPaint.measureText(getString(R.string.app_name));
            return tw + dp(44f);
        }

        /** 白色墨描边贴纸:大→小过冲拍下 + 微旋转,拍定后高光斜扫 */
        private void drawSticker(Canvas c, float cx, float cy, float w) {
            String name = getString(R.string.app_name);
            textPaint.setColor(0xFF40354E);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(w * 0.085f);
            textPaint.setFakeBoldText(true);
            float tw = textPaint.measureText(name);
            float th = textPaint.getTextSize();
            float sw = tw + dp(44f);
            float sh = th + dp(30f);

            // 拍击:scale 1.9→1(overshoot 会短暂压到 0.9x 再回弹),旋转 -14°→-4°
            float e = slapT;
            float scale = 1.9f - 0.9f * e;
            float rot = -14f + 10f * e;
            float alpha = Math.min(1f, slapT * 4f);

            RectF card = new RectF(cx - sw / 2f, cy - sh / 2f,
                    cx + sw / 2f, cy + sh / 2f);

            c.save();
            c.translate(cx, cy);
            c.rotate(rot);
            c.scale(scale, scale);
            // 墨色硬投影(贴纸语言)
            stickerPaint.setColor(0x6640354E);
            c.drawRoundRect(card.left - cx + dp(3f), card.top - cy + dp(4f),
                    card.right - cx + dp(3f), card.bottom - cy + dp(4f),
                    dp(16f), dp(16f), stickerPaint);
            // 卡身
            stickerPaint.setColor(0xFFFFFFFF);
            stickerPaint.setAlpha(Math.round(255 * alpha));
            c.drawRoundRect(card.left - cx, card.top - cy,
                    card.right - cx, card.bottom - cy, dp(16f), dp(16f), stickerPaint);
            // 2dp 墨描边
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(2f));
            strokePaint.setColor(0xFF40354E);
            strokePaint.setAlpha(Math.round(255 * alpha));
            c.drawRoundRect(card.left - cx, card.top - cy,
                    card.right - cx, card.bottom - cy, dp(16f), dp(16f), strokePaint);
            // 高光斜扫(裁进卡身)
            if (shineT > 0f && shineT < 1f) {
                float bandW = sw * 0.45f;
                float x0 = card.left - cx - bandW + (sw + bandW * 2f) * shineT;
                shinePaint.setShader(new LinearGradient(x0, cy - sh, x0 + bandW, cy + sh,
                        0x00FFFFFF, 0x99FFFFFF, Shader.TileMode.CLAMP));
                c.drawRect(card.left - cx, card.top - cy,
                        card.right - cx, card.bottom - cy, shinePaint);
                shinePaint.setShader(null);
            }
            // 文字
            textPaint.setAlpha(Math.round(255 * alpha));
            c.drawText(name, 0, th * 0.34f, textPaint);
            c.restore();
        }

        final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        /** 糖果拼豆:底色 + 墨调细描边 + 镜面高光 */
        private void drawBead(Canvas c, float cx, float cy, float r,
                              int color, float sx, float sy, float alpha) {
            c.save();
            c.translate(cx, cy);
            c.scale(sx, sy);
            beadPaint.setColor(color);
            beadPaint.setAlpha(Math.round(255 * alpha));
            c.drawCircle(0, 0, r, beadPaint);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(r * 0.16f);
            ringPaint.setColor(0x2940354E);
            c.drawCircle(0, 0, r - ringPaint.getStrokeWidth() / 2f, ringPaint);
            if (r > dp(6)) {
                glossPaint.setColor(0x66FFFFFF);
                glossPaint.setAlpha(Math.round(0x66 * alpha));
                c.drawCircle(-r * 0.32f, -r * 0.34f, r * 0.26f, glossPaint);
            }
            c.restore();
            beadPaint.setAlpha(255);
            glossPaint.setAlpha(255);
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }
    }
}
