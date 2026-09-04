package com.pindou.app.util;

import com.pindou.app.R;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPalettes;

import android.content.Context;

/**
 * i18n 钩子:把资源里的本地化数组/标签套到纯 Java 数据层
 * (BeadPalettes 的档位名与 120 通用色名、BeadColor 的编号后缀)
 * 和编辑器的静态标签(砖块/去噪/星期)上。
 * 各 Activity onCreate 时调用 L10n.apply(this) 一次;
 * 系统语言切换会重建进程,无需监听。未调用时保持中文默认(qa 纯 JVM 可跑)。
 */
public final class L10n {

    /** 抽象程度标签(导出图纸名里用) */
    public static String[] BRICK_LABELS = {"轻度", "中度", "强度", "超强"};
    /** 杂色清理档位标签 */
    public static String[] DENOISE_LABELS = {"关", "轻", "中", "强"};
    /** 打卡日历星期缩写 */
    public static String[] WEEK_SHORT = {"一", "二", "三", "四", "五", "六", "日"};

    public static void apply(Context c) {
        BRICK_LABELS = c.getResources().getStringArray(R.array.brick_labels);
        DENOISE_LABELS = c.getResources().getStringArray(R.array.denoise_labels);
        WEEK_SHORT = c.getResources().getStringArray(R.array.week_short);
        BeadColor.codeSuffix = c.getString(R.string.code_suffix);
        BeadPalettes.applyLocalization(
                c.getResources().getStringArray(R.array.tier_names),
                c.getString(R.string.count_fmt),
                c.getResources().getStringArray(R.array.generic_color_names),
                c.getString(R.string.my_color_fmt));
    }

    private L10n() {
    }
}
