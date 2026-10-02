package com.triphtml.viewer;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A short message floating over the bottom of a rendered document. It overlays the page instead of
 * pushing it down, stays until dismissed or until the next document load, and is not timed, so slow
 * readers and screen-reader users are not rushed.
 */
final class ReadingNotice {
    final LinearLayout view;
    private final Context context;
    private final TextView message;

    ReadingNotice(Context context) {
        this.context = context;
        view = new LinearLayout(context);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackgroundResource(R.drawable.notice_background);
        view.setPaddingRelative(ScreenUnits.dp(context, 18), ScreenUnits.dp(context, 6),
                ScreenUnits.dp(context, 6), ScreenUnits.dp(context, 6));

        message = new TextView(context);
        message.setId(R.id.reading_notice);
        message.setTextColor(context.getColor(R.color.on_ink));
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        message.setLineSpacing(0, 1.2f);
        message.setPadding(0, ScreenUnits.dp(context, 8), 0, ScreenUnits.dp(context, 8));
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        KoreanLineBreaks.keepWordsWhole(message);
        view.addView(message, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button dismiss = new Button(context);
        dismiss.setId(R.id.dismiss_notice);
        dismiss.setText(R.string.dismiss_notice);
        dismiss.setAllCaps(false);
        dismiss.setTextColor(context.getColor(R.color.on_ink));
        dismiss.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        dismiss.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        dismiss.setBackgroundResource(ripple.resourceId);
        dismiss.setStateListAnimator(null);
        int touchTarget = ScreenUnits.dp(context, 48);
        dismiss.setMinWidth(touchTarget);
        dismiss.setMinimumWidth(touchTarget);
        dismiss.setMinHeight(touchTarget);
        dismiss.setMinimumHeight(touchTarget);
        dismiss.setOnClickListener(clicked -> hide());
        view.addView(dismiss, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        applySafeArea(new Rect()); // Bottom placement even before the first window insets arrive.
        view.setVisibility(View.GONE);
    }

    void applySafeArea(Rect safe) {
        int gap = ScreenUnits.dp(context, 16);
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        layout.setMargins(safe.left + gap, 0, safe.right + gap, safe.bottom + gap);
        view.setLayoutParams(layout);
    }

    void show(int text) {
        message.setText(text);
        view.setVisibility(View.VISIBLE);
    }

    void hide() {
        view.setVisibility(View.GONE);
    }
}
