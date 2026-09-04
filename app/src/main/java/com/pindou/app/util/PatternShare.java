package com.pindou.app.util;

import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.PatternEngine;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拼豆图纸分享格式(v1,规范见 docs/SHARE-FORMAT.md):
 * 自含色板 + 行程编码(RLE)格子,不依赖任何特定 APP 的色板体系,
 * 任何工具都能解析。cells 的值 = colors 数组下标,-1 = 空格。
 */
public final class PatternShare {

    public static final String FORMAT = "pindou-pattern";
    public static final int VERSION = 1;

    /** 从图纸生成分享 JSON(只含用到的颜色,cells 重映射到新下标,按用量排序) */
    public static JSONObject build(BeadPattern p, String name) throws Exception {
        Map<Integer, Integer> remap = new HashMap<>();
        JSONArray colors = new JSONArray();
        for (int i = 0; i < p.usedColors.size(); i++) {
            BeadPattern.UsedColor uc = p.usedColors.get(i);
            remap.put(uc.index, i);
            JSONObject c = new JSONObject();
            c.put("code", uc.color.code);
            c.put("name", uc.color.name);
            c.put("rgb", uc.color.rgb);
            colors.put(c);
        }
        JSONArray rle = new JSONArray();
        int runVal = Integer.MIN_VALUE;
        int runLen = 0;
        for (int i = 0; i < p.cells.length; i++) {
            int v = p.cells[i] < 0 ? -1 : remap.get(p.cells[i]);
            if (v == runVal) {
                runLen++;
            } else {
                if (runLen > 0) {
                    rle.put(runVal);
                    rle.put(runLen);
                }
                runVal = v;
                runLen = 1;
            }
        }
        if (runLen > 0) {
            rle.put(runVal);
            rle.put(runLen);
        }
        JSONObject o = new JSONObject();
        o.put("format", FORMAT);
        o.put("version", VERSION);
        o.put("app", "PindouPhoto");
        o.put("name", name == null || name.isEmpty() ? "未命名图纸" : name);
        o.put("savedAt", System.currentTimeMillis());
        o.put("cols", p.cols);
        o.put("rows", p.rows);
        o.put("round", p.round);
        o.put("colors", colors);
        o.put("cells", rle);
        return o;
    }

    /** 解析分享 JSON 并还原成图纸;格式不对抛异常(消息可直接给用户看) */
    public static BeadPattern parse(JSONObject o) throws Exception {
        if (!FORMAT.equals(o.optString("format"))) {
            throw new Exception("这不是拼豆图纸分享文件");
        }
        int version = o.optInt("version", 0);
        if (version < 1 || version > VERSION) {
            throw new Exception("不支持的格式版本:" + version);
        }
        int cols = o.optInt("cols", 0);
        int rows = o.optInt("rows", 0);
        if (cols < 4 || rows < 4 || cols > 400 || rows > 400) {
            throw new Exception("图纸尺寸不合法");
        }
        JSONArray colors = o.optJSONArray("colors");
        if (colors == null || colors.length() == 0) {
            throw new Exception("缺少颜色表");
        }
        List<BeadColor> palette = new ArrayList<>();
        for (int i = 0; i < colors.length(); i++) {
            JSONObject c = colors.optJSONObject(i);
            if (c == null) throw new Exception("颜色表损坏");
            palette.add(new BeadColor(c.optInt("code", 0),
                    c.optString("name", "色" + (i + 1)), c.optInt("rgb", 0xFF000000)));
        }
        JSONArray rle = o.optJSONArray("cells");
        if (rle == null) throw new Exception("缺少格子数据");
        int[] cells = new int[cols * rows];
        int idx = 0;
        for (int i = 0; i + 1 < rle.length(); i += 2) {
            int v = rle.optInt(i, Integer.MIN_VALUE);
            int len = rle.optInt(i + 1, 0);
            if (v < -1 || v >= palette.size() || len <= 0 || idx + len > cells.length) {
                throw new Exception("格子数据损坏");
            }
            for (int k = 0; k < len; k++) cells[idx++] = v;
        }
        if (idx != cells.length) throw new Exception("格子数量与尺寸不符");
        return assemble(cols, rows, palette, cells, o.optBoolean("round", false));
    }

