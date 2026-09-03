package com.pindou.app.bead;

/** 一颗拼豆的颜色定义 */
public final class BeadColor {
    /** 色号(1 起,本 APP 内部编号;品牌色板下仅作列表序号) */
    public final int code;
    /** 中文名称(品牌色板为官方英文名) */
    public final String name;
    /** 颜色值 0xRRGGBB */
    public final int rgb;
    /** 品牌官方色号(如 "S-47");通用色板为空串,此时展示内部编号 */
    public final String tag;

    public BeadColor(int code, String name, int rgb) {
        this(code, name, rgb, "");
    }

    public BeadColor(int code, String name, int rgb, String tag) {
        this.code = code;
        this.name = name;
        this.rgb = rgb;
        this.tag = tag == null ? "" : tag;
    }

    /** 展示用色号:品牌色板显示官方色号,通用色板显示内部编号 */
    public String displayCode() {
        return tag.isEmpty() ? code + "号" : tag;
    }

    /** 完整展示名:"名称(色号)";品牌色号与名称相同时只显示一次 */
    public String fullLabel() {
        if (!tag.isEmpty() && tag.equals(name)) return name;
        return name + "(" + displayCode() + ")";
    }

    /** 是否是品牌官方色号表里的颜色 */
    public boolean hasOfficialCode() {
        return !tag.isEmpty();
    }
}
