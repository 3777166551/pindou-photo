package com.pindou.app.util;

import java.util.List;

/**
 * 拍照识别拼豆图纸:从照片里估出网格间距/旋转角与行列数,并按格采样颜色。
 * 纯 Java 零依赖(输入输出都是 int[] argb),桌面 JVM 可直接测试。
 *
 * 原理(v2.63 重写,实物成品照实测调优;真实径向剖面:豆心仅暗 3.5%、
 * 无硬高光,格间梯度主要来自相邻豆色差):
 * 1. 用户先框住图纸区域(粗定位);
 * 2. 方向梯度图:|gx| 服务竖线(X 投影)、|gy| 服务横线(Y 投影)——
 *    实物豆粒的纹理各向同性,混用全梯度会把周期信号淹掉;
 * 3. 每行/列投影前先做行内归一,高纹理区不再主导投影;
 * 4. 旋转角:剪切投影方差最大处(±6°,0.3° 步进+细化);
 * 5. 格距:两遍峰值法——显著峰(>0.6σ)取峰距中位数,再以 0.55×粗格距
 *    窗口抑峰(豆孔/高光的伴生小峰压不掉会污染),一致性门 ≥60%,
 *    漏检峰的 2x/3x 峰距按整数比折回基频,峰位最小二乘精修(斜率稳健);
 * 6. 相位:峰位 mod 格距聚类 + 半格解释变体(峰族可能是格线也可能是
 *    格心,先验无法判定),±1.5px/0.5px 细扫,格内方差最小者胜——
 *    骑缝相位混两色方差暴涨两个数量级,鉴别力足够;
 * 7. 采样:按旋转后的格心取内圈 52% 环带(跳过豆孔),亮度截尾均值
 *    (丢最暗 25% 缝隙阴影 + 最亮 15% 高光),贴近豆身本色;
 * 8. 边缘背景裁剪:外圈主色(量化 4bit+容差)占边 ≥90% 且占全图 ≥30%
 *    (或四角近同色)时裁边,每边最多裁 35%。
 */
public final class GridScanner {

    /** 检测结果:pitchX/pitchY 为格距(像素),ox/oy 为第一格中心,angle 为网格旋转角 */
    public static final class Grid {
        public final int cols;
        public final int rows;
        public final float ox;
        public final float oy;
        public final float pitchX;
        public final float pitchY;
        /** 网格旋转角(弧度,图像坐标系;0 = 横平竖直) */
        public final double angle;

        Grid(int cols, int rows, float ox, float oy, float px, float py, double angle) {
            this.cols = cols;
            this.rows = rows;
            this.ox = ox;
            this.oy = oy;
            this.pitchX = px;
            this.pitchY = py;
            this.angle = angle;
        }
    }

    private GridScanner() {
    }

    /** 临时调试开关(排查用,生产恒 false) */
    static final boolean DEBUG = Boolean.getBoolean("pindou.gridscan.debug");

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

