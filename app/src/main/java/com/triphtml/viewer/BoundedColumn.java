package com.triphtml.viewer;

import android.content.Context;
import android.widget.LinearLayout;

/** A vertical column that stops growing at a readable width on landscape phones and tablets. */
final class BoundedColumn extends LinearLayout {
    private int maxWidthPx = Integer.MAX_VALUE;

    BoundedColumn(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    void setMaxWidthPx(int maxWidthPx) {
        this.maxWidthPx = maxWidthPx;
        requestLayout();
    }

    /** Fills the offered width up to the maximum, even inside a wrap-content parent such as a centred sheet. */
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            int width = Math.min(MeasureSpec.getSize(widthSpec), maxWidthPx);
            widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
