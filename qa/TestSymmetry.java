import com.pindou.app.bead.Symmetry;

/**
 * Symmetry 万花筒对称姊妹格测试(纯 JVM):
 *  - LR:左右镜像;中线格无姊妹
 *  - QUAD:四象限去重(角落格只补 3 个)
 *  - KALEIDO:方格 D4 八向 = 7 个姊妹;非方形网格越界自动丢弃
 * 失败时 exit 1。
 */
public class TestSymmetry {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    /** 在 out 里找坐标;返回出现次数 */
    static int has(int[] out, int n, int x, int y) {
        int hit = 0;
        for (int i = 0; i < n; i++) {
            if (out[i * 2] == x && out[i * 2 + 1] == y) hit++;
        }
        return hit;
    }

    public static void main(String[] args) {
        int[] buf = new int[16];

        // ---- LR:10 宽,中线在 4.5,普通格补 1 个 ----
        int n = Symmetry.siblings(Symmetry.LR, 3, 4, 10, 10, buf);
        check("LR count", n == 1);
        check("LR mirrors x", has(buf, n, 6, 4) == 1);

        // ---- LR:中线格(4,*)与 (5,*) 互为镜像;宽 9 时 (4,*) 无姊妹 ----
        n = Symmetry.siblings(Symmetry.LR, 4, 4, 9, 9, buf);
        check("LR center col no sibling", n == 0);

        // ---- QUAD:10x10,(3,4) -> (6,4) (3,5) (6,5) ----
        n = Symmetry.siblings(Symmetry.QUAD, 3, 4, 10, 10, buf);
        check("QUAD count", n == 3);
        check("QUAD mirrorX", has(buf, n, 6, 4) == 1);
        check("QUAD mirrorY", has(buf, n, 3, 5) == 1);
        check("QUAD rot180", has(buf, n, 6, 5) == 1);
        check("QUAD no dup of self", has(buf, n, 3, 4) == 0);

        // ---- QUAD:角落格 (0,0) -> (9,0) (0,9) (9,9) ----
        n = Symmetry.siblings(Symmetry.QUAD, 0, 0, 10, 10, buf);
        check("QUAD corner count", n == 3);

        // ---- KALEIDO:9x9,(3,2) 应有 7 个不同姊妹,构成 D4 闭包 ----
        n = Symmetry.siblings(Symmetry.KALEIDO, 3, 2, 9, 9, buf);
        check("KALEIDO 7 siblings", n == 7);
        check("KALEIDO quad pair", has(buf, n, 5, 2) == 1 && has(buf, n, 3, 6) == 1
                && has(buf, n, 5, 6) == 1);
        check("KALEIDO diagonal pair", has(buf, n, 2, 3) == 1 && has(buf, n, 6, 5) == 1);
        check("KALEIDO rot pair", has(buf, n, 6, 3) == 1 && has(buf, n, 2, 5) == 1);
        check("KALEIDO no self", has(buf, n, 3, 2) == 0);

        // ---- KALEIDO:6x4 长网格,越界姊妹被丢弃且不重复 ----
        n = Symmetry.siblings(Symmetry.KALEIDO, 1, 1, 6, 4, buf);
        check("KALEIDO rect no OOB", n >= 1 && n <= 7);
        boolean oob = false;
        for (int i = 0; i < n; i++) {
            if (buf[i * 2] < 0 || buf[i * 2] >= 6
                    || buf[i * 2 + 1] < 0 || buf[i * 2 + 1] >= 4) oob = true;
        }
        check("KALEIDO rect bounds", !oob);
        // 去重:同一坐标不能出现两次
        boolean dup = false;
        for (int i = 0; i < n; i++) {
            if (has(buf, n, buf[i * 2], buf[i * 2 + 1]) > 1) dup = true;
        }
        check("KALEIDO rect dedup", !dup);

        // ---- OFF 恒为 0 ----
        check("OFF zero", Symmetry.siblings(Symmetry.OFF, 3, 4, 10, 10, buf) == 0);

        System.out.println("TestSymmetry: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
