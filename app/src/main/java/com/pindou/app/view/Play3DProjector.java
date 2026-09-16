package com.pindou.app.view;

/**
 * 3D 把玩(Play3DView)的投影数学内核:平板拼豆的正交投影。
 * 板面在 XZ 平面(豆位 u=列向右,v=行向里),豆顶面高 h(单位=格);
 * yaw 绕竖直轴旋转,tilt 为相机仰角(90°=正俯视,趋近 0°=贴地平视)。
 *
 * 屏幕坐标:以板中心为原点、Canvas 方向(+x 右,+y 下),深度 depth
 * 越大越靠近相机(画家算法:按 depth 升序画,远的先画)。
 * 板面圆投影为椭圆:长轴 r(水平)、短轴 r·sinT(竖直)。
 *
 * 纯 Java 无 android 依赖,qa 可桌面单测(TestPlay3D)。
 */
public final class Play3DProjector {

    private Play3DProjector() {
    }

    /** tilt 角度限制:俯仰太贴地时椭圆退化、逆投影奇异 */
    public static final double MIN_TILT_DEG = 15.0;
    public static final double MAX_TILT_DEG = 88.0;

    /**
     * 正交投影一个点。
     * @return [sx, sy, depth]——sx/sy 为屏幕坐标(板中心原点,Canvas 方向),
     *         depth 越大越靠近相机
     */
    public static double[] project(double u, double v, double h,
                                   double yawRad, double sinT, double cosT) {
        double cy = Math.cos(yawRad), sy = Math.sin(yawRad);
        double x1 = u * cy + v * sy;
        double z1 = -u * sy + v * cy;
        double sx = x1;
        double sY = z1 * sinT - h * cosT;          // Canvas y 向下
        double depth = h * sinT + z1 * cosT;
        return new double[]{sx, sY, depth};
    }

    /**
     * 逆投影:屏幕点 → 板面(y=0)坐标 (u, v)。熨斗定位用。
     * scale 为屏幕像素/格;tilt 太小时返回 null(奇异,几乎平视)。
     */
    public static double[] surfaceFromScreen(double px, double py, double scale,
                                             double yawRad, double sinT) {
        if (sinT < Math.sin(Math.toRadians(MIN_TILT_DEG)) * 0.75) return null;
        double x1 = px / scale;
        double z1 = py / (scale * sinT);
        double cy = Math.cos(yawRad), sy = Math.sin(yawRad);
        double u = x1 * cy - z1 * sy;
        double v = x1 * sy + z1 * cy;
        return new double[]{u, v};
    }

    /** 深度排序键:同一批豆(同高 h)之间按此值升序画即可(与 tilt 无关) */
    public static double depthKey(double u, double v, double yawRad) {
        return -u * Math.sin(yawRad) + v * Math.cos(yawRad);
    }

    /** 板面圆在屏幕上的短轴系数(0..1):椭圆 ry = r · ellipseFactor */
    public static double ellipseFactor(double sinT) {
        return sinT;
    }

    /** 限制 tilt 到 [MIN,MAX] 并返回弧度 */
    public static double clampTiltRad(double tiltDeg) {
        double t = Math.max(MIN_TILT_DEG, Math.min(MAX_TILT_DEG, tiltDeg));
        return Math.toRadians(t);
    }
}
