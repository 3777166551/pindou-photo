package com.pindou.app.util;

/**
 * M3 Expressive 弹簧物理插值器(2026-10,v2.63 批次一):
 * 有阻尼简谐运动单位阶跃响应的解析解,零依赖纯数学(qa 桌面可测)。
 * dampingRatio ζ < 1 过冲回弹(Expressive 默认观感),= 1 临界平滑不回弹。
 * stiffness ω0(rad/s):越大越快。输入 0..1 线性映射物理时间,输出 = 位移
 * (0 出发收敛到 1);建议时长 = settleMs(约 4.5 个时间常数后残差 <1%)。
 */
public class SpringInterpolator implements android.animation.TimeInterpolator {

    private final float zeta;
    private final float omega;

    public SpringInterpolator(float dampingRatio, float stiffnessRadPerSec) {
        zeta = Math.max(0.05f, dampingRatio);
        omega = Math.max(1f, stiffnessRadPerSec);
    }

    /** M3 Expressive 档位:小控件回弹(按压松手、chip),约 12% 过冲 */
    public static SpringInterpolator snappy() {
        return new SpringInterpolator(0.55f, 26f);
    }

    /** 面板/FAB 入场:更明显的一次回弹(约 9.5% 过冲,500ms 收敛) */
    public static SpringInterpolator bouncy() {
        return new SpringInterpolator(0.6f, 15f);
    }

    /** 无过冲平滑(临界阻尼),alpha/淡入/按下用 */
    public static SpringInterpolator smooth() {
        return new SpringInterpolator(1f, 18f);
    }

    /** 按物理参数建议的动画时长(ms):残差收敛 <1% 的解析估算 */
    public static int settleMs(float dampingRatio, float stiffnessRadPerSec) {
        float zeta = Math.max(0.05f, dampingRatio);
        float omega = Math.max(1f, stiffnessRadPerSec);
        return Math.round(settleSeconds(zeta, omega) * 1000f);
    }

    /** 残差 <1% 的收敛时间(秒):欠阻尼按包络×过冲系数上界,临界按 e^-x(1+x) 反解 */
    private static float settleSeconds(float zeta, float omega) {
        if (zeta < 1f) {
            // 包络上界:e^(-ζωt)·(1/√(1-ζ²)) < 0.01 → t = ln(100/√(1-ζ²))/(ζω)
            double c = Math.log(100.0 / Math.sqrt(1.0 - (double) zeta * zeta));
            return (float) (c / ((double) zeta * omega));
        }
        // 临界阻尼残差 = e^-x(1+x) = 0.01 → x ≈ 6.64
        return 6.64f / omega;
    }

    private float settleSeconds() {
        return settleSeconds(zeta, omega);
    }

    @Override
    public float getInterpolation(float input) {
        if (input <= 0f) return 0f;
        if (input > 1f) input = 1f;
        double t = input * settleSeconds();
        if (zeta < 1f) {
            double wd = omega * Math.sqrt(1.0 - (double) zeta * zeta);
            double env = Math.exp(-(double) zeta * omega * t);
            return (float) (1.0 - env * (Math.cos(wd * t)
                    + ((double) zeta * omega / wd) * Math.sin(wd * t)));
        }
        // 临界阻尼:x(t) = 1 - e^(-ωt)(1 + ωt)
        double e = Math.exp(-omega * t);
        return (float) (1.0 - e * (1.0 + omega * t));
    }
}
