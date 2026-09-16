import com.pindou.app.view.Play3DProjector;

/**
 * 3D 把玩投影内核测试(纯 JVM):
 *  - 正俯视(tilt=90°):板面点 y≈0、深度只由高度决定(豆顶盖住豆底)
 *  - 平视(tilt→0):板面点贴地、高度反映在屏幕纵轴
 *  - 深度语义:同高豆按 depthKey 升序画 = 远的先画;近侧(z1>0)更深
 *  - 椭圆系数 = sinT(俯视 1,平视 0)
 *  - 逆投影与正投影互逆(板面点往返一致),平视奇异时返回 null
 *  - tilt 钳制范围
 * 失败时 exit 1。
 */
public class TestPlay3D {

    static int passed = 0, failed = 0;
    static final double EPS = 1e-9;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        double yaw = Math.toRadians(31.0);

        // ---- 正俯视 t=90:板面点 sy≈0(只剩高度差),深度由 h 决定 ----
        double s90 = Math.sin(Math.toRadians(90.0)), c90 = Math.cos(Math.toRadians(90.0));
        double[] top = Play3DProjector.project(3.2, -1.7, 0.34, yaw, s90, c90);
        double[] base = Play3DProjector.project(3.2, -1.7, 0.0, yaw, s90, c90);
        check("top-down: surface points collapse to same sy",
                Math.abs(top[1] - base[1]) < 1e-9);
        check("top-down: sx = rotated x", Math.abs(top[0] - base[0]) < 1e-9);
        check("top-down: top bead nearer than base",
                top[2] > base[2]);

        // ---- 平视 t→小:高度出现在屏幕纵轴上(顶在上,Canvas y 更小) ----
        double sT = Math.sin(Math.toRadians(20.0)), cT = Math.cos(Math.toRadians(20.0));
        double[] top2 = Play3DProjector.project(0, 0, 0.34, yaw, sT, cT);
        double[] base2 = Play3DProjector.project(0, 0, 0.0, yaw, sT, cT);
        check("low tilt: bead top renders above base",
                top2[1] < base2[1]);

        // ---- 深度语义:俯视时近侧(z1>0 侧)豆更大 depth → 更晚画 ----
        double s45 = Math.sin(Math.toRadians(45.0)), c45 = Math.cos(Math.toRadians(45.0));
        double yaw0 = 0;
        double[] far = Play3DProjector.project(0, -5, 0.34, yaw0, s45, c45);
        double[] near = Play3DProjector.project(0, 5, 0.34, yaw0, s45, c45);
        check("depth: near side (+z) has larger depth",
                near[2] > far[2]);
        // depthKey 只依赖 yaw:与 project 的 z1 一致(同高豆排序键)
        double k1 = Play3DProjector.depthKey(2.0, -3.0, yaw);
        double k2 = Play3DProjector.depthKey(-4.0, 1.0, yaw);
        boolean monotonic = true;
        // 大 key 更近 → 应更晚画;直接验证 key 与 project depth 的 z1 分量一致
        double[] p1 = Play3DProjector.project(2.0, -3.0, 0.34, yaw, s45, c45);
        double[] p2 = Play3DProjector.project(-4.0, 1.0, 0.34, yaw, s45, c45);
        if ((k1 - k2) * (p1[2] - p2[2]) < 0) monotonic = false;
        check("depthKey ordering matches project depth", monotonic);

        // ---- 屏幕位置与手算一致:中心豆、无旋转、t=45 ----
        double[] c = Play3DProjector.project(0, 0, 0.34, 0, s45, c45);
        check("center bead position", Math.abs(c[0]) < EPS
                && Math.abs(c[1] - (-0.34 * c45)) < 1e-9);

        // ---- 椭圆系数 = sinT ----
        check("ellipse factor equals sinT",
                Math.abs(Play3DProjector.ellipseFactor(s90) - 1.0) < EPS
                        && Math.abs(Play3DProjector.ellipseFactor(0.5) - 0.5) < EPS);

        // ---- 逆投影互逆(板面点 h=0 往返一致;豆顶点不在板面上,不参与) ----
        double scale = 13.0;
        double[] sp = Play3DProjector.project(2.0, -3.0, 0.0, yaw, s45, c45);
        double[] uv = Play3DProjector.surfaceFromScreen(
                sp[0] * scale, sp[1] * scale, scale, yaw, s45);
        check("surface roundtrip u", Math.abs(uv[0] - 2.0) < 1e-9);
        check("surface roundtrip v", Math.abs(uv[1] - (-3.0)) < 1e-9);

        // ---- 平视奇异:逆投影返回 null ----
        double sFlat = Math.sin(Math.toRadians(8.0));
        check("flat tilt: inverse projection refused",
                Play3DProjector.surfaceFromScreen(10, 10, scale, yaw, sFlat) == null);

        // ---- tilt 钳制 ----
        check("tilt clamp",
                Math.abs(Math.toDegrees(Play3DProjector.clampTiltRad(5.0))
                        - Play3DProjector.MIN_TILT_DEG) < 1e-9
                        && Math.abs(Math.toDegrees(Play3DProjector.clampTiltRad(95.0))
                        - Play3DProjector.MAX_TILT_DEG) < 1e-9
                        && Math.abs(Math.toDegrees(Play3DProjector.clampTiltRad(60.0)) - 60.0) < 1e-9);

        System.out.println("TestPlay3D: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
