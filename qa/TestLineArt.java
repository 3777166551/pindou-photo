import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.PatternEngine;

import java.util.ArrayList;
import java.util.List;

/**
 * PatternEngine 线稿模式(v2.43)纯数组函数测试(不依赖 Bitmap):
 *  - lineArtCells:全平图全空 / 黑方块白底描线环 / 180° 旋转对称
 *    / 灵敏度单调 / 透明底覆盖率门槛(描边不污染纯透明区)
 *    / inkIndex 透传 / 输出尺寸
 *  - darkestBeadIndex:L* 最深豆 / 空色板 -1
 * 需要 android.jar 在 classpath(PatternEngine 引用 Bitmap)。
 * 失败时 exit 1。
 */
public class TestLineArt {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static int px(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 白底 64×64,黑方块 [x0..x1]×[y0..y1](含端点) */
    static int[] squareOnWhite(int x0, int y0, int x1, int y1) {
        int[] img = new int[64 * 64];
        java.util.Arrays.fill(img, px(255, 255, 255, 255));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                img[y * 64 + x] = px(255, 0, 0, 0);
            }
        }
        return img;
    }

    static int countInk(int[] cells) {
        int n = 0;
        for (int c : cells) if (c >= 0) n++;
        return n;
    }

    public static void main(String[] args) {
        // ---- 全平图(纯白)→ 全部留空 ----
        int[] flat = new int[64 * 64];
        java.util.Arrays.fill(flat, px(255, 255, 255, 255));
        int[] r1 = PatternEngine.lineArtCells(flat, 64, 64, 16, 16, 50, 3);
        boolean allEmpty = r1.length == 256;
        for (int c : r1) if (c != -1) allEmpty = false;
        check("lineArt flat image -> all empty", allEmpty);

        // ---- 黑方块白底:描线环在边界带,内部/外部留空,墨色下标正确 ----
        int[] sq = squareOnWhite(16, 16, 47, 47);
        int[] r2 = PatternEngine.lineArtCells(sq, 64, 64, 16, 16, 50, 3);
        boolean inkOnly = true, interiorEmpty = true, exteriorEmpty = true;
        int inkCount = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = r2[y * 16 + x];
                if (c >= 0) {
                    inkCount++;
                    if (c != 3) inkOnly = false;
                }
                if (x >= 6 && x <= 9 && y >= 6 && y <= 9 && c != -1) interiorEmpty = false;
                if ((x <= 1 || x >= 14 || y <= 1 || y >= 14) && c != -1) exteriorEmpty = false;
            }
        }
        check("lineArt square ink uses inkIndex", inkOnly);
        check("lineArt square interior empty", interiorEmpty);
        check("lineArt deep exterior empty", exteriorEmpty);
        check("lineArt square band size sane", inkCount >= 40 && inkCount <= 96);
        boolean sym = true;
        for (int i = 0; i < 256; i++) {
            if (r2[i] != r2[255 - i]) sym = false;
        }
        check("lineArt square 180-degree symmetric", sym);

        // ---- 灵敏度:同一图上不出现反向;弱边缘只在灵敏度高时被描出 ----
        int hi = countInk(PatternEngine.lineArtCells(sq, 64, 64, 16, 16, 90, 3));
        int mid = countInk(PatternEngine.lineArtCells(sq, 64, 64, 16, 16, 50, 3));
        int lo = countInk(PatternEngine.lineArtCells(sq, 64, 64, 16, 16, 10, 3));
        check("lineArt sensitivity monotonic", hi >= mid && mid >= lo);

        // 强(黑方块)+ 弱(深灰方块)双边缘:低灵敏度只描强边缘
        int[] two = new int[64 * 64];
        java.util.Arrays.fill(two, px(255, 255, 255, 255));
        for (int y = 2; y <= 13; y++) {
            for (int x = 2; x <= 13; x++) {
                two[y * 64 + x] = px(255, 140, 140, 140);
            }
        }
        for (int y = 20; y <= 43; y++) {
            for (int x = 20; x <= 43; x++) {
                two[y * 64 + x] = px(255, 0, 0, 0);
            }
        }
        int densHi = countInk(PatternEngine.lineArtCells(two, 64, 64, 16, 16, 90, 3));
        int densLo = countInk(PatternEngine.lineArtCells(two, 64, 64, 16, 16, 0, 3));
        check("lineArt sensitivity adds weak edges", densHi > densLo && densLo >= 30);

        // ---- 透明底:2px 黑竖线只在其覆盖格描线,纯透明格被覆盖率门槛挡住 ----
        int[] stroke = new int[64 * 64];   // 全透明
        for (int y = 8; y <= 55; y++) {
            stroke[y * 64 + 30] = px(255, 0, 0, 0);
            stroke[y * 64 + 31] = px(255, 0, 0, 0);
        }
        int[] r3 = PatternEngine.lineArtCells(stroke, 64, 64, 16, 16, 50, 0);
        boolean strokeInk = true, bleedGated = true;
        int strokeCount = 0;
        for (int y = 2; y <= 13; y++) {
            if (r3[y * 16 + 7] != 0) strokeInk = false;
            if (r3[y * 16 + 7] >= 0) strokeCount++;
            if (r3[y * 16 + 8] != -1) bleedGated = false;
            if (r3[y * 16 + 6] != -1) bleedGated = false;
        }
        check("lineArt transparent stroke inked on covered cells", strokeInk && strokeCount == 12);
        check("lineArt transparent bleed cells gated", bleedGated);

        // ---- inkIndex = -1(空色板兜底)→ 全空 ----
        int[] r4 = PatternEngine.lineArtCells(sq, 64, 64, 16, 16, 50, -1);
        boolean none = r4.length == 256;
        for (int c : r4) if (c != -1) none = false;
        check("lineArt inkIndex -1 -> all empty", none);

        // ---- 输出尺寸与放大场景(网格比源大)不越界 ----
        int[] tiny = squareOnWhite(1, 1, 2, 2);
        int[] r5 = PatternEngine.lineArtCells(tiny, 64, 64, 8, 8, 50, 2);
        check("lineArt output size matches grid", r5.length == 64);

        // ---- darkestBeadIndex:挑 L* 最深的豆;空色板 -1 ----
        List<BeadColor> palette = new ArrayList<>();
        palette.add(new BeadColor(1, "白", 0xFFFFFF));
        palette.add(new BeadColor(2, "黑", 0x000000));
        palette.add(new BeadColor(3, "灰", 0x808080));
        check("darkestBeadIndex picks darkest", PatternEngine.darkestBeadIndex(palette) == 1);
        check("darkestBeadIndex empty palette -> -1",
                PatternEngine.darkestBeadIndex(new ArrayList<BeadColor>()) == -1);

        System.out.println("TestLineArt: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
