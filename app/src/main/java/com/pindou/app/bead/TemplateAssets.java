package com.pindou.app.bead;

import java.util.ArrayList;
import java.util.List;

/**
 * 开源像素素材包 → 拼豆模板。
 * 数据来自 Kenney(Tiny Dungeon / Animal Pack Redux / Fish Pack / Emotes Pack,
 * 作者 Kenney,CC0 公共领域),经 tools/palette_data/ 下量化工具生成,
 * 1 像素 = 1 颗豆(动物/鱼先盒式均值缩到 32x32)。
 */
public final class TemplateAssets {

    /** 模板库全部分类(两级导航:先选分类,再看网格) */
    public static List<Templates.Cat> allCategories() {
        List<Templates.Cat> cats = new ArrayList<>();
        cats.add(cuteCategory("🐾 萌宠动物",
                TemplateCuteData.CUTE_ANIMAL, TemplateCuteData.CUTE_ANIMAL_NAMES));
        cats.add(cuteCategory("💬 情绪气泡",
                TemplateCuteData.CUTE_EMOTE, TemplateCuteData.CUTE_EMOTE_NAMES));
        cats.add(tinyCategory());
        return cats;
    }

    /** Kenney 可爱系通用工厂(32x32 网格) */
    private static Templates.Cat cuteCategory(String title, String[] specs, String[] names) {
        List<BeadColor> master = BeadPalettes.getPalette(3);
        List<Templates.Tpl> list = new ArrayList<>();
        for (int i = 0; i < specs.length && i < names.length; i++) {
            list.add(gridFromSpec(names[i], 29, specs[i], master));
        }
        return new Templates.Cat(title, list.toArray(new Templates.Tpl[0]));
    }

    /** Kenney Tiny Dungeon 萌系像素分类(36 个,16x16) */
    public static Templates.Cat tinyCategory() {
        List<BeadColor> master = BeadPalettes.getPalette(3);
        List<Templates.Tpl> list = new ArrayList<>();
        String[] specs = TemplateTinyData.TINY;
        String[] names = TemplateTinyData.TINY_NAMES;
        for (int i = 0; i < specs.length && i < names.length; i++) {
            list.add(gridFromSpec(names[i], 29, specs[i], master));
        }
        return new Templates.Cat("🏰 迷你地牢", list.toArray(new Templates.Tpl[0]));
    }

    /** 解析 "key:W:H:cells" 生成网格模板 */
    private static Templates.Tpl gridFromSpec(String name, int suggestedSize,
                                              String spec, List<BeadColor> master) {
        int p1 = spec.indexOf(':');
        int p2 = spec.indexOf(':', p1 + 1);
        int p3 = spec.indexOf(':', p2 + 1);
        int w = Integer.parseInt(spec.substring(p1 + 1, p2));
        int h = Integer.parseInt(spec.substring(p2 + 1, p3));
        String cells = spec.substring(p3 + 1);

        int[][] grid = new int[h][w];
        int limit = Math.min(cells.length(), w * h);
        for (int k = 0; k < limit; k++) {
            char c = cells.charAt(k);
            if (c != '.') {
                grid[k / w][k % w] = master.get(c - 35).rgb;
            }
        }
        return Templates.gridTpl(name, suggestedSize, grid);
    }

    private TemplateAssets() {
    }
}
