package com.pindou.app.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 拍照识别拼豆图纸:从照片里估出网格间距与行列数,并按格采样颜色。
 * 纯 Java 零依赖(输入输出都是 int[] argb),桌面 JVM 可直接测试。
 *
 * 原理:
 * 1. 用户先框住图纸区域(透视/倾斜由用户尽量摆正,框选只做粗定位);
 * 2. 对框内做亮度图,按列/行求梯度投影(网格线处颜色突变,投影出现尖峰);
 * 3. 对投影做自相关,峰最集中的 lag 就是网格间距 pitch;相位由峰位置取平均;
 * 4. 采样:每格取中心 5×5 邻域的中位均值颜色。
 */
public final class GridScanner {

    /** 检测结果:pitchX/pitchY 为格距(像素),ox/oy 为第一格中心 */
    public static final class Grid {
        public final int cols;
        public final int rows;
        public final float ox;
        public final float oy;
        public final float pitchX;
        public final float pitchY;

        Grid(int cols, int rows, float ox, float oy, float px, float py) {
            this.cols = cols;
            this.rows = rows;
            this.ox = ox;
            this.oy = oy;
            this.pitchX = px;
            this.pitchY = py;
        }
    }

    private GridScanner() {
    }

    /**
     * @param argb      整图像素
     * @param w h       整图尺寸
     * @param x0 y0 x1 y1 图纸区域(含端点)
     * @return 网格参数;检测不到规则网格时返回 null(调用方退回手工指定)
     */
    public static Grid detect(int[] argb, int w, int h, int x0, int y0, int x1, int y1) {
        x0 = Math.max(1, x0);
        y0 = Math.max(1, y0);
        x1 = Math.min(w - 2, x1);
        y1 = Math.min(h - 2, y1);
        int cw = x1 - x0 + 1;
        int ch = y1 - y0 + 1;
        if (cw < 24 || ch < 24) return null;

        float[] lum = luminance(argb, w, h);

        // 列投影:每个 x 上所有 y 的水平梯度绝对值之和(竖直网格线处现峰)
        float[] px = new float[cw];
        for (int x = x0; x <= x1; x++) {
            float s = 0;
            for (int y = y0; y <= y1; y++) {
                s += Math.abs(lum[y * w + x] - lum[y * w + x - 1]);
            }
            px[x - x0] = s;
        }
        // 行投影
        float[] py = new float[ch];
        for (int y = y0; y <= y1; y++) {
            float s = 0;
            for (int x = x0; x <= x1; x++) {
                s += Math.abs(lum[y * w + x] - lum[(y - 1) * w + x]);
            }
            py[y - y0] = s;
        }

        float pitchX = bestPitch(px);
        float pitchY = bestPitch(py);
        // 拼豆网格横竖格距相同:一个方向失手(文档标题区干扰等)就借用另一个
        if (pitchX <= 0 && pitchY <= 0) return null;
        if (pitchX <= 0) pitchX = pitchY;
        if (pitchY <= 0) pitchY = pitchX;

        // 格数:做半格偏置——框选通常会比图纸多框一点边,
        // 直接 round 会多出一格;先扣掉半个 pitch 再取整
        int cols = Math.round((cw - pitchX * 0.5f) / pitchX);
        int rows = Math.round((ch - pitchY * 0.5f) / pitchY);
        // 拼豆图纸实际常见 3~200 格,过滤离谱结果
        if (cols < 3 || rows < 3 || cols > 200 || rows > 200) return null;
        if (Math.abs(cw - cols * pitchX) > pitchX * 0.75f
                || Math.abs(ch - rows * pitchY) > pitchY * 0.75f) {
            return null;   // 尾部剩大半格以上,说明 pitch 估计不可靠
        }

        float ox = x0 + firstCenter(px, pitchX, cols);
        float oy = y0 + firstCenter(py, pitchY, rows);
        return new Grid(cols, rows, ox, oy, pitchX, pitchY);
    }

    /**
     * 自相关找周期:对投影去掉均值后,与自身平移 lag 卷积,
     * 在合法 lag 范围内取归一化相关系数最大的 lag。
     */
    static float bestPitch(float[] p) {
        int n = p.length;
        double mean = 0;
        for (float v : p) mean += v;
        mean /= n;
        float[] c = new float[n];
        for (int i = 0; i < n; i++) c[i] = (float) (p[i] - mean);

        int minLag = Math.max(4, n / 200);
        int maxLag = n / 4;
        if (maxLag <= minLag) return -1;

        // 平滑投影(照片噪点会产生 1~3px 的伪峰)
        int sm = Math.max(2, n / 150);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            double acc = 0;
            int cnt = 0;
            for (int k = -sm; k <= sm; k++) {
                int j = i + k;
                if (j >= 0 && j < n) {
                    acc += c[j];
                    cnt++;
                }
            }
            s[i] = (float) (acc / cnt);
        }
        double sd = 0;
        for (float v : s) sd += v * v;
        sd = Math.sqrt(sd / Math.max(1, n));
        final float prominence = (float) (sd * 0.6);
        final int minGap = minLag;

