package com.pindou.app.bead;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * 主体识别 v4:找出画面中的"主角",返回前景掩码。
 *
 * 思路(相比 FT 显著性更稳):背景一定连着画面边界,
 * 所以先用边界环聚类得到"背景参考色",再把每个像素的显著度定义为
 * 与最近背景参考色的 Lab 距离——离背景越远越像主体。
 * 这样浅色主体(白兔/白衣)只要与背景有色差就能整块保住,
 * 不像"与全图均值比距离"那样只留下高对比小色块。
 *
 * 流程:边界聚类 → 距离显著性(模糊去噪) → Otsu 阈值 × 松紧系数
 *   → 闭运算补描边缺口 → 连通域筛选 → 孔洞填充 → 膨胀安全边。
 * 配套的机制在 PatternEngine 里:主体占比异常时(满幅特写/无主体)
 * 直接不抠,不会硬伤图。
 *
 * 已验证的负优化(后台 32 图回归测试,勿再加回):
 *  - FT 显著性(与全图均值比距离):浅色大主体被挖成碎片;
 *  - 边界簇占比门槛提到 7%:主体压边的小簇被排除反而引发误抠;
 *  - 四角窗口背景规则:合法背景条带被排除,整体变保守且特写误抠;
 *  - 行/列边缘局部参考 + 测地线生长:列参考把底部渐变背景引回
 *    显著度(兔子 s87 回归),生长在背景簇含主体色时永远失效。
 */
public final class SubjectSegmenter {

    /** 边界聚类:与已有簇中心 ΔE 小于该值并入同簇 */
    private static final double CLUSTER_JOIN_DE = 16.0;
    /** 边界最多聚出的簇数 */
    private static final int MAX_BORDER_CLUSTERS = 8;
    /** 占比 <4% 的边界簇不算背景色(主体压边时会被排除)。
     *  已知局限(纯颜色统计方法的天花板,参数调整已证明无效):
     *  ① 主体触碰边界且占比大(马蹄/大衣)时会污染背景参考;
     *  ② 满幅微距(脸占满画面)可能误判;
     *  ③ 黑白照片主体灰度夹在背景灰度之间时可能被吃。
     *  这些场景靠容差滑杆 + 画笔橡皮修正,质变需上 ML 抠图模型。 */
    private static final double MIN_BORDER_FRACTION = 0.04;

    /** 上一次 findSubject 的分离度 = Otsu 类间方差 / 总方差(0~1)。
     *  实测对"满幅特写无背景"的区分度不足,暂未参与判定,仅供调试观察。 */
    public static float lastSeparation;

    /**
     * @param thresholdFactor Otsu 阈值系数:1.0=标准;>1 主体圈得更小
     *                        (背景抠得更多);<1 保得更多
     */
    public static boolean[] findSubject(int[] px, int w, int h, float thresholdFactor) {
        int n = w * h;
        boolean[] fg = new boolean[n];
        if (n < 16 || w < 5 || h < 5) return fg;

        // ---- ① Lab 缓存 + 模糊去噪 ----
        double[] L = new double[n], A = new double[n], B = new double[n];
        for (int i = 0; i < n; i++) {
            double[] lab = ColorMath.rgbToLab(px[i]);
            L[i] = lab[0];
            A[i] = lab[1];
            B[i] = lab[2];
        }
        double[] Lb = box3(box3(L, w, h), w, h);
        double[] Ab = box3(box3(A, w, h), w, h);
        double[] Bb = box3(box3(B, w, h), w, h);

        // ---- ② 背景参考色:边界环贪心聚类 ----
        ArrayList<double[]> centers = new ArrayList<>();
        int ring = 0;
        for (int i = 0; i < n; i++) {
            boolean border = (i < w) || (i >= n - w) || (i % w == 0) || (i % w == w - 1);
            if (!border) continue;
            ring++;
            double[] p = {L[i], A[i], B[i], 1};
            boolean joined = false;
            for (double[] c : centers) {
                if (dist(p, c) <= CLUSTER_JOIN_DE) {
                    double k = c[3];
                    c[0] = (c[0] * k + p[0]) / (k + 1);
                    c[1] = (c[1] * k + p[1]) / (k + 1);
                    c[2] = (c[2] * k + p[2]) / (k + 1);
                    c[3] = k + 1;
                    joined = true;
                    break;
                }
            }
            if (!joined && centers.size() < MAX_BORDER_CLUSTERS) centers.add(p);
        }
        double minCount = Math.max(4, ring * MIN_BORDER_FRACTION);
        for (int j = centers.size() - 1; j >= 0; j--) {
            if (centers.get(j)[3] < minCount) centers.remove(j);
        }
        if (centers.isEmpty()) return fg;   // 边缘全是花色,不敢定义背景

        // ---- ③ 显著性 = 与最近背景色的距离,Otsu 二值化 ----
        float[] sal = new float[n];
        for (int i = 0; i < n; i++) {
            double best = Double.MAX_VALUE;
            for (double[] c : centers) {
                double dl = Lb[i] - c[0], da = Ab[i] - c[1], db = Bb[i] - c[2];
                double d = dl * dl + da * da + db * db;
                if (d < best) best = d;
            }
            sal[i] = (float) Math.sqrt(best);
        }
        float th = otsu(sal) * thresholdFactor;
        for (int i = 0; i < n; i++) {
            fg[i] = sal[i] >= th;
        }
        lastSeparation = separation(sal);

        // ---- ④ 闭运算:先胀后缩,补上描边的小缺口,让轮廓能圈住内部 ----
        fg = dilate1(fg, w, h);
        fg = erode1(fg, w, h);

        // ---- ⑤ 连通域筛选 ----
        keepMainComponents(fg, w, h);

        // ---- ⑥ 孔洞填充:描边圈内哪怕颜色和背景一样也归主体 ----
        fillHoles(fg, w, h);

        // ---- ⑦ 膨胀安全边 ----
        return dilate1(fg, w, h);
    }

