package com.triphtml.viewer;

import android.content.Context;

/** Converts density-independent sizes for views that are built in code. */
final class ScreenUnits {
    private ScreenUnits() { }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
