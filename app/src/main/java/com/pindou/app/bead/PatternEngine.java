package com.pindou.app.bead;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 核心:把一张照片变成拼豆图纸。
 *
 * 两种风格:
 *  - 写实(STYLE_REALISTIC):逐像素匹配固定拼豆色板,尽量贴合照片
 *  - 抽象(STYLE_ABSTRACT):乐高积木风。先把图像缩小到"砖块"粒度
 *    (每块 = brickSize × brickSize 颗豆,双线性缩放即完成邻域平均),
 *    一整块共用一颗豆的颜色;抽象程度由块大小控制。
 *    可再用 k 均值把全图限定到少数几种主色(海报感),并可吸附到真实豆色。
 *
 * 全程在 CIELAB 空间做最近色匹配,符合人眼感知。
 */
public final class PatternEngine {

    public static final int STYLE_REALISTIC = 0;
    public static final int STYLE_ABSTRACT = 1;

    private static final String SYMBOLS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** 生成参数 */
    public static final class Options {
        public int cols = 58;
        public int rows = 58;
        public boolean dither = false;
        public int brightness = 0;
        public int contrast = 0;
        public int saturation = 0;
        public int style = STYLE_REALISTIC;
        /** 抽象模式:砖块边长(几颗豆见方),2 轻 / 3 中 / 4 强 / 6 超强 */
        public int brickSize = 3;
        /** 抽象模式:是否限定主色调(k 均值提色);关闭则用整套拼豆色板 */
        public boolean abstractUsePalette = true;
        /** 抽象模式:提取的主色数量(4~16) */
        public int abstractColors = 8;
        /** 抽象模式:主色是否吸附到最近的拼豆豆色 */
        public boolean abstractSnapToBeads = true;
        /** 是否自动抠图去背景 */
        public boolean bgRemove = false;
        /** 去背景容差 0~100,映射到 Lab 距离阈值约 14~58 */
        public int bgTolerance = 55;
        /** 圆形拼板:内切圆以外的格子全部置空 */
        public boolean roundBoard = false;
        /** 降色数:限制最终使用的颜色种数,0 = 不限制(贪心合并最相近的色) */
        public int maxColors = 0;
    }

