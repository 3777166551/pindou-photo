package com.pindou.app.bead;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 内置图纸模板库:网红爆款 + 按题材六类,共 50+ 个手绘像素图案。
 * '.' 表示该格留空(不放豆)。建议 29×29 单板尺寸打开,做挂件正好。
 * 注意:所有行宽约定 16 格;渲染时会垫成正方形画布再交给编辑器,
 *     因此各模板高矮不一也不会被居中裁剪。
 */
public final class Templates {

    /** 一个模板:name 展示名,rows 像素行,keyChar 与颜色一一对应;
     *  网格模式(grid != null)直接存每格 RGB,0 = 空格,用于开源素材包 */
    public static final class Tpl {
        public final String name;
        public final String[] rows;
        public final char[] keys;
        public final int[] rgbs;
        public final int[][] grid;
        /** 打开时建议的画幅边长 */
        public final int suggestedSize;

        Tpl(String name, int suggestedSize, String[] rows, char[] keys, int[] rgbs) {
            this.name = name;
            this.suggestedSize = suggestedSize;
            this.rows = rows;
            this.keys = keys;
            this.rgbs = rgbs;
            this.grid = null;
        }

        Tpl(String name, int suggestedSize, int[][] grid) {
            this.name = name;
            this.suggestedSize = suggestedSize;
            this.rows = null;
            this.keys = null;
            this.rgbs = null;
            this.grid = grid;
        }

        private int colorOf(char c) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] == c) return 0xFF000000 | rgbs[i];
            }
            return 0;
        }

        /** 取某格颜色(0 = 留空),两种模式统一入口 */
        int colorAt(int x, int y) {
            if (grid != null) {
                return grid[y][x] == 0 ? 0 : 0xFF000000 | grid[y][x];
            }
            return colorOf(rows[y].charAt(x));
        }

        int width() {
            if (grid != null) return grid[0].length;
            int m = 0;
            for (String row : rows) {
                if (row.length() > m) m = row.length();
            }
            return Math.max(1, m);
        }

        int height() {
            return grid != null ? grid.length : rows.length;
        }
    }

    /** 题材分类 */
    public static final class Cat {
        public final String name;
        public final Tpl[] items;

        Cat(String name, Tpl[] items) {
            this.name = name;
            this.items = items;
        }
    }

    private static final char K_R = 'R';
    private static final char K_D = 'D';

    /** 手绘图案大类已下线(v2.26 起模板库改用 Kenney CC0 素材分类) */
    public static final Cat[] CATEGORIES = {
    };

    /** 开源素材包用的网格模式模板工厂 */
    static Tpl gridTpl(String name, int suggestedSize, int[][] grid) {
        return new Tpl(name, suggestedSize, grid);
    }

    /**
     * 把模板渲染成位图(cellPx 是每格像素数)。
     * 渲染后垫成正方形画布居中放置——编辑器会把源图按画幅比例居中裁剪,
     * 正方形输入能保证 29×29 等方形画幅不裁掉任何一格。
     */
    public static Bitmap build(Tpl t, int cellPx) {
        int w = t.width();
        int h = t.height();
        Bitmap art = Bitmap.createBitmap(w * cellPx, h * cellPx,
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(art);
        Paint p = new Paint();
        for (int y = 0; y < h; y++) {
            String row = t.rows == null ? null : t.rows[y];
            int limit = row == null ? w : Math.min(row.length(), w);
            for (int x = 0; x < limit; x++) {
                int color = t.colorAt(x, y);
                if (color == 0) continue;   // '.' 留空
                p.setColor(color);
                c.drawRect(x * cellPx, y * cellPx,
                        (x + 1) * cellPx, (y + 1) * cellPx, p);
            }
        }
        int sidePx = Math.max(w, h) * cellPx;
        if (art.getWidth() == sidePx && art.getHeight() == sidePx) return art;
        Bitmap square = Bitmap.createBitmap(sidePx, sidePx, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(square);
        sc.drawBitmap(art, (sidePx - art.getWidth()) / 2f,
                (sidePx - art.getHeight()) / 2f, null);
        art.recycle();
        return square;
    }

    /** 列表缩略图用的小尺寸版本 */
    public static Bitmap buildThumb(Tpl t) {
        return build(t, 4);
    }

    private Templates() {
    }
}
