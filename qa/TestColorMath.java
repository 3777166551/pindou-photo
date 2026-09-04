import com.pindou.app.bead.ColorMath;

/**
 * ColorMath 单元测试:
 *  - CIEDE2000 官方参考色对 (Sharma, Wu, Dalal 2005, 34 对)
 *  - 色差对称性 / 同色零距
 *  - sRGB <-> CIELAB 往返精度
 *  - 亮度 / 前景色选择 / 亮度对比度饱和度调节 / 变暗
 * 纯 Java,无 Android 依赖。失败时 exit 1。
 */
public class TestColorMath {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        // ---- CIEDE2000 官方参考色对 ----
        double[][][] pairs = {
            {{50.0000,2.6772,-79.7751},{50.0000,0.0000,-82.7485}},
            {{50.0000,3.1571,-77.2803},{50.0000,0.0000,-82.7485}},
            {{50.0000,2.8361,-74.0200},{50.0000,0.0000,-82.7485}},
            {{50.0000,-1.3802,-84.2814},{50.0000,0.0000,-82.7485}},
            {{50.0000,-1.1848,-84.8006},{50.0000,0.0000,-82.7485}},
            {{50.0000,-0.9009,-85.5211},{50.0000,0.0000,-82.7485}},
            {{50.0000,0.0000,0.0000},{50.0000,-1.0000,2.0000}},
            {{50.0000,-1.0000,2.0000},{50.0000,0.0000,0.0000}},
            {{50.0000,2.4900,-0.0010},{50.0000,-2.4900,0.0009}},
            {{50.0000,2.4900,-0.0010},{50.0000,-2.4900,0.0010}},
            {{50.0000,2.4900,-0.0010},{50.0000,-2.4900,0.0011}},
            {{50.0000,2.4900,-0.0010},{50.0000,-2.4900,0.0012}},
            {{50.0000,-0.0010,2.4900},{50.0000,0.0009,-2.4900}},
            {{50.0000,-0.0010,2.4900},{50.0000,0.0010,-2.4900}},
            {{50.0000,-0.0010,2.4900},{50.0000,0.0011,-2.4900}},
            {{50.0000,2.5000,0.0000},{50.0000,0.0000,-2.5000}},
            {{50.0000,2.5000,0.0000},{73.0000,25.0000,-18.0000}},
            {{50.0000,2.5000,0.0000},{61.0000,-5.0000,29.0000}},
            {{50.0000,2.5000,0.0000},{56.0000,-27.0000,-3.0000}},
            {{50.0000,2.5000,0.0000},{58.0000,24.0000,15.0000}},
            {{50.0000,2.5000,0.0000},{50.0000,3.1736,0.5854}},
            {{50.0000,2.5000,0.0000},{50.0000,3.2972,0.0000}},
            {{50.0000,2.5000,0.0000},{50.0000,1.8634,0.5757}},
            {{50.0000,2.5000,0.0000},{50.0000,3.2592,0.3350}},
            {{60.2574,-34.0099,36.2677},{60.4626,-34.1751,39.4387}},
            {{63.0109,-31.0961,-5.8663},{62.8187,-29.7946,-4.0864}},
            {{61.2901,3.7196,-5.3901},{61.4292,2.2480,-4.9620}},
            {{35.0831,-44.1164,3.7933},{35.0232,-40.0716,1.5901}},
            {{22.7233,20.0904,-46.6940},{23.0331,14.9730,-42.5619}},
            {{36.4612,47.8580,18.3852},{36.2715,50.5065,21.2231}},
            {{90.8027,-2.0831,1.4410},{91.1528,-1.6435,0.0447}},
            {{90.9257,-0.5406,-0.9208},{88.6381,-0.8985,-0.7239}},
            {{6.7747,-0.2908,-2.4247},{5.8714,-0.0985,-2.2286}},
            {{2.0776,0.0795,-1.1350},{0.9033,-0.0636,-0.5514}},
        };
        double[] want = {
            2.0425, 2.8615, 3.4412, 1.0000, 1.0000, 1.0000, 2.3669, 2.3669,
            7.1792, 7.1792, 7.2195, 7.2195, 4.8045, 4.8045, 4.7461, 4.3065,
            27.1492, 22.8977, 31.9030, 19.4535, 1.0000, 1.0000, 1.0000, 1.0000,
            1.2644, 1.2630, 1.8731, 1.8645, 2.0373, 1.4146, 1.4441, 1.5381,
            0.6377, 0.9082,
        };
        int refPass = 0;
        for (int i = 0; i < pairs.length; i++) {
            double got = ColorMath.deltaE2000(pairs[i][0], pairs[i][1]);
            if (Math.abs(got - want[i]) <= 0.0112) refPass++;
            else System.out.println("  ref pair " + (i + 1) + " got=" + got + " want=" + want[i]);
        }
        check("CIEDE2000 Sharma reference pairs 34/34", refPass == pairs.length);

