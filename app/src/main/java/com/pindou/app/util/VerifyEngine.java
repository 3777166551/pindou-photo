package com.pindou.app.util;

import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.ColorMath;

/**
 * 拍照验收的纯 Java 核心:四点透视映射 + 逐格采样读色 + CIEDE2000 就近匹配。
 * 不 import 任何 Android 类,桌面 JVM 可直接单测(qa/TestVerify 用带
 * ground truth 的合成板做量化测试)。
 *
 * 坐标约定:quad 为照片像素坐标的板四角,顺序 TL,TR,BR,BL;
 * 图纸坐标以"格"为单位,(gx+0.5, gy+0.5) 为格心。
 */
public final class VerifyEngine {

    /** 检测到豆子的判定:与图纸最近色的 CIEDE2000 小于该值 */
    public static final double DE_BEAD = 28.0;
    /** 与板底色的色差小于该值判"空格"(塑料底/漏摆) */
    public static final double DE_BOARD = 18.0;

    /** 单格判定:0 无关(空格且无多余) 1 匹配 2 错色 3 漏摆 4 多余 */
    public static final class VerifyResult {
        public int total, ok, wrong, missing, extra;
        public byte[] state;
    }

    private VerifyEngine() {
    }

    /**
     * 单位方格 (u,v)∈[0,1]² 到四边形(照片像素)的透视映射。
     * Heckbert 标准解;四角为仿射(共线退化)时自动走仿射分支。
     * 返回 float[2]{x,y}。
     */
    public static float[] mapPoint(float[] quad, float u, float v) {
        float x0 = quad[0], y0 = quad[1];
        float x1 = quad[2], y1 = quad[3];
        float x2 = quad[4], y2 = quad[5];
        float x3 = quad[6], y3 = quad[7];
        float sx = x0 - x1 + x2 - x3;
        float sy = y0 - y1 + y2 - y3;
        float a, b, c, d, e, f, g, h;
        if (Math.abs(sx) < 1e-9f && Math.abs(sy) < 1e-9f) {
            a = x1 - x0; b = x2 - x1; c = x0;
            d = y1 - y0; e = y2 - y1; f = y0;
            g = 0; h = 0;
        } else {
            float dx1 = x1 - x2, dx2 = x3 - x2;
            float dy1 = y1 - y2, dy2 = y3 - y2;
            float den = dx1 * dy2 - dx2 * dy1;
            g = (sx * dy2 - sy * dx2) / den;
            h = (sy * dx1 - sx * dy1) / den;
            a = x1 - x0 + g * x1;
            b = x3 - x0 + h * x3;
            c = x0;
            d = y1 - y0 + g * y1;
            e = y3 - y0 + h * y3;
            f = y0;
        }
        float w = g * u + h * v + 1f;
        return new float[]{(a * u + b * v + c) / w, (d * u + e * v + f) / w};
    }

    /**
     * 逐格比对。
     *
     * @param px    照片 ARGB 像素(行主序,长度 w*h)
     * @param w h   照片尺寸
     * @param p     图纸
     * @param quad  板四角(照片像素坐标)
     */
    public static VerifyResult compare(int[] px, int w, int h,
                                       BeadPattern p, float[] quad) {
        // 单格在照片里的像素尺寸:映射 (1,0)/(0,1) 两个单位向量取较短者
        float[] o = mapPoint(quad, 0, 0);
        float[] ux = mapPoint(quad, 1f / p.cols, 0);
        float[] uy = mapPoint(quad, 0, 1f / p.rows);
        float cellPx = Math.min(dist(o, ux), dist(o, uy));
        float pr = Math.max(1.5f, cellPx * 0.16f);

        // 板底色参考:图纸空格处的照片平均色(样本不足时退化为纯色差判据)
        float boardRgb = -1;
        int boardCnt = 0;
        long br = 0, bg = 0, bb = 0;
        for (int y = 0; y < p.rows && boardCnt < 16; y++) {
            for (int x = 0; x < p.cols && boardCnt < 16; x++) {
                if (p.cellAt(x, y) >= 0) continue;
                float[] c = mapPoint(quad, (x + 0.5f) / p.cols, (y + 0.5f) / p.rows);
                int rgb = patchAvg(px, w, h, c[0], c[1], pr);
                if (rgb < 0) continue;
                br += (rgb >> 16) & 0xFF;
                bg += (rgb >> 8) & 0xFF;
                bb += rgb & 0xFF;
                boardCnt++;
            }
        }
        double[] boardLab = null;
        if (boardCnt >= 4) {
            int rgb = ((int) (br / boardCnt) << 16)
                    | ((int) (bg / boardCnt) << 8) | (int) (bb / boardCnt);
            boardLab = ColorMath.rgbToLab(0xFF000000 | rgb);
        }

        int n = p.palette.size();
        double[][] palLab = new double[n][];
        for (int i = 0; i < n; i++) {
            palLab[i] = ColorMath.rgbToLab(0xFF000000 | p.palette.get(i).rgb);
        }

        VerifyResult r = new VerifyResult();
        r.state = new byte[p.cols * p.rows];
        for (int y = 0; y < p.rows; y++) {
            for (int x = 0; x < p.cols; x++) {
                int idx = y * p.cols + x;
                int expected = p.cellAt(x, y);
                float[] c = mapPoint(quad, (x + 0.5f) / p.cols, (y + 0.5f) / p.rows);
                int rgb = patchAvg(px, w, h, c[0], c[1], pr);
                if (rgb < 0) {
                    r.state[idx] = 0;
                    continue;   // 超出照片范围,不判定
                }
                double[] lab = ColorMath.rgbToLab(0xFF000000 | rgb);
                int best = -1;
                double bestDe = Double.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    double de = ColorMath.deltaE2000(lab, palLab[i]);
                    if (de < bestDe) {
                        bestDe = de;
                        best = i;
                    }
                }
                boolean bead = boardLab != null
                        ? ColorMath.deltaE2000(lab, boardLab) > DE_BOARD
                        : bestDe < DE_BEAD;
                if (expected < 0) {
                    if (bead) {
                        r.extra++;
                        r.state[idx] = 4;
                    } else {
                        r.state[idx] = 0;
                    }
                } else {
                    r.total++;
                    if (!bead) {
                        r.missing++;
                        r.state[idx] = 3;
                    } else if (best == expected) {
                        r.ok++;
                        r.state[idx] = 1;
                    } else {
                        r.wrong++;
                        r.state[idx] = 2;
                    }
                }
            }
        }
        return r;
    }

    /** 小邻域平均色;中心孔区(半径 0.35r)跳过,出照片范围返回 -1 */
    public static int patchAvg(int[] px, int w, int h, float cx, float cy, float r) {
        long rs = 0, gs = 0, bs = 0;
        int cnt = 0;
        int x0 = Math.max(0, Math.round(cx - r));
        int x1 = Math.min(w - 1, Math.round(cx + r));
        int y0 = Math.max(0, Math.round(cy - r));
        int y1 = Math.min(h - 1, Math.round(cy + r));
        if (x1 < x0 || y1 < y0) return -1;
        float hole = r * 0.35f;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy < hole * hole) continue;
                int c = px[y * w + x];
                if ((c >>> 24) < 200) continue;
                rs += (c >> 16) & 0xFF;
                gs += (c >> 8) & 0xFF;
                bs += c & 0xFF;
                cnt++;
            }
        }
        if (cnt == 0) return -1;
        return ((int) (rs / cnt) << 16) | ((int) (gs / cnt) << 8) | (int) (bs / cnt);
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
