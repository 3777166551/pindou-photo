package com.pindou.app.util;

import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * 微动画集合:按钮按压回弹、面板展开/收起、内容切换脉冲。
 * M3 Expressive(v2.63 批次一):按压回弹与面板入场改弹簧物理
 * (SpringInterpolator),零依赖;其余保持轻量补间,点到为止不误事。
 */
public final class Anim {

    private static final int FAST = 90;
    private static final int NORMAL = 180;

    private Anim() {
    }

    /** 按压缩到 0.95(临界弹簧,快)、松手弹回(snappy 过冲):物理反馈 */
    public static void pressScale(final View v) {
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v1, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.95f).scaleY(0.95f)
                                .setDuration(SpringInterpolator.settleMs(1f, 40f))
                                .setInterpolator(new SpringInterpolator(1f, 40f))
                                .start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(SpringInterpolator.settleMs(0.55f, 26f))
                                .setInterpolator(SpringInterpolator.snappy())
                                .start();
                        break;
                }
                return false;   // 不消费,点击/ripple 照常触发
            }
        });
    }

    /** 面板展开:淡入 + 弹簧上滑(轻微过冲一次落定) */
    public static void expand(final View v) {
        if (v == null || v.getVisibility() == View.VISIBLE) return;
        v.setAlpha(0f);
        v.setTranslationY(dp(v, 10));
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).translationY(0f)
                .setDuration(SpringInterpolator.settleMs(0.6f, 15f))
                .setInterpolator(SpringInterpolator.bouncy())
                .start();
    }

    /** 面板收起:淡出 + 轻微下滑,结束后 GONE */
    public static void collapse(final View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        v.animate().alpha(0f).translationY(dp(v, 8))
                .setDuration(140)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        v.setVisibility(View.GONE);
                        v.setAlpha(1f);
                        v.setTranslationY(0f);
                    }
                })
                .start();
    }

    /** 内容切换脉冲:轻微下沉淡入,用于 tab 切换/刷新预览 */
    public static void pulse(View v) {
        if (v == null) return;
        v.setAlpha(0.45f);
        v.animate().alpha(1f)
                .setDuration(NORMAL)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private static float dp(View v, float d) {
        return d * v.getResources().getDisplayMetrics().density;
    }
}
