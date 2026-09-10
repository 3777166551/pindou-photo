package com.pindou.app.view;

/**
 * 假 AR(陀螺仪叠层)的纯数学部分:不 import 任何 Android 类,
 * 桌面 JVM 可直接单测(qa/TestBoardProjector)。
 *
 * 坐标约定:
 *  - 世界系:与旋转矢量传感器一致(实际只要求 Z 轴近似朝天)
 *  - 设备系:+X 屏幕右 +Y 屏幕上 +Z 指向用户;后置相机沿 -Z 方向看
 *  - rotDevToWorld 把设备系向量变到世界系;相机就放在世界原点,
 *    世界系向量左乘其转置即回到设备系(=相机系)
 *
 * 所有矩阵为行主序 float[9],四元数为 (x,y,z,w)。
 */
public final class BoardProjector {

    /** 位姿数组的分段下标:中心 / 右向单位向量 / 上向单位向量 */
    public static final int CENTER = 0;
    public static final int RIGHT = 3;
    public static final int UP = 6;

    /** 近平面:板子任一角转到相机身后就整块隐藏,避免透视炸裂 */
    public static final float NEAR = 0.05f;

    private BoardProjector() {
    }

    /** 四元数 (x,y,z,w) -> 3x3 旋转矩阵(设备系 -> 世界系) */
    public static void rotationFromQuaternion(float[] q, float[] out9) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;
        out9[0] = 1 - 2 * (yy + zz);
        out9[1] = 2 * (xy - wz);
        out9[2] = 2 * (xz + wy);
        out9[3] = 2 * (xy + wz);
        out9[4] = 1 - 2 * (xx + zz);
        out9[5] = 2 * (yz - wx);
        out9[6] = 2 * (xz - wy);
        out9[7] = 2 * (yz + wx);
        out9[8] = 1 - 2 * (xx + yy);
    }

    /** 3x3 旋转矩阵 -> 四元数 (x,y,z,w);用于加速度+磁场兜底路径 */
    public static void quaternionFromRotationMatrix(float[] m9, float[] out4) {
        float tr = m9[0] + m9[4] + m9[8];
        if (tr > 0) {
            float s = (float) Math.sqrt(tr + 1.0) * 2f;
            out4[3] = 0.25f * s;
            out4[0] = (m9[7] - m9[5]) / s;
            out4[1] = (m9[2] - m9[6]) / s;
            out4[2] = (m9[3] - m9[1]) / s;
        } else if (m9[0] > m9[4] && m9[0] > m9[8]) {
            float s = (float) Math.sqrt(1.0 + m9[0] - m9[4] - m9[8]) * 2f;
            out4[3] = (m9[7] - m9[5]) / s;
            out4[0] = 0.25f * s;
            out4[1] = (m9[1] + m9[3]) / s;
            out4[2] = (m9[2] + m9[6]) / s;
        } else if (m9[4] > m9[8]) {
            float s = (float) Math.sqrt(1.0 + m9[4] - m9[0] - m9[8]) * 2f;
            out4[3] = (m9[2] - m9[6]) / s;
            out4[0] = (m9[1] + m9[3]) / s;
            out4[1] = 0.25f * s;
            out4[2] = (m9[5] + m9[7]) / s;
        } else {
            float s = (float) Math.sqrt(1.0 + m9[8] - m9[0] - m9[4]) * 2f;
            out4[3] = (m9[3] - m9[1]) / s;
            out4[0] = (m9[2] + m9[6]) / s;
            out4[1] = (m9[5] + m9[7]) / s;
            out4[2] = 0.25f * s;
        }
        normalizeQuaternion(out4);
    }

    /** 归一化四元数(就地) */
    public static void normalizeQuaternion(float[] q) {
        float len = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (len < 1e-8f) {
            q[0] = 0;
            q[1] = 0;
            q[2] = 0;
            q[3] = 1;
            return;
        }
        q[0] /= len;
        q[1] /= len;
        q[2] /= len;
        q[3] /= len;
    }

    /**
     * 姿态平滑:nlerp 插值 + 归一化。小步长下视觉效果与 slerp 等价且便宜;
     * q 与 -q 表示同一旋转,先翻符号再插,防止插值走"远路"抖动。
     */
    public static void lerpQuaternion(float[] a, float[] b, float t, float[] out) {
        float dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        float s = dot < 0 ? -1f : 1f;
        out[0] = a[0] + t * (s * b[0] - a[0]);
        out[1] = a[1] + t * (s * b[1] - a[1]);
        out[2] = a[2] + t * (s * b[2] - a[2]);
        out[3] = a[3] + t * (s * b[3] - a[3]);
        normalizeQuaternion(out);
    }

    /**
     * 放置拼板:立在相机当前视线正前方 distance 米处(像靠在桌上的相框),
     * 板面朝向相机、板身保持与地面垂直。dropRatio 控制中心比视线低多少
     * (模拟放在桌面上,而不是悬浮在眼前)。
     *
     * @param rotDevToWorld 当前设备->世界旋转,决定视线方向
     * @param out9          输出位姿 [center, right, up]
     */
    public static void placeBoard(float[] rotDevToWorld, float distance,
                                  float dropRatio, float[] out9) {
        // 视线方向 = R * (0,0,-1),即 R 第三列取反
        float fx = -rotDevToWorld[2];
        float fy = -rotDevToWorld[5];
        float fz = -rotDevToWorld[8];
        // 水平分量:几乎垂直看(朝正上/正下)时退化为固定朝北,避免除零
        float hx = fx, hy = fy;
        float hlen = (float) Math.sqrt(hx * hx + hy * hy);
        if (hlen < 1e-3f) {
            hx = 0;
            hy = 1;
            hlen = 1;
        }
        hx /= hlen;
        hy /= hlen;
        // 板面法线指向相机(水平分量),板身垂直地面
        float nx = -hx, ny = -hy, nz = 0f;
        // 上向 = 世界 Z 天
        float ux = 0f, uy = 0f, uz = 1f;
        // 右向 = up × normal(推出面向相机的"右",与相机右向一致)
        float rx = uy * nz - uz * ny;
        float ry = uz * nx - ux * nz;
        float rz = ux * ny - uy * nx;
        float rlen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rlen < 1e-6f) {
            rx = 1;
            ry = 0;
            rz = 0;
        } else {
            rx /= rlen;
            ry /= rlen;
            rz /= rlen;
        }
        out9[CENTER] = hx * distance;
        out9[CENTER + 1] = hy * distance;
        out9[CENTER + 2] = -dropRatio * distance;
        out9[RIGHT] = rx;
        out9[RIGHT + 1] = ry;
        out9[RIGHT + 2] = rz;
        out9[UP] = ux;
        out9[UP + 1] = uy;
        out9[UP + 2] = uz;
    }

    /**
     * 把板子四角投影到屏幕。
     *
     * @param rotDevToWorld 设备->世界旋转矩阵
     * @param pose          placeBoard 输出的位姿
     * @param halfW halfH   板子半宽/半高(米)
     * @param vFovRad       垂直视场角(弧度)
     * @param vw vh         视口尺寸(像素)
     * @param outXy8        输出四角屏幕坐标,顺序 TL,TR,BR,BL(与
     *                      Matrix.setPolyToPoly 的 (0,0)(w,0)(w,h)(0,h) 对应)
     * @return 任一角在相机身后(近面裁剪)返回 false,调用方应整块隐藏
     */
    public static boolean project(float[] rotDevToWorld, float[] pose,
                                  float halfW, float halfH, float vFovRad,
                                  int vw, int vh, float[] outXy8) {
        // 焦距:像素单位,由垂直视场角推出
        float f = (vh * 0.5f) / (float) Math.tan(vFovRad * 0.5f);
        float cx = vw * 0.5f, cy = vh * 0.5f;
        for (int c = 0; c < 4; c++) {
            // 角点 = 中心 + right*(±halfW) + up*(±halfH);行序 TL,TR,BR,BL
            float su = (c == 0 || c == 1) ? halfH : -halfH;
            float sr = (c == 0 || c == 3) ? -halfW : halfW;
            float wx = pose[CENTER] + pose[RIGHT] * sr + pose[UP] * su;
            float wy = pose[CENTER + 1] + pose[RIGHT + 1] * sr + pose[UP + 1] * su;
            float wz = pose[CENTER + 2] + pose[RIGHT + 2] * sr + pose[UP + 2] * su;
            // 世界系 -> 相机(设备)系:左乘 R 的转置(相机在世界原点)
            float ex = rotDevToWorld[0] * wx + rotDevToWorld[3] * wy + rotDevToWorld[6] * wz;
            float ey = rotDevToWorld[1] * wx + rotDevToWorld[4] * wy + rotDevToWorld[7] * wz;
            float ez = rotDevToWorld[2] * wx + rotDevToWorld[5] * wy + rotDevToWorld[8] * wz;
            if (ez > -NEAR) {
                return false;
            }
            float depth = -ez;
            outXy8[c * 2] = cx + f * ex / depth;
            outXy8[c * 2 + 1] = cy - f * ey / depth;
        }
        return true;
    }
}
