package com.pindou.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 拼豆知识页:规格、模板、选购、熨烫、看图技巧等 */
public class KnowledgeActivity extends Activity {

    private static final String[][] SECTIONS = {
            {"拼豆是什么?",
                    "拼豆(Perler Beads / Fuse Beads,又叫熨斗豆、拼拼豆豆)是一种直径约 5 毫米、中间带孔的塑料小珠子。"
                            + "把豆子按图纸插在带凸起的模板(拼板)上,摆好图案后垫上熨烫纸,用熨斗加热让豆子互相粘连,"
                            + "冷却后就得到一块像素风的作品。常用来做钥匙扣、杯垫、挂饰、摆件和相框画。"},
            {"豆子规格",
                    "• 标准豆(5mm):最常见,颜色最多、价格便宜。每格约 0.5cm,一块 29×29 模板成品约 15×15cm。\n"
                            + "• 迷你豆(2.6mm):更精细,适合细节多的小图,成品尺寸约为标准豆的一半,拼起来费眼力,建议配镊子。\n"
                            + "注意:两种豆子孔距不同,不能混用在同一块模板上。"},
            {"模板(拼板)常识",
                    "最常用的是方形模板,标准豆方板一般是 29×29 孔。多块模板可以拼起来扩大画面:"
                            + "58×58 需要 4 块,87×87 需要 9 块,116×116 需要 16 块。"
                            + "另有圆形、六边形、爱心等异形板,适合做小挂饰。拼大图时建议先把多块板用边框扣固定好再下豆。"},
            {"颜色与选购",
                    "市面常见套装有 24、44、48、60、90、120 色等。颜色越多照片还原度越高:"
                            + "24 色适合简单卡通和 emoji,48-60 色适合一般图案,90 色以上适合人像和风景。\n"
                            + "品牌方面,进口有 Perler(美)、Hama(丹麦)、Artkal,国产有漫德、伊诺思等,色号体系各不相同。"
                            + "本 APP 的颜色按常见色系整理,仅供参考,建议对照自己手上豆子的实物颜色。\n"
                            + "小提示:黑色、深蓝、深棕和白色、肤色的用量通常最大,建议多备。"},
            {"熨烫定型步骤",
                    "① 作品拼完后,在豆子上垫一层熨烫纸(或烘焙纸),不要让熨斗直接接触豆子。\n"
                            + "② 熨斗调到中温(约 120~150℃),关闭蒸汽。单手轻压、来回匀速移动,每面大约 10~20 秒。\n"
                            + "③ 透过纸观察:豆孔略微变小、表面微微融化即可,烫太久会过度熔化变形。\n"
                            + "④ 完全冷却后再揭纸;冷却时压一本厚书可以让作品更平整。\n"
                            + "⑤ 翻面重复以上步骤,两面都烫过更结实;边缘容易脱落的可以垫纸再补烫一下。"},
            {"看图拼豆技巧",
                    "• 从图纸的一个角开始,一行一行拼,每拼完一行核对一遍,不容易错。\n"
                            + "• 先放数量少的深色轮廓线,再填充大片浅色区域。\n"
                            + "• 把豆子按颜色分装在小分格盒里,对照图纸上的符号取豆。\n"
                            + "• 用镊子夹豆更省力,拼迷你豆基本必须用镊子。\n"
                            + "• 拼错了不要硬拔,用另一颗豆子从模板背面把它顶出去。"},
            {"用本 APP 出图的建议",
                    "• 选图:主体清晰、背景简单的照片效果最好,比如卡通头像、宠物大头照、证件照。"
                            + "背景杂乱的人像建议先把背景去掉,或把尺寸开到 87 以上。\n"
                            + "• 尺寸:29×29 适合 emoji 和 Q 版;58×58 适合头像;87×87 以上适合半身像和风景。\n"
                            + "• 色板:先默认用 90 色预览;如果某些颜色你手头没有,可以换小一点的色板,或拼的时候用相近色替代。\n"
                            + "• 画面调节:照片变像素后颜色会显得\"灰\"一些,适当加饱和度(+10~+30)和对比度(+5~+20)会更接近原图。\n"
                            + "• 抖动:开启后照片的明暗过渡更自然,但图纸会更复杂,新手建议关闭。"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_knowledge);
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        LinearLayout box = findViewById(R.id.container);
        for (String[] s : SECTIONS) {
            box.addView(card(s[0], s[1]));
        }
        com.pindou.app.util.Skin.apply(box);
    }

    private LinearLayout card(String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation(dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFF22B57F);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(t);

        TextView b = new TextView(this);
        b.setText(body);
        b.setTextColor(0xFF4E4A46);
        b.setTextSize(14);
        b.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(8);
        b.setLayoutParams(blp);
        card.addView(b);
        return card;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
