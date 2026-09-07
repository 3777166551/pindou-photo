package com.pindou.app.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * v2.39 框选共享绘制(糖果贴纸风)。
 * 选中框的统一视觉语言 = 墨色描边 + 黄油色 L 形角标(圆头笔触),
 * 裁剪框/去水印框共用,保证全 APP 框选手感一致。
 * 颜色与 values/colors.xml 的糖果贴纸风调色板对应,改色两处同步。
 * 每个 View 持有一个实例(Paint 预分配,不在 onDraw 里建对象)。
 */
public class SelectionPainter {

    public static final int INK = 0xFF40354E;
    public static final int BUTTER = 0xFFFFCF56;
    public static final int WHITE = 0xF2FFFFFF;

    private final float density;
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint main = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SelectionPainter() {
        density = 1f;
        init();
    }

    public SelectionPainter(float density) {
        this.density = density;
        init();
    }

    private void init() {
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(INK);
        edge.setStrokeWidth(6.5f * density);
        edge.setStrokeCap(Paint.Cap.ROUND);
        edge.setStrokeJoin(Paint.Join.ROUND);
        main.setStyle(Paint.Style.STROKE);
        main.setColor(BUTTER);
        main.setStrokeWidth(3.5f * density);
        main.setStrokeCap(Paint.Cap.ROUND);
        main.setStrokeJoin(Paint.Join.ROUND);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0x59FFFFFF);
        line.setStrokeWidth(density);
    }

    /**
     * 四角 L 形角标:先画一圈更粗的墨色衬底(在照片上勾出轮廓),
     * 再画黄油色主体;拐点圆头,是当下图片编辑器的标准做法。
     */
    public void drawBrackets(Canvas c, RectF v) {
        float len = 26f * density;
        drawBracketsWith(c, v, len, edge, main);
    }

    /** 三分构图线(拖动时辅助) */
    public void drawThirds(Canvas c, RectF v) {
        c.drawLine(v.left, v.top + v.height() / 3f,
                v.right, v.top + v.height() / 3f, line);
        c.drawLine(v.left, v.top + v.height() * 2f / 3f,
                v.right, v.top + v.height() * 2f / 3f, line);
        c.drawLine(v.left + v.width() / 3f, v.top,
                v.left + v.width() / 3f, v.bottom, line);
        c.drawLine(v.left + v.width() * 2f / 3f, v.top,
                v.left + v.width() * 2f / 3f, v.bottom, line);
    }

    /** 用给定画笔画角标(两个画笔 = 衬底色 + 主色) */
    public static void drawBracketsWith(Canvas c, RectF v, float len,
                                        Paint edge, Paint main) {
        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? v.left : v.right;
            float y = (i & 2) == 0 ? v.top : v.bottom;
            float dx = (i & 1) == 0 ? len : -len;
            float dy = (i & 2) == 0 ? len : -len;
            c.drawLine(x, y + dy, x, y, edge);
            c.drawLine(x, y, x + dx, y, edge);
            c.drawLine(x, y + dy, x, y, main);
            c.drawLine(x, y, x + dx, y, main);
        }
    }
}
