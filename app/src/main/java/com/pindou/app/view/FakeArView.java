package com.pindou.app.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 假 AR 叠层:把拼好的效果图位图当成一块"板子",按陀螺仪姿态
 * 透视贴在相机取景上面。板子锚定在放置时的视线前方,固定在世界里,
 * 转动手机时透视随之变化;纯展示,不做任何触摸交互。
 */
public final class FakeArView extends View {

    /** 板子默认放在视线正前方多远(米) */
    private static final float DISTANCE = 0.6f;
    /** 板子中心比视线低多少(比例):模拟放在桌面上,而不是悬浮在眼前 */
    private static final float DROP_RATIO = 0.12f;
    /** 假设的相机垂直视场角(度):只影响透视夸张程度 */
    private static final float V_FOV_DEG = 52f;
    /** 姿态插值步长:越小越"稳",越大越跟手 */
    private static final float SMOOTH = 0.35f;

    private Bitmap board;
    private float boardWm;
    private float boardHm;

    private final float[] qCur = {0, 0, 0, 1};
    private final float[] qNew = new float[4];
    private final float[] rot = new float[9];
    private final float[] pose = new float[9];
    private final float[] quad = new float[8];
    private final float[] srcQuad = new float[8];
    private final Matrix matrix = new Matrix();
    private boolean hasPose;

    private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** XML 膨胀必须的构造器:缺了它 setContentView 直接 InflateException 崩 */
    public FakeArView(Context context, AttributeSet attrs) {
        this(context);
    }

    public FakeArView(Context context) {
        super(context);
        shadowPaint.setColor(0x38000000);
    }

    /** 传入效果图位图与板的物理尺寸(米);位图由调用方管理生命周期 */
    public void setBoard(Bitmap bmp, float widthMeters, float heightMeters) {
        board = bmp;
        boardWm = widthMeters;
        boardHm = heightMeters;
        invalidate();
    }

    /** 传感器每帧送来旋转四元数 (x,y,z,w) */
    public void onQuaternion(float x, float y, float z, float w) {
        qNew[0] = x;
        qNew[1] = y;
        qNew[2] = z;
        qNew[3] = w;
        BoardProjector.normalizeQuaternion(qNew);
        if (qCur[3] == 1f && qCur[0] == 0f && qCur[1] == 0f && qCur[2] == 0f) {
            // 第一帧直接吸附,避免从单位姿态"飞"过来
            qCur[0] = qNew[0];
            qCur[1] = qNew[1];
            qCur[2] = qNew[2];
            qCur[3] = qNew[3];
        } else {
            BoardProjector.lerpQuaternion(qCur, qNew, SMOOTH, qCur);
        }
        if (!hasPose) {
            BoardProjector.rotationFromQuaternion(qCur, rot);
            BoardProjector.placeBoard(rot, DISTANCE, DROP_RATIO, pose);
            hasPose = true;
        }
        invalidate();
    }

    /** 重新放置:下一次姿态到来时按当前视线重新锚定 */
    public void recenter() {
        hasPose = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board == null || board.isRecycled() || !hasPose) {
            return;
        }
        BoardProjector.rotationFromQuaternion(qCur, rot);
        float halfW = boardWm * 0.5f;
        float halfH = boardHm * 0.5f;
        if (!BoardProjector.project(rot, pose, halfW, halfH,
                (float) Math.toRadians(V_FOV_DEG), getWidth(), getHeight(), quad)) {
            return;   // 转到板子背后去了,整块隐藏
        }
        // 地面阴影:贴着板子底边的椭圆,模糊宽度随投影大小走
        float bx = quad[4] - quad[6];
        float by = quad[5] - quad[7];
        float bottomW = (float) Math.sqrt(bx * bx + by * by);
        if (bottomW > 8f) {
            float cx = (quad[4] + quad[6]) * 0.5f;
            float cy = (quad[5] + quad[7]) * 0.5f + bottomW * 0.03f;
            float rx = bottomW * 0.55f;
            float ry = Math.max(2f, rx * 0.16f);
            shadowPaint.setMaskFilter(new BlurMaskFilter(
                    Math.max(4f, rx * 0.10f), BlurMaskFilter.Blur.NORMAL));
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, shadowPaint);
        }
        // 四角透视:src 顺序 (0,0)(w,0)(w,h)(0,h) 对应 quad 的 TL,TR,BR,BL
        srcQuad[0] = 0;
        srcQuad[1] = 0;
        srcQuad[2] = board.getWidth();
        srcQuad[3] = 0;
        srcQuad[4] = board.getWidth();
        srcQuad[5] = board.getHeight();
        srcQuad[6] = 0;
        srcQuad[7] = board.getHeight();
        matrix.setPolyToPoly(srcQuad, 0, quad, 0, 4);
        canvas.drawBitmap(board, matrix, boardPaint);
    }
}
