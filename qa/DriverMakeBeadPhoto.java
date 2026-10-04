import com.pindou.app.bead.BeadColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Manual helper: render a synthetic "finished beadwork photo" (round beads
 * with center holes on a board, warm lighting gradient + noise) so the
 * bead-photo flow can be exercised on the emulator. Not part of qa suite.
 * Usage: java DriverMakeBeadPhoto <outPng>
 */
public class DriverMakeBeadPhoto {

    public static void main(String[] args) throws Exception {
        int cols = 29, rows = 29, P = 40;
        int W = cols * P, H = rows * P;
        List<BeadColor> pal = new ArrayList<>();
        pal.add(new BeadColor(1, "红", 0xD93A2B));
        pal.add(new BeadColor(2, "白", 0xF2EFE8));
        pal.add(new BeadColor(3, "黄", 0xF2C230));

        // 29x29 heart pattern (0=红 1=白底 2=黄高光)
        int[][] g = new int[rows][cols];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                g[y][x] = 1;
                double fx = (x - 14.0) / 10.5;
                double fy = (13.0 - y) / 11.0 + 0.18;
                double v = Math.pow(fx * fx + fy * fy - 0.42, 3)
                        - fy * fy * fx * fx * fx;
                if (v < 0) g[y][x] = 0;
                if (g[y][x] == 0 && x > 8 && x < 13 && y > 8 && y < 12)
                    g[y][x] = 2;
            }
        }

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        long seed = 42;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int cx = x / P, cy = y / P;
                double dx = (x % P) - P / 2.0 + 0.5;
                double dy = (y % P) - P / 2.0 + 0.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                int rgb;
                if (dist > 0.47 * P) {
                    rgb = 0xCFCBC2;                            // 板底
                } else if (dist < 0.13 * P) {
                    rgb = scale(pal.get(g[cy][cx]).rgb, 0.42); // 中心孔
                } else {
                    double light = 1.06 - 0.14 * x / (double) W
                            + 0.05 * y / (double) H;           // 暖光渐变
                    rgb = scale(pal.get(g[cy][cx]).rgb, light);
                }
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int n = (int) ((seed >> 33) % 7) - 3;
                img.setRGB(x, y, clamp(((rgb >> 16) & 0xFF) + n) << 16
                        | clamp(((rgb >> 8) & 0xFF) + n) << 8
                        | clamp((rgb & 0xFF) + n));
            }
        }
        File f = new File(args[0]);
        ImageIO.write(img, "jpg", f);
        System.out.println("wrote " + f.getAbsolutePath() + " " + W + "x" + H);
    }

    static int scale(int rgb, double f) {
        return clamp((int) Math.round(((rgb >> 16) & 0xFF) * f)) << 16
                | clamp((int) Math.round(((rgb >> 8) & 0xFF) * f)) << 8
                | clamp((int) Math.round((rgb & 0xFF) * f));
    }

    static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
