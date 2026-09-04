package com.pindou.app.bead;

import java.util.ArrayList;
import java.util.List;

/**
 * 拼豆色板。参考市面上常见的拼豆颜色体系(Perler / Hama / Artkal / 国产套装)
 * 整理为 4 档:24色经典 / 48色标准 / 90色进阶 / 120色全彩。
 * 颜色为通用参考值,不同品牌实物会略有差异。
 */
public final class BeadPalettes {

    public static final String[] TIER_NAMES = {
            "24色 · 经典", "48色 · 标准", "90色 · 进阶", "120色 · 全彩"
    };

    private static final int[] HEX_T1 = {
            0xFFFFFF, 0xF7F0DD, 0xA8A8A8, 0x4A4A4A, 0x141414, 0xE3242B, 0x9C1C1C, 0xE4007C,
            0xF48FB1, 0xF57C00, 0xF7E01E, 0xF5A623, 0x43A047, 0x1B5E20, 0x26A69A, 0x42A5F5,
            0x1E5AA8, 0x0D2C6B, 0x7B3FA0, 0x795548, 0xC8A17B, 0xF5CBA0, 0x6D4C41, 0x90CAF9
    };
    private static final String[] NAME_T1 = {
            "白色", "奶白", "灰色", "深灰", "黑色", "大红", "深红", "玫红",
            "粉红", "橙色", "柠檬黄", "大黄", "草绿", "深绿", "青色", "天蓝",
            "宝蓝", "深蓝", "紫色", "棕色", "浅棕", "肤色", "咖啡色", "浅蓝"
    };

    private static final int[] HEX_T2 = {
            0xD9D9D9, 0x757575, 0x7B1113, 0xA04A3A, 0xFF7F6E, 0xFFB6C1, 0xE91E63, 0xFFB74D,
            0xE65100, 0xFFF59D, 0xFBC02D, 0xB8A233, 0x7CB342, 0x556B2F, 0x00838F, 0x4DD0E1,
            0x039BE5, 0x1A237E, 0x607D8B, 0x512DA8, 0xD1C4E9, 0xA1887F, 0xC3B091, 0xFADCC8
    };
    private static final String[] NAME_T2 = {
            "银灰", "中灰", "酒红", "砖红", "珊瑚红", "浅粉", "艳粉", "浅橙",
            "深橙", "浅黄", "深黄", "芥末黄", "苹果绿", "军绿", "深青", "浅青",
            "湖蓝", "藏蓝", "灰蓝", "深紫", "浅紫", "驼色", "卡其", "浅肤色"
    };

    private static final int[] HEX_T3 = {
            0xEAE0C0, 0x2B2B2B, 0xA29A8C, 0x6B7075, 0xEA4A28, 0x5C0E1E, 0xDDBAC2, 0xF2CD9A,
            0xE8A575, 0x9E8B3A, 0xFFBE0B, 0xA9C24A, 0x0E3620, 0xA5DBC8, 0x92A683, 0x63E62E,
            0x13876A, 0xD6E9F8, 0x3949AB, 0x2E7CFF, 0x6A5ACD, 0x452159, 0xC0A5C9, 0x3E2723,
            0x6E2C1B, 0x8B4226, 0xA9713F, 0xD2A24C, 0xC0C7CE, 0xD4AF37, 0xFF6F3C, 0x0277BD,
            0x38623C, 0xE3D5AE, 0xCB6843, 0xA9BFD4, 0xD3ACAF, 0x2F211A, 0x00A651, 0x6E7623,
            0xAD0A63, 0xC4825A
    };
    private static final String[] NAME_T3 = {
            "象牙白", "炭灰", "暖灰", "冷灰", "番茄红", "深酒红", "藕粉", "杏色",
            "肤橙", "土黄", "金黄", "黄绿", "墨绿", "薄荷绿", "灰绿", "荧光绿",
            "孔雀绿", "淡蓝", "靛蓝", "亮蓝", "蓝紫", "葡萄紫", "藕紫", "深棕",
            "栗色", "红棕", "深肤色", "小麦色", "银色", "金色", "荧光橙", "深天蓝",
            "森林绿", "浅卡其", "砖橘", "雾蓝", "灰粉", "黑棕", "翠绿", "橄榄绿",
            "深玫红", "古铜色"
    };

    private static final int[] HEX_T4 = {
            0xC96B5D, 0xEF6FA8, 0xFF36A5, 0xD5324F, 0xA04225, 0xFFE45C, 0xE0F53C, 0x00CFFF,
            0x00A98F, 0x01579B, 0x8E7CC3, 0x283593, 0x8D6E63, 0x12203F, 0xB08968, 0x46586A,
            0xDCC2DD, 0x0B5D5A, 0x3F4A21, 0xFFB3A7, 0xFFCC9E, 0x5A6E60, 0xE2C878, 0x9FB2B4,
            0x6F7C3E, 0xA66BB8, 0xE86A50, 0x55C1A5, 0x8E1F4B, 0xFFF9EA
    };
    private static final String[] NAME_T4 = {
            "浅砖红", "玫粉", "荧光粉", "樱桃红", "铁锈红", "鹅黄", "荧光黄", "荧光蓝",
            "宝石绿", "深湖蓝", "灰紫", "深靛", "咖啡驼", "深海军", "豆沙色", "深灰蓝",
            "香芋紫", "深青绿", "深橄榄", "珊瑚粉", "蜜桃色", "深灰绿", "浅金", "青灰",
            "卡其绿", "亮紫", "深珊瑚", "湖水绿", "紫红", "奶油白"
    };