        // 候选峰:局部极大 + 显著性(>0.6σ)
        List<int[]> cand = new ArrayList<>();
        for (int i = 2; i < n - 2; i++) {
            if (s[i] > s[i - 1] && s[i] >= s[i + 1] && s[i] > s[i - 2]
                    && s[i] > s[i + 2] && s[i] > prominence) {
                cand.add(new int[]{i, Math.round(s[i])});
            }
        }
        // 非极大抑制:按高度降序,minLag 内只留最高峰
        java.util.Collections.sort(cand, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return b[1] - a[1];
            }
        });
        List<Integer> peaks = new ArrayList<>();
        for (int[] cd : cand) {
            boolean near = false;
            for (int pk : peaks) {
                if (Math.abs(pk - cd[0]) < minGap) {
                    near = true;
                    break;
                }
            }
            if (!near) peaks.add(cd[0]);
        }
        java.util.Collections.sort(peaks);

        // 主估计:峰间距中位数(自相关在 2x/3x 周期的 lag 上同样相关,
        // 会掉进"倍频陷阱";峰间距对它免疫)
        if (peaks.size() >= 3) {
            List<Float> gaps = new ArrayList<>();
            for (int i = 1; i < peaks.size(); i++) {
                gaps.add((float) (peaks.get(i) - peaks.get(i - 1)));
            }
            float med = median(gaps);
            if (med >= minLag && med <= maxLag) {
                // 一致性门:至少 60% 的 gap 是中位数的整数倍(±18%),
                // 否则说明峰来自随机纹理(如实物照片)而非规则网格
                int consistent = 0;
                for (float gp : gaps) {
                    float k = gp / med;
                    if (Math.abs(k - Math.round(k)) < 0.18f) consistent++;
                }
                if (consistent >= Math.max(3, (int) (gaps.size() * 0.6f))) {
                    // 漏检峰会产生 2x/3x 的 gap:按中位比值折算回基频
                    List<Float> units = new ArrayList<>();
                    for (float gp : gaps) {
                        int k = Math.max(1, Math.round(gp / med));
                        units.add(gp / k);
                    }
                    return median(units);
                }
            }
        }

        // 兜底:自相关(峰太少时)
        double best = -1;
        int bestLag = -1;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double dot = 0, e0 = 0, e1 = 0;
            for (int i = 0; i + lag < n; i++) {
                dot += c[i] * c[i + lag];
                e0 += c[i] * c[i];
                e1 += c[i + lag] * c[i + lag];
            }
            if (e0 <= 0 || e1 <= 0) continue;
            double r = dot / Math.sqrt(e0 * e1);
            if (r > best) {
                best = r;
                bestLag = lag;
            }
        }
        if (bestLag < 0 || best < 0.15) return -1;   // 相关性太弱 = 没有规则网格

        // 细化:峰位置对 bestLag 的余数中位
        List<Float> phases = new ArrayList<>();
        for (int pk : peaks) {
            float k = pk / (float) bestLag;
            if (Math.abs(k - Math.round(k)) < 0.3f) {
                phases.add(pk - Math.round(k) * (float) bestLag);
            }
        }
        if (phases.size() < 2) return bestLag;
        float phase = median(phases);
        return bestLag + phase / Math.max(1, Math.round(n / (float) bestLag));
    }

    static float median(List<Float> v) {
        List<Float> s = new ArrayList<>(v);
        java.util.Collections.sort(s);
        return s.get(s.size() / 2);
    }

    /** 第一格中心:网格线(或色块边界)永远在格边界上,故 = 首个显著峰 + 半个 pitch */
    static float firstCenter(float[] p, float pitch, int cols) {
        int n = p.length;
        double mean = 0;
        for (float v : p) mean += v;
        mean /= n;
        for (int i = 1; i < n - 1; i++) {
            if (p[i] > mean && p[i] >= p[i - 1] && p[i] >= p[i + 1]) {
                float c = i + pitch * 0.5f;
                return Math.max(pitch * 0.5f, Math.min(n - pitch * 0.5f, c));
            }
        }
        return pitch * 0.5f;
    }

    /**
     * 按网格采样(含边缘背景裁剪):返回裁剪后的网格(行优先),
     * 实际 cols/rows 写入 outDims[0]/outDims[1]。
     * 框选常会把图纸外的空白/网站底色框进来,形成"整行/列都是背景"的假格子。
     * 背景色 = 采样结果最外圈出现最多的颜色;某条边 ≥90% 是它、且它占全图 ≥30%
     * 时才裁该边(避免误裁纯色边框的图纸);每边最多裁 35%。
     */
    public static int[] sample(int[] argb, int w, int h, Grid g, int[] outDims) {
        int cols = g.cols, rows = g.rows;
        int[] raw = new int[cols * rows];
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                float cx = g.ox + i * g.pitchX;
                float cy = g.oy + j * g.pitchY;
                raw[j * cols + i] = sampleCell(argb, w, h, cx, cy,
                        Math.min(g.pitchX, g.pitchY) * 0.22f);
            }
        }
        return trimBackground(raw, cols, rows, outDims);
    }

    /** 边缘背景裁剪,返回裁剪后网格(行优先) */
    public static int[] trimBackground(int[] cells, int cols, int rows) {
        return trimBackground(cells, cols, rows, new int[2]);
    }

    /** 边缘背景裁剪:实际裁剪后的 cols/rows 写入 outColsRows */
    public static int[] trimBackground(int[] cells, int cols, int rows, int[] outColsRows) {
        // 量化到 4bit/通道,抗噪
        int n = cols * rows;
        int[] q = new int[n];
        for (int i = 0; i < n; i++) {
            int p = cells[i];
            q[i] = (((p >> 16) & 0xF0) << 12) | (((p >> 8) & 0xF0) << 8)
                    | ((p & 0xF0) << 4);
        }
        // 边界主色 = 四条边并集里出现最多的量化色
        java.util.Map<Integer, Integer> cnt = new java.util.HashMap<>();
        for (int x = 0; x < cols; x++) {
            add(cnt, q[x]);
            add(cnt, q[(rows - 1) * cols + x]);
        }
        for (int y = 0; y < rows; y++) {
            add(cnt, q[y * cols]);
            add(cnt, q[y * cols + cols - 1]);
        }
        int bg = 0, bgMax = 0;
        for (java.util.Map.Entry<Integer, Integer> e : cnt.entrySet()) {
            if (e.getValue() > bgMax) {
                bgMax = e.getValue();
                bg = e.getKey();
            }
        }
        int bgTotal = 0;
        for (int v : q) {
            if (v == bg) bgTotal++;
        }
        if (bgTotal < n * 0.3) {
            if (outColsRows != null) {
                outColsRows[0] = cols;
                outColsRows[1] = rows;
            }
            return cells;
        }

        int x0 = 0, x1 = cols - 1, y0 = 0, y1 = rows - 1;
        int maxCutX = (int) (cols * 0.35f), maxCutY = (int) (rows * 0.35f);
        // 左
        while (x0 < x1 && x0 < maxCutX && edgeCol(q, cols, rows, x0, bg, 0.9f)) x0++;
        while (x1 > x0 && (cols - 1 - x1) < maxCutX && edgeCol(q, cols, rows, x1, bg, 0.9f)) x1--;
        while (y0 < y1 && y0 < maxCutY && edgeRow(q, cols, rows, y0, bg, 0.9f)) y0++;
        while (y1 > y0 && (rows - 1 - y1) < maxCutY && edgeRow(q, cols, rows, y1, bg, 0.9f)) y1--;
        if (x0 == 0 && y0 == 0 && x1 == cols - 1 && y1 == rows - 1) {
            if (outColsRows != null) {
                outColsRows[0] = cols;
                outColsRows[1] = rows;
            }
            return cells;
        }
        int nw = x1 - x0 + 1;
        int nh = y1 - y0 + 1;
        if (nw < 3 || nh < 3) {
            if (outColsRows != null) {
                outColsRows[0] = cols;
                outColsRows[1] = rows;
            }
            return cells;
        }
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            System.arraycopy(cells, (y0 + y) * cols + x0, out, y * nw, nw);
        }
        if (outColsRows != null) {
            outColsRows[0] = nw;
            outColsRows[1] = nh;
        }
        return out;
    }

    private static void add(java.util.Map<Integer, Integer> m, int k) {
        Integer v = m.get(k);
        m.put(k, v == null ? 1 : v + 1);
    }

    private static boolean edgeCol(int[] q, int cols, int rows, int x, int bg, float ratio) {
        int hit = 0;
        for (int y = 0; y < rows; y++) {
            if (q[y * cols + x] == bg) hit++;
        }
        return hit >= rows * ratio;
    }

    private static boolean edgeRow(int[] q, int cols, int rows, int y, int bg, float ratio) {
        int hit = 0;
        for (int x = 0; x < cols; x++) {
            if (q[y * cols + x] == bg) hit++;
        }
        return hit >= cols * ratio;
    }

    static int sampleCell(int[] argb, int w, int h, float cx, float cy, float r) {
        int x0 = Math.max(0, Math.round(cx - r));
        int y0 = Math.max(0, Math.round(cy - r));
        int x1 = Math.min(w - 1, Math.round(cx + r));
        int y1 = Math.min(h - 1, Math.round(cy + r));
        long sr = 0, sg = 0, sb = 0;
        int n = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int p = argb[y * w + x];
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0;
        return 0xFF000000 | ((int) (sr / n) << 16) | ((int) (sg / n) << 8) | (int) (sb / n);
    }

    static float[] luminance(int[] argb, int w, int h) {
        float[] l = new float[w * h];
        for (int i = 0; i < l.length; i++) {
            int p = argb[i];
            l[i] = 0.299f * ((p >> 16) & 0xFF)
                    + 0.587f * ((p >> 8) & 0xFF)
                    + 0.114f * (p & 0xFF);
        }
        return l;
    }
}
