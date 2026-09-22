import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPalettes;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;
import com.pindou.app.bead.PatternEngine;

import java.util.List;
import java.util.Random;

/**
 * 算法判据门禁 (04 报告 §十三 方案4,2026-09-21 定稿进 CI)。
 *
 * 在合成场景上同时跑两条管线并断言相对判据,防止取色算法回归:
 *   OLD = 盒平均(sRGB) + Lab 欧氏最近豆  (= 现状语义)
 *   NEW = 先配后投 WorkGrid(4bit LUT + 格内多数票 + 线条救援 + 门控)  (= V11)
 *
 * 判据(v2 口径,与 probes/auto_judge.py 同源):
 *   图形类: 碎豆v2(非深豆<=2格碎片) <= 70% 现状;无主豆格(格内无像素配该豆)==0;
 *           线保留 dark 格数 >= 现状(救援保线);
 *   照片类: ΔE(对盒平均参照) <= 现状 + 0.3(合成平滑场景口径;
 *           真实图锐利化成本 +3~7 由 probes/bench 人工校准,不在此重复);
 *   门控:   欠曝照片自动提亮生效(L* 上移)且全程无主豆仍为 0。
 *
 * 全部纯像素/纯 Java,不依赖 Bitmap 实例。
 */
public class TestAlgoGate {

