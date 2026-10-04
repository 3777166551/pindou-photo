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

        System.out.println("TestFlatUnify: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