    public static BeadPattern generate(Bitmap source, List<BeadColor> beadPalette, Options o) {
        int cols = Math.max(4, Math.min(200, o.cols));
        int rows = Math.max(4, Math.min(200, o.rows));

        boolean abs = o.style == STYLE_ABSTRACT;
        int b = abs ? Math.max(1, Math.min(8, o.brickSize)) : 1;
        int gw = (cols + b - 1) / b;   // 工作网格宽(块数)
        int gh = (rows + b - 1) / b;

        // 1. 居中裁剪到画幅比例
        double target = (double) cols / rows;
        int sw = source.getWidth(), sh = source.getHeight();
        double cur = (double) sw / sh;
        int cw, ch;
        if (cur > target) {
            ch = sh;
            cw = Math.max(1, (int) Math.round(sh * target));
        } else {
            cw = sw;
            ch = Math.max(1, (int) Math.round(sw / target));
        }
        Bitmap cropped;
        if (cw == sw && ch == sh) {
            cropped = source;
        } else {
            cropped = Bitmap.createBitmap(source, (sw - cw) / 2, (sh - ch) / 2, cw, ch);
        }

        // 2. 取像素:过大的源图先压到长边 1024(两步缩放保证质量,避免超大数组),
        //    之后所有缩放/抠图都在纯像素上做,行为与桌面测试完全一致
        int pw, ph;
        int[] srcPx;
        {
            int longSide = Math.max(cropped.getWidth(), cropped.getHeight());
            if (longSide > 1024) {
                float s = 1024f / longSide;
                int tw = Math.max(1, Math.round(cropped.getWidth() * s));
                int th = Math.max(1, Math.round(cropped.getHeight() * s));
                Bitmap mid = Bitmap.createScaledBitmap(cropped, tw, th, true);
                pw = tw;
                ph = th;
                srcPx = new int[pw * ph];
                mid.getPixels(srcPx, 0, pw, 0, 0, pw, ph);
                if (mid != cropped && mid != source) mid.recycle();
            } else {
                pw = cropped.getWidth();
                ph = cropped.getHeight();
                srcPx = new int[pw * ph];
                cropped.getPixels(srcPx, 0, pw, 0, 0, pw, ph);
            }
        }
        if (cropped != source) cropped.recycle();

        // 2.5 工作网格像素:盒式面积平均降采样(块内所有源像素都参与,
        //     不像双线性大比例缩小只采零星几个点);开去背景时先在高分辨率上
        //     求掩码,再按覆盖率逐格加权平均--背景色不混进主体边缘
        int[] px;
        if (o.bgRemove) {
            px = gridWithBackground(srcPx, pw, ph, gw, gh, o.bgTolerance);
        } else {
            px = boxResample(srcPx, pw, ph, gw, gh);
        }

        // 3. 画面调节
        if (o.brightness != 0 || o.contrast != 0 || o.saturation != 0) {
            for (int i = 0; i < px.length; i++) {
                px[i] = ColorMath.adjust(px[i], o.brightness, o.contrast, o.saturation);
            }
        }

        // 4. 确定生效色板
        List<BeadColor> palette;
        if (!abs) {
            palette = beadPalette;
        } else if (o.abstractUsePalette) {
            palette = buildAbstractPalette(px, beadPalette,
                    o.abstractColors, o.abstractSnapToBeads);
        } else {
            palette = beadPalette;
        }
        int n = palette.size();

        // 5. 工作网格逐块匹配最近豆色(可选 FS 抖动在块层面扩散)
        int[] workCells = new int[gw * gh];
        int[] counts = new int[Math.max(1, n)];
        if (n > 0) {
            double[][] labs = new double[n][];
            for (int i = 0; i < n; i++) labs[i] = ColorMath.rgbToLab(palette.get(i).rgb);

            if (o.dither) {
                double[] curRow = new double[gw * 3];
                double[] nextRow = new double[gw * 3];
                for (int y = 0; y < gh; y++) {
                    for (int x = 0; x < gw; x++) {
                        int p = px[y * gw + x];
                        if (((p >>> 24) & 0xFF) < 128) {
                            workCells[y * gw + x] = -1;
                            continue;
                        }
                        double[] lab = ColorMath.rgbToLab(p);
                        double l = clampL(lab[0] + curRow[x * 3]);
                        double a = lab[1] + curRow[x * 3 + 1];
                        double bl = lab[2] + curRow[x * 3 + 2];
                        int idx = nearest(labs, l, a, bl);
                        workCells[y * gw + x] = idx;

                        double el = l - labs[idx][0];
                        double ea = a - labs[idx][1];
                        double eb = bl - labs[idx][2];
                        if (x + 1 < gw) addErr(curRow, x + 1, el, ea, eb, 7.0 / 16);
                        if (y + 1 < gh) {
                            if (x > 0) addErr(nextRow, x - 1, el, ea, eb, 3.0 / 16);
                            addErr(nextRow, x, el, ea, eb, 5.0 / 16);
                            if (x + 1 < gw) addErr(nextRow, x + 1, el, ea, eb, 1.0 / 16);
                        }
                    }
                    double[] t = curRow;
                    curRow = nextRow;
                    nextRow = t;
                    Arrays.fill(nextRow, 0.0);
                }
            } else {
                for (int i = 0; i < px.length; i++) {
                    int p = px[i];
                    if (((p >>> 24) & 0xFF) < 128) {
                        workCells[i] = -1;
                    } else {
                        double[] lab = ColorMath.rgbToLab(p);
                        workCells[i] = nearest(labs, lab[0], lab[1], lab[2]);
                    }
                }
            }
        } else {
            Arrays.fill(workCells, -1);
        }

        // 6. 展开成最终画幅(一块填满 b×b 颗豆),并统计用量
        int[] cells = new int[cols * rows];
        Arrays.fill(cells, -1);
        expandBricks(workCells, gw, gh, cells, cols, rows, b);

        // 6.5 圆形板:内切圆以外的格子视为板外
        if (o.roundBoard) {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (BeadPattern.isOutsideRound(cols, rows, x, y)) {
                        cells[y * cols + x] = -1;
                    }
                }
            }
        }

        int empty = 0;
        Arrays.fill(counts, 0);
        for (int c : cells) {
            if (c < 0) {
                empty++;
            } else {
                counts[c]++;
            }
        }

        // 6.8 降色数:合并最相近的颜色,直到不超过上限(写实模式的"限 N 色")
        if (o.maxColors > 0) {
            mergeToMaxColors(cells, counts, n, palette, o.maxColors);
        }

        // 7. 统计
        int total = 0;
        List<BeadPattern.UsedColor> used = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (counts[i] > 0) {
                used.add(new BeadPattern.UsedColor(i, palette.get(i), symbolFor(i), counts[i]));
                total += counts[i];
            }
        }
        BeadPattern.sortByCountDesc(used);

        return new BeadPattern(cols, rows, palette, cells, counts, used, total, empty,
                o.roundBoard);
    }

    /**
     * 把粗网格按 b×b 一块展开成细网格;最后一行/列不满一块时钳制到边界。
     */
    public static void expandBricks(int[] coarse, int gw, int gh,
                                    int[] fine, int fw, int fh, int b) {
        for (int y = 0; y < fh; y++) {
            int cy = Math.min(y / b, gh - 1);
            int rowBase = y * fw;
            int srcBase = cy * gw;
            for (int x = 0; x < fw; x++) {
                fine[rowBase + x] = coarse[srcBase + Math.min(x / b, gw - 1)];
            }
        }
    }

    /**
     * 降色数:反复把 Lab ΔE 最近的两色合并(用量小的并入用量大的),
     * 重定向所有格子,直到使用的颜色种数不超过 max。合并保留色的 Lab
     * 按用量加权平均,后续合并以混合后的代表色计算距离。
     */
    private static void mergeToMaxColors(int[] cells, int[] counts, int n,
                                         List<BeadColor> palette, int max) {
        int used = 0;
        for (int i = 0; i < n; i++) {
            if (counts[i] > 0) used++;
        }
        if (used <= max) return;

        double[][] lab = new double[n][];
        long[] weight = new long[n];      // 合并后的加权总量(用于平均 Lab)
        double[][] accLab = new double[n][];
        int[] map = new int[n];           // k -> 合并后代表色
        for (int i = 0; i < n; i++) {
            map[i] = i;
            if (counts[i] > 0) {
                lab[i] = ColorMath.rgbToLab(0xFF000000 | palette.get(i).rgb);
                accLab[i] = new double[]{lab[i][0] * counts[i], lab[i][1] * counts[i],
                        lab[i][2] * counts[i]};
                weight[i] = counts[i];
            }
        }

        while (used > max) {
            int bi = -1, bj = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (counts[i] <= 0) continue;
                for (int j = i + 1; j < n; j++) {
                    if (counts[j] <= 0) continue;
                    double de = ColorMath.dist2(lab[i], lab[j]);
                    if (de < best) {
                        best = de;
                        bi = i;
                        bj = j;
                    }
                }
            }
            int from = counts[bi] <= counts[bj] ? bi : bj;
            int to = from == bi ? bj : bi;
            for (int k = 0; k < n; k++) {
                if (map[k] == from) map[k] = to;
            }
            counts[to] += counts[from];
            counts[from] = 0;
            // 代表色 = 加权平均 Lab
            for (int c = 0; c < 3; c++) {
                accLab[to][c] += accLab[from][c];
                lab[to][c] = accLab[to][c] / weight[to];
            }
            weight[to] += weight[from];
            accLab[from] = null;
            used--;
        }

        for (int i = 0; i < cells.length; i++) {
            if (cells[i] >= 0) cells[i] = map[cells[i]];
        }
    }

    // ---- 去背景 v2 的调参常量 ----
    /** 边界聚类:与已有簇中心 ΔE 小于该值并入同簇 */
    private static final double CLUSTER_JOIN_DE = 16.0;
    /** 边界最多聚出的簇数(防止把五彩边缘碎成几十簇) */
    private static final int MAX_BORDER_CLUSTERS = 8;
    /** 局部连续判据相对全局容差的比例(更严,只用来顺着渐变走) */
    private static final float LOCAL_RATIO = 0.55f;
    /** 连续"纯局部"跳变的最长链(云朵/渐变这类软边缘大块区域靠它吃掉) */
    private static final int LOCAL_HOP_LIMIT = 30;

    /** U2NetP 小模型主体分割的接入点(MlSegmenter 实现)。
     *  输入任意宽高的 RGB 像素,返回 NET×NET 的前景概率图(整幅拉伸映射,
     *  不裁剪);返回 null 或抛异常时自动回退到颜色统计算法,引擎其余部分不依赖它。 */
    public interface MlProvider {
        float[] findSubjectProbs(int[] rgb, int w, int h);
    }

    private static volatile MlProvider mlProvider;

    public static void setMlProvider(MlProvider p) {
        mlProvider = p;
    }

    /** 最近一次 subjectMask 的决策:"ML"=模型掩码 / "V4"=颜色统计兜底 /
     *  "NONE"=守卫拒绝不抠(调试与批量测试观察用) */
    public static volatile String lastMaskSource = "?";

    /** 去背景工作图目标长边:网格 × S,尽量贴近模型的 320 分辨率 */
    private static final int MASK_WORK_SIDE = 320;
    /** 掩码可信区间:前景占比 8%~88%,超出视为满幅特写/无主体,放弃抠图 */
    private static final float FG_MIN_RATIO = 0.08f;
    private static final float FG_MAX_RATIO = 0.88f;
    /** ML 漏抠校验:ML 前景小于该比例时才可能触发 */
    private static final float CROSSCHECK_ML_MAX = 0.20f;
    /** ML 漏抠校验:ML 前景比颜色统计前景少超过该值 → ML 掩码塌缩成碎片 */
    private static final float CROSSCHECK_GAP = 0.12f;
    /** 细结构救援:救援区域与所有背景簇中心的 Lab 距离必须超过该值
     *  (确保救回的是"前景色"部件,排除渐变/阴影的泛洪渗漏) */
    private static final float RESCUE_MIN_DE = 16f;
    /** 细结构救援:救援面积超过画幅该比例判定泛洪失控,放弃 */
    private static final float RESCUE_MAX_RATIO = 0.30f;
    /** 细结构救援:小于画幅该比例的孤立碎点视为噪声丢弃 */
    private static final float RESCUE_MIN_BLOB = 0.0005f;

    /**
     * 一键去背景 v6:先在高分辨率工作图上求前景掩码,再按"覆盖率"决定
     * 每个拼豆格子去留,格子颜色只平均掩码内的像素。
     *
     *  v5 及之前的问题(用户实测反馈,后台语料可复现):
     *  ① 先把图缩到网格再抠图,主体边缘格子被背景色污染 -> 转成豆色后
     *    边界颜色失真(描边变浅、轮廓发灰);
     *  ② ML 掩码 320×320 硬阈值后最近邻缩到网格,边界一半细节被丢弃
     *    -> 去除不干净(残渣)或啃掉主体;
     *  ③ 容差滑杆只作用于颜色统计兜底,ML 路径固定 0.5 阈值,调了没反应;
     *  ④ MlSegmenter 对已按画幅裁剪的图再次居中裁方,非方形画幅时
     *    掩码与图像错位。
     *
     * 流程:盒式降采样到工作图(网格 × S,长边≈320)-> ML 概率图
     *  (双线性重采样 + 容差阈值)优先 / 颜色统计 v4 兜底 -> 占比守卫
     *  -> 逐格覆盖率 ≥50% 保留,颜色 = 格内掩码内像素平均 -> 补钉眼。
     * 掩码不可信时退回普通盒式采样,不会为了抠图而毁图。
     *
     * @param srcPx 已按画幅比例裁剪好的源图像素(不透明 ARGB)
     */
    public static int[] gridWithBackground(int[] srcPx, int sw, int sh,
                                           int gw, int gh, int tolerancePct) {
        int s = Math.max(1, Math.min(12,
                (int) Math.round((double) MASK_WORK_SIDE / Math.max(gw, gh))));
        while (s > 1 && (gw * s > sw || gh * s > sh)) s--;
        int mw = gw * s, mh = gh * s;

        int[] work = boxResample(srcPx, sw, sh, mw, mh);
        boolean[] fg = subjectMask(work, mw, mh, tolerancePct);
        if (fg == null) {
            return boxResample(srcPx, sw, sh, gw, gh);
        }
        return maskCoverageDownscale(work, mw, mh, fg, gw, gh, s);
    }

    /**
     * ML 概率图(容差 -> 阈值)优先,颜色统计 v4 兜底;都不占比异常返回 null。
     * 加一道交叉校验:U2NetP 在满幅特写/大面积主体上的已知失败模式是
     * "漏抠"——只留下眼睛、logo 等高显著度小碎片(后台语料里的猫脸
     * chelsea、人像 rommel、篮球、海象都复现)。当 ML 前景占比很小
     * (<20%)却比颜色统计前景少 12 个百分点以上时,判定掩码塌缩,
     * 改用颜色统计掩码;ML 前景较大时两算法分歧属正常(颜色统计在
     * 复杂背景上常翻车,如 horse/chicky),仍以 ML 为准。
     */
    private static boolean[] subjectMask(int[] work, int mw, int mh, int tolerancePct) {
        boolean[] ml = null;
        MlProvider p = mlProvider;
        if (p != null) {
            try {
                float[] probs = p.findSubjectProbs(work, mw, mh);
                if (probs != null) {
                    int net = (int) Math.round(Math.sqrt(probs.length));
                    if (net >= 8 && probs.length == net * net) {
                        float thr = 0.30f + 0.45f * clampPct(tolerancePct) / 100f;
                        float[] plane = resizePlane(probs, net, net, mw, mh);
                        // 占比守卫固定在 0.5 阈值上(min-max 归一化后模型的"本意"),
                        // 防止容差调低时噪声概率混进来把垃圾掩码放大到可信区间
                        int canonCount = 0;
                        for (float v : plane) {
                            if (v >= 0.5f) canonCount++;
                        }
                        float canonRatio = canonCount / (float) plane.length;
                        if (canonRatio >= FG_MIN_RATIO && canonRatio <= FG_MAX_RATIO) {
                            boolean[] mask = new boolean[plane.length];
                            int cnt = 0;
                            for (int i = 0; i < mask.length; i++) {
                                mask[i] = plane[i] >= thr;
                                if (mask[i]) cnt++;
                            }
                            // 应用掩码自身也要有底线(高容差可能缩到没有)
                            float appRatio = cnt / (float) plane.length;
                            if (appRatio >= 0.03f && appRatio <= FG_MAX_RATIO) {
                                ml = mask;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // 模型路径任何问题都走颜色统计兜底
            }
        }
        float mlRatio = -1;
        if (ml != null) {
            mlRatio = trueRatio(ml);
            if (mlRatio < FG_MIN_RATIO || mlRatio > FG_MAX_RATIO) ml = null;
        }

        // 颜色统计 v4(常驻计算:既是兜底,也做 ML 的交叉校验)
        float factor = 0.70f + clampPct(tolerancePct) * 0.0065f;
        boolean[] v4 = SubjectSegmenter.findSubject(work, mw, mh, factor);
        float v4Ratio = trueRatio(v4);
        boolean v4ok = v4Ratio >= FG_MIN_RATIO && v4Ratio <= FG_MAX_RATIO;

        if (ml != null && v4ok
                && mlRatio < CROSSCHECK_ML_MAX
                && v4Ratio - mlRatio > CROSSCHECK_GAP) {
            ml = null;
        }
        boolean[] base;
        if (ml != null) {
            base = ml;
            lastMaskSource = "ML";
        } else if (v4ok) {
            base = v4;
            lastMaskSource = "V4";
        } else {
            lastMaskSource = "NONE";
            return null;
        }
        boolean[] merged = rescueDetachedForeground(work, mw, mh, base, tolerancePct);
        if (merged == base) return base;
        float mergedRatio = trueRatio(merged);
        if (mergedRatio < FG_MIN_RATIO || mergedRatio > FG_MAX_RATIO) return base;
        lastMaskSource += "+R";
        return merged;
    }

    private static float trueRatio(boolean[] mask) {
        int c = 0;
        for (boolean b : mask) {
            if (b) c++;
        }
        return c / (float) mask.length;
    }

    /**
     * 细结构救援:U²-Net 系模型对"与主体分离的细小部件"(太阳光芒、
     * 漂浮装饰、孤立的耳朵/尾巴尖)会给出接近 0 的显著度——概率图里
     * 根本没有,任何阈值都救不回(实测 u2net 完整版同样如此;SOD 训练
     * 数据的主体几乎都是连通整块)。而纯色/浅色背景的插画恰恰是拼豆的
     * 高频素材:从边界泛洪"与背景同色的连通域",这些部件天然会被留下。
     *
     * 做法:边界背景聚类 → 双门槛泛洪(全局容差 + 局部连续 + Sobel
     * 边缘门,v2 算法的复活)得背景连通域 B;候选救援 = 泛洪前景 ∧
     * ML 掩码之外;仅当候选与所有背景簇中心的 Lab 距离都超过
     * RESCUE_MIN_DE(真是"前景色",排除渐变/阴影渗漏)且总面积不超过
     * 画幅 RESCUE_MAX_RATIO 时并入。不适用时原样返回 fg。
     */
    private static boolean[] rescueDetachedForeground(int[] work, int mw, int mh,
                                                      boolean[] fg, int tolerancePct) {
        java.util.ArrayList<double[]> centers = borderClusterCenters(work, mw, mh);
        if (centers.isEmpty()) return fg;
        boolean[] bgFlood = floodBackgroundRegion(work, mw, mh, centers, tolerancePct);

        int n = mw * mh;
        boolean[] add = new boolean[n];
        int addCount = 0;
        for (int i = 0; i < n; i++) {
            if (fg[i] || bgFlood[i]) continue;
            double[] lab = ColorMath.rgbToLab(work[i]);
            double best = Double.MAX_VALUE;
            for (double[] c : centers) {
                double dl = lab[0] - c[0], da = lab[1] - c[1], db = lab[2] - c[2];
                double d = Math.sqrt(dl * dl + da * da + db * db);
                if (d < best) best = d;
            }
            if (best > RESCUE_MIN_DE) {
                add[i] = true;
                addCount++;
            }
        }
        if (addCount == 0 || addCount > n * RESCUE_MAX_RATIO) return fg;

        add = dropTinyComponents(add, mw, mh, (int) Math.max(4, n * RESCUE_MIN_BLOB));
        add = dilate1(add, mw, mh);

        boolean[] out = fg.clone();
        boolean changed = false;
        for (int i = 0; i < n; i++) {
            if (add[i] && !out[i]) {
                out[i] = true;
                changed = true;
            }
        }
        return changed ? out : fg;
    }

    /** 边界环背景聚类(与 SubjectSegmenter 同参数):返回 {L,a,b,count} 列表 */
    private static java.util.ArrayList<double[]> borderClusterCenters(int[] px, int w, int h) {
        int n = w * h;
        java.util.ArrayList<double[]> centers = new java.util.ArrayList<>();
        int ring = 0;
        for (int i = 0; i < n; i++) {
            boolean border = (i < w) || (i >= n - w) || (i % w == 0) || (i % w == w - 1);
            if (!border) continue;
            ring++;
            double[] lab = ColorMath.rgbToLab(px[i]);
            double[] p = {lab[0], lab[1], lab[2], 1};
            boolean joined = false;
            for (double[] c : centers) {
                double dl = p[0] - c[0], da = p[1] - c[1], db = p[2] - c[2];
                if (Math.sqrt(dl * dl + da * da + db * db) <= CLUSTER_JOIN_DE) {
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
        double minCount = Math.max(4, ring * 0.04);
        for (int j = centers.size() - 1; j >= 0; j--) {
            if (centers.get(j)[3] < minCount) centers.remove(j);
        }
        return centers;
    }

    /**
     * 边界种子泛洪(v2 算法,仅作细结构救援用):全局容差门 + 局部连续门
     * (限跳)+ Sobel 边缘门,返回"与边界背景同色的连通域"。
     */
    private static boolean[] floodBackgroundRegion(int[] px, int w, int h,
                                                   java.util.ArrayList<double[]> centers,
                                                   int tolerancePct) {
        int n = w * h;
        float tol = 14f + clampPct(tolerancePct) * 44f / 100f;
        float localTol = tol * LOCAL_RATIO;

        double[] L = new double[n], A = new double[n], B = new double[n];
        for (int i = 0; i < n; i++) {
            double[] lab = ColorMath.rgbToLab(px[i]);
            L[i] = lab[0];
            A[i] = lab[1];
            B[i] = lab[2];
        }
        float[] lum = new float[n];
        for (int i = 0; i < n; i++) {
            int p = px[i];
            lum[i] = 0.299f * ((p >> 16) & 0xFF) + 0.587f * ((p >> 8) & 0xFF)
                    + 0.114f * (p & 0xFF);
        }
        float[] mag = new float[n];
        double magSum = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                float tl = lum[i - w - 1], tc = lum[i - w], tr = lum[i - w + 1];
                float ml = lum[i - 1], mr = lum[i + 1];
                float bl = lum[i + w - 1], bc = lum[i + w], br = lum[i + w + 1];
                float gx = -tl - 2 * ml - bl + tr + 2 * mr + br;
                float gy = -tl - 2 * tc - tr + bl + 2 * bc + br;
                float mg = (float) Math.sqrt(gx * gx + gy * gy);
                mag[i] = mg;
                magSum += mg;
            }
        }
        float edgeGate = (float) Math.max(22.0, magSum / n * 2.2);

        boolean[] bg = new boolean[n];
        int[] hops = new int[n];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            boolean border = (i < w) || (i >= n - w) || (i % w == 0) || (i % w == w - 1);
            if (!border) continue;
            for (double[] c : centers) {
                double dl = L[i] - c[0], da = A[i] - c[1], db = B[i] - c[2];
                if (dl * dl + da * da + db * db <= tol * tol) {
                    bg[i] = true;
                    hops[i] = 0;
                    queue.add(i);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            int j = queue.poll();
            int x = j % w, y = j / w;
            spread(bg, hops, queue, L, A, B, centers, mag, edgeGate,
                    x - 1, y, w, h, j, tol, localTol);
            spread(bg, hops, queue, L, A, B, centers, mag, edgeGate,
                    x + 1, y, w, h, j, tol, localTol);
            spread(bg, hops, queue, L, A, B, centers, mag, edgeGate,
                    x, y - 1, w, h, j, tol, localTol);
            spread(bg, hops, queue, L, A, B, centers, mag, edgeGate,
                    x, y + 1, w, h, j, tol, localTol);
        }
        return bg;
    }

    /** 丢弃小于 minSize 的 4 连通碎点(救援区域去噪) */
    private static boolean[] dropTinyComponents(boolean[] m, int w, int h, int minSize) {
        int n = w * h;
        boolean[] out = new boolean[n];
        boolean[] seen = new boolean[n];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        java.util.ArrayList<Integer> blob = new java.util.ArrayList<>();
        for (int seed = 0; seed < n; seed++) {
            if (!m[seed] || seen[seed]) continue;
            seen[seed] = true;
            queue.add(seed);
            blob.clear();
            while (!queue.isEmpty()) {
                int i = queue.poll();
                blob.add(i);
                int x = i % w, y = i / w;
                if (x > 0 && m[i - 1] && !seen[i - 1]) {
                    seen[i - 1] = true;
                    queue.add(i - 1);
                }
                if (x < w - 1 && m[i + 1] && !seen[i + 1]) {
                    seen[i + 1] = true;
                    queue.add(i + 1);
                }
                if (y > 0 && m[i - w] && !seen[i - w]) {
                    seen[i - w] = true;
                    queue.add(i - w);
                }
                if (y < h - 1 && m[i + w] && !seen[i + w]) {
                    seen[i + w] = true;
                    queue.add(i + w);
                }
            }
            if (blob.size() >= minSize) {
                for (int i : blob) out[i] = true;
            }
        }
        return out;
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

    /** 逐格统计掩码覆盖率:≥50% 保留;保留格颜色 = 格内掩码内像素的平均 */
    private static int[] maskCoverageDownscale(int[] work, int mw, int mh,
                                               boolean[] fg, int gw, int gh, int s) {
        int[] out = new int[gw * gh];
        boolean[] bg = new boolean[gw * gh];
        int cell = s * s;
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                int inside = 0, sr = 0, sg = 0, sb = 0;
                for (int y = 0; y < s; y++) {
                    int row = (gy * s + y) * mw + gx * s;
                    for (int x = 0; x < s; x++) {
                        int i = row + x;
                        if (fg[i]) {
                            inside++;
                            int c = work[i];
                            sr += (c >> 16) & 0xFF;
                            sg += (c >> 8) & 0xFF;
                            sb += c & 0xFF;
                        }
                    }
                }
                int k = gy * gw + gx;
                if (inside * 2 >= cell) {
                    out[k] = 0xFF000000 | (sr / inside << 16) | (sg / inside << 8) | sb / inside;
                } else {
                    out[k] = 0;
                    bg[k] = true;
                }
            }
        }
        fillPinholes(out, bg, gw, gh);
        return out;
    }

    private static int clampPct(int v) {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }

    /**
     * 盒式面积平均重采样(RGB,忽略 alpha)。
     * 每个目标像素取源图对应矩形内全部像素的平均--大比例缩小时
     * 所有源像素都参与,不像双线性只零星采样,边缘颜色不会被漏掉。
     * 目标比源大时退化为双线性。
     */
    public static int[] boxResample(int[] src, int sw, int sh, int dw, int dh) {
        if (dw == sw && dh == sh) return src.clone();
        if (dw > sw || dh > sh) return resampleBilinear(src, sw, sh, dw, dh);
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int sy0 = y * sh / dh;
            int sy1 = Math.max(sy0 + 1, ceilDiv((y + 1) * sh, dh));
            sy1 = Math.min(sy1, sh);
            for (int x = 0; x < dw; x++) {
                int sx0 = x * sw / dw;
                int sx1 = Math.max(sx0 + 1, ceilDiv((x + 1) * sw, dw));
                sx1 = Math.min(sx1, sw);
                long r = 0, g = 0, b = 0;
                int cnt = 0;
                for (int yy = sy0; yy < sy1; yy++) {
                    int row = yy * sw;
                    for (int xx = sx0; xx < sx1; xx++) {
                        int c = src[row + xx];
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                        cnt++;
                    }
                }
                out[y * dw + x] = 0xFF000000
                        | ((int) Math.round(r / (double) cnt) << 16)
                        | ((int) Math.round(g / (double) cnt) << 8)
                        | (int) Math.round(b / (double) cnt);
            }
        }
        return out;
    }

    /** 双线性重采样(RGB,忽略 alpha) */
    public static int[] resampleBilinear(int[] src, int sw, int sh, int dw, int dh) {
        if (dw == sw && dh == sh) return src.clone();
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            double fy = (dh == 1) ? 0 : (y + 0.5) * sh / (double) dh - 0.5;
            int y0 = (int) Math.floor(fy);
            double ty = fy - y0;
            if (y0 < 0) {
                y0 = 0;
                ty = 0;
            }
            if (y0 >= sh - 1) {
                y0 = sh - 1;
                ty = 0;
            }
            int y1 = Math.min(sh - 1, y0 + 1);
            for (int x = 0; x < dw; x++) {
                double fx = (dw == 1) ? 0 : (x + 0.5) * sw / (double) dw - 0.5;
                int x0 = (int) Math.floor(fx);
                double tx = fx - x0;
                if (x0 < 0) {
                    x0 = 0;
                    tx = 0;
                }
                if (x0 >= sw - 1) {
                    x0 = sw - 1;
                    tx = 0;
                }
                int x1 = Math.min(sw - 1, x0 + 1);
                int c00 = src[y0 * sw + x0], c10 = src[y0 * sw + x1];
                int c01 = src[y1 * sw + x0], c11 = src[y1 * sw + x1];
                int r = bilinear1((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF,
                        (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, tx, ty);
                int g = bilinear1((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF,
                        (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, tx, ty);
                int b = bilinear1(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, tx, ty);
                out[y * dw + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    private static int bilinear1(int v00, int v10, int v01, int v11, double tx, double ty) {
        double top = v00 + (v10 - v00) * tx;
        double bot = v01 + (v11 - v01) * tx;
        int v = (int) Math.round(top + (bot - top) * ty);
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /**
     * 概率平面重采样:缩小取面积平均,放大取双线性。
     * 掩码概率是平滑量,重采样不产生硬边界锯齿。
     */
    static float[] resizePlane(float[] src, int sw, int sh, int dw, int dh) {
        float[] out = new float[dw * dh];
        for (int y = 0; y < dh; y++) {
            for (int x = 0; x < dw; x++) {
                out[y * dw + x] = (dw <= sw && dh <= sh)
                        ? areaAvg(src, sw, sh, x * sw / (double) dw, (x + 1) * sw / (double) dw,
                                y * sh / (double) dh, (y + 1) * sh / (double) dh)
                        : bilinearAt(src, sw, sh,
                                (x + 0.5) * sw / (double) dw - 0.5,
                                (y + 0.5) * sh / (double) dh - 0.5);
            }
        }
        return out;
    }

    /** [x0,x1)×[y0,y1) 覆盖的源像素平均(矩形至少含一个像素中心) */
    private static float areaAvg(float[] src, int sw, int sh,
                                 double x0, double x1, double y0, double y1) {
        int ix0 = (int) Math.ceil(x0 - 0.5);
        int ix1 = (int) Math.ceil(x1 - 0.5);
        int iy0 = (int) Math.ceil(y0 - 0.5);
        int iy1 = (int) Math.ceil(y1 - 0.5);
        if (ix0 < 0) ix0 = 0;
        if (iy0 < 0) iy0 = 0;
        if (ix1 > sw) ix1 = sw;
        if (iy1 > sh) iy1 = sh;
        if (ix0 >= ix1 || iy0 >= iy1) {
            // 覆盖不到任何像素中心:取矩形中心做双线性
            return bilinearAt(src, sw, sh, (x0 + x1) / 2 - 0.5, (y0 + y1) / 2 - 0.5);
        }
        double sum = 0;
        int cnt = 0;
        for (int y = iy0; y < iy1; y++) {
            int row = y * sw;
            for (int x = ix0; x < ix1; x++) {
                sum += src[row + x];
                cnt++;
            }
        }
        return (float) (sum / cnt);
    }

    private static float bilinearAt(float[] src, int sw, int sh, double fx, double fy) {
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        double tx = fx - x0, ty = fy - y0;
        if (x0 < 0) {
            x0 = 0;
            tx = 0;
        }
        if (y0 < 0) {
            y0 = 0;
            ty = 0;
        }
        if (x0 > sw - 1) {
            x0 = sw - 1;
            tx = 0;
        }
        if (y0 > sh - 1) {
            y0 = sh - 1;
            ty = 0;
        }
        int x1 = Math.min(sw - 1, x0 + 1);
        int y1 = Math.min(sh - 1, y0 + 1);
        double top = src[y0 * sw + x0] + (src[y0 * sw + x1] - src[y0 * sw + x0]) * tx;
        double bot = src[y1 * sw + x0] + (src[y1 * sw + x1] - src[y1 * sw + x0]) * tx;
        return (float) (top + (bot - top) * ty);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    /**
     * 旧版算法(原 removeBackground):多色边界聚类 + 双门槛区域生长 + 边缘门槛。
     * 现仅作为 v3 主体识别失败时的兜底。
     */
    private static void removeBackgroundByColorFlood(int[] px, int w, int h, int tolerancePct) {
        int n = w * h;
        if (n == 0 || w < 3 || h < 3) return;
        float tol = 14f + Math.max(0, Math.min(100, tolerancePct)) * 44f / 100f;

        double[] L = new double[n], A = new double[n], B = new double[n];
        for (int i = 0; i < n; i++) {
            double[] lab = ColorMath.rgbToLab(px[i]);
            L[i] = lab[0];
            A[i] = lab[1];
            B[i] = lab[2];
        }

        // ---- ① 边界环聚类(贪心,中心均值在线更新)。centers: {L,a,b,count} ----
        java.util.ArrayList<double[]> centers = new java.util.ArrayList<>();
        int ringCount = 0;
        for (int i = 0; i < n; i++) {
            boolean border = (i < w) || (i >= n - w) || (i % w == 0) || (i % w == w - 1);
            if (!border) continue;
            ringCount++;
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
        if (centers.isEmpty()) return;

        // 占比 >=4% 的簇才算真背景色(主题压到边上的小簇直接忽略)
        double minCount = Math.max(4, ringCount * 0.04);
        int m = centers.size();
        for (int j = m - 1; j >= 0; j--) {
            if (centers.get(j)[3] < minCount) centers.remove(j);
        }
        if (centers.isEmpty()) return;   // 边缘全是花色的复杂照片,不硬抠

        float localTol = tol * LOCAL_RATIO;

        // ---- ③ 边缘门槛:Sobel 梯度图 + 自适应阈值 ----
        float[] lum = new float[n];
        for (int i = 0; i < n; i++) {
            int p = px[i];
            lum[i] = 0.299f * ((p >> 16) & 0xFF) + 0.587f * ((p >> 8) & 0xFF)
                    + 0.114f * (p & 0xFF);
        }
        float[] mag = new float[n];
        double magSum = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                float tl = lum[i - w - 1], tc = lum[i - w], tr = lum[i - w + 1];
                float ml = lum[i - 1], mr = lum[i + 1];
                float bl = lum[i + w - 1], bc = lum[i + w], br = lum[i + w + 1];
                float gx = -tl - 2 * ml - bl + tr + 2 * mr + br;
                float gy = -tl - 2 * tc - tr + bl + 2 * bc + br;
                float mg = (float) Math.sqrt(gx * gx + gy * gy);
                mag[i] = mg;
                magSum += mg;
            }
        }
        // 阈值 = max(22, 平均梯度的 2.2 倍):平整插画/照片取下限,
        // 纹理杂乱的照片自动放宽,只挡真正的轮廓线
        float edgeGate = (float) Math.max(22.0, magSum / n * 2.2);

        // ---- ② 种子 = 属于背景簇的边界格,BFS 生长 ----
        boolean[] bg = new boolean[n];
        int[] hops = new int[n];   // 距上次"全局确认"连跳了几步纯局部扩散
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            boolean border = (i < w) || (i >= n - w) || (i % w == 0) || (i % w == w - 1);
            if (!border) continue;
            double[] p = {L[i], A[i], B[i], 0};
            for (double[] c : centers) {
                if (dist(p, c) <= tol) {
                    bg[i] = true;
                    hops[i] = 0;
                    queue.add(i);
                    break;
                }
            }
        }

        while (!queue.isEmpty()) {
            int j = queue.poll();
            int x = j % w, y = j / w;
            spread(bg, hops, queue, L, A, B, centers, mag, edgeGate,
                    x - 1, y, w, h, j, tol, localTol);
            spread(bg, hops, queue, L, A, B, centers, mag, edgeGate,
                    x + 1, y, w, h, j, tol, localTol);
            spread(bg, hops, queue, L, A, B, centers, mag, edgeGate,
                    x, y - 1, w, h, j, tol, localTol);
            spread(bg, hops, queue, L, A, B, centers, mag, edgeGate,
                    x, y + 1, w, h, j, tol, localTol);
        }

        // ---- ③ 面积安全阀 ----
        int removed = 0;
        for (boolean b : bg) {
            if (b) removed++;
        }
        if (removed == 0 || removed > n * 92 / 100) return;

        for (int i = 0; i < n; i++) {
            if (bg[i]) px[i] &= 0x00FFFFFF;   // 只清 alpha,RGB 留着给补钉眼用
        }

        // ---- ④ 补钉眼 ----
        fillPinholes(px, bg, w, h);
    }

    /**
     * BFS 单步扩展。三道门按顺序过:
     * 1) 边缘门:目标格梯度超阈值(轮廓线)→ 永不越过,保护浅色主体;
     * 2) 颜色门:与任一背景簇中心近似(不限步数)或与相邻已抠格近似
     *    (连续纯局部跳变限 LOCAL_HOP_LIMIT 步,用来吃掉云朵/渐变)。
     */
    private static void spread(boolean[] bg, int[] hops, java.util.ArrayDeque<Integer> queue,
                               double[] L, double[] A, double[] B,
                               java.util.ArrayList<double[]> centers,
                               float[] mag, float edgeGate,
                               int x, int y, int w, int h, int from,
                               float tol, float localTol) {
        if (x < 0 || x >= w || y < 0 || y >= h) return;
        int k = y * w + x;
        if (bg[k]) return;
        if (mag[k] > edgeGate) return;   // 轮廓线/强边缘:泛洪止步

        boolean nearAnyCenter = false;
        for (double[] c : centers) {
            double dl = L[k] - c[0], da = A[k] - c[1], db = B[k] - c[2];
            if (dl * dl + da * da + db * db <= tol * tol) {
                nearAnyCenter = true;
                break;
            }
        }
        double dnl = L[k] - L[from], dna = A[k] - A[from], dnb = B[k] - B[from];
        boolean nearNeighbor = dnl * dnl + dna * dna + dnb * dnb <= localTol * localTol;

        if (!nearAnyCenter && !nearNeighbor) return;
        if (!nearAnyCenter) {
            if (hops[from] + 1 > LOCAL_HOP_LIMIT) return;
            hops[k] = hops[from] + 1;
        } else {
            hops[k] = 0;
        }
        bg[k] = true;
        queue.add(k);
    }

    /**
     * 补钉眼:某个被抠空的格子若 8 个邻居全是实心格,且这些邻居的
     * 颜色基本一致(主体内部),则把该格恢复成邻居的主色。
     * 只做一轮,避免连锁把大面积透明区域误填。
     */
    private static void fillPinholes(int[] px, boolean[] bg, int w, int h) {
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                if (!bg[i]) continue;
                int solid = 0;
                int sumR = 0, sumG = 0, sumB = 0;
                boolean allSame = true;
                int baseR = -1, baseG = -1, baseB = -1;
                for (int dy = -1; dy <= 1 && allSame; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int j = (y + dy) * w + (x + dx);
                        if (bg[j]) {
                            allSame = false;
                            break;
                        }
                        solid++;
                        int r = (px[j] >> 16) & 0xFF;
                        int g = (px[j] >> 8) & 0xFF;
                        int b = px[j] & 0xFF;
                        if (baseR < 0) {
                            baseR = r;
                            baseG = g;
                            baseB = b;
                        } else if (Math.abs(r - baseR) > 40
                                || Math.abs(g - baseG) > 40
                                || Math.abs(b - baseB) > 40) {
                            allSame = false;
                            break;
                        }
                        sumR += r;
                        sumG += g;
                        sumB += b;
                    }
                }
                if (solid == 8 && allSame) {
                    px[i] = 0xFF000000 | (sumR / 8 << 16) | (sumG / 8 << 8) | (sumB / 8);
                    bg[i] = false;
                }
            }
        }
    }

    /** 4 维距离(L,a,b + 占位),用于簇比较 */
    private static double dist(double[] a, double[] b) {
        double dl = a[0] - b[0], da = a[1] - b[1], db = a[2] - b[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /**
     * 抽象模式:从工作网格像素里提取主色构成色板。
     * snapToBeads=true 时主色吸附到最近的拼豆色(可直接照单买豆);
     * 否则直接使用照片原色。返回按用量多优先排序。
     */
    public static List<BeadColor> buildAbstractPalette(int[] px, List<BeadColor> beadPalette,
                                                       int wantColors, boolean snapToBeads) {
        List<BeadColor> result = new ArrayList<>();
        ArrayList<double[]> list = new ArrayList<>();
        for (int p : px) {
            if (((p >>> 24) & 0xFF) >= 128) list.add(ColorMath.rgbToLab(p));
        }
        if (list.isEmpty()) return result;
        double[][] labs = list.toArray(new double[0][]);
        double[][] centers = kmeansLab(labs, wantColors);
        if (centers.length == 0) return result;

        if (snapToBeads) {
            double[][] beadLabs = new double[beadPalette.size()][];
            for (int i = 0; i < beadPalette.size(); i++) {
                beadLabs[i] = ColorMath.rgbToLab(beadPalette.get(i).rgb);
            }
            Set<Integer> used = new HashSet<>();
            for (double[] c : centers) {
                int bi = nearest(beadLabs, c[0], c[1], c[2]);
                if (used.add(bi)) result.add(beadPalette.get(bi));
            }
        } else {
            for (int i = 0; i < centers.length; i++) {
                result.add(new BeadColor(i + 1, "主色" + (i + 1),
                        ColorMath.labToRgb(centers[i][0], centers[i][1], centers[i][2])));
            }
        }
        return result;
    }

    /**
     * k 均值聚类(Lab 空间)。按亮度分位初始化(确定性),最多 12 轮,
     * 合并距离过近(ΔE<3)的簇,返回 [L,a,b,像素数] 数组,按簇大小降序。
     */
    public static double[][] kmeansLab(double[][] labs, int wantK) {
        int n = labs.length;
        if (n == 0) return new double[0][];
        int k = Math.max(1, Math.min(wantK, n));

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(labs[a][0], labs[b][0]);
            }
        });
        double[][] centers = new double[k][];
        for (int j = 0; j < k; j++) {
            int idx = order[(int) Math.min(n - 1, (long) ((j + 0.5) * n / k))];
            centers[j] = new double[]{labs[idx][0], labs[idx][1], labs[idx][2]};
        }

        int[] assign = new int[n];
        for (int iter = 0; iter < 12; iter++) {
            double shift = 0;
            double[] sumL = new double[k], sumA = new double[k], sumB = new double[k];
            int[] cnt = new int[k];
            for (int i = 0; i < n; i++) {
                int best = nearest(centers, labs[i][0], labs[i][1], labs[i][2]);
                assign[i] = best;
                sumL[best] += labs[i][0];
                sumA[best] += labs[i][1];
                sumB[best] += labs[i][2];
                cnt[best]++;
            }
            for (int j = 0; j < k; j++) {
                if (cnt[j] == 0) continue;
                double nl = sumL[j] / cnt[j];
                double na = sumA[j] / cnt[j];
                double nb = sumB[j] / cnt[j];
                double dl = nl - centers[j][0];
                double da = na - centers[j][1];
                double db = nb - centers[j][2];
                shift += dl * dl + da * da + db * db;
                centers[j][0] = nl;
                centers[j][1] = na;
                centers[j][2] = nb;
            }
            if (shift < 0.5) break;
        }

        int[] cnt = new int[k];
        for (int a : assign) cnt[a]++;
        Integer[] cOrder = new Integer[k];
        for (int j = 0; j < k; j++) cOrder[j] = j;
        Arrays.sort(cOrder, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return cnt[b] - cnt[a];
            }
        });

        List<double[]> merged = new ArrayList<>();
        for (int j : cOrder) {
            if (cnt[j] == 0) continue;
            double[] c = centers[j];
            boolean dup = false;
            for (double[] m : merged) {
                double dl = c[0] - m[0];
                double da = c[1] - m[1];
                double db = c[2] - m[2];
                if (dl * dl + da * da + db * db < 9.0) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                merged.add(new double[]{c[0], c[1], c[2], cnt[j]});
            }
        }
        return merged.toArray(new double[0][]);
    }

    private static void addErr(double[] row, int x, double el, double ea, double eb, double w) {
        row[x * 3] += el * w;
        row[x * 3 + 1] += ea * w;
        row[x * 3 + 2] += eb * w;
    }

    private static double clampL(double v) {
        return v < 0 ? 0 : (v > 100 ? 100 : v);
    }

    private static int nearest(double[][] labs, double l, double a, double b) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < labs.length; i++) {
            double dl = l - labs[i][0];
            double da = a - labs[i][1];
            double db = b - labs[i][2];
            double d = dl * dl + da * da + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** 给色板下标分配一个图纸符号:先 A-Z a-z 0-9,再 AA、AB… */
    public static String symbolFor(int paletteIndex) {
        if (paletteIndex < SYMBOLS.length()) {
            return String.valueOf(SYMBOLS.charAt(paletteIndex));
        }
        int i = paletteIndex - SYMBOLS.length();
        char c1 = (char) ('A' + (i / 26) % 26);
        char c2 = (char) ('A' + i % 26);
        return new String(new char[]{c1, c2});
    }

    private PatternEngine() {
    }
}