    /** 由 cells 统计用量并组装 BeadPattern */
    public static BeadPattern assemble(int cols, int rows, List<BeadColor> palette,
                                       int[] cells, boolean round) {
        int n = palette.size();
        int[] counts = new int[Math.max(1, n)];
        int empty = 0;
        for (int c : cells) {
            if (c < 0) empty++;
            else if (c < n) counts[c]++;
        }
        int total = 0;
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (counts[i] > 0) {
                used.add(new BeadPattern.UsedColor(i, palette.get(i),
                        PatternEngine.symbolFor(i), counts[i]));
                total += counts[i];
            }
        }
        BeadPattern.sortByCountDesc(used);
        return new BeadPattern(cols, rows, palette, cells, counts, used, total, empty, round);
    }

    /**
     * 从项目存档 JSON 还原图纸(合并采购清单用):
     * 分享格式导入的项目直接解析;普通项目用保存的照片 + 参数重新生成。
     * 失败返回 null。
     */
    public static BeadPattern fromProject(JSONObject o) {
        try {
            JSONObject sh = o.optJSONObject("share");
            if (sh != null) return parse(sh);
            JSONObject s = o.optJSONObject("settings");
            if (s == null) return null;
            String photo = o.optString("photo", "");
            if (photo.isEmpty()) return null;
            android.graphics.Bitmap src = Jsons.decodeBitmap(photo);
            if (src == null) return null;

            com.pindou.app.bead.PatternEngine.Options opt =
                    new com.pindou.app.bead.PatternEngine.Options();
            opt.cols = s.optInt("cols", 58);
            opt.rows = s.optInt("rows", 58);
            opt.dither = s.optBoolean("dither", false);
            opt.brightness = s.optInt("brightness", 0);
            opt.contrast = s.optInt("contrast", 0);
            opt.saturation = s.optInt("saturation", 0);
            opt.style = s.optInt("style", 0);
            int[] bricks = {2, 3, 4, 6};
            opt.brickSize = bricks[Math.max(0, Math.min(bricks.length - 1,
                    s.optInt("brickIdx", 1)))];
            opt.abstractUsePalette = s.optBoolean("absUse", true);
            opt.abstractColors = Math.max(4, Math.min(16, s.optInt("absColors", 8)));
            opt.abstractSnapToBeads = s.optBoolean("absSnap", true);
            opt.bgRemove = s.optBoolean("bgOn", false);
            opt.bgTolerance = s.optInt("bgTol", 45);
            opt.roundBoard = s.optBoolean("round", false);
            opt.maxColors = new int[]{0, 12, 18, 26, 40}[Math.max(0,
                    Math.min(4, s.optInt("limitIdx", 0)))];
            opt.dominant = s.optBoolean("dominant", false);
            opt.denoise = Math.max(0, Math.min(3, s.optInt("denoise", 0)));
            opt.preciseColor = s.optBoolean("precise", false);

            List<BeadColor> palette = com.pindou.app.bead.BeadPalettes.getPalette(
                    Math.max(0, Math.min(
                            com.pindou.app.bead.BeadPalettes.selCount() - 1,
                            s.optInt("tierIdx", 2))));
            BeadPattern raw = com.pindou.app.bead.PatternEngine.generate(src, palette, opt);

            JSONArray ed = o.optJSONArray("edits");
            if (ed != null && ed.length() > 0) {
                java.util.Map<Integer, Integer> editMap =
                        new java.util.HashMap<>();
                for (int i = 0; i < ed.length(); i++) {
                    JSONArray pair = ed.optJSONArray(i);
                    if (pair == null || pair.length() < 2) continue;
                    int k = pair.optInt(0, -1);
                    int v = pair.optInt(1, -99);
                    if (k >= 0 && k < opt.cols * opt.rows && v >= -1) {
                        editMap.put(k, v);
                    }
                }
                return com.pindou.app.bead.PatternPatch.apply(raw, editMap);
            }
            return raw;
        } catch (Throwable t) {
            return null;
        }
    }

    private PatternShare() {
    }
}
