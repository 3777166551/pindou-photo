package com.pindou.app.bead;

import java.util.HashMap;
import java.util.Map;

/**
 * 品牌色号近似对照。
 * 给定本 APP 任一拼豆颜色,在品牌色表中找感知(Lab)最近的颜色,
 * 返回形如 "AK-S08｜MD 23号" 的标签。
 *
 * ⚠️ 重要说明:内置品牌色值是社区/教程整理的近似数据,并非官方逐色核对,
 *    结论仅供买豆参考;不同批次实物有差异,请以官方色卡为准。
 *    后续拿到可靠的品牌全量色卡时,只需替换 CHART 数组即可。
 */
public final class BeadBrand {

    /** 品牌名前缀 */
    private static final String[] BRAND_NAMES = {"AK", "MD"};

    // 近似 Artkal S 系(软豆 5mm)常用色:代码 + 近似RGB
    private static final String[][] CHART_ARTKAL = {
            {"S-01", "FFFFFF"}, {"S-02", "FFF6DE"}, {"S-03", "D8D8D8"},
            {"S-04", "A0A0A0"}, {"S-05", "5A5A5A"}, {"S-06", "171717"},
            {"S-08", "E43A3A"}, {"S-09", "B02A2A"}, {"S-10", "7E1A22"},
            {"S-11", "F47BA0"}, {"S-13", "FF8A44"}, {"S-14", "E2571B"},
            {"S-15", "F8C24A"}, {"S-16", "F49E1C"}, {"S-17", "BB8B2E"},
            {"S-19", "84BC4C"}, {"S-20", "3F9B41"}, {"S-21", "1E6633"},
            {"S-22", "79C6A9"}, {"S-23", "2FA7A0"}, {"S-24", "3E76BE"},
            {"S-25", "2559A8"}, {"S-26", "17396B"}, {"S-28", "8656AF"},
            {"S-29", "5F3490"}, {"S-31", "F5A2B2"}, {"S-32", "EC6D9C"},
            {"S-33", "8E6A52"}, {"S-34", "C69C6D"}, {"S-35", "F2CDA9"}
    };

    // 近似漫德(Mard)常见套装编号:仅覆盖高频基础色,待社区校准
    private static final String[][] CHART_MARD = {
            {"1", "FFFFFF"}, {"2", "FFF3DC"}, {"3", "C8C8C8"}, {"4", "707070"},
            {"5", "141414"}, {"6", "E23333"}, {"7", "A02020"}, {"8", "EF5FA0"},
            {"9", "FF8C42"}, {"10", "F9D423"}, {"11", "F5A623"}, {"12", "57B14C"},
            {"13", "2C7D36"}, {"14", "27A79F"}, {"15", "46A8F5"}, {"16", "2B62C4"},
            {"17", "16337A"}, {"18", "8752A8"}, {"19", "6B4A38"}, {"20", "CBA97E"},
            {"21", "F2CFAD"}, {"22", "8C8C8C"}, {"23", "FFB6C1"}, {"24", "00A99D"}
    };

    private static final String[][][] CHARTS = {CHART_ARTKAL, CHART_MARD};

    /** rgb(hex) -> 品牌标签 缓存 */
    private static final Map<Integer, String> CACHE = new HashMap<>();

    /** 返回品牌色号标签,如 "AK-S03｜MD 3";无足够接近的对照时返回 "" */
    public static String tagOf(int rgb) {
        Integer key = rgb & 0xFFFFFF;
        String cached = CACHE.get(key);
        if (cached != null) return cached;
        StringBuilder sb = new StringBuilder();
        double[] lab = ColorMath.rgbToLab(0xFF000000 | rgb);
        for (int b = 0; b < CHARTS.length; b++) {
            int bestIdx = -1;
            double bestD = Double.MAX_VALUE;
            String[][] chart = CHARTS[b];
            for (int i = 0; i < chart.length; i++) {
                int hex = (int) Long.parseLong(chart[i][1], 16);
                double[] bl = ColorMath.rgbToLab(0xFF000000 | hex);
                double dl = lab[0] - bl[0], da = lab[1] - bl[1], db = lab[2] - bl[2];
                double d = dl * dl + da * da + db * db;
                if (d < bestD) {
                    bestD = d;
                    bestIdx = i;
                }
            }
            if (bestIdx >= 0 && Math.sqrt(bestD) <= 12.0) {
                if (sb.length() > 0) sb.append("｜");
                sb.append(BRAND_NAMES[b]).append(' ').append(chart[bestIdx][0]);
            }
        }
        String tag = sb.toString();
        CACHE.put(key, tag);
        return tag;
    }

    /** 导出图纸上用的短标签(更紧凑),如 "(AK S-03/MD 3)";无对照返回 "" */
    public static String shortTagOf(int rgb) {
        String t = tagOf(rgb);
        if (t.isEmpty()) return "";
        return "(" + t.replace("｜", "/") + ")";
    }

    private BeadBrand() {
    }
}
