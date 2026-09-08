package com.pindou.app.bead;

/**
 * 万花筒对称绘画(v2.42):画笔落笔时按对称模式补齐姊妹格。
 * 纯函数、无 Android 依赖,qa 里有 TestSymmetry 覆盖。
 * KALEIDO 为方形网格的 D4 八向对称(横竖镜像 + 两条对角线 + 旋转);
 * 非方形网格里越界的姊妹格直接丢弃。
 */
public final class Symmetry {

    public static final int OFF = 0;
    public static final int LR = 1;
    public static final int QUAD = 2;
    public static final int KALEIDO = 3;

    private Symmetry() {
    }

    /**
     * 计算 (x, y) 的所有对称姊妹格(去重、去自身、越界丢弃)。
     * @return 写入 out 的坐标个数(out 至少 14 长:7 姊妹 × 2)
     */
    public static int siblings(int mode, int x, int y, int w, int h, int[] out) {
        if (mode <= OFF || w <= 0 || h <= 0) return 0;
        int mw = w - 1 - x;
        int mh = h - 1 - y;
        int n = 0;
        // QUAD 与 KALEIDO 共有的四象限
        if (mode == QUAD || mode == KALEIDO) {
            n = put(out, n, mw, y, x, y, w, h);
            n = put(out, n, x, mh, x, y, w, h);
            n = put(out, n, mw, mh, x, y, w, h);
        }
        if (mode == KALEIDO) {
            // 对角镜像与 90° 旋转(方格网才有意义,越界自动丢弃)
            n = put(out, n, y, x, x, y, w, h);
            n = put(out, n, mh, mw, x, y, w, h);
            n = put(out, n, mh, x, x, y, w, h);
            n = put(out, n, y, mw, x, y, w, h);
        }
        if (mode == LR) {
            n = put(out, n, mw, y, x, y, w, h);
        }
        return n;
    }

    /** 查界、去自身、去重后写入 */
    private static int put(int[] out, int n, int nx, int ny,
                           int x, int y, int w, int h) {
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) return n;
        if (nx == x && ny == y) return n;
        for (int i = 0; i < n; i++) {
            if (out[i * 2] == nx && out[i * 2 + 1] == ny) return n;
        }
        out[n * 2] = nx;
        out[n * 2 + 1] = ny;
        return n + 1;
    }
}
