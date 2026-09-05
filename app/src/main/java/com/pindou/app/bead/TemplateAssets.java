package com.pindou.app.bead;

import java.util.ArrayList;
import java.util.List;

/**
 * 开源像素素材包 → 拼豆模板。
 * 数据来自 Microsoft Fluent Emoji 3D(MIT 许可),经 tools/emoji_src/ 下的
 * 下载与量化脚本生成:1 像素 = 1 颗豆,32x32,颜色映射到 120 色通用色板。
 * 数据在 TemplateEmojiData(自动生成,勿手改)。
 */
public final class TemplateAssets {

    /** 模板库全部分类(单屏导航:顶部标签切换) */
    public static List<Templates.Cat> allCategories() {
        List<Templates.Cat> cats = new ArrayList<>();
        cats.add(cuteCategory("😹 流行表情",
                TemplateEmojiData.EMOJI_SMILEY_SPECS, TemplateEmojiData.EMOJI_SMILEY_NAMES));
        cats.add(cuteCategory("🐾 萌宠动物",
                TemplateEmojiData.EMOJI_ANIMAL_SPECS, TemplateEmojiData.EMOJI_ANIMAL_NAMES));
        cats.add(cuteCategory("🍔 美食饮料",
                TemplateEmojiData.EMOJI_FOOD_SPECS, TemplateEmojiData.EMOJI_FOOD_NAMES));
        cats.add(cuteCategory("🌸 花草节日",
                TemplateEmojiData.EMOJI_NATURE_SPECS, TemplateEmojiData.EMOJI_NATURE_NAMES));
        return cats;
    }

    /** Fluent Emoji 通用工厂(32x32 网格;建议画幅 36 = 图案 + 一圈余量) */
    private static Templates.Cat cuteCategory(String title, String[] specs, String[] names) {
        List<BeadColor> master = BeadPalettes.getPalette(3);
        List<Templates.Tpl> list = new ArrayList<>();
        for (int i = 0; i < specs.length && i < names.length; i++) {
            list.add(gridFromSpec(names[i], 36, specs[i], master));
        }
        return new Templates.Cat(title, list.toArray(new Templates.Tpl[0]));
    }

    /** 解析 "key:W:H:cells" 生成网格模板;字符 → 色板下标用 TemplateEmojiData.ALPHABET */
    private static Templates.Tpl gridFromSpec(String name, int suggestedSize,
                                              String spec, List<BeadColor> master) {
        int p1 = spec.indexOf(':');
        int p2 = spec.indexOf(':', p1 + 1);
        int p3 = spec.indexOf(':', p2 + 1);
        int w = Integer.parseInt(spec.substring(p1 + 1, p2));
        int h = Integer.parseInt(spec.substring(p2 + 1, p3));
        String cells = spec.substring(p3 + 1);
        String alphabet = TemplateEmojiData.ALPHABET;

        int[][] grid = new int[h][w];
        int limit = Math.min(cells.length(), w * h);
        for (int k = 0; k < limit; k++) {
            char c = cells.charAt(k);
            if (c == '.') continue;   // '.' 留空
            int idx = alphabet.indexOf(c);
            if (idx >= 0) {
                grid[k / w][k % w] = master.get(idx).rgb;
            }
        }
        return Templates.gridTpl(name, suggestedSize, grid);
    }

    private TemplateAssets() {
    }
}