        // ---- 色差基本性质 ----
        double[] red = ColorMath.rgbToLab(0xFF0000);
        double[] red2 = ColorMath.rgbToLab(0xFE0000);
        check("deltaE2000 same color = 0",
                ColorMath.deltaE2000(red, red) == 0.0);
        check("deltaE2000 symmetric",
                Math.abs(ColorMath.deltaE2000(red, red2)
                        - ColorMath.deltaE2000(red2, red)) < 1e-9);
        check("deltaE2000 distinct colors > 0",
                ColorMath.deltaE2000(red, red2) > 0);
        check("dist2 same color = 0", ColorMath.dist2(red, red) == 0.0);
        check("dist2 known value", ColorMath.dist2(
                new double[]{100, 0, 0}, new double[]{90, 0, 0}) == 100.0);

        // ---- sRGB <-> CIELAB 往返 ----
        int[] samples = {0xFF0000, 0x00FF00, 0x0000FF, 0x808080, 0xF0F0F0,
                0x141414, 0xFF8040, 0x123456, 0xFFFFFF, 0x000000};
        boolean rt = true;
        for (int rgb : samples) {
            double[] lab = ColorMath.rgbToLab(0xFF000000 | rgb);
            int back = ColorMath.labToRgb(lab[0], lab[1], lab[2]);
            int dr = Math.abs(((back >> 16) & 0xFF) - ((rgb >> 16) & 0xFF));
            int dg = Math.abs(((back >> 8) & 0xFF) - ((rgb >> 8) & 0xFF));
            int db = Math.abs((back & 0xFF) - (rgb & 0xFF));
            if (dr > 2 || dg > 2 || db > 2) {
                rt = false;
                System.out.println("  roundtrip off for " + Integer.toHexString(rgb)
                        + " -> " + Integer.toHexString(back));
            }
        }
        check("rgbToLab/labToRgb roundtrip (<=2/channel)", rt);
        double[] white = ColorMath.rgbToLab(0xFFFFFF);
        check("Lab(white) L~100 a~0 b~0",
                Math.abs(white[0] - 100) < 0.5
                        && Math.abs(white[1]) < 0.5 && Math.abs(white[2]) < 0.5);
        double[] black = ColorMath.rgbToLab(0x000000);
        check("Lab(black) L~0", Math.abs(black[0]) < 0.5);

        // ---- 亮度 / 前景色 ----
        check("luminance white=255 black=0",
                ColorMath.luminance(0xFFFFFFFF) == 255
                        && ColorMath.luminance(0xFF000000) == 0);
        check("textColorOn bright->dark text",
                ColorMath.textColorOn(0xFFFFFF) == 0xFF1B1B1B);
        check("textColorOn dark->white text",
                ColorMath.textColorOn(0x000000) == 0xFFFFFFFF);

        // ---- 画面调节 ----
        int brightGray = ColorMath.adjust(0xFF808080, 100, 0, 0);
        check("adjust brightness+100 pushes gray near white",
                (brightGray >> 16 & 0xFF) == 255);
        int darkWhite = ColorMath.adjust(0xFFFFFFFF, -100, 0, 0);
        check("adjust brightness-100: white -> 127 mid gray",
                (darkWhite >> 16 & 0xFF) == 127);
        int darkGray = ColorMath.adjust(0xFF808080, -100, 0, 0);
        check("adjust brightness-100: gray 128 -> black",
                (darkGray >> 16 & 0xFF) == 0);
        int gray = ColorMath.adjust(0xFF8040C0, 0, 0, 0);
        check("adjust neutral is identity", gray == 0xFF8040C0);
        int halfWhite = ColorMath.darken(0xFFFFFFFF, 0.5f);
        check("darken white 50% -> 7F7F7F",
                (halfWhite >> 16 & 0xFF) == 0x7F && (halfWhite & 0xFF) == 0x7F);

        System.out.println("TestColorMath: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
