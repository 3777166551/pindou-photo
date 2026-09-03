package com.pindou.app.util;

import java.util.Arrays;

/**
 * 选框水印修复(纯 Java,零 Android 依赖,桌面 JVM 可直接测)。
 * 两段式:
 *  1. 掩码检测:取选框外 3px 环带的中值色为"背景参考",框内与中值色差超阈值的
 *     像素视为水印笔画(再膨胀 3px 盖住抗锯齿边)。选框内容与周围一致(框错了/
 *     只有噪声)时直接不动,背景纹理、结构边缘全部原样保留;
 *  2. 填充:只把掩码像素视为未知,用周围真实像素做"洋葱剥皮"逐层内推,
 *     再雅可比松弛过渡。水印占满选框(>65%)或背景太花时退化为整框填充。
 * 拼豆图纸最终只取低频颜色(≤116×116 格),平滑填充转图纸后基本无痕。
 */
public final class WatermarkRemover {

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};
    /** 与环带中值的最大通道差超过该值 => 疑似水印像素 */
    private static final int MASK_THRESHOLD = 28;
    /** 环带宽度(px) */
    private static final int RING = 3;
    /** 掩码膨胀半径(px),盖住水印抗锯齿过渡边 */
    private static final int DILATE = 3;

    private WatermarkRemover() {
    }

    /**
     * @param argb        图像像素(原地修改)
     * @param w h         图像尺寸
     * @param x0 y0 x1 y1 修复框(像素坐标,含端点;自动钳制,最小 3×3)
     */
    public static void remove(int[] argb, int w, int h, int x0, int y0, int x1, int y1) {
        x0 = Math.max(1, x0);
        y0 = Math.max(1, y0);
        x1 = Math.min(w - 2, x1);
        y1 = Math.min(h - 2, y1);
        if (x1 - x0 < 2 || y1 - y0 < 2) return;
        int rw = x1 - x0 + 1;
        int rh = y1 - y0 + 1;
        int n = rw * rh;

        // ---- 1) 掩码:与环带中值色比对 ----
        int[] med = ringMedian(argb, w, h, x0, y0, x1, y1);
        boolean[] mask = new boolean[n];
        int hits = 0;
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int p = argb[(y0 + y) * w + (x0 + x)];
                int dev = Math.max(Math.max(
                        Math.abs(((p >> 16) & 0xFF) - med[0]),
                        Math.abs(((p >> 8) & 0xFF) - med[1])),
                        Math.abs((p & 0xFF) - med[2]));
                if (dev > MASK_THRESHOLD) {
                    mask[y * rw + x] = true;
                    hits++;
                }
            }
        }
        double frac = hits / (double) n;
        if (frac < 0.02) {
            return;   // 选框内与周围一致:框错了或只有噪声,不动图
        }
        if (frac > 0.65) {
            Arrays.fill(mask, true);   // 水印占满/背景太花:整框填充
        } else {
            dilate(mask, rw, rh, DILATE);
        }

        // ---- 2) 洋葱剥皮填充(unknown = 掩码;未掩码像素保持原色) ----
        boolean[] unknown = mask.clone();
        boolean[] next = new boolean[n];
        int[] fill = new int[n];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                if (!mask[y * rw + x]) {
                    fill[y * rw + x] = argb[(y0 + y) * w + (x0 + x)];
                }
            }
        }
        int filled = 0;
        while (filled < n) {
            System.arraycopy(unknown, 0, next, 0, n);
            int now = 0;
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < rw; x++) {
                    int i = y * rw + x;
                    if (!unknown[i]) continue;
                    int gx = x0 + x;
                    int gy = y0 + y;
                    int sr = 0, sg = 0, sb = 0, cnt = 0;
                    for (int d = 0; d < 8; d++) {
                        int nx = gx + DX[d];
                        int ny = gy + DY[d];
                        int cp;
                        if (nx < x0 || nx > x1 || ny < y0 || ny > y1) {
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                            cp = argb[ny * w + nx];          // 选框外 = 真实像素
                        } else {
                            int j = (ny - y0) * rw + (nx - x0);
                            if (unknown[j]) continue;
                            cp = fill[j];                    // 已填充像素
                        }
                        sr += (cp >> 16) & 0xFF;
                        sg += (cp >> 8) & 0xFF;
                        sb += cp & 0xFF;
                        cnt++;
                    }
                    if (cnt > 0) {
                        fill[i] = 0xFF000000
                                | ((sr / cnt) << 16) | ((sg / cnt) << 8) | (sb / cnt);
                        next[i] = false;
                        now++;
                    }
                }
            }
            boolean[] t = unknown;
            unknown = next;
            next = t;
            if (now == 0) break;
            filled += now;
        }

        // ---- 3) 雅可比松弛(只动掩码像素,边界保持真实像素) ----
        int iters = Math.min(120, Math.max(40, (rw + rh) / 2));
        int[] a = new int[n];
        int[] b = new int[n];
        System.arraycopy(fill, 0, a, 0, n);
        for (int it = 0; it < iters; it++) {
            int[] src = (it & 1) == 0 ? a : b;
            int[] dst = (it & 1) == 0 ? b : a;
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < rw; x++) {
                    int i = y * rw + x;
                    if (!mask[i]) {
                        dst[i] = src[i];
                        continue;
                    }
                    int gx = x0 + x;
                    int gy = y0 + y;
                    int sr = 0, sg = 0, sb = 0, cnt = 0;
                    for (int d = 0; d < 4; d++) {
                        int nx = gx + DX[d];
                        int ny = gy + DY[d];
                        int cp;
                        if (nx < x0 || nx > x1 || ny < y0 || ny > y1) {
                            cp = argb[ny * w + nx];
                        } else {
                            cp = src[(ny - y0) * rw + (nx - x0)];
                        }
                        sr += (cp >> 16) & 0xFF;
                        sg += (cp >> 8) & 0xFF;
                        sb += cp & 0xFF;
                        cnt++;
                    }
                    dst[i] = 0xFF000000
                            | ((sr / cnt) << 16) | ((sg / cnt) << 8) | (sb / cnt);
                }
            }
        }
        int[] result = (iters % 2 == 0) ? a : b;
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                if (mask[y * rw + x]) {
                    argb[(y0 + y) * w + (x0 + x)] = result[y * rw + x];
                }
            }
        }
    }

    /** 选框外 RING px 环带各通道中值(选框贴图边时用剩余侧) */
    private static int[] ringMedian(int[] argb, int w, int h,
                                    int x0, int y0, int x1, int y1) {
        int rx0 = Math.max(0, x0 - RING), ry0 = Math.max(0, y0 - RING);
        int rx1 = Math.min(w - 1, x1 + RING), ry1 = Math.min(h - 1, y1 + RING);
        int cnt = (rx1 - rx0 + 1) * (ry1 - ry0 + 1) - (x1 - x0 + 1) * (y1 - y0 + 1);
        int[] rs = new int[Math.max(1, cnt)];
        int[] gs = new int[rs.length];
        int[] bs = new int[rs.length];
        int k = 0;
        for (int y = ry0; y <= ry1; y++) {
            for (int x = rx0; x <= rx1; x++) {
                if (x >= x0 && x <= x1 && y >= y0 && y <= y1) continue;
                int p = argb[y * w + x];
                rs[k] = (p >> 16) & 0xFF;
                gs[k] = (p >> 8) & 0xFF;
                bs[k] = p & 0xFF;
                k++;
            }
        }
        if (k == 0) return new int[]{128, 128, 128};
        Arrays.sort(rs, 0, k);
        Arrays.sort(gs, 0, k);
        Arrays.sort(bs, 0, k);
        int m = k / 2;
        return new int[]{rs[m], gs[m], bs[m]};
    }

    /** 掩码膨胀 r 轮(8 邻域) */
    private static void dilate(boolean[] mask, int rw, int rh, int r) {
        for (int round = 0; round < r; round++) {
            int[] src = new int[mask.length];
            for (int i = 0; i < mask.length; i++) src[i] = mask[i] ? 1 : 0;
            for (int y = 0; y < rh; y++) {
                for (int x = 0; x < rw; x++) {
                    if (src[y * rw + x] == 1) continue;
                    boolean hit = false;
                    for (int d = 0; d < 8 && !hit; d++) {
                        int nx = x + DX[d];
                        int ny = y + DY[d];
                        if (nx >= 0 && ny >= 0 && nx < rw && ny < rh
                                && src[ny * rw + nx] == 1) hit = true;
                    }
                    mask[y * rw + x] = hit;
                }
            }
        }
    }
}
