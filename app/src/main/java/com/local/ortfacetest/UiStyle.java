package com.local.ortfacetest;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

// فرع الشكل: ألوان وبطاقات وأزرار وحالات موحدة لكل الشاشة (مطابقة لملف التصميم)
final class UiStyle {
    // ------------------------------------------------------------------ الألوان
    static final int PAGE_BG = 0xFFE9EEED;
    static final int CARD = 0xFFFFFFFF;
    static final int SUB_BG = 0xFFF3F6F5;
    static final int LINE = 0xFFD5DEDC;
    static final int INK = 0xFF10222A;
    static final int MUTED = 0xFF5B6E73;
    static final int BRAND = 0xFF0B6B6A;
    static final int BRAND_PRESS = 0xFF085453;
    static final int BRAND_SOFT = 0xFFDDF0EE;
    static final int BRAND_SOFT_PRESS = 0xFFC7E5E2;
    static final int ERR = 0xFFB42318;
    static final int ERR_PRESS = 0xFF8F1B12;
    static final int ERR_BG = 0xFFFBE4E1;
    static final int OK_INK = 0xFF157347;
    static final int OK_BG = 0xFFDFF3E8;
    static final int OFF_BG = 0xFFE2E8E7;
    static final int OFF_INK = 0xFF93A3A6;

    private UiStyle() {
    }

    // ------------------------------------------------------------------ أدوات أساسية

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

    // مستطيل بحد متقطع (مكان معاينة الصورة)
    static GradientDrawable dashed(Context c, int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 1), stroke, dp(c, 4), dp(c, 3));
        return g;
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    // ------------------------------------------------------------------ البطاقة ورأسها

    // بطاقة قسم: خلفية بيضاء بزوايا 16 وحد خفيف، تُضاف إلى parent
    static LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(shape(c, CARD, 16, LINE));
        box.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 12);
        parent.addView(box, lp);
        return box;
    }

    // رأس البطاقة: شارة برقم القسم ثم العنوان
    static void cardHead(Context c, LinearLayout box, int number, String title) {
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = new TextView(c);
        badge.setText(String.valueOf(number));
        badge.setTextSize(14);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(badge.getTypeface(), Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(shape(c, BRAND, 9, 0));
        head.addView(badge, new LinearLayout.LayoutParams(dp(c, 28), dp(c, 28)));

        TextView h2 = new TextView(c);
        h2.setText(title);
        h2.setTextSize(17);
        h2.setTextColor(INK);
        h2.setTypeface(medium());
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.setMarginStart(dp(c, 10));
        head.addView(h2, hp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        box.addView(head, lp);
    }

    // ------------------------------------------------------------------ الأزرار

    // يضيف الزر بعرض نصه وارتفاع 48
    static void addButton(Context c, LinearLayout parent, Button b, float topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(c, 48));
        lp.topMargin = dp(c, topMarginDp);
        parent.addView(b, lp);
    }

    static void primary(Context c, Button b) {
        paint(c, b, BRAND, BRAND_PRESS, Color.WHITE, 0);
    }

    static void secondary(Context c, Button b) {
        paint(c, b, BRAND_SOFT, BRAND_SOFT_PRESS, BRAND, BRAND_SOFT_PRESS);
    }

    static void danger(Context c, Button b) {
        paint(c, b, ERR, ERR_PRESS, Color.WHITE, 0);
    }

    private static void paint(Context c, Button b, int fill, int pressed, int text, int stroke) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, shape(c, OFF_BG, 12, 0));
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, pressed, 12, stroke));
        s.addState(new int[]{}, shape(c, fill, 12, stroke));
        b.setBackground(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(medium());
        b.setSingleLine(true);
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setTextColor(new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{OFF_INK, text}));
    }

    // زر أيقونة التشغيل ▶ بنفس ألوان الزر الأساسي
    static void playButton(Context c, ImageButton b) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, shape(c, OFF_BG, 10, 0));
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, BRAND_PRESS, 10, 0));
        s.addState(new int[]{}, shape(c, BRAND, 10, 0));
        b.setBackground(s);
        b.setStateListAnimator(null);
        b.setPadding(0, 0, 0, 0);
        b.setScaleType(ImageView.ScaleType.CENTER);
        StateListDrawable icon = new StateListDrawable();
        icon.addState(new int[]{-android.R.attr.state_enabled}, new PlayTriangle(dp(c, 18), OFF_INK));
        icon.addState(new int[]{}, new PlayTriangle(dp(c, 18), Color.WHITE));
        b.setImageDrawable(icon);
    }

    // مثلث التشغيل بزوايا مستديرة قليلاً
    private static final class PlayTriangle extends Drawable {
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PlayTriangle(int size, int color) {
            this.size = size;
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect r = getBounds();
            float u = Math.min(r.width(), r.height()) / 24f;
            paint.setPathEffect(new CornerPathEffect(2f * u));
            Path p = new Path();
            p.moveTo(r.left + 8f * u, r.top + 5f * u);
            p.lineTo(r.left + 8f * u, r.top + 19f * u);
            p.lineTo(r.left + 20f * u, r.top + 12f * u);
            p.close();
            canvas.drawPath(p, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------ حبة الحالة

    // تجهيز نص الحالة كحبة: سطر واحد، بلا نقطة
    static void statusPill(Context c, TextView t, float sidePaddingDp) {
        t.setTextSize(12);
        t.setTypeface(medium());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, sidePaddingDp), dp(c, 6), dp(c, sidePaddingDp), dp(c, 6));
        t.setTextColor(MUTED);
        t.setBackground(shape(c, SUB_BG, 14, LINE));
    }

    // يغيّر نص الحالة ولونها: أخضر نجاح، أحمر خطأ، وغير ذلك محايد
    static void status(TextView t, String text, int color) {
        Context c = t.getContext();
        t.setText(text);
        t.setTextColor(color);
        if (color == OK_INK) t.setBackground(shape(c, OK_BG, 14, 0));
        else if (color == ERR) t.setBackground(shape(c, ERR_BG, 14, 0));
        else t.setBackground(shape(c, SUB_BG, 14, LINE));
        t.setVisibility(View.VISIBLE);
    }
}
