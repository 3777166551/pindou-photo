package com.pindou.app.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

/**
 * 系统栏沉浸适配(状态栏顶 + 导航栏底)。
 *
 * 背景:全 APP 此前没有任何 WindowInsets 处理。在全面屏手势机型、
 * 国产 ROM"全屏显示"模式、Android 15+ 强制 edge-to-edge 下,窗口
 * 会画到半透明导航栏下面,滚动区/底栏最后一排按钮和文字被系统导航栏
 * 盖住一截(用户反馈"按钮下面总是少一块",CI 截图 32 同样实锤)。
 *
 * 做法:把系统栏 inset 追加到内容根布局的 padding。框架已消费
 * insets 的老设备(非沉浸)getSystemWindowInset* 返回 0,padding
 * 不变、视觉零差异;沉浸设备返回真实栏高,内容自动抬出系统栏。
 * 平台 API 实现(minSdk 24 可用),不依赖 AndroidX。
 */
public final class Insets {
    private Insets() {
    }

    /**
     * 内容根布局 padding 追加 状态栏顶 + 导航栏底 inset。
     * 在 setContentView(...) 之后调用一次即可。
     */
    public static void padRoot(Activity act) {
        ViewGroup content = act.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        pad(content.getChildAt(0), true, true);
    }

    /** 只补底部导航栏(相机全屏页等顶部不想动的场景)。 */
    public static void padBottom(View v) {
        pad(v, false, true);
    }

    private static void pad(final View v, final boolean top, final boolean bottom) {
        // 初始 padding 在监听器首次派发前捕获,inset 是"追加"不是"覆盖"
        final int pl = v.getPaddingLeft();
        final int pt = v.getPaddingTop();
        final int pr = v.getPaddingRight();
        final int pb = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(pl,
                        pt + (top ? insets.getSystemWindowInsetTop() : 0),
                        pr,
                        pb + (bottom ? insets.getSystemWindowInsetBottom() : 0));
                return insets;   // 不消费,子视图(如滚动区)照常可自取
            }
        });
        v.requestApplyInsets();
    }
}