    private static final List<BeadColor> MASTER = buildMaster();

    /** 通用 4 档之外的选项:各品牌官方色号表 */
    public static final int GENERIC_COUNT = 4;

    private static String[] selNamesCache;

    /** 色板列表变化(生成/重建我的豆板)后调用,让下拉框重新取名字 */
    public static void resetCache() {
        selNamesCache = null;
    }

    private static List<BeadColor> buildMaster() {
        List<BeadColor> list = new ArrayList<>(120);
        append(list, HEX_T1, NAME_T1);
        append(list, HEX_T2, NAME_T2);
        append(list, HEX_T3, NAME_T3);
        append(list, HEX_T4, NAME_T4);
        return list;
    }

    private static void append(List<BeadColor> list, int[] hex, String[] names) {
        for (int i = 0; i < hex.length; i++) {
            list.add(new BeadColor(list.size() + 1, names[i], hex[i]));
        }
    }

    /** 色板选择器总数:通用 4 档 + 品牌色号表 + 我的豆板(若有) */
    public static int selCount() {
        return GENERIC_COUNT + BeadBrandCharts.ALL.length + BeadBrandCharts.extraCount();
    }

    /** 色板选择器的全部名称(下拉框直接用) */
    public static String[] selNames() {
        if (selNamesCache != null) return selNamesCache;
        String[] n = new String[selCount()];
        System.arraycopy(TIER_NAMES, 0, n, 0, GENERIC_COUNT);
        for (int i = 0; i < BeadBrandCharts.ALL.length; i++) {
            BeadBrandCharts.Chart c = BeadBrandCharts.ALL[i];
            n[GENERIC_COUNT + i] = c.name + "(" + c.colors.size() + "色)";
        }
        if (BeadBrandCharts.getCustom() != null) {
            BeadBrandCharts.Chart c = BeadBrandCharts.getCustom();
            n[GENERIC_COUNT + BeadBrandCharts.ALL.length] = c.name;
        }
        selNamesCache = n;
        return n;
    }

    /**
     * 按选择序号取色板。0~3 = 通用 4 档;之后 = 品牌官方色号表
     * (里面的 BeadColor 带官方 tag,清单和图纸直接显示真实色号);
     * 最后 = 我的豆板(从豆仓库存生成,若已注册)。
     */
    public static List<BeadColor> getPalette(int sel) {
        int brandCount = BeadBrandCharts.ALL.length;
        if (sel >= GENERIC_COUNT + brandCount) {
            if (BeadBrandCharts.extraCount() > 0) {
                return new ArrayList<>(BeadBrandCharts.getCustom().colors);
            }
            sel = GENERIC_COUNT + brandCount - 1;
        }
        if (sel >= GENERIC_COUNT) {
            int j = sel - GENERIC_COUNT;
            if (j < brandCount) {
                return new ArrayList<>(BeadBrandCharts.ALL[j].colors);
            }
            j = brandCount - 1;
            return new ArrayList<>(BeadBrandCharts.ALL[Math.max(0, j)].colors);
        }
        int[] sizes = {24, 48, 90, 120};
        int n = sizes[Math.max(0, Math.min(3, sel))];
        if (n > MASTER.size()) n = MASTER.size();
        return new ArrayList<>(MASTER.subList(0, n));
    }

    /**
     * "我的豆板":把豆仓里登记过、手头有货(数量>0)的颜色按色相排序
     * 生成自定义色板——用真实拥有的豆子画图,豆单就是购买/取用清单。
     * 颜色不足 1 种返回 null。
     */
    public static List<BeadColor> buildInventoryPalette(android.content.Context c) {
        List<Integer> rgbs = BeadInventory.ownedColors(c);
        if (rgbs.isEmpty()) return null;
        final double[][] labs = new double[rgbs.size()][];
        for (int i = 0; i < rgbs.size(); i++) {
            labs[i] = ColorMath.rgbToLab(rgbs.get(i));
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rgbs.size(); i++) order.add(i);
        java.util.Collections.sort(order, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                double ha = Math.toDegrees(Math.atan2(labs[a][2], labs[a][1]));
                double hb = Math.toDegrees(Math.atan2(labs[b][2], labs[b][1]));
                if (ha < 0) ha += 360;
                if (hb < 0) hb += 360;
                return Double.compare(ha, hb);
            }
        });
        List<BeadColor> out = new ArrayList<>(rgbs.size());
        for (int i = 0; i < order.size(); i++) {
            int rgb = rgbs.get(order.get(i));
            out.add(new BeadColor(i + 1, "我的色" + (i + 1), rgb));
        }
        return out;
    }

    public static String tierName(int sel) {
        String[] names = selNames();
        return names[Math.max(0, Math.min(names.length - 1, sel))];
    }

    private BeadPalettes() {
    }
}