    static final double GRAPHIC_FRAG_RATIO = 0.7;   // 图形类碎豆v2 上限(现状的 70%)
    static final double PHOTO_DE_DELTA = 0.3;       // 照片类 ΔE 允许增量

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) passed++;
        else failed++;
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
    }

    static int px(int rgb) {
        return 0xFF000000 | rgb;
    }

    // ---------- 场景 ----------

    /** 图形:白底 + 红圆 + 蓝矩形 + 黄条 + 1px 黑线两条(细线救援必须保住) */
    static int[] sceneGraphic(int side) {
        int[] p = new int[side * side];
        java.util.Arrays.fill(p, px(0xFFFFFF));
        int cx = side / 2, cy = side / 2, r = side / 5;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r * r) p[y * side + x] = px(0xE3242B);
                if (x >= side / 12 && x < side / 3 && y >= side / 12 && y < side / 3)
                    p[y * side + x] = px(0x1E5AA8);
                if (y >= 2 * side / 3 && y < 2 * side / 3 + 6 && x > side / 4 && x < 3 * side / 4)
                    p[y * side + x] = px(0xF7E01E);
            }
        }
        // 1px 横线 + 1px 竖线(黑,细线)
        int ly = side / 8;
        for (int x = side / 10; x < 9 * side / 10; x++) p[ly * side + x] = px(0x141414);
        int lx = 7 * side / 8;
        for (int y = side / 10; y < 9 * side / 10; y++) p[y * side + lx] = px(0x141414);
        return p;
    }

    /** 照片:平滑渐变天空 + 草地 + 低幅噪声(正常曝光) */
    static int[] scenePhoto(int side, long seed) {
        int[] p = new int[side * side];
        Random rnd = new Random(seed);
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int r, g, b;
                if (y < side / 2) {
                    double t = (double) y / (side / 2);
                    r = (int) (120 + 60 * t);
                    g = (int) (160 + 40 * t);
                    b = (int) (230 - 30 * t);
                } else {
                    double t = (double) (y - side / 2) / (side / 2);
                    r = (int) (60 + 30 * t);
                    g = (int) (120 + 50 * t);
                    b = (int) (50 + 20 * t);
                }
                int n = rnd.nextInt(13) - 6;   // ±6 噪声
                p[y * side + x] = px(clamp8(r + n) << 16 | clamp8(g + n) << 8 | clamp8(b + n));
            }
        }
        return p;
    }

    /** 平滑三通道斜坡(空间连贯,类照片;4bit 色桶 >= 64)——专测门控触发。
     *  注意不能用逐像素独立噪声:纯噪声格里 64 像素互不相同,均值参照 L2 最优,
     *  vote 的 ΔE 会被假性放大(真实照片纹理连贯,不发生)。 */
    static int[] sceneTexture(int side, long seed) {
        int[] p = new int[side * side];
        Random rnd = new Random(seed);
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int r = x * 255 / side + rnd.nextInt(9) - 4;
                int g = y * 255 / side + rnd.nextInt(9) - 4;
                int b = (x + y) * 255 / (2 * side) + rnd.nextInt(9) - 4;
                p[y * side + x] = px(clamp8(r) << 16 | clamp8(g) << 8 | clamp8(b));
            }
        }
        return p;
    }

    /** 欠曝照片:整体 ×0.45(触发门控:桶数>=64 且 亮度<125) */
    static int[] darken(int[] src, double f) {
        int[] p = new int[src.length];
        for (int i = 0; i < src.length; i++) {
            int v = src[i];
            int r = (int) (((v >> 16) & 0xFF) * f);
            int g = (int) (((v >> 8) & 0xFF) * f);
            int b = (int) ((v & 0xFF) * f);
            p[i] = px(clamp8(r) << 16 | clamp8(g) << 8 | clamp8(b));
        }
        return p;
    }

    static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    // ---------- 管线 ----------

    /** OLD:盒平均 + Lab 欧氏最近豆(现状语义) */
    static int[] runOld(int[] srcPx, int side, int gw, List<BeadColor> pal) {
        int[] cells = PatternEngine.boxResample(srcPx, side, side, gw, gw);
        int[] beads = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            double[] lab = ColorMath.rgbToLab(cells[i] & 0xFFFFFF);
            double bd = Double.MAX_VALUE;
            int best = 0;
            for (int j = 0; j < pal.size(); j++) {
                double d = ColorMath.dist2(lab, ColorMath.rgbToLab(pal.get(j).rgb));
                if (d < bd) {
                    bd = d;
                    best = j;
                }
            }
            beads[i] = best;
        }
        return beads;
    }

    /** WorkGrid 复制 buildVoteGrid 的入格分组 + 门控统计(与生产逐行同语义) */
    static PatternEngine.WorkGrid buildGrid(int[] srcPx, int side, int gw) {
        PatternEngine.WorkGrid g = new PatternEngine.WorkGrid();
        int cells = gw * gw;
        int per = (side / gw) * (side / gw);
        int[] cellOf = new int[srcPx.length];
        int[] cnt = new int[cells];
        for (int y = 0; y < side; y++) {
            int cy = y * gw / side;
            for (int x = 0; x < side; x++) {
                int c = cy * gw + (x * gw / side);
                cellOf[y * side + x] = c;
                cnt[c]++;
            }
        }
        int[] start = new int[cells + 1];
        for (int i = 0; i < cells; i++) start[i + 1] = start[i] + cnt[i];
        int[] pix = new int[srcPx.length];
        int[] cur = new int[cells];
        System.arraycopy(start, 0, cur, 0, cells);
        long lumSum = 0;
        int opaque = 0;
        java.util.BitSet buckets = new java.util.BitSet(4096);
        for (int i = 0; i < srcPx.length; i++) {
            int p = srcPx[i];
            pix[cur[cellOf[i]]++] = p;
            int r = (p >> 16) & 0xFF, gg = (p >> 8) & 0xFF, b = p & 0xFF;
            lumSum += (r * 299 + gg * 587 + b * 114) / 1000;
            buckets.set(((r >> 4) << 8) | ((gg >> 4) << 4) | (b >> 4));
            opaque++;
        }
        g.cellPix = pix;
        g.cellStart = start;
        g.gw = gw;
        g.gh = gw;
        g.brick = 1;
        g.gateGain = 1f;
        g.gateSat = 1f;
        if (opaque > 0) {
            int meanLum = (int) (lumSum / opaque);
            if (buckets.cardinality() >= 64 && meanLum < 125) {
                g.gateGain = Math.min(1.35f, 125f / Math.max(1, meanLum));
                g.gateSat = 1.10f;
            }
        }
        return g;
    }

    /** NEW:先配后投(V11) */
    static int[] runNew(int[] srcPx, int side, int gw, List<BeadColor> pal,
                        PatternEngine.WorkGrid gridOut) {
        PatternEngine.WorkGrid g = buildGrid(srcPx, side, gw);
        if (gridOut != null) {
            gridOut.gateGain = g.gateGain;
            gridOut.gateSat = g.gateSat;
        }
        PatternEngine.Options o = new PatternEngine.Options();
        BeadPattern p = PatternEngine.generateFromGrid(g, pal, o, gw, gw);
        return p.cells;
    }

    // ---------- 指标(与 probes/auto_judge.py v2 同口径) ----------

    static boolean isDark(int bead, List<BeadColor> pal) {
        return ColorMath.rgbToLab(pal.get(bead).rgb)[0] < 45;
    }

    /** 碎豆v2: 非"深豆"的 <=2 格连通碎片格数 */
    static int fragV2(int[] beads, int gw, List<BeadColor> pal) {
        boolean[] nonDark = new boolean[beads.length];
        for (int i = 0; i < beads.length; i++) nonDark[i] = !isDark(beads[i], pal);
        boolean[] seen = new boolean[beads.length];
        int frag = 0;
        int[] stack = new int[beads.length];
        for (int i = 0; i < beads.length; i++) {
            if (seen[i] || !nonDark[i]) continue;
            int top = 0, size = 0;
            stack[top++] = i;
            seen[i] = true;
            while (top > 0) {
                int c = stack[--top];
                size++;
                int y = c / gw, x = c % gw;
                int[] nb = {y > 0 ? c - gw : -1, y < gw - 1 ? c + gw : -1,
                            x > 0 ? c - 1 : -1, x < gw - 1 ? c + 1 : -1};
                for (int n : nb) {
                    if (n >= 0 && !seen[n] && nonDark[n]) {
                        seen[n] = true;
                        stack[top++] = n;
                    }
                }
            }
            if (size <= 2) frag += size;
        }
        return frag;
    }

    /** 无主豆格:格内没有任何源像素把该豆当最近色(OLD 语义:精确 Lab 欧氏) */
    static int ownerless(int[] srcPx, int side, int gw, int[] beads, List<BeadColor> pal) {
        int cells = gw * gw;
        int per = side / gw;
        boolean[] owned = new boolean[cells];
        double[][] palLab = new double[pal.size()][];
        for (int j = 0; j < pal.size(); j++) palLab[j] = ColorMath.rgbToLab(pal.get(j).rgb);
        for (int i = 0; i < srcPx.length; i++) {
            double[] lab = ColorMath.rgbToLab(srcPx[i] & 0xFFFFFF);
            double bd = Double.MAX_VALUE;
            int best = 0;
            for (int j = 0; j < pal.size(); j++) {
                double d = ColorMath.dist2(lab, palLab[j]);
                if (d < bd) {
                    bd = d;
                    best = j;
                }
            }
            int x = i % side, y = i / side;
            int c = (y / per) * gw + (x / per);
            if (best == beads[c]) owned[c] = true;
        }
        int n = 0;
        for (int c = 0; c < cells; c++) if (!owned[c]) n++;
        return n;
    }

    /** NEW 的无主豆检查必须用管线自己的 4bit-LUT 语义(buildLut 非精准档 = Lab 欧氏,
     *  桶中心匹配;门控提亮后量化),否则 LUT 豆 vs 精确豆的合法差异会被误判为无主。 */
    static int[] buildLutEuclid(List<BeadColor> pal) {
        int[] lut = new int[4096];
        double[][] labs = new double[pal.size()][];
        for (int j = 0; j < pal.size(); j++) labs[j] = ColorMath.rgbToLab(pal.get(j).rgb);
        for (int key = 0; key < 4096; key++) {
            int r = ((key >> 8) & 0xF) * 17 + 8;
            int g = ((key >> 4) & 0xF) * 17 + 8;
            int b = (key & 0xF) * 17 + 8;
            double[] lab = ColorMath.rgbToLab(0xFF000000 | (r << 16) | (g << 8) | b);
            double bd = Double.MAX_VALUE;
            int best = 0;
            for (int j = 0; j < pal.size(); j++) {
                double d = ColorMath.dist2(lab, labs[j]);
                if (d < bd) {
                    bd = d;
                    best = j;
                }
            }
            lut[key] = best;
        }
        return lut;
    }

    static int quantize(int p) {
        int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        return ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
    }

    static int gatePixel(int p, float gain, float sat) {
        int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        r = clamp8(Math.round(r * gain));
        g = clamp8(Math.round(g * gain));
        b = clamp8(Math.round(b * gain));
        int gray = (r * 299 + g * 587 + b * 114) / 1000;
        r = clamp8(gray + Math.round((r - gray) * sat));
        g = clamp8(gray + Math.round((g - gray) * sat));
        b = clamp8(gray + Math.round((b - gray) * sat));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 无主豆格(NEW 语义:LUT 配豆 + 门控后量化) */
    static int ownerlessNew(int[] srcPx, int side, int gw, int[] beads,
                            int[] lut, float gain, float sat) {
        int cells = gw * gw;
        int per = side / gw;
        boolean[] owned = new boolean[cells];
        for (int i = 0; i < srcPx.length; i++) {
            int p = srcPx[i];
            if (gain > 1f) p = gatePixel(p, gain, sat);
            int bead = lut[quantize(p)];
            int x = i % side, y = i / side;
            int c = (y / per) * gw + (x / per);
            if (bead == beads[c]) owned[c] = true;
        }
        int n = 0;
        for (int c = 0; c < cells; c++) if (!owned[c]) n++;
        return n;
    }

    /** ΔE: 结果豆色 vs 盒平均参照的 Lab 欧氏均值 */
    static double meanDe(int[] beads, int[] boxCells, List<BeadColor> pal) {
        double tot = 0;
        for (int i = 0; i < beads.length; i++) {
            double[] bl = ColorMath.rgbToLab(pal.get(beads[i]).rgb);
            double[] rl = ColorMath.rgbToLab(boxCells[i] & 0xFFFFFF);
            tot += Math.sqrt(ColorMath.dist2(bl, rl));
        }
        return tot / beads.length;
    }

    static double meanL(int[] beads, List<BeadColor> pal) {
        double tot = 0;
        for (int b : beads) tot += ColorMath.rgbToLab(pal.get(b).rgb)[0];
        return tot / beads.length;
    }

    public static void main(String[] args) {
        List<BeadColor> pal = BeadPalettes.getPalette(2);   // 90 色通用板
        int[] lut = buildLutEuclid(pal);
        int side = 464, gw = 58;

        // ---- 图形类判据 ----
        int[] graphic = sceneGraphic(side);
        int[] oldG = runOld(graphic, side, gw, pal);
        int[] newG = runNew(graphic, side, gw, pal, null);

        check("graphic: NEW 无主豆格 == 0 (先配后投机制保证)",
              ownerlessNew(graphic, side, gw, newG, lut, 1f, 1f) == 0);
        check("graphic: OLD 存在无主豆格(指标有效性 sanity)",
              ownerless(graphic, side, gw, oldG, pal) > 0);
        int f2o = fragV2(oldG, gw, pal), f2n = fragV2(newG, gw, pal);
        check("graphic: 碎豆v2 不增 (NEW " + f2n + " <= OLD " + f2o + "),"
                      + " 有基线时收紧到 " + GRAPHIC_FRAG_RATIO + "x",
              f2n <= f2o && (f2o == 0 || f2n <= f2o * GRAPHIC_FRAG_RATIO));
        java.util.HashSet<Integer> co = new java.util.HashSet<>(),
                cn = new java.util.HashSet<>();
        for (int b : oldG) co.add(b);
        for (int b : newG) cn.add(b);
        check("graphic: 用色不增 (NEW " + cn.size() + " <= OLD " + co.size() + ")",
              cn.size() <= co.size());
        int dko = 0, dkn = 0;
        for (int i = 0; i < oldG.length; i++) {
            if (isDark(oldG[i], pal)) dko++;
            if (isDark(newG[i], pal)) dkn++;
        }
        check("graphic: 线保留 dark NEW " + dkn + " >= OLD " + dko, dkn >= dko);
        // 红圆中心 = 红豆(投票基本正确性)
        int redExpect = 0;
        double bd = Double.MAX_VALUE;
        for (int j = 0; j < pal.size(); j++) {
            double d = ColorMath.dist2(ColorMath.rgbToLab(0xE3242B),
                    ColorMath.rgbToLab(pal.get(j).rgb));
            if (d < bd) {
                bd = d;
                redExpect = j;
            }
        }
        check("graphic: 红圆中心投红", newG[(gw / 2) * gw + gw / 2] == redExpect);

        // ---- 照片类判据(正常曝光,门控不触发) ----
        int[] photo = scenePhoto(side, 42L);
        int[] boxP = PatternEngine.boxResample(photo, side, side, gw, gw);
        int[] oldP = runOld(photo, side, gw, pal);
        int[] newP = runNew(photo, side, gw, pal, null);
        double deO = meanDe(oldP, boxP, pal), deN = meanDe(newP, boxP, pal);
        check("photo: dE NEW " + String.format("%.2f", deN) + " <= OLD "
                      + String.format("%.2f", deO) + " + " + PHOTO_DE_DELTA,
              deN <= deO + PHOTO_DE_DELTA);
        check("photo: 无主豆格 == 0",
              ownerlessNew(photo, side, gw, newP, lut, 1f, 1f) == 0);

        // ---- 门控判据(五彩纹理压暗:桶数>=64 且 亮度<125 必触发) ----
        int[] dark = darken(sceneTexture(side, 7L), 0.45);
        int[] boxD = PatternEngine.boxResample(dark, side, side, gw, gw);
        int[] oldD = runOld(dark, side, gw, pal);
        PatternEngine.WorkGrid gD = new PatternEngine.WorkGrid();
        int[] newD = runNew(dark, side, gw, pal, gD);
        check("gate: 欠曝场景触发门控 (gateGain=" + gD.gateGain + ")", gD.gateGain > 1f);
        check("gate: 提亮生效 (NEW L* > OLD L*)",
              meanL(newD, pal) > meanL(oldD, pal));
        int olGated = ownerlessNew(dark, side, gw, newD, lut, gD.gateGain, gD.gateSat);
        check("gate: 门控下无主豆格 == 0 (实际 " + olGated + ")", olGated == 0);

        System.out.println("TestAlgoGate: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
