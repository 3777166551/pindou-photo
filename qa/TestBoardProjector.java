import com.pindou.app.view.BoardProjector;

/**
 * 假 AR(v2.49)投影数学测试:BoardProjector 全部是纯 Java,桌面 JVM 直跑。
 * 坐标约定见 BoardProjector 头注释;本文件用"人拿手机站着看北边"的场景
 * 构造 device->world 矩阵:世界 X 东 / Y 北 / Z 天。
 * 失败时 exit 1。
 */
public class TestBoardProjector {

    static int passed = 0, failed = 0;
    static final float EPS = 1e-4f;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    /** 手机竖握、视线朝北抬高角 0 的 device->world 矩阵;azimuth 为向右转的角度(度) */
    static float[] uprightPhone(float azimuthDeg) {
        double t = Math.toRadians(azimuthDeg);
        float c = (float) Math.cos(t), s = (float) Math.sin(t);
        // 列0=屏幕右(世界),列1=屏幕上(天),列2=指向用户(=-视线)
        return new float[]{
                c, 0, -s,
                -s, 0, -c,
                0, 1, 0
        };
    }

    public static void main(String[] args) {
        float[] q = new float[4];
        float[] m = new float[9];

        // 1. 单位四元数 -> 单位矩阵
        BoardProjector.rotationFromQuaternion(new float[]{0, 0, 0, 1}, m);
        check("identity quat -> identity matrix",
                near(m[0], 1) && near(m[4], 1) && near(m[8], 1)
                        && near(m[1], 0) && near(m[2], 0) && near(m[3], 0)
                        && near(m[5], 0) && near(m[6], 0) && near(m[7], 0));

        // 2. 绕 Z 转 90° 的四元数 -> 矩阵应把设备 X 转到世界 Y
        float h = (float) Math.sqrt(0.5);
        BoardProjector.rotationFromQuaternion(new float[]{0, 0, h, h}, m);
        check("90 deg yaw quat maps device X -> world Y",
                near(m[0], 0) && near(m[3], 1) && near(m[6], 0));

        // 3. 矩阵 -> 四元数 roundtrip(允许整体取负)
        float[] q0 = {0.2f, -0.3f, 0.1f, 0.9f};
        BoardProjector.normalizeQuaternion(q0);
        BoardProjector.rotationFromQuaternion(q0, m);
        BoardProjector.quaternionFromRotationMatrix(m, q);
        boolean fwd = near(q[0], q0[0]) && near(q[1], q0[1]) && near(q[2], q0[2]) && near(q[3], q0[3]);
        boolean flip = near(q[0], -q0[0]) && near(q[1], -q0[1]) && near(q[2], -q0[2]) && near(q[3], -q0[3]);
        check("matrix->quat roundtrip", fwd || flip);

        // 4. lerpQuaternion 处理 q/-q 双覆盖:不应插出零向量
        float[] out = new float[4];
        BoardProjector.lerpQuaternion(q0, new float[]{-q0[0], -q0[1], -q0[2], -q0[3]}, 0.5f, out);
        float len = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1]
                + out[2] * out[2] + out[3] * out[3]);
        check("lerp handles sign flip", near(len, 1) && near(out[0], q0[0]));

        // 5. 放板:朝北水平看,板应在正前 0.6m、中心比视线低、右向=东、上向=天
        float[] rot = uprightPhone(0);
        float[] pose = new float[9];
        BoardProjector.placeBoard(rot, 0.6f, 0.12f, pose);
        check("board center 0.6m north, 0.072m below eye",
                near(pose[0], 0) && near(pose[1], 0.6f) && near(pose[2], -0.072f));
        check("board right = east", near(pose[3], 1) && near(pose[4], 0) && near(pose[5], 0));
        check("board up = sky", near(pose[6], 0) && near(pose[7], 0) && near(pose[8], 1));
        // 垂直朝天看(退化方向,水平分量为零)不炸
        float[] down = new float[9];
        down[8] = -1;   // 第三列 = (0,0,-1) -> 视线 = -第三列 = 正天顶
        BoardProjector.placeBoard(down, 0.6f, 0.12f, new float[9]);
        check("degenerate straight-down does not blow up", true);

        // 6. 投影:1080x1920 视口、52° 视场,半宽 0.15 半高 0.10
        //    TL 角(世界 (-0.15,0.6,0.028))解析解:sx≈47.9 sy≈868.1
        int vw = 1080, vh = 1920;
        double f = (vh * 0.5) / Math.tan(Math.toRadians(26));
        float expectSx = (float) (540 + f * (-0.15) / 0.6);
        float expectSy = (float) (960 - f * 0.028 / 0.6);
        float[] quad = new float[8];
        boolean ok = BoardProjector.project(rot, pose, 0.15f, 0.10f,
                (float) Math.toRadians(52), vw, vh, quad);
        check("board in front projects", ok);
        check("TL corner matches analytic solution",
                Math.abs(quad[0] - expectSx) < 1f && Math.abs(quad[1] - expectSy) < 1f);
        // 四角关系:左上在左/上,不镜像不翻倒
        check("TL left of TR (no mirroring)", quad[0] < quad[2] && quad[6] < quad[4]);
        check("top edge above bottom edge", quad[1] < quad[5] && quad[3] < quad[7]);
        // 板中心在视线水平面以下 -> 应出现在屏幕中线下方
        float midY = (quad[1] + quad[7]) * 0.5f;
        check("board center below screen center", midY > 960);

        // 7. 手机向右转 30°:板固定在原地(北),应出现在屏幕左侧
        float[] quad2 = new float[8];
        boolean ok2 = BoardProjector.project(uprightPhone(30), pose, 0.15f, 0.10f,
                (float) Math.toRadians(52), vw, vh, quad2);
        check("board visible after 30 deg turn", ok2);
        check("board moves left on screen when turning right", quad2[2] < 540);

        // 8. 转身背对 -> 整块隐藏(近面裁剪)
        check("board hidden when facing away",
                !BoardProjector.project(uprightPhone(180), pose, 0.15f, 0.10f,
                        (float) Math.toRadians(52), vw, vh, new float[8]));

        System.out.println("TestBoardProjector: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static boolean near(float a, float b) {
        return Math.abs(a - b) < EPS;
    }
}