        // 亮度 + 方向梯度(裁剪区内)
        float[] lum = new float[cw * ch];
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                int p = argb[(y0 + y) * w + (x0 + x)];
                lum[y * cw + x] = 0.299f * ((p >> 16) & 0xFF)
                        + 0.587f * ((p >> 8) & 0xFF) + 0.114f * (p & 0xFF);
            }
        }
        float[] gxm = new float[cw * ch];
        float[] gym = new float[cw * ch];
        for (int y = 1; y < ch - 1; y++) {
            for (int x = 1; x < cw - 1; x++) {
                gxm[y * cw + x] = Math.abs(lum[y * cw + x + 1] - lum[y * cw + x - 1]);
                gym[y * cw + x] = Math.abs(lum[(y + 1) * cw + x] - lum[(y - 1) * cw + x]);
            }
        }

        // 旋转角:X 投影(竖线)的剪切方差最大处;网格对齐时峰最尖。
        // ±12°(v2.70 放宽,原 ±6°):手持拍成品 6° 以上的歪并不罕见;
        // 作品内 45° 树枝纹理远在范围外不会误锁。三段式控制开销:
        // 0.6° 粗扫(41 次)→ 0.1°(13 次)→ 0.05° 终细化(5 次)
        double bestTheta = 0, bestVar = -1;
        for (int deg100 = -1200; deg100 <= 1200; deg100 += 60) {
            double th = Math.toRadians(deg100 / 100.0);
            double var = shearVariance(gxm, cw, ch, Math.tan(th), true);
            if (var > bestVar) {
                bestVar = var;
                bestTheta = th;
            }
        }
        double t0 = bestTheta - Math.toRadians(0.6), t1 = bestTheta + Math.toRadians(0.6);
        for (int i = 0; i <= 12; i++) {
            double th = t0 + (t1 - t0) * i / 12.0;
            double var = shearVariance(gxm, cw, ch, Math.tan(th), true);
            if (var > bestVar) {
                bestVar = var;
                bestTheta = th;
            }
        }
        double u0 = bestTheta - Math.toRadians(0.1), u1 = bestTheta + Math.toRadians(0.1);
        for (int i = 0; i <= 4; i++) {
            double th = u0 + (u1 - u0) * i / 4.0;
            double var = shearVariance(gxm, cw, ch, Math.tan(th), true);
            if (var > bestVar) {
                bestVar = var;
                bestTheta = th;
            }
        }

        // 投影(带去旋剪切 + 行内归一)
        float[] px = axisProjection(gxm, cw, ch, Math.tan(bestTheta), true);
        float[] py = axisProjection(gym, cw, ch, -Math.tan(bestTheta), false);

        // 格距(峰只出格距,斜率稳健);显著性 0.6σ 失手降到 0.45σ 重试
        float[] gx = gridPitchPhase(px, 0.6f);
        if (gx == null) gx = gridPitchPhase(px, 0.45f);
        float[] gy = gridPitchPhase(py, 0.6f);
        if (gy == null) gy = gridPitchPhase(py, 0.45f);
        float pitchX = gx == null ? -1 : gx[0];
        float pitchY = gy == null ? -1 : gy[0];
        // 拼豆网格横竖格距相同:一个方向失手就借用另一个的格距
        // (只借格距,不借相位——两轴坐标系不通用)
        if (pitchX <= 0 && pitchY <= 0) {
            if (DEBUG) System.out.printf("[GridScanner] FAIL no pitch: gx=%s gy=%s%n",
                    gx == null ? "null" : java.util.Arrays.toString(gx),
                    gy == null ? "null" : java.util.Arrays.toString(gy));
            return null;
        }
        if (pitchX > 0 && pitchY > 0
                && Math.max(pitchX, pitchY) / Math.min(pitchX, pitchY) > 1.35f) {
            return null;   // 两方向差太多,至少一个被纹理带偏
        }
        if (pitchX <= 0) pitchX = pitchY;
        if (pitchY <= 0) pitchY = pitchX;

        // 相位:全局密集扫描。相位的多解性(格线族/格心族/峰族系统
        // 偏移)用候选法反复翻车,直接 fx/fy ∈ [0,格距) 步进 1px 全扫
        // + 胜点 ±1px/0.25px 抛光,保证覆盖真值邻域;判据 = 内部格
        // 中位类内方差(骑缝混两色方差暴涨两个数量级)。
        // 复杂度 ~(格距²)×81格×40px,18px 格距约 1.3M 像素读,毫秒级
        double bestScore = Double.MAX_VALUE;
        float bestFx = 0, bestFy = 0;
        int step = Math.max(2, Math.round(pitchX / 18f));
        for (float fx = 0; fx < pitchX; fx += step) {
            for (float fy = 0; fy < pitchY; fy += step) {
                double score = gridScore(argb, w, h,
                        x0 + fx, y0 + fy, pitchX, pitchY, bestTheta, cw, ch);
                if (score < bestScore) {
                    bestScore = score;
                    bestFx = fx;
                    bestFy = fy;
                }
            }
        }
        // 抛光:胜点 ±1px,0.25px 步进
        float px0 = bestFx, py0 = bestFy;
        for (float ddx = -1f; ddx <= 1f; ddx += 0.25f) {
            for (float ddy = -1f; ddy <= 1f; ddy += 0.25f) {
                double score = gridScore(argb, w, h,
                        x0 + px0 + ddx, y0 + py0 + ddy,
                        pitchX, pitchY, bestTheta, cw, ch);
                if (score < bestScore) {
                    bestScore = score;
                    bestFx = px0 + ddx;
                    bestFy = py0 + ddy;
                }
            }
        }
        if (DEBUG) {
            System.out.printf("[GridScanner] theta=%.2f° pitch=%.2f/%.2f fc=(%.2f,%.2f) score=%.6f%n",
                    Math.toDegrees(bestTheta), pitchX, pitchY, bestFx, bestFy, bestScore);
        }
        // 首格中心回绕到 ≥ -0.3 格距的最小值
        // (边界格线必漏检,宁可多含余量格,由 trimBackground 裁掉)
        float fcX = bestFx;
        while (fcX - pitchX >= -pitchX * 0.3f) fcX -= pitchX;
        float fcY = bestFy;
        while (fcY - pitchY >= -pitchY * 0.3f) fcY -= pitchY;
        // 格数从首格中心数到区域末
        int cols = Math.max(3, (int) Math.floor((cw - 1 - fcX) / pitchX) + 1);
        int rows = Math.max(3, (int) Math.floor((ch - 1 - fcY) / pitchY) + 1);
        // 拼豆图纸实际常见 3~200 格,过滤离谱结果
        if (cols < 3 || rows < 3 || cols > 200 || rows > 200) {
            if (DEBUG) System.out.printf("[GridScanner] FAIL dims %dx%d fc=(%.1f,%.1f) pitch=%.1f%n",
                    cols, rows, fcX, fcY, pitchX);
            return null;
        }

        return new Grid(cols, rows, x0 + fcX, y0 + fcY, pitchX, pitchY, bestTheta);
    }

    /**
     * 轴投影:rows=true 时对每行做 tan 剪切再按列累加(X 投影),
     * 每行先归一(该行总梯度=1),高纹理区不再主导;
     * rows=false 时对每列做剪切按行累加(Y 投影)。
     */
    static float[] axisProjection(float[] g, int cw, int ch, double tan, boolean rows) {
        if (rows) {
            float[] out = new float[cw];
            double cy = ch * 0.5;
            for (int y = 0; y < ch; y++) {
                int sh = (int) Math.round(tan * (y - cy));
                int xs = Math.max(0, -sh), xe = Math.min(cw, cw - sh);
                double sum = 0;
                int row = y * cw;
                for (int x = xs; x < xe; x++) sum += g[row + x];
                if (sum <= 1e-6) continue;
                double inv = 1.0 / sum;
                for (int x = xs; x < xe; x++) out[x + sh] += (float) (g[row + x] * inv);
            }
            return out;
        } else {
            float[] out = new float[ch];
            double cx = cw * 0.5;
            for (int x = 0; x < cw; x++) {
                int sh = (int) Math.round(tan * (x - cx));
                int ys = Math.max(0, -sh), ye = Math.min(ch, ch - sh);
                double sum = 0;
                for (int y = ys; y < ye; y++) sum += g[y * cw + x];
                if (sum <= 1e-6) continue;
                double inv = 1.0 / sum;
                for (int y = ys; y < ye; y++) out[y + sh] += (float) (g[y * cw + x] * inv);
            }
            return out;
        }
    }

    private static double shearVariance(float[] g, int cw, int ch, double tan, boolean rows) {
        float[] p = axisProjection(g, cw, ch, tan, rows);
        double mean = 0;
        for (float v : p) mean += v;
        mean /= p.length;
        double var = 0;
        for (float v : p) {
            double d = v - mean;
            var += d * d;
        }
        return var / p.length;
    }

    /**
     * 两遍峰值法找格距,峰位写入 peaksOut;判定无规则网格返回 null:
     * 1. 平滑投影上取显著局部峰(>0.6σ),峰距中位数出粗格距;
     * 2. 用 0.55×粗格距做窗口抑峰(每窗只留最强峰)——豆孔/高光的
     *    伴生小峰离真峰只有几像素,一遍法的 minLag 窗口压不掉;
     * 3. 一致性门:≥60% 峰距是中位数的整数倍,否则判定无规则网格;
     * 4. 漏检峰产生 2x/3x 峰距,按整数比折算回基频取中位;
     * 5. 峰位最小二乘拟合位置=a+k×格距出格距——峰位有 ±2~4px 的
     *    系统偏移(纹理不对称,局部极大倒向强的一侧),但偏差对同一
     *    投影近似恒定,LSQ 斜率(格距)不受影响。
     */
    static float[] gridPitchPhase(float[] p, float promK) {
        int n = p.length;
        double mean = 0;
        for (float v : p) mean += v;
        mean /= n;
        int minLag = Math.max(4, n / 200), maxLag = n / 4;
        if (maxLag <= minLag) return null;
        // 平滑投影(照片噪点会产生 1~3px 的伪峰)
        int sm = Math.max(1, n / 400);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            double acc = 0;
            int cnt = 0;
            for (int k = -sm; k <= sm; k++) {
                int j = i + k;
                if (j >= 0 && j < n) {
                    acc += p[j] - mean;
                    cnt++;
                }
            }
            s[i] = (float) (acc / cnt);
        }
        double sd = 0;
        for (float v : s) sd += v * v;
        sd = Math.sqrt(sd / Math.max(1, n));
        final double prominence = sd * promK;

        // 第一遍:所有显著局部峰(按高度降序备第二遍用)
        List<int[]> c1 = new java.util.ArrayList<>();
        for (int i = 2; i < n - 2; i++) {
            if (s[i] > prominence && s[i] >= s[i - 1] && s[i] > s[i + 1]
                    && s[i] > s[i - 2] && s[i] > s[i + 2]) {
                c1.add(new int[]{i, Math.round(s[i])});
            }
        }
        if (c1.size() < 3) return null;
        List<Float> g1 = new java.util.ArrayList<>();
        for (int i = 1; i < c1.size(); i++) g1.add((float) (c1.get(i)[0] - c1.get(i - 1)[0]));
        java.util.Collections.sort(g1);
        float med1 = g1.get(g1.size() / 2);
        if (med1 < minLag || med1 > maxLag) return null;

        // 第二遍:0.55×粗格距窗口内只留最强峰
        java.util.Collections.sort(c1, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return b[1] - a[1];
            }
        });
        List<Integer> peaks = new java.util.ArrayList<>();
        int win = Math.max(minLag, Math.round(med1 * 0.55f));
        for (int[] cd : c1) {
            boolean near = false;
            for (int pk : peaks) {
                if (Math.abs(pk - cd[0]) < win) {
                    near = true;
                    break;
                }
            }
            if (!near) peaks.add(cd[0]);
        }
        java.util.Collections.sort(peaks);
        if (peaks.size() < 3) return null;

        List<Float> gaps = new java.util.ArrayList<>();
        for (int i = 1; i < peaks.size(); i++) {
            gaps.add((float) (peaks.get(i) - peaks.get(i - 1)));
        }
        java.util.Collections.sort(gaps);
        float med = gaps.get(gaps.size() / 2);
        if (med < minLag || med > maxLag * 2) return null;
        int consistent = 0;
        for (float gp : gaps) {
            float k = gp / med;
            if (Math.abs(k - Math.round(k)) < 0.15f) consistent++;
        }
        if (consistent < Math.max(3, (int) (gaps.size() * 0.6f))) return null;
        List<Float> units = new java.util.ArrayList<>();
        for (float gp : gaps) {
            int k = Math.max(1, Math.round(gp / med));
            units.add(gp / k);
        }
        java.util.Collections.sort(units);
        float pitch = units.get(units.size() / 2);

        // 精修:峰位最小二乘拟合 位置=a+k×格距出格距
        float fitted = lsqPitch(peaks, peaks.get(0), pitch);
        if (fitted > 0) pitch = fitted;
        // 拒检:折叠对比度太弱 = 纹理不是网格(噪声图 <0.1,真网格 >0.5)
        if (foldContrast(p, pitch) < 0.15f) return null;
        return new float[]{pitch};
    }

    /** 峰位最小二乘:位置 = a + k×格距,k 由粗格距取整;返回拟合格距,-1 放弃 */
    static float lsqPitch(List<Integer> peaks, int p0, float rough) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int m = peaks.size();
        for (int pk : peaks) {
            double k = Math.round((pk - p0) / (double) rough);
            sx += k;
            sy += pk;
            sxx += k * k;
            sxy += k * pk;
        }
        double den = m * sxx - sx * sx;
        if (Math.abs(den) < 1e-9) return -1;
        double slope = (m * sxy - sx * sy) / den;
        if (slope < rough * 0.9 || slope > rough * 1.1) return -1;
        return (float) slope;
    }

    /**
     * 网格自洽度(组合判据,越小越好):
     * 类内方差(纯度) + 0.5×格内梯度/格间梯度。
     * 真相位:格心对齐 → 每格内部颜色纯(类内小),格间是色差边界
     * (格间梯度大)——两项同时最优。骑缝相位:混两色(类内大)且
     * "格间"采在豆心(格间梯度小)——两项同时恶化,谷更陡。
     * 在距边 1.5 格距的内部区抽 ≤81 格,类内取中位数抗坏格。
     */
    private static double gridScore(int[] argb, int w, int h, float ox, float oy,
                                    float pitchX, float pitchY, double angle,
                                    int cw, int ch) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        float mx = pitchX * 1.5f, my = pitchY * 1.5f;
        int i0 = (int) Math.ceil(mx / pitchX), i1 = (int) Math.floor((cw - 1 - mx) / pitchX);
        int j0 = (int) Math.ceil(my / pitchY), j1 = (int) Math.floor((ch - 1 - my) / pitchY);
        if (i1 < i0 || j1 < j0) return Double.MAX_VALUE;
        int stepX = Math.max(1, (i1 - i0) / 8);
        int stepY = Math.max(1, (j1 - j0) / 8);
        List<Double> vars = new java.util.ArrayList<>();
        double inGrad = 0, bndGrad = 0;
        int gn = 0;
        float rx = pitchX * 0.22f, ry = pitchY * 0.22f;
        for (int j = j0; j <= j1; j += stepY) {
            for (int i = i0; i <= i1; i += stepX) {
                float cx = ox + (float) (i * pitchX * cos - j * pitchY * sin);
                float cy = oy + (float) (i * pitchX * sin + j * pitchY * cos);
                double[] vm = cellVarMean(argb, w, h, cx, cy, rx, ry);
                if (vm == null) continue;
                vars.add(vm[0]);
                inGrad += vm[2];
                if (i + stepX <= i1) {
                    float bx = cx + (float) (stepX * pitchX * cos) * 0.5f;
                    float by = cy + (float) (stepX * pitchX * sin) * 0.5f;
                    bndGrad += lumGradAt(argb, w, h, bx, by, pitchX * 0.18f);
                    gn++;
                }
                if (j + stepY <= j1) {
                    float bx = cx - (float) (stepY * pitchY * sin) * 0.5f;
                    float by = cy + (float) (stepY * pitchY * cos) * 0.5f;
                    bndGrad += lumGradAt(argb, w, h, bx, by, pitchY * 0.18f);
                    gn++;
                }
            }
        }
        if (vars.size() < 4 || gn == 0) return Double.MAX_VALUE;
        java.util.Collections.sort(vars);
        double within = vars.get(vars.size() / 2);
        double ratio = (inGrad / vars.size()) / (bndGrad / gn + 1e-6);
        return within + 0.5 * ratio;
    }

    /** 点邻域亮度梯度均值(±r 窗内的 |中心差| 平均) */
    private static double lumGradAt(int[] argb, int w, int h, float cx, float cy, float r) {
        int x0 = Math.max(1, Math.round(cx - r));
        int y0 = Math.max(1, Math.round(cy - r));
        int x1 = Math.min(w - 2, Math.round(cx + r));
        int y1 = Math.min(h - 2, Math.round(cy + r));
        if (x1 < x0 || y1 < y0) return 0;
        double sum = 0;
        int n = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                sum += lumGradPx(argb, w, x, y);
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    private static double lumGradPx(int[] argb, int w, int x, int y) {
        int pr = argb[y * w + x + 1], pl = argb[y * w + x - 1];
        int pd = argb[(y + 1) * w + x], pu = argb[(y - 1) * w + x];
        double gx = Math.abs(0.299 * (((pr >> 16) & 0xFF) - ((pl >> 16) & 0xFF))
                + 0.587 * (((pr >> 8) & 0xFF) - ((pl >> 8) & 0xFF))
                + 0.114 * ((pr & 0xFF) - (pl & 0xFF)));
        double gy = Math.abs(0.299 * (((pd >> 16) & 0xFF) - ((pu >> 16) & 0xFF))
                + 0.587 * (((pd >> 8) & 0xFF) - ((pu >> 8) & 0xFF))
                + 0.114 * ((pd & 0xFF) - (pu & 0xFF)));
        return gx + gy;
    }

    /** 单格内圈统计(返回 [相对方差, 均值, 平均梯度],无像素返回 null) */
    private static double[] cellVarMean(int[] argb, int w, int h,
                                        float cx, float cy, float rx, float ry) {
        int x0 = Math.max(1, Math.round(cx - rx));
        int y0 = Math.max(1, Math.round(cy - ry));
        int x1 = Math.min(w - 2, Math.round(cx + rx));
        int y1 = Math.min(h - 2, Math.round(cy + ry));
        if (x1 < x0 || y1 < y0) return null;
        double s = 0, ss = 0, g = 0;
        int n = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int p = argb[y * w + x];
                double l = 0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF)
                        + 0.114 * (p & 0xFF);
                s += l;
                ss += l * l;
                g += lumGradPx(argb, w, x, y);
                n++;
            }
        }
        if (n == 0) return null;
        double mean = s / n;
        double var = ss / n - mean * mean;
        return new double[]{var / (mean * mean + 1), mean, g / n};
    }

    /** 折叠对比度:投影按 period 折叠,(最强相位 bin - 半周期外 bin)/均值 */
    static float foldContrast(float[] p, float period) {
        int np = Math.max(4, Math.round(period * 4));
        double[] acc = new double[np];
        int[] cnt = new int[np];
        double total = 0;
        for (int i = 0; i < p.length; i++) {
            int k = Math.round((i / period) * np) % np;
            acc[k] += p[i];
            cnt[k]++;
            total += p[i];
        }
        double mean = total / Math.max(1, p.length);
        double best = 0, bestOpp = 0;
        for (int i = 0; i < np; i++) {
            if (cnt[i] == 0) continue;
            double v = acc[i] / cnt[i];
            if (v > best) {
                best = v;
                int o = (i + np / 2) % np;
                bestOpp = cnt[o] > 0 ? acc[o] / cnt[o] : 0;
            }
        }
        return (float) ((best - bestOpp) / Math.max(1e-9, mean));
    }

    /**
     * 按网格采样(含边缘背景裁剪):返回裁剪后的网格(行优先),
     * 实际 cols/rows 写入 outDims[0]/outDims[1]。
     * 格心按 angle 旋转后的位置取,内圈 52% 环带做亮度截尾均值。
     */
    public static int[] sample(int[] argb, int w, int h, Grid g, int[] outDims) {
        int cols = g.cols, rows = g.rows;
        int[] raw = new int[cols * rows];
        double cos = Math.cos(g.angle), sin = Math.sin(g.angle);
        float rx = g.pitchX * 0.26f, ry = g.pitchY * 0.26f;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                float cx = g.ox + (float) (i * g.pitchX * cos - j * g.pitchY * sin);
                float cy = g.oy + (float) (i * g.pitchX * sin + j * g.pitchY * cos);
                raw[j * cols + i] = trimmedCell(argb, w, h, cx, cy, rx, ry);
            }
        }
        return trimBackground(raw, cols, rows, outDims);
    }

    /**
     * 亮度截尾均值:格内环带像素按亮度排序,丢最暗 25%(缝隙阴影)
     * 与最亮 15%(高光),平均其余——比中心均值更贴豆身本色。
     * 环带:按格距估孔半径(约 0.18)跳过中心区,孔会透出板底深色。
     */
    static int trimmedCell(int[] argb, int w, int h, float cx, float cy, float rx, float ry) {
        int x0 = Math.max(0, Math.round(cx - rx));
        int y0 = Math.max(0, Math.round(cy - ry));
        int x1 = Math.min(w - 1, Math.round(cx + rx));
        int y1 = Math.min(h - 1, Math.round(cy + ry));
        int cap = (x1 - x0 + 1) * (y1 - y0 + 1);
        if (cap <= 0) return 0xFF000000;
        int[] lums = new int[cap];
        int[] pix = new int[cap];
        int n = 0;
        float holeR2 = (rx * rx + ry * ry) * 0.24f;   // 孔半径 ≈ 0.18×格距
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                float dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy < holeR2) continue;
                int p = argb[y * w + x];
                pix[n] = p;
                lums[n] = 299 * ((p >> 16) & 0xFF) + 587 * ((p >> 8) & 0xFF) + 114 * (p & 0xFF);
                n++;
            }
        }
        int[] sorted = lums.clone();
        java.util.Arrays.sort(sorted);
        int lo = sorted[Math.min(n - 1, n * 25 / 100)];
        int hi = sorted[Math.min(n - 1, n * 85 / 100)];
        long sr = 0, sg = 0, sb = 0;
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (lums[i] < lo || lums[i] > hi) continue;
            sr += (pix[i] >> 16) & 0xFF;
            sg += (pix[i] >> 8) & 0xFF;
            sb += pix[i] & 0xFF;
            m++;
        }
        if (m == 0) {
            for (int i = 0; i < n; i++) {
                sr += (pix[i] >> 16) & 0xFF;
                sg += (pix[i] >> 8) & 0xFF;
                sb += pix[i] & 0xFF;
            }
            m = n;
        }
        return 0xFF000000 | ((int) (sr / m) << 16) | ((int) (sg / m) << 8) | (int) (sb / m);
    }

    /**
     * 边缘背景裁剪,返回裁剪后网格(行优先)。
     * 框选常会把图纸外的空白/桌面底色框进来,形成"整行/列都是背景"的假格子。
     * 背景色 = 采样结果最外圈出现最多的颜色;某条边 ≥90% 是它、且它占全图
     * ≥30%(或四角近同色)时才裁该边;每边最多裁 35%。
     */
    public static int[] trimBackground(int[] cells, int cols, int rows) {
        return trimBackground(cells, cols, rows, new int[2]);
    }

    /**
     * 白缝参考角(v2.74,用户思路:找图里水平的白线做参考):成品照里最可靠的
     * 水平参考是作品自身的白色分界缝(条带拼接缝)。梯度/自相关类估计在含
     * 织布/桌面的实拍图上会偏差 1°+(实测 -1.55° vs 白缝实测 -0.22°,过转)。
     * 做法:逐行统计近白像素占比 → 聚出白色横带(上下相邻行必须偏彩色,
     * 排除织布/外框整片白区)→ 每带逐列白像素质心最小二乘拟合 → 斜率取中位。
     * 返回弧度;找不到清晰白带返回 0。
     */
    public static double horizontalGapAngle(int[] argb, int w, int h) {
        if (argb == null || w < 96 || h < 96) return 0;
        int side = Math.max(w, h);
        int dw = w, dh = h;
        int[] sm = argb;
        if (side > 1000) {
            // 1000 长边:白缝宽 ~13px@2155,降到 600 只剩 3 行会被高度过滤掉
            dw = Math.max(64, Math.round(w * 1000f / side));
            dh = Math.max(64, Math.round(h * 1000f / side));
            sm = new int[dw * dh];
            float sxf = w / (float) dw, syf = h / (float) dh;
            for (int y = 0; y < dh; y++) {
                int ys = (int) (y * syf), ye = Math.max(ys + 1, (int) ((y + 1) * syf));
                for (int x = 0; x < dw; x++) {
                    int xs = (int) (x * sxf), xe = Math.max(xs + 1, (int) ((x + 1) * sxf));
                    long r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int yy = ys; yy < ye && yy < h; yy++) {
                        for (int xx = xs; xx < xe && xx < w; xx++) {
                            int q = argb[yy * w + xx];
                            r += (q >> 16) & 0xFF;
                            g += (q >> 8) & 0xFF;
                            b += q & 0xFF;
                            n++;
                        }
                    }
                    if (n == 0) n = 1;
                    sm[y * dw + x] = 0xFF000000 | ((int) (r / n) << 16)
                            | ((int) (g / n) << 8) | (int) (b / n);
                }
            }
        }
        // 近白判定:亮度高且低饱和
        boolean[] white = new boolean[dw * dh];
        for (int i = 0; i < dw * dh; i++) {
            int q = sm[i];
            int r = (q >> 16) & 0xFF, g = (q >> 8) & 0xFF, b = q & 0xFF;
            int lum = r * 299 + g * 587 + b * 114;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            white[i] = lum > 185 * 1000 && (mx - mn) < 60;
        }
        // 逐行白占比
        float[] rowFrac = new float[dh];
        for (int y = 0; y < dh; y++) {
            int n = 0;
            for (int x = 0; x < dw; x++) {
                if (white[y * dw + x]) n++;
            }
            rowFrac[y] = n / (float) dw;
        }
        // 白色横带:连续 frac>0.5 且高 ≥4 行;带须离图像上下边缘 ≥4%
        // (贴边的白带是织布/桌面临界区,deskew 已对齐它们,混入会拉偏
        // 中位数——实测底部织物边带 +0.03° 混入使估计从 -0.24° 偏到 -0.01°);
        // 且带上下 6 行内 frac<0.35(上下都偏彩色 = 作品内部缝隙)
        java.util.List<int[]> bands = new java.util.ArrayList<>();
        int runStart = -1;
        int edge = Math.max(6, dh / 25);
        for (int y = 0; y <= dh; y++) {
            boolean wrow = y < dh && rowFrac[y] > 0.5f;
            if (wrow && runStart < 0) runStart = y;
            if (!wrow && runStart >= 0) {
                int ya = runStart, yb = y - 1;
                if (yb - ya + 1 >= 4 && ya >= edge && yb <= dh - 1 - edge) {
                    float top = rowFrac[ya - 6], bot = rowFrac[Math.min(dh - 1, yb + 6)];
                    if (top < 0.35f && bot < 0.35f) {
                        bands.add(new int[]{ya, yb});
                    }
                }
                runStart = -1;
            }
        }
        if (bands.isEmpty()) return 0;
        // 每带逐列质心 → 最小二乘斜率
        java.util.List<Double> slopes = new java.util.ArrayList<>();
        for (int[] band : bands) {
            int ya = band[0], yb = band[1];
            double sx = 0, sy = 0, sxx = 0, sxy = 0;
            int n = 0;
            for (int x = 4; x < dw - 4; x++) {
                double acc = 0;
                int cnt = 0;
                for (int y = Math.max(0, ya - 6); y <= Math.min(dh - 1, yb + 6); y++) {
                    if (white[y * dw + x]) {
                        acc += y;
                        cnt++;
                    }
                }
                if (cnt < 3) continue;
                double yv = acc / cnt;
                sx += x;
                sy += yv;
                sxx += (double) x * x;
                sxy += (double) x * yv;
                n++;
            }
            if (n < dw / 8) continue;   // 覆盖不足
            double cov = sxy - sx * sy / n;
            double var = sxx - sx * sx / n;
            if (Math.abs(var) < 1e-6) continue;
            slopes.add(cov / var);
        }
        if (slopes.isEmpty()) return 0;
        java.util.Collections.sort(slopes);
        double med = slopes.get(slopes.size() / 2);
        return Math.atan(med);
    }

    /**
     * 豆格单应细化(v2.76 重构):此前各路参考(织布框/白缝/角点)只修单一
     * 形变且各有污染源;本方法以豆子本身为参考——粗对格基础上,分块搜索
     * 局部网格相位(类内方差最小),得到一批"格索引→图像位置"对应,
     * 最小二乘拟合单应,按单应重采样使豆格精确对齐。可迭代。
     * 一次修正透视残差、镜头畸变等一切平滑形变。
     *
     * @param gx gy 网格原点(首格左上角),pitchX/pitchY 全局豆距(粗值)
     * @param outCell 输出每格像素(通常 = round(pitch))
     * @return 拉平后图像,outWH 写出尺寸;高质量对应不足返回 null
     */
    public static int[] refineLattice(int[] argb, int w, int h,
            double gx, double gy, double pitchX, double pitchY,
            int outCell, int[] outWH) {
        if (argb == null || pitchX < 6 || pitchY < 6
                || pitchX > Math.min(w, h) * 0.5 || pitchY > Math.min(w, h) * 0.5) {
            return null;   // 粗估豆距离谱(强畸变图上自相关失手)时拒绝
        }
        int cols = (int) Math.floor((w - gx) / pitchX);
        int rows = (int) Math.floor((h - gy) / pitchY);
        if (cols < 8 || rows < 8) return null;
        // ---- 分块:渐进跟踪 + 形变场 mesh ----
        // 织物悬挂/鼓包的形变不是单应(实测缝1/缝2 水平而缝3 斜 -80px,
        // 全局线性变换原理上修不掉):每块的实测偏移直接构成形变场,
        // 重采样时双线性插值逐像素施加——鼓包/剪切/透视一视同仁
        int K = Math.max(3, Math.min(8, Math.min(cols, rows) / 9));
        int N = 8;   // 每块格数
        java.util.List<double[]> src = new java.util.ArrayList<>();
        java.util.List<double[]> dst = new java.util.ArrayList<>();
        double search = Math.min(pitchX, pitchY) * 0.35;
        // 块处理顺序:按到图像中心的距离排序(中心块偏移最小,作跟踪起点)
        java.util.List<int[]> order = new java.util.ArrayList<>();
        for (int ty = 0; ty < K; ty++) {
            for (int tx = 0; tx < K; tx++) {
                order.add(new int[]{tx, ty});
            }
        }
        final double midU = cols / 2.0, midV = rows / 2.0;
        java.util.Collections.sort(order, new java.util.Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                double da = Math.abs((a[0] + 0.5) * cols / K - midU)
                        + Math.abs((a[1] + 0.5) * rows / K - midV);
                double db = Math.abs((b[0] + 0.5) * cols / K - midU)
                        + Math.abs((b[1] + 0.5) * rows / K - midV);
                return Double.compare(da, db);
            }
        });
        double[][] offX = new double[K][K], offY = new double[K][K];
        boolean[][] has = new boolean[K][K];
        int evaluated = 0, accepted = 0;
        for (int[] t : order) {
            int tx = t[0], ty = t[1];
            int ci = (int) Math.round((tx + 0.5) * cols / K);
            int cj = (int) Math.round((ty + 0.5) * rows / K);
            int i0 = ci - N / 2, j0 = cj - N / 2;
            if (i0 < 0 || j0 < 0 || i0 + N > cols || j0 + N > rows) continue;
            // 预测偏移:已测邻块偏移的双线性插值(无邻块则 0)
            double[] pred = meshAt(offX, offY, has, tx, ty, K);
            double pdx = pred[0], pdy = pred[1];
            double best = Double.MAX_VALUE, bdx = 0, bdy = 0;
            for (double dy = pdy - search; dy <= pdy + search; dy += 1) {
                for (double dx = pdx - search; dx <= pdx + search; dx += 1) {
                    double score = tileVariance(argb, w, h,
                            gx + dx, gy + dy, i0, j0, N, pitchX, pitchY);
                    if (score < best) {
                        best = score;
                        bdx = dx;
                        bdy = dy;
                    }
                }
            }
            // 平坦块(白框/织布/大面积纯色)判别:最优与预测位置分数差
            // 不足 15% 则弃——纯色区的方差曲线近乎平坦,最小值是噪声,
            // 混入观测会拟出发散单应(四季树天空区实测翻车)
            evaluated++;
            double atP = tileVariance(argb, w, h,
                    gx + pdx, gy + pdy, i0, j0, N, pitchX, pitchY);
            if (atP - best < atP * 0.15) continue;
            accepted++;
            offX[ty][tx] = bdx;
            offY[ty][tx] = bdy;
            has[ty][tx] = true;
        }
        if (accepted * 2 < evaluated || accepted < 3) return null;
        // 未测块填充:已测邻居的平均(迭代扩散)
        for (int pass = 0; pass < 6; pass++) {
            for (int ty = 0; ty < K; ty++) {
                for (int tx = 0; tx < K; tx++) {
                    if (has[ty][tx]) continue;
                    double sx2 = 0, sy2 = 0;
                    int c2 = 0;
                    for (int[] d2 : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = tx + d2[0], ny = ty + d2[1];
                        if (nx < 0 || ny < 0 || nx >= K || ny >= K) continue;
                        if (!has[ny][nx]) continue;
                        sx2 += offX[ny][nx];
                        sy2 += offY[ny][nx];
                        c2++;
                    }
                    if (c2 > 0) {
                        offX[ty][tx] = sx2 / c2;
                        offY[ty][tx] = sy2 / c2;
                        has[ty][tx] = true;
                    }
                }
            }
        }
        // 形变幅度检查:最大 |偏移| < 1.5px = 网格已直,无需细化
        double maxOff = 0;
        for (int ty = 0; ty < K; ty++) {
            for (int tx = 0; tx < K; tx++) {
                maxOff = Math.max(maxOff,
                        Math.hypot(offX[ty][tx], offY[ty][tx]));
            }
        }
        if (maxOff < 1.5) return null;
        // ---- 按形变场重采样:格 (i,j) 中心 → ((i+0.5)*outCell,(j+0.5)*outCell) ----
        int W = cols * outCell, H = rows * outCell;
        int[] out = new int[W * H];
        for (int y = 0; y < H; y++) {
            double v = (y + 0.5) / (double) outCell - 0.5;   // 格索引(0 基)
            double fty = v * K / rows - 0.5;
            for (int x = 0; x < W; x++) {
                double u = (x + 0.5) / (double) outCell - 0.5;
                double ftx = u * K / cols - 0.5;
                double[] off = meshAt2(offX, offY, has, ftx, fty, K);
                out[y * W + x] = bilinear(argb, w, h,
                        gx + (u + 0.5) * pitchX + off[0],
                        gy + (v + 0.5) * pitchY + off[1]);
            }
        }
        if (outWH != null) {
            outWH[0] = W;
            outWH[1] = H;
        }
        return out;
    }

    /**
     * 块内类内方差和:格 (i0..i0+N, j0..j0+N) 以 (ox,oy) 为原点,
     * 每格取中心圆盘(r=0.30 pitch)像素对方均色差的方差和——
     * 网格对齐豆边界时最小,骑缝/错位时暴涨。
     */
    private static double tileVariance(int[] argb, int w, int h,
            double ox, double oy, int i0, int j0, int n,
            double pitchX, double pitchY) {
        double total = 0;
        int cnt = 0;
        int rx = (int) Math.ceil(pitchX * 0.30);
        int ry = (int) Math.ceil(pitchY * 0.30);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double cx = ox + (i0 + i + 0.5) * pitchX;
                double cy = oy + (j0 + j + 0.5) * pitchY;
                int x0 = Math.max(0, (int) (cx - rx));
                int x1 = Math.min(w - 1, (int) (cx + rx));
                int y0 = Math.max(0, (int) (cy - ry));
                int y1 = Math.min(h - 1, (int) (cy + ry));
                int m = 0;
                long sr = 0, sg = 0, sb = 0;
                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        double ddx = x + 0.5 - cx, ddy = y + 0.5 - cy;
                        if (ddx * ddx / (pitchX * pitchX)
                                + ddy * ddy / (pitchY * pitchY) > 0.09) {
                            continue;
                        }
                        int q = argb[y * w + x];
                        sr += (q >> 16) & 0xFF;
                        sg += (q >> 8) & 0xFF;
                        sb += q & 0xFF;
                        m++;
                    }
                }
                if (m < 4) continue;
                double mr = sr / (double) m, mg = sg / (double) m, mb = sb / (double) m;
                double var = 0;
                for (int y = y0; y <= y1; y++) {
                    for (int x = x0; x <= x1; x++) {
                        double ddx = x + 0.5 - cx, ddy = y + 0.5 - cy;
                        if (ddx * ddx / (pitchX * pitchX)
                                + ddy * ddy / (pitchY * pitchY) > 0.09) {
                            continue;
                        }
                        int q = argb[y * w + x];
                        double dr = (q >> 16 & 0xFF) - mr;
                        double dg = (q >> 8 & 0xFF) - mg;
                        double db = (q & 0xFF) - mb;
                        var += dr * dr + dg * dg + db * db;
                    }
                }
                total += var / m;
                cnt++;
            }
        }
        return cnt > 0 ? total / cnt : Double.MAX_VALUE;
    }

    /** 块级偏移的双线性插值(tx,ty 为块索引浮点;未测块权重 0) */
    private static double[] meshAt(double[][] offX, double[][] offY,
            boolean[][] has, int tx, int ty, int K) {
        double sx = 0, sy = 0, wsum = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = tx + dx, ny = ty + dy;
                if (nx < 0 || ny < 0 || nx >= K || ny >= K || !has[ny][nx]) {
                    continue;
                }
                double wgt = 1.0 / (1 + Math.abs(dx) + Math.abs(dy));
                sx += offX[ny][nx] * wgt;
                sy += offY[ny][nx] * wgt;
                wsum += wgt;
            }
        }
        return wsum > 0 ? new double[]{sx / wsum, sy / wsum}
                : new double[]{0, 0};
    }

    /** 输出像素级偏移:块网格上的双线性(未测/越界用最近块兜底) */
    private static double[] meshAt2(double[][] offX, double[][] offY,
            boolean[][] has, double ftx, double fty, int K) {
        int x0 = Math.max(0, Math.min(K - 1, (int) Math.floor(ftx)));
        int y0 = Math.max(0, Math.min(K - 1, (int) Math.floor(fty)));
        int x1 = Math.min(K - 1, x0 + 1);
        int y1 = Math.min(K - 1, y0 + 1);
        double fx = Math.max(0, Math.min(K - 1.001, ftx)) - x0;
        double fy = Math.max(0, Math.min(K - 1.001, fty)) - y0;
        // 未测块用最近已测块兜底
        double ox0 = has[y0][x0] ? offX[y0][x0] : nearest(offX, has, x0, y0, K);
        double oy0 = has[y0][x0] ? offY[y0][x0] : nearest(offY, has, x0, y0, K);
        double ox1 = has[y0][x1] ? offX[y0][x1] : nearest(offX, has, x1, y0, K);
        double oy1 = has[y0][x1] ? offY[y0][x1] : nearest(offY, has, x1, y0, K);
        double ox2 = has[y1][x0] ? offX[y1][x0] : nearest(offX, has, x0, y1, K);
        double oy2 = has[y1][x0] ? offY[y1][x0] : nearest(offY, has, x0, y1, K);
        double ox3 = has[y1][x1] ? offX[y1][x1] : nearest(offX, has, x1, y1, K);
        double oy3 = has[y1][x1] ? offY[y1][x1] : nearest(offY, has, x1, y1, K);
        return new double[]{
                (ox0 * (1 - fx) + ox1 * fx) * (1 - fy)
                        + (ox2 * (1 - fx) + ox3 * fx) * fy,
                (oy0 * (1 - fx) + oy1 * fx) * (1 - fy)
                        + (oy2 * (1 - fx) + oy3 * fx) * fy,
        };
    }

    /** 最近的已测块偏移(BFS 一圈圈找) */
    private static double nearest(double[][] off, boolean[][] has, int x, int y, int K) {
        for (int r = 1; r < K; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx >= 0 && ny >= 0 && nx < K && ny < K && has[ny][nx]) {
                        return off[ny][nx];
                    }
                }
            }
        }
        return 0;
    }

    /** 最小二乘单应:n≥4 对 (u,v)→(x,y),标准 DLT 正则方程 8×8 高斯消元 */
    private static double[] homographyLS(java.util.List<double[]> src,
                                         java.util.List<double[]> dst) {
        int n = src.size();
        double[][] A = new double[8][8];
        double[] B = new double[8];
        for (int k = 0; k < n; k++) {
            double u = src.get(k)[0], v = src.get(k)[1];
            double x = dst.get(k)[0], y = dst.get(k)[1];
            // x*(h6u+h7v+1) = h0u+h1v+h2 → h0u+h1v+h2 - x*h6u - x*h7v = x
            double[] r1 = {u, v, 1, 0, 0, 0, -u * x, -v * x};
            double[] r2 = {0, 0, 0, u, v, 1, -u * y, -v * y};
            for (int c = 0; c < 8; c++) {
                A[c][0] += r1[0] * r1[c] + r2[0] * r2[c];
                A[c][1] += r1[1] * r1[c] + r2[1] * r2[c];
                A[c][2] += r1[2] * r1[c] + r2[2] * r2[c];
                A[c][3] += r1[3] * r1[c] + r2[3] * r2[c];
                A[c][4] += r1[4] * r1[c] + r2[4] * r2[c];
                A[c][5] += r1[5] * r1[c] + r2[5] * r2[c];
                A[c][6] += r1[6] * r1[c] + r2[6] * r2[c];
                A[c][7] += r1[7] * r1[c] + r2[7] * r2[c];
            }
            B[0] += r1[0] * x + r2[0] * y;
            B[1] += r1[1] * x + r2[1] * y;
            B[2] += r1[2] * x + r2[2] * y;
            B[3] += r1[3] * x + r2[3] * y;
            B[4] += r1[4] * x + r2[4] * y;
            B[5] += r1[5] * x + r2[5] * y;
            B[6] += r1[6] * x + r2[6] * y;
            B[7] += r1[7] * x + r2[7] * y;
        }
        for (int col = 0; col < 8; col++) {
            int piv = col;
            for (int r = col + 1; r < 8; r++) {
                if (Math.abs(A[r][col]) > Math.abs(A[piv][col])) piv = r;
            }
            if (Math.abs(A[piv][col]) < 1e-9) return null;
            double[] t = A[col];
            A[col] = A[piv];
            A[piv] = t;
            double tb = B[col];
            B[col] = B[piv];
            B[piv] = tb;
            for (int r = 0; r < 8; r++) {
                if (r == col) continue;
                double f = A[r][col] / A[col][col];
                if (f == 0) continue;
                for (int c = col; c < 8; c++) A[r][c] -= f * A[col][c];
                B[r] -= f * B[col];
            }
        }
        double[] hm = new double[9];
        for (int i = 0; i < 8; i++) hm[i] = B[i] / A[i][i];
        hm[8] = 1;
        return hm;
    }

    /**
     * 竖直白带剪切角(v2.77,用户思路的竖直版):作品左缘(白框→彩色过渡)
     * 是竖直方向的可靠参考。逐行从左起找第一个近白游程(≥6px)的右缘
     * (= 彩色起点),最小二乘拟合 x = a + b·y,返回剪切系数 b(dx/dy)。
     * 行覆盖不足或斜率不显著返回 0。
     */
    public static double verticalBandShear(int[] argb, int w, int h) {
        if (argb == null || w < 96 || h < 96) return 0;
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (int y = 4; y < h - 4; y += 2) {
            int run = 0, start = -1, end = -1;
            for (int x = 0; x < w / 2; x++) {
                int q = argb[y * w + x];
                int r = (q >> 16) & 0xFF, g = (q >> 8) & 0xFF, b = q & 0xFF;
                int lum = r * 299 + g * 587 + b * 114;
                int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
                boolean white = lum > 185000 && (mx - mn) < 60;
                if (white) {
                    if (run == 0) start = x;
                    run++;
                    if (run >= 6) {
                        end = x;
                        break;
                    }
                } else {
                    run = 0;
                }
            }
            if (end > 0) {
                pts.add(new double[]{y, end});
            }
        }
        if (pts.size() < 20) return 0;
        double sy = 0, sx = 0, syy = 0, sxy = 0;
        for (double[] q : pts) {
            sy += q[0];
            sx += q[1];
            syy += q[0] * q[0];
            sxy += q[0] * q[1];
        }
        int n = pts.size();
        double var = syy - sy * sy / n;
        if (Math.abs(var) < 1e-6) return 0;
        double b = (sxy - sy * sx / n) / var;
        return Math.abs(b) > Math.tan(Math.toRadians(0.1)) ? b : 0;
    }

    /**
     * 去剪切(v2.77):按剪切系数 b(dx/dy)水平重排,使竖直参考线竖直。
     * 横线不受影响(y 不变)。画布两侧各扩 |b|·h/2,越界夹持(边缘为
     * 桌面/织物,后续 trimBackground 清理)。
     */
    public static int[] deshear(int[] argb, int w, int h, double b, int[] outWH) {
        if (argb == null || Math.abs(b) < 1e-6) return null;
        int pad = (int) Math.ceil(Math.abs(b) * h / 2) + 1;
        int W = w + 2 * pad;
        int[] out = new int[W * h];
        for (int y = 0; y < h; y++) {
            double s = b * (y - h / 2.0);
            for (int x = 0; x < W; x++) {
                out[y * W + x] = bilinear(argb, w, h, x + s - pad, y);
            }
        }
        if (outWH != null) {
            outWH[0] = W;
            outWH[1] = h;
        }
        return out;
    }

    /**
     * 分带整平(v2.77,最终形态):织物悬挂/鼓包/条带拼接的形变不是单应
     * (实测缝1/缝2 水平而缝3 斜 -6.5°,全局线性变换原理上修不掉,网格
     * 单应/剪切亦然)。以作品自身基准线为锚:白色分界缝(逐列白质心)+
     * 上下彩色边界,每条基准线逐列最小二乘拟合;输出行按所在条带的两条
     * 边界线的线性插值重排——每一条带的行平行于它自己的边界线,
     * 逐条带校平,重建设计意图(条带本应水平)。列方向由 deshear 先行校直。
     * 基准线不足(≥2 条分界缝)返回 null,调用方保留原图。
     */
    public static int[] flattenBands(int[] argb, int w, int h, int[] outWH) {
        if (argb == null || w < 96 || h < 96) return null;
        int side = Math.max(w, h);
        int dw = w, dh = h;
        int[] sm = argb;
        if (side > 1000) {
            dw = Math.max(64, Math.round(w * 1000f / side));
            dh = Math.max(64, Math.round(h * 1000f / side));
            sm = new int[dw * dh];
            float sxf = w / (float) dw, syf = h / (float) dh;
            for (int y = 0; y < dh; y++) {
                int ys = (int) (y * syf), ye = Math.max(ys + 1, (int) ((y + 1) * syf));
                for (int x = 0; x < dw; x++) {
                    int xs = (int) (x * sxf), xe = Math.max(xs + 1, (int) ((x + 1) * sxf));
                    long r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int yy = ys; yy < ye && yy < h; yy++) {
                        for (int xx = xs; xx < xe && xx < w; xx++) {
                            int q = argb[yy * w + xx];
                            r += (q >> 16) & 0xFF;
                            g += (q >> 8) & 0xFF;
                            b += q & 0xFF;
                            n++;
                        }
                    }
                    if (n == 0) n = 1;
                    sm[y * dw + x] = 0xFF000000 | ((int) (r / n) << 16)
                            | ((int) (g / n) << 8) | (int) (b / n);
                }
            }
        }
        boolean[] white = new boolean[dw * dh];
        for (int i = 0; i < dw * dh; i++) {
            int q = sm[i];
            int r = (q >> 16) & 0xFF, g = (q >> 8) & 0xFF, b = q & 0xFF;
            int lum = r * 299 + g * 587 + b * 114;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            white[i] = lum > 185000 && (mx - mn) < 60;
        }
        float[] rowFrac = new float[dh];
        for (int y = 0; y < dh; y++) {
            int n = 0;
            for (int x = 0; x < dw; x++) {
                if (white[y * dw + x]) n++;
            }
            rowFrac[y] = n / (float) dw;
        }
        // 白色横带:frac>0.5 连续 ≥3 行;分界缝 = 上下 6 行偏彩(两侧都是作品)
        java.util.List<int[]> gaps = new java.util.ArrayList<>();
        int runStart = -1;
        for (int y = 0; y <= dh; y++) {
            boolean wr = y < dh && rowFrac[y] > 0.5f;
            if (wr && runStart < 0) runStart = y;
            if (!wr && runStart >= 0) {
                int ya = runStart, yb = y - 1;
                if (yb - ya + 1 >= 3 && ya >= 6 && yb <= dh - 7) {
                    float top = rowFrac[ya - 6], bot = rowFrac[Math.min(dh - 1, yb + 6)];
                    if (top < 0.35f && bot < 0.35f) {
                        gaps.add(new int[]{ya, yb});
                    }
                }
                runStart = -1;
            }
        }
        if (gaps.size() < 2) return null;
        // 基准线逐列 y:分界缝 = 带内白像素质心;上下边界 = 彩色起止行
        java.util.List<double[]> lineA = new java.util.ArrayList<>();   // 截距(ds)
        java.util.List<double[]> lineB = new java.util.ArrayList<>();   // 斜率(ds)
        for (int[] gap : gaps) {
            fitCentroidLine(white, dw, dh, gap[0], gap[1], lineA, lineB);
        }
        // 上下彩色边界:每列第一/最后一个彩色行(连续 3 行彩色算确认)
        fitColorEdge(white, dw, dh, true, lineA, lineB);
        fitColorEdge(white, dw, dh, false, lineA, lineB);
        if (lineA.size() < 4) return null;
        // 边界按平均 y 排序,相邻间距须 ≥5 行
        int m = lineA.size();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                if (lineA.get(j)[0] < lineA.get(i)[0]) {
                    double[] t = lineA.get(i);
                    lineA.set(i, lineA.get(j));
                    lineA.set(j, t);
                    t = lineB.get(i);
                    lineB.set(i, lineB.get(j));
                    lineB.set(j, t);
                }
            }
        }
        for (int i = 1; i < m; i++) {
            if (lineA.get(i)[0] - lineA.get(i - 1)[0] < 5) return null;
        }
        // 输出行位置:各边界的平均 y(转全分辨率),累计间距 = 输出行
        double[] meanY = new double[m];
        double[] meanYFull = new double[m];
        double scaleY = h / (double) dh;
        for (int i = 0; i < m; i++) {
            meanY[i] = lineA.get(i)[0] + lineB.get(i)[0] * dw / 2.0;
            meanYFull[i] = meanY[i] * scaleY;
        }
        int H = (int) Math.round(meanYFull[m - 1] - meanYFull[0]);
        if (H < 48) return null;
        double scaleX = w / (double) dw;
        int[] out = new int[w * H];
        // 逐行:所在条带 k,t = 行在带内的相对位置
        for (int Y = 0; Y < H; Y++) {
            double yOut = Y + meanYFull[0];
            int k = 0;
            while (k < m - 2 && yOut > meanYFull[k + 1]) k++;
            double t = (yOut - meanYFull[k])
                    / Math.max(1e-6, meanYFull[k + 1] - meanYFull[k]);
            t = Math.max(0, Math.min(1, t));
            for (int X = 0; X < w; X++) {
                double xds = X / scaleX;
                double ySrcDs = (1 - t) * (lineA.get(k)[0] + lineB.get(k)[0] * xds)
                        + t * (lineA.get(k + 1)[0] + lineB.get(k + 1)[0] * xds);
                out[Y * w + X] = bilinear(argb, w, h, X, ySrcDs * scaleY);
            }
        }
        if (outWH != null) {
            outWH[0] = w;
            outWH[1] = H;
        }
        return out;
    }

    /** 白带质心线:带 ±6 行内白像素逐列质心,最小二乘拟合 y = a + b·x */
    private static void fitCentroidLine(boolean[] white, int dw, int dh,
            int ya, int yb, java.util.List<double[]> lineA,
            java.util.List<double[]> lineB) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int x = 4; x < dw - 4; x++) {
            double acc = 0;
            int cnt = 0;
            for (int y = Math.max(0, ya - 6); y <= Math.min(dh - 1, yb + 6); y++) {
                if (white[y * dw + x]) {
                    acc += y;
                    cnt++;
                }
            }
            if (cnt < 3) continue;
            double yv = acc / cnt;
            sx += x;
            sy += yv;
            sxx += (double) x * x;
            sxy += (double) x * yv;
            n++;
        }
        if (n < dw / 8) {
            lineA.add(new double[]{(ya + yb) / 2.0});
            lineB.add(new double[]{0});
            return;
        }
        double var = sxx - sx * sx / n;
        double b = Math.abs(var) < 1e-6 ? 0 : (sxy - sx * sy / n) / var;
        lineA.add(new double[]{sy / n - b * sx / n});
        lineB.add(new double[]{b});
    }

    /** 彩色边界线:每列第一(或最后)个连续 3 行彩色的行,最小二乘拟合 */
    private static void fitColorEdge(boolean[] white, int dw, int dh,
            boolean fromTop, java.util.List<double[]> lineA,
            java.util.List<double[]> lineB) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int x = 4; x < dw - 4; x++) {
            int found = -1;
            if (fromTop) {
                for (int y = 0; y < dh - 3; y++) {
                    if (!white[y * dw + x] && !white[(y + 1) * dw + x]
                            && !white[(y + 2) * dw + x]) {
                        found = y;
                        break;
                    }
                }
            } else {
                for (int y = dh - 1; y > 2; y--) {
                    if (!white[y * dw + x] && !white[(y - 1) * dw + x]
                            && !white[(y - 2) * dw + x]) {
                        found = y;
                        break;
                    }
                }
            }
            if (found < 0) continue;
            sx += x;
            sy += found;
            sxx += (double) x * x;
            sxy += (double) x * found;
            n++;
        }
        if (n < dw / 8) {
            lineA.add(new double[]{fromTop ? 4 : dh - 5});
            lineB.add(new double[]{0});
            return;
        }
        double var = sxx - sx * sx / n;
        double b = Math.abs(var) < 1e-6 ? 0 : (sxy - sx * sy / n) / var;
        lineA.add(new double[]{sy / n - b * sx / n});
        lineB.add(new double[]{b});
    }

    /**
     * 纯旋转校正(v2.73):把图像绕中心旋转 -angle(弧度),画布扩到包容
     * 全部内容,双线性采样,越界取边缘(夹持)。deskew 对齐织布外框后,
     * 作品相对底布的残余旋转用此法转平,下游对格/叠加层保持轴对齐。
     * 拉正后尺寸写入 outWH[0]/[1]。
     */
    public static int[] rotate(int[] argb, int w, int h, double angle, int[] outWH) {
        if (argb == null || w < 2 || h < 2) return null;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        // 输出画布 = 输入四角旋转后的包围盒(绕各自中心对齐)
        double[] cx = {0, w - 1d, w - 1d, 0}, cyy = {0, 0, h - 1d, h - 1d};
        double mnx = Double.MAX_VALUE, mny = Double.MAX_VALUE;
        double mxx = -Double.MAX_VALUE, mxy = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            // 输出坐标 = R(-angle)·(输入角 - 输入中心) + 输出中心(原点暂用 0)
            double dx = cx[i] - w / 2.0, dy = cyy[i] - h / 2.0;
            double ox = dx * cos + dy * sin;
            double oy = -dx * sin + dy * cos;
            mnx = Math.min(mnx, ox);
            mxx = Math.max(mxx, ox);
            mny = Math.min(mny, oy);
            mxy = Math.max(mxy, oy);
        }
        int W = Math.max(2, (int) Math.round(mxx - mnx) + 1);
        int H = Math.max(2, (int) Math.round(mxy - mny) + 1);
        int[] out = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double dx = x - W / 2.0, dy = y - H / 2.0;
                // 逆映射:输出点旋回输入坐标系
                double sx = w / 2.0 + dx * cos + dy * sin;
                double sy = h / 2.0 - dx * sin + dy * cos;
                out[y * W + x] = bilinear(argb, w, h, sx, sy);
            }
        }
        if (outWH != null) {
            outWH[0] = W;
            outWH[1] = H;
        }
        return out;
    }

    /**
     * 透视校正(v2.71):成品照斜拍必然带梯形畸变+残余旋转,单一旋转角的
     * 网格模型对不齐整幅(用户实测:解码图一侧白边呈"下宽上窄"楔形)。
     * 流程:背景色=四周边框环中位色 → 非背景最大连通域=成品面板 → 凸包 →
     * 最大面积内接四边形 → 单应变换拉正成矩形(双线性采样)。
     * 无清晰面板(面板 <25% 图面、四角贴原图角、图太小)返回 null,调用方走原图。
     * 拉正后尺寸写入 outWH[0]/[1]。
     */
    public static int[] deskew(int[] argb, int w, int h, int[] outWH) {
        if (argb == null || w < 96 || h < 96) return null;
        // ---- 分析降采样(长边 ≤420,box 平均) ----
        int side = Math.max(w, h);
        int dw = w, dh = h;
        int[] sm = argb;
        if (side > 420) {
            dw = Math.max(48, Math.round(w * 420f / side));
            dh = Math.max(48, Math.round(h * 420f / side));
            sm = new int[dw * dh];
            float sxf = w / (float) dw, syf = h / (float) dh;
            for (int y = 0; y < dh; y++) {
                int ys = (int) (y * syf), ye = Math.max(ys + 1, (int) ((y + 1) * syf));
                for (int x = 0; x < dw; x++) {
                    int xs = (int) (x * sxf), xe = Math.max(xs + 1, (int) ((x + 1) * sxf));
                    long r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int yy = ys; yy < ye && yy < h; yy++) {
                        for (int xx = xs; xx < xe && xx < w; xx++) {
                            int p = argb[yy * w + xx];
                            r += (p >> 16) & 0xFF;
                            g += (p >> 8) & 0xFF;
                            b += p & 0xFF;
                            n++;
                        }
                    }
                    if (n == 0) n = 1;
                    sm[y * dw + x] = 0xFF000000 | ((int) (r / n) << 16)
                            | ((int) (g / n) << 8) | (int) (b / n);
                }
            }
        }
        // ---- 背景色 = 边框环中位色 ----
        int[] rs = new int[6 * (dw + dh)], gs = new int[rs.length], bs = new int[rs.length];
        int nn = 0;
        for (int x = 0; x < dw; x++) {
            for (int k = 0; k < 3; k++) {
                int p1 = sm[x], p2 = sm[(dh - 1 - k) * dw + x];
                rs[nn] = (p1 >> 16) & 0xFF; gs[nn] = (p1 >> 8) & 0xFF; bs[nn++] = p1 & 0xFF;
                rs[nn] = (p2 >> 16) & 0xFF; gs[nn] = (p2 >> 8) & 0xFF; bs[nn++] = p2 & 0xFF;
            }
        }
        for (int y = 0; y < dh; y++) {
            for (int k = 0; k < 3; k++) {
                int p1 = sm[y * dw], p2 = sm[y * dw + dw - 1 - k];
                rs[nn] = (p1 >> 16) & 0xFF; gs[nn] = (p1 >> 8) & 0xFF; bs[nn++] = p1 & 0xFF;
                rs[nn] = (p2 >> 16) & 0xFF; gs[nn] = (p2 >> 8) & 0xFF; bs[nn++] = p2 & 0xFF;
            }
        }
        java.util.Arrays.sort(rs, 0, nn);
        java.util.Arrays.sort(gs, 0, nn);
        java.util.Arrays.sort(bs, 0, nn);
        int br = rs[nn / 2], bg = gs[nn / 2], bb = bs[nn / 2];
        // 背景 = 边框中位色的近邻(桌面等) ∪ 白色织物/作品白框
        // (v2.75:单应参考改用作品彩色区四角——白框与织布同色不可分,旧"非背景
        //  含白框"让四角落在织布外缘,作品相对织布的歪斜修不掉;彩色区四角
        //  (豆画与白框交界)对比强、角点清晰,单应一次修正全部透视)
        boolean[] whiteish = new boolean[dw * dh];
        for (int i = 0; i < dw * dh; i++) {
            int q = sm[i];
            int r = (q >> 16) & 0xFF, g = (q >> 8) & 0xFF, b = q & 0xFF;
            int lum = r * 299 + g * 587 + b * 114;
            int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
            whiteish[i] = lum > 185000 && (mx - mn) < 60;
        }
        // ---- 非背景掩码 + 最大连通域(4 邻接,迭代栈) ----
        int n = dw * dh;
        int[] comp = new int[n];          // 0=背景/未访问,1=当前域,2=最大域
        int[] stack = new int[n];
        int bestSize = 0, bestId = 1;
        int bb0 = 0, b0y = 0, bx1 = 0, by1 = 0;   // 最佳组件 bbox
        for (int seed = 0; seed < n; seed++) {
            if (comp[seed] != 0) continue;
            int p = sm[seed];
            int sad = Math.abs((p >> 16 & 0xFF) - br) + Math.abs((p >> 8 & 0xFF) - bg)
                    + Math.abs((p & 0xFF) - bb);
            if (sad <= 100 || whiteish[seed]) {
                comp[seed] = 1;
                continue;   // 背景,标记已访问
            }
            // 新的非背景域
            int id = bestId + 1;
            int sp = 0;
            stack[sp++] = seed;
            comp[seed] = id;
            int size = 0;
            int bb0Cur = dw, by0Cur = dh, bx1Cur = 0, by1Cur = 0;
            while (sp > 0) {
                int cur = stack[--sp];
                size++;
                int cx = cur % dw, cy = cur / dw;
                if (cx < bb0Cur) bb0Cur = cx;
                if (cx > bx1Cur) bx1Cur = cx;
                if (cy < by0Cur) by0Cur = cy;
                if (cy > by1Cur) by1Cur = cy;
                if (cx > 0 && comp[cur - 1] == 0) {
                    int q = sm[cur - 1];
                    int d = Math.abs((q >> 16 & 0xFF) - br) + Math.abs((q >> 8 & 0xFF) - bg)
                            + Math.abs((q & 0xFF) - bb);
                    boolean isBg = d <= 100 || whiteish[cur - 1];
                    comp[cur - 1] = isBg ? 1 : id;
                    if (!isBg) stack[sp++] = cur - 1;
                }
                if (cx < dw - 1 && comp[cur + 1] == 0) {
                    int q = sm[cur + 1];
                    int d = Math.abs((q >> 16 & 0xFF) - br) + Math.abs((q >> 8 & 0xFF) - bg)
                            + Math.abs((q & 0xFF) - bb);
                    boolean isBg = d <= 100 || whiteish[cur + 1];
                    comp[cur + 1] = isBg ? 1 : id;
                    if (!isBg) stack[sp++] = cur + 1;
                }
                if (cy > 0 && comp[cur - dw] == 0) {
                    int q = sm[cur - dw];
                    int d = Math.abs((q >> 16 & 0xFF) - br) + Math.abs((q >> 8 & 0xFF) - bg)
                            + Math.abs((q & 0xFF) - bb);
                    boolean isBg2 = d <= 100 || whiteish[cur - dw];
                    comp[cur - dw] = isBg2 ? 1 : id;
                    if (!isBg2) stack[sp++] = cur - dw;
                }
                if (cy < dh - 1 && comp[cur + dw] == 0) {
                    int q = sm[cur + dw];
                    int d = Math.abs((q >> 16 & 0xFF) - br) + Math.abs((q >> 8 & 0xFF) - bg)
                            + Math.abs((q & 0xFF) - bb);
                    boolean isBg3 = d <= 100 || whiteish[cur + dw];
                    comp[cur + dw] = isBg3 ? 1 : id;
                    if (!isBg3) stack[sp++] = cur + dw;
                }
            }
            if (size > bestSize) {
                bestSize = size;
                bestId = id;
                bb0 = bb0Cur; b0y = by0Cur; bx1 = bx1Cur; by1 = by1Cur;
            }
        }
        if (DEBUG) System.out.printf("[deskew] bestComp=%d n=%d (%.1f%%)%n",
                bestSize, n, bestSize * 100.0 / n);
        if (bestSize < n * 0.25) {
            if (DEBUG) System.out.println("[deskew] REJECT panel<25%");
            return null;   // 面板不清晰
        }
        // ---- 组件膨胀(把面板外圈的底板环并进来)----
        // 连通域=纯豆区,而透视下豆区外的底板环宽度沿边不均,豆区包络的
        // 四角不共单应(实测拉正残角 4.5°、采样命中 5%)。chamfer 距离膨胀
        // R(≈2.5% 短边)后取包络,角点回落到真实面板四角附近
        // 膨胀:彩色区角点已清晰(v2.75 改作品参考),只留小膨胀稳边界
        int rad = Math.max(2, Math.min(dw, dh) / 300);   // chamfer 权重 3/4
        int[] dd = new int[n];
        for (int i = 0; i < n; i++) dd[i] = comp[i] == bestId ? 0 : 1 << 20;
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                int i = y * dw + x;
                if (dd[i] == 0) continue;
                int v = dd[i];
                if (x > 0) v = Math.min(v, dd[i - 1] + 3);
                if (y > 0) {
                    v = Math.min(v, dd[i - dw] + 3);
                    if (x > 0) v = Math.min(v, dd[i - dw - 1] + 4);
                    if (x < dw - 1) v = Math.min(v, dd[i - dw + 1] + 4);
                }
                dd[i] = v;
            }
        }
        for (int y = dh - 1; y >= 0; y--) {
            for (int x = dw - 1; x >= 0; x--) {
                int i = y * dw + x;
                if (dd[i] == 0) continue;
                int v = dd[i];
                if (x < dw - 1) v = Math.min(v, dd[i + 1] + 3);
                if (y < dh - 1) {
                    v = Math.min(v, dd[i + dw] + 3);
                    if (x < dw - 1) v = Math.min(v, dd[i + dw + 1] + 4);
                    if (x > 0) v = Math.min(v, dd[i + dw - 1] + 4);
                }
                dd[i] = v;
            }
        }
        // ---- 最大连通域的每行 [minX,maxX] → 顶点集 → 凸包 ----
        int[] minX = new int[dh], maxX = new int[dh];
        java.util.Arrays.fill(minX, Integer.MAX_VALUE);
        java.util.Arrays.fill(maxX, -1);
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                if (dd[y * dw + x] <= rad) {
                    if (x < minX[y]) minX[y] = x;
                    if (x > maxX[y]) maxX[y] = x;
                }
            }
        }
        java.util.ArrayList<double[]> pts = new java.util.ArrayList<>();
        for (int y = 0; y < dh; y++) {
            if (maxX[y] < 0) continue;
            pts.add(new double[]{minX[y], y});
            pts.add(new double[]{maxX[y], y});
        }
        List<double[]> hull = convexHull(pts);
        if (DEBUG) {
            System.out.println("[deskew] hull=" + hull.size());
            StringBuilder sb = new StringBuilder("[deskew] hull pts: ");
            for (double[] q : hull) sb.append(String.format("(%.0f,%.0f) ", q[0], q[1]));
            System.out.println(sb);
        }
        if (hull.size() < 4) return null;
        // ---- 最大面积内接四边形:凸包降采样至 ≤60 点后穷举 C(60,4) ≈ 49 万
        // 组合(周界有序四元组在凸多边形上必为合法凸四边形),一次性且精确;
        // 双指针法的环绕不变量在 c 快速推进时易出简并解(实测三解退化) ----
        int m = hull.size();
        int step = 1;
        while (m / step > 60) step++;
        int q = (m + step - 1) / step;
        double bestQuadArea = 0;
        int bi = -1, bj = 0, bk = 0, bl = 0;
        for (int va = 0; va < q; va++) {
            for (int vb = va + 1; vb < q; vb++) {
                for (int vc = vb + 1; vc < q; vc++) {
                    for (int vd = vc + 1; vd < q; vd++) {
                        int ia = (va * step) % m, ib = (vb * step) % m;
                        int ic = (vc * step) % m, id = (vd * step) % m;
                        double area = crossArea(hull, ia, ib, ic)
                                + crossArea(hull, ia, ic, id);
                        if (area > bestQuadArea) {
                            bestQuadArea = area;
                            bi = ia;
                            bj = ib;
                            bk = ic;
                            bl = id;
                        }
                    }
                }
            }
        }
        if (DEBUG) System.out.printf("[deskew] quad idx %d,%d,%d,%d of m=%d step=%d%n",
                bi, bj, bk, bl, m, step);
        if (bi < 0) return null;
        if (bestQuadArea <= 0) return null;
        double[] TL = hull.get(bi), TR = hull.get(bj), BR = hull.get(bk), BL = hull.get(bl);
        // 规范角序:四角按质心角排序后,旋转到"min(x+y) 的角"当 TL,
        // 再用叉积统一手性——最大四边形的槽位起点/手性取决于凸包,
        // 直接用会转 90°(实测 H=258 的根因)
        double[] qx = {TL[0], TR[0], BR[0], BL[0]};
        double[] qy = {TL[1], TR[1], BR[1], BL[1]};
        double fcx = (qx[0] + qx[1] + qx[2] + qx[3]) / 4;
        double fcy = (qy[0] + qy[1] + qy[2] + qy[3]) / 4;
        Integer[] ord = {0, 1, 2, 3};
        final double fx = fcx, fy = fcy;
        java.util.Arrays.sort(ord, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer p, Integer r) {
                return Double.compare(
                        Math.atan2(qy[p] - fy, qx[p] - fx),
                        Math.atan2(qy[r] - fy, qx[r] - fx));
            }
        });
        int start = 0;
        double bestSum = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (qx[i] + qy[i] < bestSum) {
                bestSum = qx[i] + qy[i];
                start = i;
            }
        }
        int rankStart = 0;
        for (int i = 0; i < 4; i++) {
            if (ord[i] == start) {
                rankStart = i;
                break;
            }
        }
        double[][] qq = new double[4][2];
        for (int i = 0; i < 4; i++) {
            qq[i][0] = qx[ord[(rankStart + i) % 4]];
            qq[i][1] = qy[ord[(rankStart + i) % 4]];
        }
        double cross01_3 = (qq[1][0] - qq[0][0]) * (qq[3][1] - qq[0][1])
                - (qq[1][1] - qq[0][1]) * (qq[3][0] - qq[0][0]);
        if (cross01_3 < 0) {
            double[] t = qq[1];
            qq[1] = qq[3];
            qq[3] = t;
        }
        TL = qq[0];
        TR = qq[1];
        BR = qq[2];
        BL = qq[3];
        // ---- 目标尺寸 ----
        double wTop = dist(TL, TR), wBot = dist(BL, BR);
        double hL = dist(TL, BL), hR = dist(TR, BR);
        // 四角在分析分辨率(dw/dh)空间,输出按全分辨率等比放大
        // (否则拉正图只有 ~420px,豆距缩到几像素,后续对格必然失败)
        double scale = w / (double) dw;
        int W = (int) Math.round((wTop + wBot) / 2 * scale);
        int H = (int) Math.round((hL + hR) / 2 * scale);
        int maxSide = Math.max(w, h);
        if (W > maxSide || H > maxSide) {
            double f = maxSide / (double) Math.max(W, H);
            W = Math.max(64, (int) Math.round(W * f));
            H = Math.max(64, (int) Math.round(H * f));
        }
        if (DEBUG) System.out.println("[deskew] W=" + W + " H=" + H);
        if (W < 64 || H < 64) return null;
        // ---- 单应:矩形(0,0)-(W,0)-(W,H)-(0,H) → 四边形,求逆,反演采样 ----
        if (DEBUG) System.out.printf("[deskew] quad TL=(%.0f,%.0f) TR=(%.0f,%.0f) BR=(%.0f,%.0f) BL=(%.0f,%.0f) dw=%d dh=%d%n",
                TL[0], TL[1], TR[0], TR[1], BR[0], BR[1], BL[0], BL[1], dw, dh);
        double[] hm = homography(0, 0, W, 0, W, H, 0, H,
                TL[0], TL[1], TR[0], TR[1], BR[0], BR[1], BL[0], BL[1]);
        if (DEBUG) System.out.println("[deskew] hm=" + (hm == null));
        if (hm == null) return null;
        // 正向采样:hm 把拉正图坐标(rect)映射回原图四边形区域(quad),
        // 对输出每像素施加 hm 即得源坐标。(旧版误用逆矩阵:分析分辨率下
        // rect/quad 数值量级恰好接近,扭曲表现为小幅残角,极难察觉)
        double[] fwd = hm;
        // 四角全贴原图角 = 本来就正,不折腾
        int marginOk = Math.max(dw, dh) / 50;
        if (DEBUG) System.out.printf("[deskew] quad TL=(%.0f,%.0f) TR=(%.0f,%.0f) BR=(%.0f,%.0f) BL=(%.0f,%.0f) dw=%d dh=%d margin=%d%n",
                TL[0], TL[1], TR[0], TR[1], BR[0], BR[1], BL[0], BL[1], dw, dh, marginOk);
        // 组件 bbox 占满画框(双向 ≥98%)= 无背景可测,本就摆正不折腾。
        // (旧"四角贴原图角"防御在用户紧裁剪+膨胀顶边时误拒,v2.71 改 bbox 判据)
        if (bb0 <= dw * 0.01 && bx1 >= dw * 0.99 - 1
                && b0y <= dh * 0.01 && by1 >= dh * 0.99 - 1) {
            if (DEBUG) System.out.println("[deskew] REJECT bbox fills frame");
            return null;
        }
        int[] out = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double d = fwd[6] * x + fwd[7] * y + 1;
                double sx = (fwd[0] * x + fwd[1] * y + fwd[2]) / d;
                double sy = (fwd[3] * x + fwd[4] * y + fwd[5]) / d;
                out[y * W + x] = bilinear(argb, w, h, sx * (w - 1d) / (dw - 1d),
                        sy * (h - 1d) / (dh - 1d));
            }
        }
        if (outWH != null) {
            outWH[0] = W;
            outWH[1] = H;
        }
        return out;
    }

    private static boolean near(double v, double t, double tol) {
        return Math.abs(v - t) <= tol;
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    /** 三角形有向面积×2(凸包顶点索引,取模处理环绕) */
    private static double crossArea(List<double[]> hull, int a, int b, int c) {
        double[] pa = hull.get(a), pb = hull.get(b), pc = hull.get(c);
        return Math.abs((pb[0] - pa[0]) * (pc[1] - pa[1])
                - (pb[1] - pa[1]) * (pc[0] - pa[0]));
    }

    /** Andrew 单调链凸包(输入任意点集) */
    private static List<double[]> convexHull(List<double[]> pts) {
        int n = pts.size();
        double[][] arr = pts.toArray(new double[0][]);
        java.util.Arrays.sort(arr, new java.util.Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return a[0] != b[0]
                        ? Double.compare(a[0], b[0]) : Double.compare(a[1], b[1]);
            }
        });
        double[][] half = new double[2 * n][];
        int k = 0;
        for (int i = 0; i < n; i++) {
            while (k >= 2 && cross(half[k - 2], half[k - 1], arr[i]) <= 0) k--;
            half[k++] = arr[i];
        }
        int lower = k + 1;
        for (int i = n - 2; i >= 0; i--) {
            while (k >= lower && cross(half[k - 2], half[k - 1], arr[i]) <= 0) k--;
            half[k++] = arr[i];
        }
        List<double[]> out = new java.util.ArrayList<>(k - 1);
        for (int i = 0; i < k - 1; i++) out.add(half[i]);
        return out;
    }

    private static double cross(double[] o, double[] a, double[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    /**
     * 单应矩阵(列主序 3×3):4 对点 (x,y)→(u,v) 解 8 元线性方程组。
     * 返回 [h0 h1 h2; h3 h4 h5; h6 h7 1],失败返回 null。
     */
    private static double[] homography(double x0, double y0, double x1, double y1,
                                       double x2, double y2, double x3, double y3,
                                       double u0, double v0, double u1, double v1,
                                       double u2, double v2, double u3, double v3) {
        double[][] A = {
                {x0, y0, 1, 0, 0, 0, -u0 * x0, -u0 * y0},
                {x1, y1, 1, 0, 0, 0, -u1 * x1, -u1 * y1},
                {x2, y2, 1, 0, 0, 0, -u2 * x2, -u2 * y2},
                {x3, y3, 1, 0, 0, 0, -u3 * x3, -u3 * y3},
                {0, 0, 0, x0, y0, 1, -v0 * x0, -v0 * y0},
                {0, 0, 0, x1, y1, 1, -v1 * x1, -v1 * y1},
                {0, 0, 0, x2, y2, 1, -v2 * x2, -v2 * y2},
                {0, 0, 0, x3, y3, 1, -v3 * x3, -v3 * y3},
        };
        double[] b = {u0, u1, u2, u3, v0, v1, v2, v3};
        // Gauss 消元(部分主元)
        for (int col = 0; col < 8; col++) {
            int piv = col;
            for (int r = col + 1; r < 8; r++) {
                if (Math.abs(A[r][col]) > Math.abs(A[piv][col])) piv = r;
            }
            if (Math.abs(A[piv][col]) < 1e-9) return null;
            double[] tmp = A[col];
            A[col] = A[piv];
            A[piv] = tmp;
            double t = b[col];
            b[col] = b[piv];
            b[piv] = t;
            for (int r = 0; r < 8; r++) {
                if (r == col) continue;
                double f = A[r][col] / A[col][col];
                if (f == 0) continue;
                for (int c = col; c < 8; c++) A[r][c] -= f * A[col][c];
                b[r] -= f * b[col];
            }
        }
        double[] hm = new double[9];
        for (int i = 0; i < 8; i++) hm[i] = b[i] / A[i][i];
        hm[8] = 1;
        return hm;
    }

    /** 3×3 求逆(伴随),奇异返回 null(numpy 逐项验证过,勿改) */
    private static double[] invert3(double[] m) {
        double a = m[0], b = m[1], c = m[2];
        double d = m[3], e = m[4], f = m[5];
        double g = m[6], hh = m[7], i = m[8];
        double A = e * i - f * hh, B = -(d * i - f * g), C = d * hh - e * g;
        double det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-12) return null;
        double id = 1 / det;
        return new double[]{
                A * id, -(b * i - c * hh) * id, (b * f - c * e) * id,
                B * id, (a * i - c * g) * id, -(a * f - c * d) * id,
                C * id, -(a * hh - b * g) * id, (a * e - b * d) * id,
        };
    }

    /** 双线性采样(坐标夹到图内) */
    private static int bilinear(int[] argb, int w, int h, double x, double y) {
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x > w - 1) x = w - 1;
        if (y > h - 1) y = h - 1;
        int x0 = (int) x, y0 = (int) y;
        int x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        double fx = x - x0, fy = y - y0;
        int p00 = argb[y0 * w + x0], p10 = argb[y0 * w + x1];
        int p01 = argb[y1 * w + x0], p11 = argb[y1 * w + x1];
        int r = (int) Math.round(((p00 >> 16 & 0xFF) * (1 - fx) + (p10 >> 16 & 0xFF) * fx) * (1 - fy)
                + ((p01 >> 16 & 0xFF) * (1 - fx) + (p11 >> 16 & 0xFF) * fx) * fy);
        int g = (int) Math.round(((p00 >> 8 & 0xFF) * (1 - fx) + (p10 >> 8 & 0xFF) * fx) * (1 - fy)
                + ((p01 >> 8 & 0xFF) * (1 - fx) + (p11 >> 8 & 0xFF) * fx) * fy);
        int b = (int) Math.round(((p00 & 0xFF) * (1 - fx) + (p10 & 0xFF) * fx) * (1 - fy)
                + ((p01 & 0xFF) * (1 - fx) + (p11 & 0xFF) * fx) * fy);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * 边缘背景裁剪,返回裁剪后网格(行优先)。
     * 框选常会把图纸外的空白/桌面底色框进来,形成"整行/列都是背景"的假格子。
     * 背景色 = 采样结果最外圈出现最多的颜色;某条边 ≥90% 是它、且它占全图
     * ≥30%(或四角近同色)时才裁该边;每边最多裁 35%。
     */

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
            if (qDist(v, bg) <= 6) bgTotal++;
        }
        if (bgTotal < n * 0.3) {
            // 细边框兜底:边框不足 30% 时旧门直接放弃——但四角近同色
            // (桌面/画布底色)仍是强背景证据,放宽裁边(实物拍照常见)。
            // 带容差:边界格混入相邻豆色,量化后未必逐位相等
            int c00 = q[0], c10 = q[cols - 1], c01 = q[(rows - 1) * cols], c11 = q[rows * cols - 1];
            if (qDist(c00, c10) <= 6 && qDist(c00, c01) <= 6 && qDist(c00, c11) <= 6) {
                bg = c00;
            } else {
                if (outColsRows != null) {
                    outColsRows[0] = cols;
                    outColsRows[1] = rows;
                }
                return cells;
            }
        }

        int x0 = 0, x1 = cols - 1, y0 = 0, y1 = rows - 1;
        int maxCutX = (int) (cols * 0.35f), maxCutY = (int) (rows * 0.35f);
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

    /** 量化色距离(各通道 nibble 差之和) */
    private static int qDist(int a, int b) {
        return Math.abs(((a >> 12) & 0xF) - ((b >> 12) & 0xF))
                + Math.abs(((a >> 8) & 0xF) - ((b >> 8) & 0xF))
                + Math.abs(((a >> 4) & 0xF) - ((b >> 4) & 0xF));
    }

    private static boolean edgeCol(int[] q, int cols, int rows, int x, int bg, float ratio) {
        int hit = 0;
        for (int y = 0; y < rows; y++) {
            if (qDist(q[y * cols + x], bg) <= 6) hit++;
        }
        return hit >= rows * ratio;
    }

    private static boolean edgeRow(int[] q, int cols, int rows, int y, int bg, float ratio) {
        int hit = 0;
        for (int x = 0; x < cols; x++) {
            if (qDist(q[y * cols + x], bg) <= 6) hit++;
        }
        return hit >= cols * ratio;
    }
}
