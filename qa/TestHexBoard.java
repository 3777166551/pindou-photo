import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.util.PatternShare;

import java.util.ArrayList;
import java.util.List;

/**
 * 六边形板纯数学测试(不依赖 Bitmap / org.json):
 *  - isOutsideHex:中心/顶点柱在内、四角在外、镜面对称、行宽对称
 *  - 29 格板的中带宽度 25、顶行 1 颗(尖顶特征)
 *  - hexChordV/H 与蒙版逐格一致(渲染裁剪不会画出板外的格子)
 *  - hexVertices:6 顶点、起点为上顶点、到中心距离 = R
 *  - PatternShare.assemble 带 hex 标志的图纸 outsideShape 生效
 * 失败时 exit 1。
 */
public class TestHexBoard {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static boolean mask(int n, int x, int y) {
        return !BeadPattern.isOutsideHex(n, n, x, y);
    }

    /** 某行板内格子数 */
    static int rowWidth(int n, int y) {
        int c = 0;
        for (int x = 0; x < n; x++) {
            if (mask(n, x, y)) c++;
        }
        return c;
    }

    public static void main(String[] args) {
        int n = 29;

        // ---- 圆形板回归(顺带守住不回归) ----
        check("round: corner outside, center inside",
                BeadPattern.isOutsideRound(n, n, 0, 0)
                        && !BeadPattern.isOutsideRound(n, n, 14, 14));

        // ---- 六边形蒙版:关键点位 ----
        check("hex: center inside", mask(n, 14, 14));
        check("hex: top/bottom vertex columns inside",
                mask(n, 14, 0) && mask(n, 14, 28));
        check("hex: four corners outside",
                !mask(n, 0, 0) && !mask(n, 28, 0)
                        && !mask(n, 0, 28) && !mask(n, 28, 28));
        check("hex: left/right edge midpoints inside",
                mask(n, 2, 14) && mask(n, 26, 14));

        // ---- 形状特征:尖顶 => 顶行 1 颗,中带最宽 = floor(R*sqrt(3)) = 25 ----
        check("hex: top row single bead", rowWidth(n, 0) == 1);
        check("hex: middle band width 25 for 29", rowWidth(n, 14) == 25);
        check("hex: width grows to middle then holds",
                rowWidth(n, 1) > rowWidth(n, 0) && rowWidth(n, 7) == 25);

        // ---- 镜面对称(水平/垂直镜像不丢格) ----
        boolean sym = true;
        for (int y = 0; y < n && sym; y++) {
            for (int x = 0; x < n && sym; x++) {
                if (mask(n, x, y) != mask(n, n - 1 - x, y)) sym = false;
                if (mask(n, x, y) != mask(n, x, n - 1 - y)) sym = false;
            }
        }
        check("hex: mirror symmetric (H/V)", sym);

        // ---- 行宽上下对称 ----
        boolean rowSym = true;
        for (int y = 0; y < n; y++) {
            if (rowWidth(n, y) != rowWidth(n, n - 1 - y)) rowSym = false;
        }
        check("hex: row widths symmetric top/bottom", rowSym);

        // ---- 偶数尺寸也成立(58) ----
        check("hex: 58 center inside, corner outside",
                mask(58, 28, 28) && mask(58, 29, 28)
                        && !mask(58, 0, 0) && !mask(58, 57, 57));

        // ---- 弦段与蒙版逐格一致(竖线取格心 x+0.5,与蒙版判定严格等价) ----
        float[] span = new float[2];
        boolean vOk = true;
        for (int x = 0; x < n && vOk; x++) {
            boolean has = BeadPattern.hexChordV(n, n, x + 0.5f, span);
            if (has != colAny(n, x)) vOk = false;
            if (!has) continue;
            for (int y = 0; y < n; y++) {
                float cy = y + 0.5f;
                boolean inSpan = cy > span[0] - 1e-3f && cy < span[1] + 1e-3f;
                if (inSpan != mask(n, x, y)) vOk = false;
            }
        }
        check("hex: chordV matches mask cell-by-cell", vOk);

        // ---- 弦段与蒙版逐格一致(横线取格心 y+0.5) ----
        boolean hOk = true;
        for (int y = 0; y < n && hOk; y++) {
            boolean has = BeadPattern.hexChordH(n, n, y + 0.5f, span);
            if (has != (rowWidth(n, y) > 0)) hOk = false;
            if (!has) continue;
            for (int x = 0; x < n; x++) {
                float cx = x + 0.5f;
                boolean inSpan = cx > span[0] - 1e-3f && cx < span[1] + 1e-3f;
                if (inSpan != mask(n, x, y)) hOk = false;
            }
        }
        check("hex: chordH matches mask cell-by-cell", hOk);

        // ---- 中带横弦 = 全宽 R*sqrt(3) ----
        BeadPattern.hexChordH(n, n, n / 2f, span);
        float width = span[1] - span[0];
        float expect = (float) (n / 2.0 * Math.sqrt(3.0));
        check("hex: middle chord is full width", Math.abs(width - expect) < 1e-3f);

        // ---- 顶点:6 个、起点上顶点、到中心距离 R ----
        float[] v = new float[12];
        BeadPattern.hexVertices(n, n, v);
        boolean vtx = Math.abs(v[0] - n / 2f) < 1e-4f && Math.abs(v[1]) < 1e-4f;
        for (int i = 0; i < 6 && vtx; i++) {
            float dx = v[i * 2] - n / 2f;
            float dy = v[i * 2 + 1] - n / 2f;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (Math.abs(d - n / 2f) > 1e-3f) vtx = false;
        }
        check("hex: vertices on circle, first is top", vtx);

        // ---- assemble 带 hex 标志:outsideShape 生效 ----
        List<BeadColor> pal = new ArrayList<>();
        pal.add(new BeadColor(1, "白", 0xFFFFFF));
        pal.add(new BeadColor(2, "黑", 0x000000));
        int[] cells = new int[16];
        java.util.Arrays.fill(cells, -1);
        cells[0] = 0;               // 角上摆一颗(板外)
        cells[2 * 4 + 2] = 1;       // 板内摆一颗
        BeadPattern hp = PatternShare.assemble(4, 4, pal, cells, false, true);
        check("hex pattern: flag set + outside corner detected",
                hp.hex && !hp.round && hp.outsideShape(0, 0) && !hp.outsideShape(2, 2));
        BeadPattern rp = PatternShare.assemble(4, 4, pal, cells, false);
        check("rect pattern: no outside cells", !rp.outsideShape(0, 0));

        System.out.println("TestHexBoard: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static boolean colAny(int n, int x) {
        for (int y = 0; y < n; y++) {
            if (mask(n, x, y)) return true;
        }
        return false;
    }
}
