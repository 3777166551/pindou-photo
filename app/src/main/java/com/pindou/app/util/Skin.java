package com.pindou.app.util;

import android.view.View;

/**
 * 全局文字风格挂钩。
 * v2.25 起界面改为"柔焦粉彩玻璃拟态"风,标题/正文统一系统无衬线字体,
 * 此类保留为空操作以维持既有调用点(Skin.apply)不报错;
 * 如需再上自定义字体,在 apply 里递归设置 Typeface 即可。
 * 历史方案(手绘风双字体)见 docs/视觉与动效设计.md。
 */
public final class Skin {

    private Skin() {
    }

    public static void apply(View root) {
        // no-op:统一使用系统无衬线,匹配柔焦粉彩风
    }
}
