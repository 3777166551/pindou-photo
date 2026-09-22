package com.pindou.app.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.EditText;

import com.pindou.app.R;

/**
 * Material 3 填充式文本框(v2.61):构造即自应用 M3 字段外观
 * (container 底 + 聚焦底部 primary 指示条,见 drawable/m3_field.xml),
 * 文字色取主题语义色(textMain,深浅/壁纸自动)。布局/对话框把
 * new EditText(...) 换成 new M3EditText(...) 即可,零其余改动。
 */
public class M3EditText extends EditText {

    public M3EditText(Context context) {
        super(context);
        init();
    }

    public M3EditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public M3EditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundResource(R.drawable.m3_field);
        float dp = getResources().getDisplayMetrics().density;
        setPadding(Math.round(12 * dp), Math.round(10 * dp),
                Math.round(12 * dp), Math.round(10 * dp));
        // 文字色 = 主题语义色(深色/壁纸自动)
        android.content.res.TypedArray ta = getContext().getTheme()
                .obtainStyledAttributes(new int[]{R.attr.textMain});
        setTextColor(ta.getColor(0, 0xFF1D1B20));
        ta.recycle();
        setHintTextColor(0xFF79747E);
    }
}
