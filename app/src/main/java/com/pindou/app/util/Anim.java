package com.pindou.app.util;

import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * 微动画集合:按钮按压回弹、面板展开/收起、内容切换脉冲。
 * 全部用 View.animate() 属性动画,零依赖;时长都在 200ms 内,点到为止不误事。
 */
public final class Anim {

    private static final int FAST = 90;
    private static final int NORMAL = 180;

    private Anim() {
    }

    /** 按压缩到 0.95、松手弹回:给"这是能按的"最直接的物理反馈 */
    public static void pressScale(final View v) {
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v1, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.95f).scaleY(0.95f)
                                .setDuration(FAST)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(150)
                                .setInterpolator(new OvershootInterpolator(1.6f))
                                .start();
                        break;
                }
                return false;   // 不消费,点击/ripple 照常触发
            }
        });
    }

    /** 面板展开:淡入 + 轻微上滑(setup 前未布局也安全) */
    public static void expand(final View v) {
        if (v == null || v.getVisibility() == View.VISIBLE) return;
        v.setAlpha(0f);
        v.setTranslationY(dp(v, 10));
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).translationY(0f)
                .setDuration(NORMAL)
                .setInterpolator(new DecelerateInterpolator(1.5f))
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
