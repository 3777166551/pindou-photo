import com.pindou.app.util.SpringInterpolator;

/**
 * M3 Expressive 弹簧插值器测试(纯 JVM,SpringInterpolator 只实现
 * TimeInterpolator 接口,不触碰 android.jar 桩类):
 *  - 端点:x(0)=0,收敛:x(1)≈1(残差 <1%)
 *  - 有界:全程 [−0.05, 1.35]
 *  - snappy/bouncy 有过冲(>1.02)且振荡衰减(峰后无更高峰)
 *  - smooth(临界阻尼)单调不过冲
 *  - settleMs 档位在合理区间
 * 失败时 exit 1。
 */
public class TestSpringMotion {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    static float[] sample(SpringInterpolator s, int n) {
        float[] out = new float[n + 1];
        for (int i = 0; i <= n; i++) out[i] = s.getInterpolation(i / (float) n);
        return out;
    }

    public static void main(String[] args) {
        SpringInterpolator snappy = SpringInterpolator.snappy();
        SpringInterpolator bouncy = SpringInterpolator.bouncy();
        SpringInterpolator smooth = SpringInterpolator.smooth();

        // 端点
        check("x(0)=0", snappy.getInterpolation(0f) == 0f
                && smooth.getInterpolation(0f) == 0f);
        check("settle residual <1% (snappy)", Math.abs(snappy.getInterpolation(1f) - 1f) < 0.01f);
        check("settle residual <1% (bouncy)", Math.abs(bouncy.getInterpolation(1f) - 1f) < 0.01f);
        check("settle residual <1% (smooth)", Math.abs(smooth.getInterpolation(1f) - 1f) < 0.01f);

        // 有界
        boolean bounded = true;
        for (SpringInterpolator s : new SpringInterpolator[]{snappy, bouncy, smooth}) {
            for (float v : sample(s, 500)) {
                if (v < -0.05f || v > 1.35f) bounded = false;
            }
        }
        check("bounded in [-0.05, 1.35]", bounded);

        // snappy/bouncy:有过冲
        float snMax = 0, bnMax = 0;
        for (float v : sample(snappy, 500)) snMax = Math.max(snMax, v);
        for (float v : sample(bouncy, 500)) bnMax = Math.max(bnMax, v);
        check("snappy overshoots (>1.02)", snMax > 1.02f);
        check("bouncy overshoots (>1.02)", bnMax > 1.02f);
        check("overshoot magnitude sane (<1.2)", snMax < 1.2f && bnMax < 1.2f);

        // 振荡衰减:第一峰之后不再出现更高的峰
        float[] xs = sample(snappy, 1000);
        float firstPeak = 0;
        int firstPeakIdx = -1;
        for (int i = 0; i < xs.length; i++) {
            if (xs[i] > firstPeak) {
                firstPeak = xs[i];
                firstPeakIdx = i;
            }
        }
        float secondPeak = 0;
        for (int i = firstPeakIdx + 1; i < xs.length; i++) {
            secondPeak = Math.max(secondPeak, xs[i]);
        }
        check("oscillation decays (second peak < first)", secondPeak < firstPeak);

        // smooth:单调不过冲
        boolean noOvershoot = true;
        for (float v : sample(smooth, 500)) {
            if (v > 1.0001f) noOvershoot = false;
        }
        check("smooth never overshoots", noOvershoot);

        // settleMs 档位合理
        int sn = SpringInterpolator.settleMs(0.55f, 26f);
        int bn = SpringInterpolator.settleMs(0.6f, 15f);
        check("settleMs snappy 150~600ms", sn >= 150 && sn <= 600);
        check("settleMs bouncy 300~900ms", bn >= 300 && bn <= 900);
        check("snappy faster than bouncy", sn < bn);

        System.out.println("TestSpringMotion: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
