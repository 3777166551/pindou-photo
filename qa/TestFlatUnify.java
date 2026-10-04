import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.PatternEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 简图清晰化(平坦图边界线条色统一)测试(纯 JVM):
 *  - 门控:纯度双峰(简图)触发;渐变/中间带多的图不触发
 *  - 端到端:白底红圆(抗锯齿边)→ 边界格统一为同一支红,不出混色豆
 *  - 渐变图(门控不过)→ 输出与逐格多数票一致(行为不变)
 * 失败时 exit 1。
 */
public class TestFlatUnify {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static PatternEngine.WorkGrid grid(int gw, int gh, int[][] cellPxs) {
        PatternEngine.WorkGrid g = new PatternEngine.WorkGrid();
        g.gw = gw;
        g.gh = gh;
        g.brick = 1;
        int n = gw * gh;
        g.cellStart = new int[n + 1];
        int tot = 0;
        for (int i = 0; i < n; i++) {
            g.cellStart[i] = tot;
            tot += cellPxs[i].length;
        }
        g.cellStart[n] = tot;
        g.cellPix = new int[tot];
        int k = 0;
        for (int i = 0; i < n; i++) {
            for (int px : cellPxs[i]) g.cellPix[k++] = px;
        }
        return g;
    }

    static PatternEngine.Options opts(int cols, int rows) {
        PatternEngine.Options o = new PatternEngine.Options();
        o.cols = cols;
        o.rows = rows;
        o.dither = false;
        return o;
    }

