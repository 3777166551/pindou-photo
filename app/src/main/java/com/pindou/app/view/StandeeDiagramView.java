package com.pindou.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import com.pindou.app.bead.BeadColor;
import com.pindou.app.bead.BeadPattern;
import com.pindou.app.bead.StandeeKit;
import com.pindou.app.export.StandeePdfExporter;

/**
 * 立牌方案装配示意图(摘要弹窗内),绘制逻辑与 PDF 封面页共用一份
 * (StandeePdfExporter.drawAssemblyDiagram),保证"看到的=印出来的"。
 */
public final class StandeeDiagramView extends View {

    private final BeadPattern sprite;
    private final StandeeKit kit;
    private final BeadColor baseColor;

    public StandeeDiagramView(Context context, BeadPattern sprite, StandeeKit kit) {
        super(context);
        this.sprite = sprite;
        this.kit = kit;
        this.baseColor = sprite.palette.get(kit.baseColorIndex);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        StandeePdfExporter.drawAssemblyDiagram(canvas, sprite, kit, baseColor,
                w / 2f, h * 0.10f, w * 0.92f, h * 0.84f);
    }
}
