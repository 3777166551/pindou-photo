import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.PatternEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * 成品照片转图纸测试(纯 JVM):
 *  - 合成"豆子照片":圆豆 + 中心孔 + 板底色 + 光照渐变 + 噪声
 *  - detectBeadGrid:豆距还原 ±1.5px,相位折回 [0,P) 后贴边 ≤2.5px
 *  - fromBeadPhoto:真实网格 ≥96% 逐格还原;检测网格 ≥88%
 *  - 非晶格平滑图 → 检测返回 null;非法参数 → 采样返回 null
 * 失败时 exit 1。
 */
public class TestBeadPhoto {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static final int P = 24;                  // 豆距(px)
    static final int COLS = 18, ROWS = 14;
    static final int W = COLS * P, H = ROWS * P;

    static int[] srcCells;                    // 真值:色板下标

    static List<BeadColor> palette() {
        List<BeadColor> pal = new ArrayList<>();
        pal.add(new BeadColor(1, "红", 0xD93A2B));
        pal.add(new BeadColor(2, "蓝", 0x2266CC));
        pal.add(new BeadColor(3, "黄", 0xF2C230));
        pal.add(new BeadColor(4, "绿", 0x3E9B4F));
        pal.add(new BeadColor(5, "白", 0xF2F2F2));
        pal.add(new BeadColor(6, "黑", 0x222222));
        return pal;
    }

    /** 渲染合成豆照:板底 + 圆豆 + 中心孔 + 从左到右 +6% 光照 + 噪声 */
    static int[] render() {
        List<BeadColor> pal = palette();
        srcCells = new int[COLS * ROWS];
        int[] img = new int[W * H];
        long seed = 12345;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int cx = x / P, cy = y / P;
                int bead = (cx * 7 + cy * 13 + (cx * cy) % 5) % 6;
                srcCells[cy * COLS + cx] = bead;
                double dx = (x % P) - P / 2.0 + 0.5;
                double dy = (y % P) - P / 2.0 + 0.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                int rgb;
                if (dist > 0.47 * P) {
                    rgb = 0xD8D4CC;                       // 板底
                } else if (dist < 0.14 * P) {
                    rgb = scale(pal.get(bead).rgb, 0.45); // 中心孔(透出深色)
                } else {
                    double light = 1.0 + 0.06 * x / W;    // 光照渐变
                    rgb = scale(pal.get(bead).rgb, light);
                }
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int n = (int) ((seed >> 33) % 9) - 4;     // ±4 噪声
                int r = clamp8(((rgb >> 16) & 0xFF) + n);
                int g = clamp8(((rgb >> 8) & 0xFF) + n);
                int b = clamp8((rgb & 0xFF) + n);
                img[y * W + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return img;
    }

    static int scale(int rgb, double f) {
        int r = clamp8((int) Math.round(((rgb >> 16) & 0xFF) * f));
        int g = clamp8((int) Math.round(((rgb >> 8) & 0xFF) * f));
        int b = clamp8((int) Math.round((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    static double accuracy(BeadPattern p) {
        // 检测网格的行列数允许与真值差 ±1(相位/豆距细化误差),取交集比较
        int cols = Math.min(p.cols, COLS), rows = Math.min(p.rows, ROWS);
        int hit = 0, total = 0;
        for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
                int got = p.cellAt(cx, cy);
                if (got < 0) continue;
                total++;
                // cellAt 是色板下标,真值就是色板下标
                if (p.palette.get(got).rgb == palette()
                        .get(srcCells[cy * COLS + cx]).rgb) {
                    hit++;
                }
            }
        }
        return total == 0 ? 0 : hit * 100.0 / total;
    }

    public static void main(String[] args) {
        int[] img = render();

        // ---- 检测:豆距 + 相位 ----
        PatternEngine.BeadGrid g = PatternEngine.detectBeadGrid(img, W, H);
        check("检测: 晶格图返回结果", g != null);
        if (g != null) {
            check("检测: 横向豆距 ±1.5 (" + String.format("%.2f", g.pitchX) + ")",
                    Math.abs(g.pitchX - P) <= 1.5);
            check("检测: 纵向豆距 ±1.5 (" + String.format("%.2f", g.pitchY) + ")",
                    Math.abs(g.pitchY - P) <= 1.5);
            double nx = ((g.lineX % P) + P) % P;
            double ny = ((g.lineY % P) + P) % P;
            nx = Math.min(nx, P - nx);
            ny = Math.min(ny, P - ny);
            check("检测: 横向相位贴边 ≤2.5 (" + String.format("%.2f", nx) + ")",
                    nx <= 2.5);
            check("检测: 纵向相位贴边 ≤2.5 (" + String.format("%.2f", ny) + ")",
                    ny <= 2.5);
        }

        // ---- 采样:真实网格 ----
        List<BeadColor> pal = palette();
        BeadPattern p1 = PatternEngine.fromBeadPhoto(img, W, H,
                0, 0, P, P, pal, true, false, false);
        check("采样: 真实网格出图", p1 != null);
        if (p1 != null) {
            check("采样: 尺寸 = 源格数", p1.cols == COLS && p1.rows == ROWS);
            double acc = accuracy(p1);
            check("采样: 真实网格还原 ≥96% (" + String.format("%.1f%%", acc) + ")",
                    acc >= 96.0);
        }

        // ---- 采样:检测网格(相位可能是第 k 条线) ----
        if (g != null) {
            BeadPattern p2 = PatternEngine.fromBeadPhoto(img, W, H,
                    g.lineX, g.lineY, g.pitchX, g.pitchY, pal, true, false, false);
            check("采样: 检测网格出图", p2 != null);
            if (p2 != null) {
                double acc2 = accuracy(p2);
                check("采样: 检测网格还原 ≥88% (" + String.format("%.1f%%", acc2) + ")",
                        acc2 >= 88.0);
            }
        }

        // ---- 非晶格图:检测不触发(纯噪声,无周期边缘) ----
        int[] noise = new int[300 * 300];
        long sd = 987654321L;
        for (int i = 0; i < noise.length; i++) {
            sd = sd * 6364136223846793005L + 1442695040888963407L;
            int v = (int) ((sd >> 33) & 0xFF);
            noise[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        check("检测: 噪声图返回 null",
                PatternEngine.detectBeadGrid(noise, 300, 300) == null);

        // ---- 非法参数 ----
        check("采样: 豆距过小返回 null",
                PatternEngine.fromBeadPhoto(img, W, H, 0, 0, 2, 2, pal, true,
                        false, false) == null);
        check("采样: 空色板返回 null",
                PatternEngine.fromBeadPhoto(img, W, H, 0, 0, P, P,
                        new ArrayList<BeadColor>(), true, false, false) == null);

        System.out.println("TestBeadPhoto: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
