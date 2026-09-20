package com.pindou.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 首次启动引导(v2.60):三页讲清"拍照出图纸 → 跟着拼 → 离线私密"。
 * 纯代码构建跟随主题色;看完或跳过即写 prefs(onboard_done),不再出现。
 */
public class OnboardingActivity extends Activity {

    private static final String PREFS = "pindou_prefs";
    private static final String KEY_ONBOARD_DONE = "onboard_done";

    private static final int[] EMOJIS = {R.string.ob1_emoji, R.string.ob2_emoji, R.string.ob3_emoji};
    private static final int[] TITLES = {R.string.ob1_title, R.string.ob2_title, R.string.ob3_title};
    private static final int[] DESCS = {R.string.ob1_desc, R.string.ob2_desc, R.string.ob3_desc};

    private LinearLayout dots;
    private TextView emoji, title, desc, action;
    private int page = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float den = getResources().getDisplayMetrics().density;
        int pad = Math.round(28 * den);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, Math.round(64 * den), pad, Math.round(32 * den));

        emoji = new TextView(this);
        emoji.setGravity(Gravity.CENTER);
        root.addView(emoji, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(160 * den)));

        title = new TextView(this);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(getResources().getColor(R.color.textMain));
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        desc = new TextView(this);
        desc.setGravity(Gravity.CENTER);
        desc.setTextSize(15);
        desc.setLineSpacing(4 * den, 1f);
        desc.setTextColor(getResources().getColor(R.color.textSub));
        root.addView(desc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotLp.topMargin = Math.round(28 * den);
        dotLp.bottomMargin = Math.round(24 * den);
        root.addView(dots, dotLp);

        TextView skip = new TextView(this);
        skip.setText(getString(R.string.ob_skip));
        skip.setTextSize(14);
        skip.setTextColor(getResources().getColor(R.color.textSub));
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, Math.round(10 * den), 0, Math.round(10 * den));
        skip.setClickable(true);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishOnboarding();
            }
        });
        root.addView(skip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        action = new TextView(this);
        action.setTextSize(15);
        action.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER);
        action.setBackgroundResource(R.drawable.bg_btn_primary);
        action.setTextColor(getResources().getColor(R.color.onPrimary));
        int apad = Math.round(14 * den);
        action.setPadding(apad, apad / 2, apad, apad / 2);
        action.setClickable(true);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page < EMOJIS.length - 1) {
                    page++;
                    render();
                } else {
                    finishOnboarding();
                }
            }
        });
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(48 * den));
        actionLp.topMargin = Math.round(6 * den);
        root.addView(action, actionLp);

        setContentView(root);
        render();
    }

    private void render() {
        emoji.setText(getString(EMOJIS[page]));
        emoji.setTextSize(64);
        title.setText(getString(TITLES[page]));
        desc.setText(getString(DESCS[page]));
        action.setText(page < EMOJIS.length - 1
                ? getString(R.string.ob_next) : getString(R.string.ob_done));

        dots.removeAllViews();
        int dot = Math.round(10 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dot, dot);
        lp.setMargins(dot / 2, 0, dot / 2, 0);
        for (int i = 0; i < EMOJIS.length; i++) {
            View v = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(getResources().getColor(i == page ? R.color.primary : R.color.subsurface));
            v.setBackground(d);
            dots.addView(v, lp);
        }
    }

    private void finishOnboarding() {
        SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putBoolean(KEY_ONBOARD_DONE, true).apply();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** 首次启动时 MainActivity 调用:还没看过引导才拉起 */
    public static boolean shouldShow(Context c) {
        return !c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ONBOARD_DONE, false);
    }
}
