package com.pindou.app.util;

import com.pindou.app.bead.BeadBrandCharts;
import com.pindou.app.bead.BeadColor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自定义色板分享格式(v1,规范见 docs/SHARE-FORMAT.md 的色板一节):
 * colors 数组结构与图纸分享格式(pindou-pattern)完全一致,两边互通。
 * 导入时也直接接受 pindou-pattern 文件,取它的颜色表当色板。
 */
public final class PaletteShare {

    public static final String FORMAT = "pindou-palette";
    public static final int VERSION = 1;
    /** 单套色板颜色数上限,防止坏文件撑爆内存 */
    public static final int MAX_COLORS = 500;

    /** 从色板生成分享 JSON(tag 非空才写,保持与图纸格式一致的精简结构) */
    public static JSONObject build(String name, List<BeadColor> colors) throws Exception {
        JSONArray arr = new JSONArray();
        for (BeadColor c : colors) {
            JSONObject o = new JSONObject();
            o.put("code", c.code);
            o.put("name", c.name);
            o.put("rgb", c.rgb);
            if (!c.tag.isEmpty()) o.put("tag", c.tag);
            arr.put(o);
        }
        JSONObject o = new JSONObject();
        o.put("format", FORMAT);
        o.put("version", VERSION);
        o.put("app", "PindouPhoto");
        o.put("name", name == null || name.isEmpty() ? "我的色板" : name);
        o.put("savedAt", System.currentTimeMillis());
        o.put("colors", arr);
        return o;
    }

    /**
     * 解析分享 JSON 成色板;也接受 pindou-pattern(取其 colors)。
     * 格式不对抛异常,消息可直接给用户看。
     */
    public static BeadBrandCharts.Chart parse(JSONObject o) throws Exception {
        String format = o.optString("format");
        if (!FORMAT.equals(format) && !"pindou-pattern".equals(format)) {
            throw new Exception("这不是拼豆色板分享文件");
        }
        int version = o.optInt("version", 0);
        if (version < 1 || version > VERSION) {
            throw new Exception("不支持的格式版本:" + version);
        }
        JSONArray colors = o.optJSONArray("colors");
        if (colors == null || colors.length() == 0) {
            throw new Exception("缺少颜色表");
        }
        if (colors.length() > MAX_COLORS) {
            throw new Exception("颜色太多(>" + MAX_COLORS + "),文件可疑");
        }
        List<BeadColor> list = new ArrayList<>();
        for (int i = 0; i < colors.length(); i++) {
            JSONObject c = colors.optJSONObject(i);
            if (c == null) throw new Exception("颜色表损坏");
            String name = c.optString("name", "").trim();
            if (name.isEmpty()) name = "色" + (i + 1);
            list.add(new BeadColor(Math.max(0, c.optInt("code", i + 1)),
                    name, c.optInt("rgb", 0xFF000000) & 0xFFFFFF,
                    c.optString("tag", "")));
        }
        List<BeadColor> clean = dedupeByRgb(list);
        if (clean.isEmpty()) throw new Exception("颜色表为空");
        String name = o.optString("name", "").trim();
        return BeadBrandCharts.make(name.isEmpty() ? "📥 导入色板" : name, clean);
    }

    /** 按 RGB 去重(同一颗豆在色板里只需要一个条目),保留先出现的名字 */
    public static List<BeadColor> dedupeByRgb(List<BeadColor> colors) {
        Set<Integer> seen = new HashSet<>();
        List<BeadColor> out = new ArrayList<>(colors.size());
        for (BeadColor c : colors) {
            if (seen.add(c.rgb & 0xFFFFFF)) out.add(c);
        }
        return out;
    }

    /**
     * 解析 "#RRGGBB" / "RRGGBB" / "#RGB" / "RGB" 十六进制颜色,
     * 非法返回 -1(调用方给用户提示)。
     */
    public static int parseHexColor(String s) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 3; i++) sb.append(t.charAt(i)).append(t.charAt(i));
            t = sb.toString();
        }
        if (t.length() != 6) return -1;
        try {
            int v = Integer.parseInt(t, 16);
            return (v & 0xFFFFFF) | 0xFF000000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** rgb 的展示文本 "#RRGGBB" */
    public static String toHex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private PaletteShare() {
    }
}
