package com.local.ortfacetest;

import android.content.Context;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

// فرع الشريط العلوي: كل أزرار استيراد المحرك والنماذج في صف أفقي قابل للتمرير، تحت كل زر حالته
final class ImportBar {
    private final Context ctx;
    private final LinearLayout row;

    ImportBar(Context ctx, LinearLayout parent) {
        this.ctx = ctx;
        HorizontalScrollView scroll = new HorizontalScrollView(ctx);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackground(UiStyle.shape(ctx, UiStyle.CARD, 16, UiStyle.LINE));
        scroll.setPadding(UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 10), UiStyle.dp(ctx, 10));
        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiStyle.dp(ctx, 12);
        parent.addView(scroll, lp);
    }

    // اسم فوق، ثم الزر، ثم الحالة تحته بعرض الزر
    void add(String name, Button button, TextView status) {
        BarItem item = new BarItem(ctx, button);

        TextView nm = new TextView(ctx);
        nm.setText(name);
        nm.setTextSize(13);
        nm.setTextColor(UiStyle.INK);
        nm.setTypeface(UiStyle.medium());
        nm.setTextDirection(android.view.View.TEXT_DIRECTION_RTL);
        nm.setGravity(android.view.Gravity.START);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        np.bottomMargin = UiStyle.dp(ctx, 6);
        item.addView(nm, np);

        item.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, UiStyle.dp(ctx, 48)));

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = UiStyle.dp(ctx, 8);
        item.addView(status, sp);

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (row.getChildCount() > 0) ip.setMarginStart(UiStyle.dp(ctx, 8));
        row.addView(item, ip);
    }
}
