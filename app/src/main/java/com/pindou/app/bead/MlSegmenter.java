package com.pindou.app.bead;

import android.content.Context;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.util.Collections;

/**
 * U2NetP 小模型主体分割(ONNX Runtime,离线,Apache-2.0 开源模型)。
 * 模型文件 assets/u2netp.onnx,一次加载常驻,单次推理手机上约 100~300ms。
 * 任何一步失败(模型缺失/内存不足/运行时不可用)都返回 null,
 * 调用方(PatternEngine)自动回退到颜色统计算法 v4。
 *
 * 预处理/后处理与 rembg 参考实现一致:
 * 整幅图像拉伸到 320×320(不裁剪,输出掩码与输入逐点对应)
 * -> ImageNet mean/std 归一化 -> 取输出 d0 -> min-max 归一化。
 * 返回 soft 概率图而不是硬阈值掩码,阈值交给调用方
 * (PatternEngine 用容差滑杆控制),并按覆盖率下采样到拼豆网格。
 */
public final class MlSegmenter {

    static final int NET = 320;
    private static volatile boolean initialised;
    private static volatile boolean available;
    private static OrtEnvironment env;
    private static OrtSession session;

    /** APP 启动时调用一次;失败只标记不可用,不影响其他功能 */
    public static void init(Context ctx) {
        if (initialised) return;
        try {
            initBytes(readAsset(ctx, "u2netp.onnx"));
        } catch (Throwable t) {
            initialised = true;
            available = false;
        }
    }

    /** 从字节数组加载模型(桌面测试环境直接读文件用,APP 走 init(Context)) */
    public static synchronized void initBytes(byte[] model) {
        if (initialised) return;
        try {
            env = OrtEnvironment.getEnvironment();
            session = env.createSession(model, new OrtSession.SessionOptions());
            available = session != null;
        } catch (Throwable t) {
            available = false;
        }
        initialised = true;
    }

    public static boolean isAvailable() {
        return available;
    }

    /**
     * 对任意宽高的 RGB 像素做主体分割。
     * 整幅输入拉伸映射到 320×320(不裁剪,任意画幅比例都逐点对应),
     * 返回 NET×NET 的前景概率(0~1,已 min-max 归一化);失败返回 null。
     */
    public static float[] findSubjectProbs(int[] rgb, int w, int h) {
        if (!initialised || !available) return null;
        if (rgb == null || rgb.length != w * h || w < 4 || h < 4) return null;
        try {
            int[] net = PatternEngine.resampleBilinear(rgb, w, h, NET, NET);
            float[][][][] input = normalize(net);
            OnnxTensor tensor = OnnxTensor.createTensor(env, input);
            OrtSession.Result res = session.run(
                    Collections.singletonMap(session.getInputNames().iterator().next(), tensor));
            float[][] plane = ((float[][][][]) res.get(0).getValue())[0][0];
            res.close();
            tensor.close();

            float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
            for (int y = 0; y < NET; y++) {
                for (int x = 0; x < NET; x++) {
                    float v = plane[y][x];
                    if (v < mn) mn = v;
                    if (v > mx) mx = v;
                }
            }
            float range = Math.max(1e-6f, mx - mn);
            float[] probs = new float[NET * NET];
            for (int y = 0; y < NET; y++) {
                for (int x = 0; x < NET; x++) {
                    probs[y * NET + x] = (plane[y][x] - mn) / range;
                }
            }
            return probs;
        } catch (Throwable t) {
            return null;
        }
    }

    private static float[][][][] normalize(int[] net) {
        float[][][][] in = new float[1][3][NET][NET];
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};
        for (int y = 0; y < NET; y++) {
            for (int x = 0; x < NET; x++) {
                int rgb = net[y * NET + x];
                float r = ((rgb >> 16) & 0xFF) / 255f;
                float g = ((rgb >> 8) & 0xFF) / 255f;
                float b = (rgb & 0xFF) / 255f;
                in[0][0][y][x] = (r - mean[0]) / std[0];
                in[0][1][y][x] = (g - mean[1]) / std[1];
                in[0][2][y][x] = (b - mean[2]) / std[2];
            }
        }
        return in;
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

    private MlSegmenter() {
    }
}
