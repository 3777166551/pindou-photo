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
 * Manual driver (not part of the qa suite): run a real photo through the
 * engine twice - flatCollapse off vs on - and print the used-bead histogram
 * for each, plus render both grids as PNGs. Desktop JVM only (ImageIO).
 * Usage: java DriverFlatDemo <image> <cols> <outPngPrefix>
 */
public class DriverFlatDemo {

    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File(args[0]));
        int cols = Integer.parseInt(args[1]);
        int rows = cols;
        String outPrefix = args[2];

        // preset scale: ss x ss source pixels per cell (box-average stand-in)
        int ss = 4;
        Image scaled = img.getScaledInstance(cols * ss, rows * ss,
                Image.SCALE_AREA_AVERAGING);
        BufferedImage sm = new BufferedImage(cols * ss, rows * ss,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D gr = sm.createGraphics();
        gr.drawImage(scaled, 0, 0, null);
        gr.dispose();

        PatternEngine.WorkGrid g = new PatternEngine.WorkGrid();
        g.gw = cols;
        g.gh = rows;
        g.brick = 1;
        g.cellStart = new int[cols * rows + 1];
        g.cellPix = new int[cols * rows * ss * ss];
        int k = 0;
        for (int c = 0; c < cols * rows; c++) {
            g.cellStart[c] = k;
            int cx = c % cols, cy = c / cols;
            for (int y = 0; y < ss; y++) {
                for (int x = 0; x < ss; x++) {
                    g.cellPix[k++] = sm.getRGB(cx * ss + x, cy * ss + y);
                }
            }
        }
        g.cellStart[cols * rows] = k;

        List<BeadColor> pal = null;
        for (BeadBrandCharts.Chart ch : BeadBrandCharts.ALL) {
            if (ch.name.contains("2.6")) {
                pal = ch.colors;
                break;
            }
        }

        for (int mode = 0; mode < 2; mode++) {
            PatternEngine.Options o = new PatternEngine.Options();
            o.cols = cols;
            o.rows = rows;
            o.flatCollapse = mode == 1;
            BeadPattern p = PatternEngine.generateFromGrid(g, pal, o, cols, rows);
            System.out.println((mode == 0 ? "collapse OFF" : "collapse ON ")
                    + " | used kinds = " + p.usedColors.size());
            for (BeadPattern.UsedColor uc : p.usedColors) {
                System.out.printf("  %-6s #%06X  %5d beads%n",
                        uc.color.code, uc.color.rgb, uc.count);
            }
            int scale = 12;
            java.util.HashMap<Integer, Integer> rgbByIdx = new java.util.HashMap<>();
            for (BeadPattern.UsedColor uc : p.usedColors) {
                rgbByIdx.put(uc.index, 0xFF000000 | uc.color.rgb);
            }
            BufferedImage out = new BufferedImage(cols * scale, rows * scale,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            for (int cy = 0; cy < rows; cy++) {
                for (int cx = 0; cx < cols; cx++) {
                    int idx = p.cellAt(cx, cy);
                    Integer rgb = rgbByIdx.get(idx);
                    g2.setColor(rgb == null ? Color.LIGHT_GRAY : new Color(rgb));
                    g2.fillRect(cx * scale, cy * scale, scale, scale);
                }
            }
            g2.dispose();
            File f = new File(outPrefix + (mode == 0 ? "_before" : "_after")
                    + ".png");
            ImageIO.write(out, "png", f);
            System.out.println("  wrote " + f.getAbsolutePath());
        }
    }
}
