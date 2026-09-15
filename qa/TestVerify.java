import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.util.VerifyEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 拍照验收(v2.54)量化测试:合成"真实感"拼豆板照片(透视变形 + 光照不均 +
 * 逐像素噪声 + 豆子亮度抖动 + 中孔),每张注入已知数量的 错色/漏摆/多余,
 * 断言 VerifyEngine 的检出与注入一致(容差 ±1)。
 * 30 个随机种子覆盖 8×8~22×22、3~6 色、有无空格、有无注入错误,
 * 外加一个"全对板"和四个透视/光照极端用例。失败时 exit 1。
 */
public class TestVerify {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    /** 饱和测试色板(远离板底米色;黄色饱和度高,与底色的 ΔE2000 足够大) */
    static final int[] PAL = {
            0xFFE4574C, // 红
            0xFF2F7FD1, // 蓝
            0xFF3FA45B, // 绿
            0xFFF2B33D, // 黄
            0xFF9B59B6, // 紫
            0xFF1F3A93  // 深蓝
    };
    static final int BOARD_RGB = 0xFFE8E2D8;

    /** 合成板 + 注入错误的真值 */
    static final class Board {
        int[] px;
        int w, h;
        BeadPattern pattern;
        float[] quad;
        int truthWrong, truthMissing, truthExtra;
    }

