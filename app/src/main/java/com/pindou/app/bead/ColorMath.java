package com.pindou.app.bead;

/**
 * 颜色计算工具:sRGB <-> CIELAB 转换、感知色距、亮度/对比度/饱和度调节。
 * 用 Lab 空间做最近色匹配,和人眼判断更一致。
 */
public final class ColorMath {

    /** sRGB (0xRRGGBB) 转 CIELAB,返回 [L, a, b] */
    public static double[] rgbToLab(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;

        r = (r > 0.04045) ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
        g = (g > 0.04045) ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
        b = (b > 0.04045) ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;

        double x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047;
        double y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) / 1.00000;
        double z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883;

        double fx = f(x), fy = f(y), fz = f(z);
        return new double[]{
                116.0 * fy - 16.0,
                500.0 * (fx - fy),
                200.0 * (fy - fz)
        };
    }

    private static double f(double t) {
        return (t > 0.008856) ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }

    /** CIELAB 转 sRGB(0xRRGGBB),与 rgbToLab 互为反函数 */
    public static int labToRgb(double l, double a, double b) {
        double fy = (l + 16.0) / 116.0;
        double fx = fy + a / 500.0;
        double fz = fy - b / 200.0;
        double x = finv(fx) * 0.95047;
        double y = finv(fy) * 1.00000;
        double z = finv(fz) * 1.08883;
        double rl = x * 3.2404542 + y * -1.5371385 + z * -0.4985314;
        double gl = x * -0.9692660 + y * 1.8760108 + z * 0.0415560;
        double bl = x * 0.0556434 + y * -0.2040259 + z * 1.0572252;
        int r = gamma(rl);
        int g = gamma(gl);
        int bb = gamma(bl);
        return (r << 16) | (g << 8) | bb;
    }

    private static double finv(double t) {
        double t3 = t * t * t;
        return t3 > 0.008856 ? t3 : (t - 16.0 / 116.0) / 7.787;
    }

    private static int gamma(double v) {
        if (v <= 0) return 0;
        if (v >= 1) return 255;
        double s = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
        int r = (int) Math.round(s * 255.0);
        return r < 0 ? 0 : (r > 255 ? 255 : r);
    }

    /** Lab 欧氏距离平方 */
    public static double dist2(double[] a, double[] b) {
        double dl = a[0] - b[0], da = a[1] - b[1], db = a[2] - b[2];
        return dl * dl + da * da + db * db;
    }

    /**
     * 画面调节。
     *
     * @param argb      原始像素(带 alpha)
     * @param bright    -100..100
     * @param contrast  -100..100
     * @param sat       -100..100
     */
    public static int adjust(int argb, int bright, int contrast, int sat) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return argb;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        if (bright != 0) {
            int d = bright * 128 / 100;
            r += d;
            g += d;
            b += d;
        }
        if (contrast != 0) {
            float k = (100f + contrast) / (100f - contrast);
            r = (int) ((r - 128) * k + 128);
            g = (int) ((g - 128) * k + 128);
            b = (int) ((b - 128) * k + 128);
        }
        if (sat != 0) {
            float s = 1f + sat / 100f;
            float luma = 0.299f * r + 0.587f * g + 0.114f * b;
            r = (int) (luma + (r - luma) * s);
            g = (int) (luma + (g - luma) * s);
            b = (int) (luma + (b - luma) * s);
        }
        return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** 相对亮度 0..255 */
    public static int luminance(int rgb) {
        return (int) (0.299 * ((rgb >> 16) & 0xFF)
                + 0.587 * ((rgb >> 8) & 0xFF)
                + 0.114 * (rgb & 0xFF));
    }

    /** 图上写字用黑色还是白色 */
    public static int textColorOn(int rgb) {
        return luminance(rgb) > 160 ? 0xFF1B1B1B : 0xFFFFFFFF;
    }

    /** 变暗,factor 0..1 */
    public static int darken(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private ColorMath() {
    }
}