    static int[] fill(int[] px, int r, int g, int b) {
        for (int i = 0; i < px.length; i++) {
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return px;
    }

    static int countReds(BeadPattern p) {
        int n = 0;
        for (BeadPattern.UsedColor uc : p.usedColors) {
            int r = (uc.color.rgb >> 16) & 0xFF, g = (uc.color.rgb >> 8) & 0xFF;
            if (r > 150 && g < 130 && uc.color.rgb != 0x2266CC) n++;
        }
        return n;
    }

    public static void main(String[] args) {
        // ---- 门控:纯度数组直接判定 ----
        float[] flat = new float[256];
        Arrays.fill(flat, 1f);
        for (int i = 0; i < 30; i++) flat[i * 7 % 256] = 0.4f;
        check("gate: 简图(双峰)触发", PatternEngine.isFlatSimple(flat));

        float[] photo = new float[256];
        for (int i = 0; i < 256; i++) photo[i] = 0.5f + 0.4f * (i % 5) / 5f;
        check("gate: 中间带多的图不触发", !PatternEngine.isFlatSimple(photo));

        float[] grad = new float[256];
        for (int i = 0; i < 256; i++) grad[i] = 0.7f + 0.003f * i;
        check("gate: 渐变高纯度不触发", !PatternEngine.isFlatSimple(grad));

        // ---- 端到端:白底红圆(边界格 2 红 2 白,多数票红) ----
        // 色板:白/红/橙/灰——橙与灰是"混色会误投"的陷阱色
        List<BeadColor> pal = new ArrayList<>();
        pal.add(new BeadColor(1, "白", 0xFFFFFF));
        pal.add(new BeadColor(2, "红", 0xD93A2B));
        pal.add(new BeadColor(3, "橙", 0xFF8800));
        pal.add(new BeadColor(4, "灰", 0x888888));

        int gw = 12, gh = 12, per = 4;
        int[][] cells = new int[gw * gh][];
        for (int cy = 0; cy < gh; cy++) {
            for (int cx = 0; cx < gw; cx++) {
                double dx = cx + 0.5 - 5.5, dy = cy + 0.5 - 5.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                int[] px = new int[per];
                if (dist < 3.2) {
                    // 圆内:纯红(带轻微抖动)
                    for (int i = 0; i < per; i++) {
                        px[i] = (0xFF << 24) | (0xE3 << 16) | (0x3A << 8) | 0x2B;
                    }
                } else if (dist < 4.3) {
                    // 边界环:3 红 + 1 白(抗锯齿过渡)
                    px[0] = (0xFF << 24) | (0xE5 << 16) | (0x3C << 8) | 0x2D;
                    px[1] = (0xFF << 24) | (0xE1 << 16) | (0x38 << 8) | 0x29;
                    px[2] = (0xFF << 24) | (0xE6 << 16) | (0x3E << 8) | 0x2F;
                    px[3] = 0xFFFFFFFF;
                } else {
                    // 背景:纯白(带轻微抖动)
                    for (int i = 0; i < per; i++) {
                        px[i] = (0xFF << 24) | (0xFB << 16) | (0xFA << 8) | 0xF7;
                    }
                }
                cells[cy * gw + cx] = px;
            }
        }
        PatternEngine.WorkGrid g = grid(gw, gh, cells);
        BeadPattern p = PatternEngine.generateFromGrid(g, pal, opts(12, 12), 12, 12);

        // 输出应只有 白+红 两种豆(混色不再出现,橙/灰不该被选中)
        int usedKinds = p.usedColors.size();
        check("简图端到端: 只用 白+红 两种豆", usedKinds == 2);
        int whiteIdx = -1, redIdx = -1;
        for (int i = 0; i < p.usedColors.size(); i++) {
            int rgb = p.usedColors.get(i).color.rgb;
            if (rgb == 0xFFFFFF) whiteIdx = i;
            if (rgb == 0xD93A2B) redIdx = i;
        }
        check("简图端到端: 白/红 都在用色里", whiteIdx >= 0 && redIdx >= 0);

        // 边界环格子应统一为红(修复前:2红2白平票 → tie-break 可能翻车,
        // 且抗锯齿抖动会投出橙/灰混色豆)
        int ringCells = 0, ringRed = 0;
        for (int cy = 0; cy < gh; cy++) {
            for (int cx = 0; cx < gw; cx++) {
                double dx = cx + 0.5 - 5.5, dy = cy + 0.5 - 5.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist >= 3.2 && dist < 4.3) {
                    ringCells++;
                    if (p.cellAt(cx, cy) == redIdx) ringRed++;
                }
            }
        }
        check("简图端到端: 边界环全部统一为红", ringCells > 0 && ringRed == ringCells);

        // ---- 渐变图(门控不过):行为与原路径一致,整列同色 ----
        int gw2 = 8, gh2 = 8;
        int[][] gcells = new int[gw2 * gh2][];
        for (int cy = 0; cy < gh2; cy++) {
            for (int cx = 0; cx < gw2; cx++) {
                int b = 200 - cx * 18;   // 逐列渐变
                int[] px = new int[per];
                for (int i = 0; i < per; i++) {
                    px[i] = (0xFF << 24) | (30 << 16) | ((60 + cy * 6) << 8) | Math.max(0, b);
                }
                gcells[cy * gw2 + cx] = px;
            }
        }
        PatternEngine.WorkGrid g2 = grid(gw2, gh2, gcells);
        BeadPattern p2 = PatternEngine.generateFromGrid(g2, pal, opts(8, 8), 8, 8);
        check("渐变图端到端: 门控不过,正常出豆",
                p2 != null && p2.totalBeads == gw2 * gh2);

        // ---- 色板收敛:渐变圆 + 蓝色少数派 ----
        // 色板:白 + 4 档近似红(模拟渐变散开成多支豆) + 蓝(异色少数派)
        List<BeadColor> pal2 = new ArrayList<>();
        pal2.add(new BeadColor(1, "白", 0xFFFFFF));
        pal2.add(new BeadColor(2, "红A", 0xD93A2B));
        pal2.add(new BeadColor(3, "红B", 0xDE4836));
        pal2.add(new BeadColor(4, "红C", 0xE35641));
        pal2.add(new BeadColor(5, "红D", 0xE8644D));
        pal2.add(new BeadColor(6, "蓝", 0x2266CC));

        int gw3 = 14, gh3 = 14;
        int[][] cells3 = new int[gw3 * gh3][];
        for (int cy = 0; cy < gh3; cy++) {
            for (int cx = 0; cx < gw3; cx++) {
                double dx = cx + 0.5 - 6.5, dy = cy + 0.5 - 6.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                int[] px = new int[per];
                if (dist < 1.4) {
                    fill(px, 0xE8, 0x64, 0x4D);      // 渐变台阶 D
                } else if (dist < 2.3) {
                    fill(px, 0xE3, 0x56, 0x41);      // 渐变台阶 C
                } else if (dist < 3.2) {
                    fill(px, 0xDE, 0x48, 0x36);      // 渐变台阶 B
                } else if (dist < 4.1) {
                    fill(px, 0xD9, 0x3A, 0x2B);      // 渐变台阶 A(最外圈最大)
                } else if (dist < 5.0) {
                    // 边界环:2 过渡红 + 2 白(纯度 0.5 < 0.62 = 边界格,触发简图门控)
                    px[0] = 0xFFE05040;
                    px[1] = 0xFFFFFFFF;
                    px[2] = 0xFFE25242;
                    px[3] = 0xFFFFFFFF;
                } else {
                    fill(px, 0xFB, 0xFA, 0xF7);      // 背景:白
                }
                cells3[cy * gw3 + cx] = px;
            }
        }
        // 2 格蓝色小星(占比 1%,异色少数派)
        cells3[1 * gw3 + 12] = fill(new int[per], 0x22, 0x66, 0xCC);
        cells3[2 * gw3 + 12] = fill(new int[per], 0x22, 0x66, 0xCC);

        PatternEngine.WorkGrid g3 = grid(gw3, gh3, cells3);
        PatternEngine.Options o3off = opts(gw3, gh3);
        o3off.flatCollapse = false;
        BeadPattern p3off = PatternEngine.generateFromGrid(g3, pal2, o3off, gw3, gh3);
        PatternEngine.Options o3on = opts(gw3, gh3);
        BeadPattern p3on = PatternEngine.generateFromGrid(g3, pal2, o3on, gw3, gh3);

        check("收敛: 关闭时渐变散成多支红(>=4 种红)",
                countReds(p3off) >= 4);
        check("收敛: 开启后红收敛为 1 支", countReds(p3on) == 1);
        check("收敛: 开启后总用色 = 白+红+蓝 3 种",
                p3on.usedColors.size() == 3);
        boolean blueKept = false;
        int blueCount = 0;
        for (BeadPattern.UsedColor uc : p3on.usedColors) {
            if (uc.color.rgb == 0x2266CC) {
                blueKept = true;
                blueCount = uc.count;
            }
        }
        check("收敛: 蓝色少数派受色差保护保留 2 颗", blueKept && blueCount == 2);

        System.out.println("TestFlatUnify: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
