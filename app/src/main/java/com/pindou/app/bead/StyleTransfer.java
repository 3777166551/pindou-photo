package com.pindou.app.bead;

import android.content.Context;

import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * AnimeGANv3 吉卜力风风格化(ONNX Runtime,全离线推理)。
 * 模型:AnimeGANv3_large_Ghibli_c1_e299.onnx(assets 内,约 7MB),
 * 作者 Asher Chan,自定义许可证:非商业用途免费使用,商用需联系作者授权;
 * 本 APP 为免费、无广告的非商业开源项目(声明见 THIRD_PARTY.md)。
 *
 * 预处理/后处理与官方 onnx_infer.py 的 v3_preprocess/v3_post_processing 一致:
 * RGB 输入,float32,/127.5 - 1,布局 NHWC,宽高为 16 的倍数;
 * 输出同布局,值域约 [-1,1],反归一化回 0~255。
 */
public final class StyleTransfer {

    private static volatile boolean initialised;
    private static volatile boolean available;
    private static OrtEnvironment env;
    private static OrtSession session;

    /** 懒加载:第一次真正用到才读模型(约 7MB,会常驻内存);返回是否可用 */
    public static synchronized boolean ensureInit(Context ctx) {
        if (initialised) return available;
        try {
            env = OrtEnvironment.getEnvironment();
            byte[] model = readAsset(ctx, "animeganv3_ghibli.onnx");
            session = env.createSession(model, new OrtSession.SessionOptions());
            available = session != null;
        } catch (Throwable t) {
            available = false;
        }
        initialised = true;
        return available;
    }

    public static boolean isAvailable() {
        return initialised && available;
    }

    /**
     * 对整幅像素做风格化(全强度;等同 strength=100)。
     *
     * @return Object[]{int[] 像素, int 宽, int 高}(输出为 16 倍数尺寸,
     *         长边最多 1024);任何一步失败返回 null,调用方保留原图。
     */
    public static Object[] stylize(int[] rgb, int w, int h) {
        return stylize(rgb, w, h, 100);
    }

    /**
     * 对整幅像素做风格化,带两步画质修复(实测明显更耐看):
     * 1. 色系统一(deblue):模型输出常带黄绿偏色,把每通道均值/方差
     *    对齐回原图,保住服装/背景的真实颜色,只留下明暗与笔触;
     * 2. 强度混合:out = 原图*(1-a) + 风格*a。人像在 60~70% 时保五官,
     *    100% 才是完全动漫化(旧行为)。
     *
     * @param strengthPercent 0~100,风格强度
     * @return Object[]{int[] 像素, int 宽, int 高};失败返回 null。
     */
    public static Object[] stylize(int[] rgb, int w, int h, int strengthPercent) {
        if (!initialised || !available || rgb == null || rgb.length != w * h) return null;
        try {
            int lw = w, lh = h;
            if (Math.max(lw, lh) > 1024) {
                float s = 1024f / Math.max(lw, lh);
                lw = Math.max(16, Math.round(lw * s));
                lh = Math.max(16, Math.round(lh * s));
            }
            lw = to16(lw);
            lh = to16(lh);
            int[] net = (lw == w && lh == h)
                    ? rgb : PatternEngine.resampleBilinear(rgb, w, h, lw, lh);

            float[][][][] in = new float[1][lh][lw][3];
            for (int y = 0; y < lh; y++) {
                for (int x = 0; x < lw; x++) {
                    int c = net[y * lw + x];
                    in[0][y][x][0] = ((c >> 16) & 0xFF) / 127.5f - 1f;
                    in[0][y][x][1] = ((c >> 8) & 0xFF) / 127.5f - 1f;
                    in[0][y][x][2] = (c & 0xFF) / 127.5f - 1f;
                }
            }
            OnnxTensor tensor = OnnxTensor.createTensor(env, in);
            ai.onnxruntime.OrtSession.Result res = session.run(
                    Collections.singletonMap(
                            session.getInputNames().iterator().next(), tensor));
            float[][][][] out = (float[][][][]) res.get(0).getValue();
            res.close();
            tensor.close();

            int[] outPx = new int[lw * lh];
            for (int y = 0; y < lh; y++) {
                for (int x = 0; x < lw; x++) {
                    int r = denorm(out[0][y][x][0]);
                    int g = denorm(out[0][y][x][1]);
                    int b = denorm(out[0][y][x][2]);
                    outPx[y * lw + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }

            // -- 1) 色系统一:每通道 mean/std 对齐回原图 --
            float[] om = new float[3], osd = new float[3];
            float[] gm = new float[3], gsd = new float[3];
            stats(net, om, osd);
            stats(outPx, gm, gsd);
            float alpha = Math.max(0f, Math.min(100f, strengthPercent)) / 100f;
            for (int i = 0; i < outPx.length; i++) {
                int o = net[i];
                int g = outPx[i];
                int r = match((g >> 16) & 0xFF, gm[0], gsd[0], om[0], osd[0]);
                int gg = match((g >> 8) & 0xFF, gm[1], gsd[1], om[1], osd[1]);
                int b = match(g & 0xFF, gm[2], gsd[2], om[2], osd[2]);
                // -- 2) 与原图按强度混合(保留五官与结构) --
                r = Math.round(((o >> 16) & 0xFF) * (1 - alpha) + r * alpha);
                gg = Math.round(((o >> 8) & 0xFF) * (1 - alpha) + gg * alpha);
                b = Math.round((o & 0xFF) * (1 - alpha) + b * alpha);
                outPx[i] = 0xFF000000 | (clamp8(r) << 16) | (clamp8(gg) << 8) | clamp8(b);
            }
            return new Object[]{outPx, lw, lh};
        } catch (Throwable t) {
            return null;
        }
    }

    /** 通道均值/方差(0~255 RGB) */
    private static void stats(int[] px, float[] mean, float[] sd) {
        long n = px.length;
        double[] s = new double[3], s2 = new double[3];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            double r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            s[0] += r; s[1] += g; s[2] += b;
            s2[0] += r * r; s2[1] += g * g; s2[2] += b * b;
        }
        for (int c = 0; c < 3; c++) {
            mean[c] = (float) (s[c] / n);
            sd[c] = (float) Math.sqrt(Math.max(0, s2[c] / n - mean[c] * mean[c]) + 1e-6);
        }
    }

    /** 按目标均值/方差重映射单通道 */
    private static int match(int v, float gm, float gsd, float om, float osd) {
        return Math.round((v - gm) / gsd * osd + om);
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int denorm(float v) {
        int i = Math.round((Math.max(-1f, Math.min(1f, v)) + 1f) * 127.5f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    private static int to16(int v) {
        return Math.max(16, v - v % 16);
    }

    private static byte[] readAsset(Context ctx, String name) throws Exception {
        android.content.res.AssetManager am = ctx.getAssets();
        java.io.InputStream is = am.open(name);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    private StyleTransfer() {
    }
}
