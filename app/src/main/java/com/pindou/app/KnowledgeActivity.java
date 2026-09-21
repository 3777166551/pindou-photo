package com.pindou.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 拼豆知识页:规格、模板、选购、熨烫、看图技巧等(内容在 values 系列的 knowledge.xml) */
public class KnowledgeActivity extends Activity {

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
        String[] titles = getResources().getStringArray(R.array.knowledge_titles);
        String[] bodies = getResources().getStringArray(R.array.knowledge_bodies);
        for (int i = 0; i < Math.min(titles.length, bodies.length); i++) {
            box.addView(card(titles[i], bodies[i], i + 1));
        }
        com.pindou.app.util.Skin.apply(box);
    }

    private LinearLayout card(String title, String body, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);

        // 标题行:24dp 圆形编号徽标 + 标题(合同 §7.5,去绿色标题)
        LinearLayout head = new LinearLayout(this);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setOrientation(LinearLayout.HORIZONTAL);

        TextView badge = new TextView(this);
        badge.setText(String.valueOf(index));
        badge.setTextColor(getResources().getColor(R.color.onPrimaryContainer));
        badge.setTextSize(13);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle =
                new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(getResources().getColor(R.color.primaryContainer));
        badge.setBackground(circle);
        head.addView(badge, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(getResources().getColor(R.color.colorPrimary));
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(10);
        t.setLayoutParams(tlp);
        head.addView(t);
        card.addView(head);

        TextView b = new TextView(this);
        b.setText(body);
        b.setTextColor(getResources().getColor(R.color.textPrimary));
        b.setTextSize(15);
        b.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(10);
        b.setLayoutParams(blp);
        card.addView(b);
        return card;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