    /** 3×3 盒滤波(边界钳制取样),两遍叠加近似高斯模糊 */
    private static double[] box3(double[] src, int w, int h) {
        double[] out = new double[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double sum = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = Math.max(0, Math.min(h - 1, y + dy));
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = Math.max(0, Math.min(w - 1, x + dx));
                        sum += src[yy * w + xx];
                    }
                }
                out[y * w + x] = sum / 9;
            }
        }
        return out;
    }

    /** 类间方差 / 总方差:衡量显著度分布有没有清晰的"背景/主体"两层 */
    private static float separation(float[] v) {
        double mean = 0;
        for (float x : v) mean += x;
        mean /= v.length;
        double varAll = 0;
        for (float x : v) {
            double d = x - mean;
            varAll += d * d;
        }
        varAll /= v.length;
        if (varAll < 1e-9) return 0;
        // 用与 otsu 相同的 256 桶重算类间方差,避免二次扫描原数组
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float x : v) {
            if (x < min) min = x;
            if (x > max) max = x;
        }
        float range = max - min;
        if (range <= 1e-6f) return 0;
        int[] hist = new int[256];
        for (float x : v) {
            int b = (int) ((x - min) / range * 255f);
            if (b < 0) b = 0;
            if (b > 255) b = 255;
            hist[b]++;
        }
        double sumAll = 0;
        for (int t = 0; t < 256; t++) {
            sumAll += (double) t * hist[t];
        }
        double sumB = 0;
        int wB = 0;
        double bestVar = 0;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = v.length - wB;
            if (wF == 0) break;
            sumB += (double) t * hist[t];
            double mB = sumB / wB;
            double mF = (sumAll - sumB) / wF;
            double var = (double) wB * wF * (mB - mF) * (mB - mF);
            if (var > bestVar) bestVar = var;
        }
        // bestVar 是桶坐标下未归一化的 wB*wF*(mB-mF)^2,
        // 除以 n^2 得到真正的类间方差,再换回原值域与总方差相除
        double varBetween = (bestVar / ((double) v.length * v.length))
                * Math.pow(range / 255.0, 2);
        double ratio = varBetween / varAll;
        return (float) Math.min(1.0, ratio);
    }

    /** 经典 Otsu:256 桶直方图,类间方差最大的切点 */
    private static float otsu(float[] v) {
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float x : v) {
            if (x < min) min = x;
            if (x > max) max = x;
        }
        float range = max - min;
        if (range <= 1e-6f) return max;

        int[] hist = new int[256];
        for (float x : v) {
            int b = (int) ((x - min) / range * 255f);
            if (b < 0) b = 0;
            if (b > 255) b = 255;
            hist[b]++;
        }
        int total = v.length;
        double sumAll = 0;
        for (int t = 0; t < 256; t++) {
            sumAll += (double) t * hist[t];
        }
        double sumB = 0;
        int wB = 0;
        double bestVar = -1;
        int bestT = 128;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += (double) t * hist[t];
            double mB = sumB / wB;
            double mF = (sumAll - sumB) / wF;
            double var = (double) wB * wF * (mB - mF) * (mB - mF);
            if (var > bestVar) {
                bestVar = var;
                bestT = t;
            }
        }
        return min + range * bestT / 255f;
    }

    /** 连通域(4 邻接):保留"含画面中心的块"和面积 ≥ 最大块 22% 的块 */
    private static void keepMainComponents(boolean[] fg, int w, int h) {
        int n = w * h;
        int[] comp = new int[n];
        ArrayList<Integer> sizes = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int seed = 0; seed < n; seed++) {
            if (!fg[seed] || comp[seed] != 0) continue;
            int id = sizes.size() + 1;
            int count = 0;
            comp[seed] = id;
            queue.add(seed);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                count++;
                int x = i % w, y = i / w;
                if (x > 0 && fg[i - 1] && comp[i - 1] == 0) {
                    comp[i - 1] = id;
                    queue.add(i - 1);
                }
                if (x < w - 1 && fg[i + 1] && comp[i + 1] == 0) {
                    comp[i + 1] = id;
                    queue.add(i + 1);
                }
                if (y > 0 && fg[i - w] && comp[i - w] == 0) {
                    comp[i - w] = id;
                    queue.add(i - w);
                }
                if (y < h - 1 && fg[i + w] && comp[i + w] == 0) {
                    comp[i + w] = id;
                    queue.add(i + w);
                }
            }
            sizes.add(count);
        }
        if (sizes.isEmpty()) return;

        int maxSize = 0;
        for (int s : sizes) {
            if (s > maxSize) maxSize = s;
        }
        int minKeep = Math.max(3, maxSize * 22 / 100);
        int centerComp = comp[(h / 2) * w + (w / 2)];
        boolean[] keep = new boolean[sizes.size() + 1];
        for (int id = 1; id <= sizes.size(); id++) {
            keep[id] = (id == centerComp) || (sizes.get(id - 1) >= minKeep);
        }
        for (int i = 0; i < n; i++) {
            fg[i] = fg[i] && keep[comp[i]];
        }
    }

    /** 孔洞填充:从边界泛洪背景,泛不到的封闭"洞"并入主体 */
    private static void fillHoles(boolean[] fg, int w, int h) {
        int n = w * h;
        boolean[] outside = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            trySeed(fg, outside, queue, x, 0, w);
            trySeed(fg, outside, queue, x, h - 1, w);
        }
        for (int y = 0; y < h; y++) {
            trySeed(fg, outside, queue, 0, y, w);
            trySeed(fg, outside, queue, w - 1, y, w);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w, y = i / w;
            trySpread(fg, outside, queue, x - 1, y, w, h);
            trySpread(fg, outside, queue, x + 1, y, w, h);
            trySpread(fg, outside, queue, x, y - 1, w, h);
            trySpread(fg, outside, queue, x, y + 1, w, h);
        }
        for (int i = 0; i < n; i++) {
            if (!fg[i] && !outside[i]) fg[i] = true;
        }
    }

    private static void trySeed(boolean[] fg, boolean[] outside,
                                ArrayDeque<Integer> queue, int x, int y, int w) {
        int i = y * w + x;
        if (!fg[i] && !outside[i]) {
            outside[i] = true;
            queue.add(i);
        }
    }

    private static void trySpread(boolean[] fg, boolean[] outside,
                                  ArrayDeque<Integer> queue, int x, int y, int w, int h) {
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        int i = y * w + x;
        if (!fg[i] && !outside[i]) {
            outside[i] = true;
            queue.add(i);
        }
    }

    private static boolean[] dilate1(boolean[] fg, int w, int h) {
        boolean[] out = new boolean[fg.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (fg[i]
                        || (x > 0 && fg[i - 1]) || (x < w - 1 && fg[i + 1])
                        || (y > 0 && fg[i - w]) || (y < h - 1 && fg[i + w])) {
                    out[i] = true;
                }
            }
        }
        return out;
    }

    private static boolean[] erode1(boolean[] fg, int w, int h) {
        boolean[] out = new boolean[fg.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                boolean solid = fg[i]
                        && (x == 0 || fg[i - 1]) && (x == w - 1 || fg[i + 1])
                        && (y == 0 || fg[i - w]) && (y == h - 1 || fg[i + w]);
                out[i] = solid;
            }
        }
        return out;
    }

    private static double dist(double[] a, double[] b) {
        double dl = a[0] - b[0], da = a[1] - b[1], db = a[2] - b[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    private SubjectSegmenter() {
    }
}
