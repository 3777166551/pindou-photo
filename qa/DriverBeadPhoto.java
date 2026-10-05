import com.pindou.app.bead.BeadBrandCharts;
import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.PatternEngine;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Manual driver: run a REAL finished-work photo through detectBeadGrid +
 * fromBeadPhoto and render the result. Not part of the qa suite.
 * Usage: java DriverBeadPhoto <photo> <outPngPrefix>
 */
public class DriverBeadPhoto {

    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File(args[0]));
        String prefix = args[1];
        // 与 APP 的 ImageLoader 同规:最长边 2400
        int m = Math.max(img.getWidth(), img.getHeight());
        if (m > 2400) {
            float s = 2400f / m;
            Image sc = img.getScaledInstance(
                    Math.round(img.getWidth() * s),
                    Math.round(img.getHeight() * s),
                    Image.SCALE_AREA_AVERAGING);
            BufferedImage nb = new BufferedImage(sc.getWidth(null),
                    sc.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = nb.createGraphics();
            g.drawImage(sc, 0, 0, null);
            g.dispose();
            img = nb;
        }
        int w = img.getWidth(), h = img.getHeight();
        System.out.println("photo " + w + "x" + h);
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);

        PatternEngine.BeadGrid g = PatternEngine.detectBeadGrid(px, w, h);
        if (g == null) {
            System.out.println("detect: null");
            return;
        }
        System.out.printf("detect: pitchX=%.2f pitchY=%.2f lineX=%.2f lineY=%.2f "
                        + "(约 %d x %d 颗)%n",
                g.pitchX, g.pitchY, g.lineX, g.lineY,
                Math.round(w / g.pitchX), Math.round(h / g.pitchY));

        List<BeadColor> pal = null;
        for (BeadBrandCharts.Chart c : BeadBrandCharts.ALL) {
            if (c.name.contains("2.6")) {
                pal = c.colors;
                break;
            }
        }
        BeadPattern p = PatternEngine.fromBeadPhoto(px, w, h,
                g.lineX, g.lineY, g.pitchX, g.pitchY, pal, true, false, false);
        if (p == null) {
            System.out.println("sample: null");
            return;
        }
        System.out.println("pattern " + p.cols + "x" + p.rows
                + " kinds=" + p.usedColors.size() + " total=" + p.totalBeads);
        for (int i = 0; i < Math.min(8, p.usedColors.size()); i++) {
            BeadPattern.UsedColor uc = p.usedColors.get(i);
            System.out.printf("  %-6s #%06X %5d %.1f%%%n", uc.color.code,
                    uc.color.rgb, uc.count, uc.count * 100.0 / p.totalBeads);
        }
        int scale = 8;
        BufferedImage out = new BufferedImage(p.cols * scale, p.rows * scale,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        java.util.HashMap<Integer, Integer> byIdx = new java.util.HashMap<>();
        for (BeadPattern.UsedColor uc : p.usedColors) {
            byIdx.put(uc.index, 0xFF000000 | uc.color.rgb);
        }
        for (int cy = 0; cy < p.rows; cy++) {
            for (int cx = 0; cx < p.cols; cx++) {
                Integer rgb = byIdx.get(p.cellAt(cx, cy));
                g2.setColor(rgb == null ? Color.LIGHT_GRAY : new Color(rgb));
                g2.fillRect(cx * scale, cy * scale, scale, scale);
            }
        }
        g2.dispose();
        File f = new File(prefix + "_pattern.png");
        ImageIO.write(out, "png", f);
        System.out.println("wrote " + f.getAbsolutePath());
    }
}