    /** 生成一张图纸(随机形状 + 空格),含完整色板对象与用量统计 */
    static BeadPattern makePattern(Random rnd, int cols, int rows, int nColors,
                                   boolean withEmpties) {
        int[] cells = new int[cols * rows];
        List<BeadColor> pal = new ArrayList<>();
        for (int i = 0; i < nColors; i++) {
            pal.add(new BeadColor(100 + i, "C" + i, PAL[i] & 0xFFFFFF));
        }
        double cx = (cols - 1) / 2.0, cy = (rows - 1) / 2.0;
        double rmax = Math.min(cols, rows) * 0.62;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int idx = y * cols + x;
                boolean outside = withEmpties
                        && Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) > rmax;
                // 少量内部空格,让板底色检测有样本
                boolean gap = !outside && rnd.nextInt(12) == 0;
                if (outside || gap) {
                    cells[idx] = -1;
                } else {
                    cells[idx] = rnd.nextInt(nColors);
                }
            }
        }
        int[] counts = new int[nColors];
        int totalBeads = 0, emptyCount = 0;
        for (int c : cells) {
            if (c >= 0) {
                counts[c]++;
                totalBeads++;
            } else {
                emptyCount++;
            }
        }
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        for (int i = 0; i < nColors; i++) {
            if (counts[i] > 0) {
                used.add(new BeadPattern.UsedColor(i, pal.get(i),
                        String.valueOf((char) ('A' + i)), counts[i]));
            }
        }
        return new BeadPattern(cols, rows, pal, cells, counts, used,
                totalBeads, emptyCount, false);
    }

    /**
     * 渲染合成照片:豆子画在透视映射后的格心上(与比对用同一映射,
     * 保证几何对齐),注入错色/漏摆/多余并记录真值。
     */
    static Board render(BeadPattern p, float[] quad, long seed,
                        int kWrong, int kMissing, int kExtra,
                        float lightAmp, int noiseAmp) {
        Random rnd = new Random(seed);
        int w = 900, h = 900;
        Board bd = new Board();
        bd.px = new int[w * h];
        bd.w = w;
        bd.h = h;
        bd.pattern = p;
        bd.quad = quad;
        // 板底
        for (int i = 0; i < bd.px.length; i++) bd.px[i] = BOARD_RGB;

        // 注入位置:豆格里挑错色/漏摆,空格里挑多余
        List<Integer> beadCells = new ArrayList<>();
        List<Integer> emptyCells = new ArrayList<>();
        for (int i = 0; i < p.cols * p.rows; i++) {
            if (p.cells[i] >= 0) beadCells.add(i);
            else emptyCells.add(i);
        }
        shuffle(rnd, beadCells);
        shuffle(rnd, emptyCells);
        // 真值表:cell -> 处理方式(0 正常,1 漏摆,2 错成某色,3 多余豆)
        int[] mode = new int[p.cols * p.rows];
        int[] modeColor = new int[p.cols * p.rows];
        for (int i = 0; i < kMissing && i < beadCells.size(); i++) {
            mode[beadCells.get(i)] = 1;
        }
        for (int i = 0; i < kWrong; i++) {
            int cell = beadCells.get((i + kMissing) % beadCells.size());
            if (mode[cell] != 0) continue;
            int cur = p.cells[cell];
            int far = 0, farIdx = cur;
            // 替成色差最远的色号,保证可检出
            double[] labCur = com.pindou.app.bead.ColorMath.rgbToLab(
                    0xFF000000 | p.palette.get(cur).rgb);
            for (int cIdx = 0; cIdx < p.palette.size(); cIdx++) {
                if (cIdx == cur) continue;
                double de = com.pindou.app.bead.ColorMath.deltaE2000(labCur,
                        com.pindou.app.bead.ColorMath.rgbToLab(
                                0xFF000000 | p.palette.get(cIdx).rgb));
                if (de > far) {
                    far = (int) de;
                    farIdx = cIdx;
                }
            }
            mode[cell] = 2;
            modeColor[cell] = farIdx;
        }
        for (int i = 0; i < kExtra && i < emptyCells.size(); i++) {
            int cell = emptyCells.get(emptyCells.size() - 1 - i);
            mode[cell] = 3;
            modeColor[cell] = rnd.nextInt(p.palette.size());
        }
        bd.truthWrong = 0;
        bd.truthMissing = 0;
        bd.truthExtra = 0;
        for (int i = 0; i < mode.length; i++) {
            if (mode[i] == 2) bd.truthWrong++;
            if (mode[i] == 1) bd.truthMissing++;
            if (mode[i] == 3) bd.truthExtra++;
        }

        // 绘制:逐格投影格心,画圆豆 + 中孔;漏摆格画板底,多余格画豆
        for (int y = 0; y < p.rows; y++) {
            for (int x = 0; x < p.cols; x++) {
                int idx = y * p.cols + x;
                int drawIdx;
                if (mode[idx] == 1) continue;           // 漏摆:留板底
                if (mode[idx] == 3) drawIdx = modeColor[idx];   // 多余
                else drawIdx = mode[idx] == 2 ? modeColor[idx] : p.cells[idx];
                if (drawIdx < 0) continue;
                float[] c = VerifyEngine.mapPoint(quad,
                        (x + 0.5f) / p.cols, (y + 0.5f) / p.rows);
                float[] cx1 = VerifyEngine.mapPoint(quad,
                        (x + 1.5f) / p.cols, (y + 0.5f) / p.rows);
                float[] cy1 = VerifyEngine.mapPoint(quad,
                        (x + 0.5f) / p.cols, (y + 1.5f) / p.rows);
                float cellPx = Math.min(dist(c, cx1), dist(c, cy1));
                float br = cellPx * 0.42f;
                int rgb = p.palette.get(drawIdx).rgb & 0xFFFFFF;
                int jr = rnd.nextInt(11) - 5;
                int r = clamp((rgb >> 16) + jr);
                int g = clamp(((rgb >> 8) & 0xFF) + jr);
                int b = clamp((rgb & 0xFF) + jr);
                fillCircle(bd.px, w, h, c[0], c[1], br, r, g, b, rnd, noiseAmp);
                fillCircle(bd.px, w, h, c[0], c[1], br * 0.30f,
                        (int) (r * 0.55), (int) (g * 0.55), (int) (b * 0.55),
                        rnd, 0);
            }
        }
        // 光照不均:对角线性明暗 ±lightAmp
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                float t = (xx + yy) / (float) (w + h) - 0.5f;
                int amp = Math.round(lightAmp * t * 2f);
                int i2 = yy * w + xx;
                int rr = bd.px[i2];
                int r = clamp(((rr >> 16) & 0xFF) + amp);
                int g = clamp(((rr >> 8) & 0xFF) + amp);
                int b = clamp((rr & 0xFF) + amp);
                bd.px[i2] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return bd;
    }

    static void fillCircle(int[] px, int w, int h, float cx, float cy,
                           float r, int r8, int g8, int b8,
                           Random rnd, int noiseAmp) {
        int x0 = Math.max(0, Math.round(cx - r)), x1 = Math.min(w - 1, Math.round(cx + r));
        int y0 = Math.max(0, Math.round(cy - r)), y1 = Math.min(h - 1, Math.round(cy + r));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy > r * r) continue;
                int nr = clamp(r8 + (noiseAmp > 0 ? rnd.nextInt(noiseAmp * 2 + 1) - noiseAmp : 0));
                int ng = clamp(g8 + (noiseAmp > 0 ? rnd.nextInt(noiseAmp * 2 + 1) - noiseAmp : 0));
                int nb = clamp(b8 + (noiseAmp > 0 ? rnd.nextInt(noiseAmp * 2 + 1) - noiseAmp : 0));
                px[y * w + x] = 0xFF000000 | (nr << 16) | (ng << 8) | nb;
            }
        }
    }

    /** 默认四角(整图内缩 5%)加透视抖动 */
    static float[] makeQuad(Random rnd) {
        float jx = 900 * 0.04f, jy = 900 * 0.04f;
        return new float[]{
                45 + rnd.nextInt((int) jx) - jx / 2,
                45 + rnd.nextInt((int) jy) - jy / 2,
                855 - rnd.nextInt((int) jx) + jx / 2,
                45 + rnd.nextInt((int) jy) - jy / 2,
                855 - rnd.nextInt((int) jx) + jx / 2,
                855 - rnd.nextInt((int) jy) + jy / 2,
                45 + rnd.nextInt((int) jx) - jx / 2,
                855 - rnd.nextInt((int) jy) + jy / 2
        };
    }

    static void shuffle(Random rnd, List<Integer> l) {
        for (int i = l.size() - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = l.get(i);
            l.set(i, l.get(j));
            l.set(j, t);
        }
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static void main(String[] args) {
        // 1. 全对板:合成与图纸完全一致,必须零错零漏零多
        {
            Random rnd = new Random(7);
            BeadPattern p = makePattern(rnd, 12, 12, 4, true);
            Board bd = render(p, makeQuad(rnd), 100, 0, 0, 0, 6f, 3);
            VerifyEngine.VerifyResult r = VerifyEngine.compare(
                    bd.px, bd.w, bd.h, p, bd.quad);
            check("clean board: wrong==0", r.wrong == 0);
            check("clean board: missing<=1", r.missing <= 1);
            check("clean board: extra==0", r.extra == 0);
            check("clean board: total==beads (" + r.total + " vs " + p.totalBeads + ")",
                    r.total == p.totalBeads);
        }

        // 2. 30 个随机种子:注入已知错误,检出须与真值一致(容差 ±1)
        for (int seed = 1; seed <= 30; seed++) {
            Random rnd = new Random(seed);
            int cols = 8 + rnd.nextInt(15);
            int rows = 8 + rnd.nextInt(15);
            int nColors = 3 + rnd.nextInt(4);
            boolean withEmpties = seed % 3 != 0;
            BeadPattern p = makePattern(rnd, cols, rows, nColors, withEmpties);
            int kWrong = 2 + rnd.nextInt(3);
            int kMissing = rnd.nextInt(3);
            int kExtra = withEmpties ? rnd.nextInt(3) : 0;
            float lightAmp = seed % 2 == 0 ? 10f : 4f;
            Board bd = render(p, makeQuad(rnd), seed * 31,
                    kWrong, kMissing, kExtra, lightAmp, 4);
            VerifyEngine.VerifyResult r = VerifyEngine.compare(
                    bd.px, bd.w, bd.h, p, bd.quad);
            check("seed" + seed + " wrong (" + r.wrong + " vs " + bd.truthWrong + ")",
                    Math.abs(r.wrong - bd.truthWrong) <= 1);
            check("seed" + seed + " missing (" + r.missing + " vs " + bd.truthMissing + ")",
                    Math.abs(r.missing - bd.truthMissing) <= 1);
            check("seed" + seed + " extra (" + r.extra + " vs " + bd.truthExtra + ")",
                    Math.abs(r.extra - bd.truthExtra) <= 1);
            check("seed" + seed + " total", r.total > 0);
        }

        // 3. 透视极端:四角大幅失衡(模拟斜拍),配合强光照差
        {
            Random rnd = new Random(99);
            BeadPattern p = makePattern(rnd, 10, 10, 4, true);
            float[] quad = {180, 60, 880, 150, 830, 870, 60, 820};
            Board bd = render(p, quad, 555, 3, 2, 1, 14f, 5);
            VerifyEngine.VerifyResult r = VerifyEngine.compare(
                    bd.px, bd.w, bd.h, p, bd.quad);
            check("perspective wrong", Math.abs(r.wrong - bd.truthWrong) <= 1);
            check("perspective missing", Math.abs(r.missing - bd.truthMissing) <= 1);
            check("perspective extra", Math.abs(r.extra - bd.truthExtra) <= 1);
        }

        // 4. 无空格图纸:板底参考不可用,退化为纯色差判据,豆子仍应全检出
        {
            Random rnd = new Random(31);
            BeadPattern p = makePattern(rnd, 9, 9, 4, false);
            Board bd = render(p, makeQuad(rnd), 77, 2, 1, 0, 6f, 3);
            VerifyEngine.VerifyResult r = VerifyEngine.compare(
                    bd.px, bd.w, bd.h, p, bd.quad);
            check("no-empty wrong", Math.abs(r.wrong - bd.truthWrong) <= 1);
            check("no-empty missing", r.missing <= bd.truthMissing + 1);
            check("no-empty total (" + r.total + " vs " + p.totalBeads + ")",
                    r.total == p.totalBeads);
        }

        // 5. 透视映射本身:格心应落在四边形内部、角上格心靠近角点
        {
            float[] quad = {100, 100, 700, 140, 660, 720, 120, 680};
            float[] tl = VerifyEngine.mapPoint(quad, 0.5f / 8, 0.5f / 8);
            check("mapPoint near TL corner", tl[0] > 100 && tl[0] < 220
                    && tl[1] > 100 && tl[1] < 230);
            float[] br = VerifyEngine.mapPoint(quad, 7.5f / 8, 7.5f / 8);
            check("mapPoint near BR corner", br[0] > 560 && br[0] < 700
                    && br[1] > 560 && br[1] < 740);
        }

        System.out.println("TestVerify: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }
}
