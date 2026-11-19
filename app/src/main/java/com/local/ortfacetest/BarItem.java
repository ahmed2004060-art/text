package com.local.ortfacetest;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

// عنصر في الشريط العلوي: عرضه يتبع عرض الزر فقط، فيأخذ الاسم والحالة نفس عرض الزر
final class BarItem extends LinearLayout {
    private final View anchor;

    BarItem(Context c, View anchor) {
        super(c);
        setOrientation(VERTICAL);
        this.anchor = anchor;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        anchor.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int w = anchor.getMeasuredWidth() + getPaddingLeft() + getPaddingRight();
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightMeasureSpec);
    }
}
