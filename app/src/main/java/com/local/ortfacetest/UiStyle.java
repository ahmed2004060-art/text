package com.local.ortfacetest;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// فرع الشكل: ألوان وبطاقات وأزرار موحدة لكل الشاشة
final class UiStyle {
    static final int BLUE = 0xFF1D4ED8;
    static final int BLUE_DARK = 0xFF1E3A8A;
    static final int RED_BTN = 0xFFB91C1C;
    static final int PAGE_BG = 0xFFF3F4F6;
    static final int SOFT_BG = 0xFFF9FAFB;
    static final int LINE = 0xFFE5E7EB;

    private UiStyle() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable shape(Context c, int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 1), stroke);
        return g;
    }

    // بطاقة قسم: خلفية بيضاء بزوايا دائرية وحد خفيف، تُضاف إلى parent
    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(shape(c, Color.WHITE, 12, LINE));
        box.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 12);
        parent.addView(box, lp);
        return box;
    }

    // عنوان القسم مع شريط ملون تحته
    static void cardTitle(Context c, LinearLayout box, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(18);
        t.setTextColor(BLUE_DARK);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        t.setPadding(0, 0, 0, dp(6));
        box.addView(t);
        View bar = new View(c);
        bar.setBackgroundColor(BLUE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 3));
        lp.bottomMargin = dp(c, 8);
        box.addView(bar, lp);
    }

    // مربع فرعي فاتح داخل البطاقة (لكل نموذج إضافي)
    static LinearLayout subBox(Context c, LinearLayout parent) {
        LinearLayout sub = new LinearLayout(c);
        sub.setOrientation(LinearLayout.VERTICAL);
        sub.setBackground(shape(c, SOFT_BG, 10, LINE));
        sub.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 8);
        parent.addView(sub, lp);
        return sub;
    }

    static void addButton(Context c, LinearLayout parent, Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 46));
        lp.topMargin = dp(c, 6);
        parent.addView(b, lp);
    }

    static void primary(Context c, Button b) {
        paint(c, b, BLUE, 0xFF1E40AF, Color.WHITE, 0);
    }

    static void secondary(Context c, Button b) {
        paint(c, b, 0xFFEFF6FF, 0xFFDBEAFE, BLUE_DARK, 0xFFBFDBFE);
    }

    static void danger(Context c, Button b) {
        paint(c, b, RED_BTN, 0xFF991B1B, Color.WHITE, 0);
    }

    private static void paint(Context c, Button b, int fill, int pressed, int text, int stroke) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, shape(c, 0xFFE5E7EB, 10, 0));
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, pressed, 10, stroke));
        s.addState(new int[]{}, shape(c, fill, 10, stroke));
        b.setBackground(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setStateListAnimator(null);
        b.setTextColor(new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{0xFF9CA3AF, text}));
    }
}
