package com.triphtml.viewer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Optional;

/**
 * A temporary panel over the document, opened with Back while reading. It is a separate layer, so the
 * document underneath keeps its size and scroll position. The sheet scrolls when large fonts or a
 * short landscape screen make it taller than the space above the scrim margin.
 */
final class DocumentToolsPanel {
    /** What the panel can ask the reader to do. */
    interface Actions {
        void resumeReading();
        void previousPosition();
        void refresh();
        void openOtherFile();
    }

    final FrameLayout view;
    private final Context context;
    private final ScrollView scroller;
    private final BoundedColumn sheet;
    private final TextView title;
    private final TextView previousPosition;
    private final Button resumeReading;

    DocumentToolsPanel(Context context, Actions actions) {
        this.context = context;
        view = new FrameLayout(context);
        view.setId(R.id.document_tools);

        View scrim = new View(context);
        scrim.setBackgroundColor(context.getColor(R.color.scrim));
        scrim.setOnClickListener(clicked -> actions.resumeReading());
        // "읽기 계속" and Back already close the panel for assistive technology. The scrim stays silent.
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        view.addView(scroller, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        sheet = new BoundedColumn(context);
        sheet.setMaxWidthPx(ScreenUnits.dp(context, 560));
        sheet.setBackgroundResource(R.drawable.sheet_background);
        sheet.setClickable(true); // Taps on empty sheet space must not fall through to the scrim.
        applySafeArea(new Rect()); // Base spacing. Window insets only add to it.
        if (Build.VERSION.SDK_INT >= 28) sheet.setAccessibilityPaneTitle(context.getString(R.string.tools_title));
        scroller.addView(sheet, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        TextView label = text(13, R.color.ink_muted);
        label.setText(R.string.tools_file_label);
        sheet.addView(label, fullWidth(0));

        title = text(20, R.color.ink);
        title.setId(R.id.document_title);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        sheet.addView(title, fullWidth(4));

        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.divider));
        LinearLayout.LayoutParams dividerLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, ScreenUnits.dp(context, 1)));
        dividerLayout.topMargin = ScreenUnits.dp(context, 16);
        dividerLayout.bottomMargin = ScreenUnits.dp(context, 8);
        sheet.addView(divider, dividerLayout);

        previousPosition = addAction(R.id.previous_position, R.drawable.ic_previous_position,
                R.string.previous_position, actions::previousPosition);
        addAction(R.id.refresh, R.drawable.ic_refresh, R.string.refresh, actions::refresh);
        addAction(R.id.open_file, R.drawable.ic_open_file, R.string.open_file, actions::openOtherFile);

        resumeReading = new Button(context);
        resumeReading.setId(R.id.hide_controls);
        resumeReading.setText(R.string.resume_reading);
        resumeReading.setBackgroundResource(R.drawable.button_primary);
        resumeReading.setStateListAnimator(null);
        resumeReading.setTextColor(context.getColor(R.color.on_ink));
        resumeReading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        resumeReading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        resumeReading.setAllCaps(false);
        resumeReading.setMinHeight(ScreenUnits.dp(context, 56));
        resumeReading.setMinimumHeight(ScreenUnits.dp(context, 56));
        resumeReading.setOnClickListener(clicked -> actions.resumeReading());
        sheet.addView(resumeReading, fullWidth(16));

        TextView exitHint = text(13, R.color.ink_muted);
        exitHint.setId(R.id.exit_hint);
        exitHint.setText(R.string.exit_hint);
        exitHint.setGravity(Gravity.CENTER_HORIZONTAL);
        sheet.addView(exitHint, fullWidth(12));

        view.setVisibility(View.GONE);
    }

    private TextView text(float sizeSp, int color) {
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setTextColor(context.getColor(color));
        KoreanLineBreaks.keepWordsWhole(text);
        return text;
    }

    /** A full-width row with an icon and a readable label. Rows grow with the font size. */
    private TextView addAction(int id, int icon, int label, Runnable action) {
        TextView row = text(17, R.color.ink);
        row.setId(id);
        row.setText(label);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        row.setCompoundDrawablePadding(ScreenUnits.dp(context, 20));
        row.setMinHeight(ScreenUnits.dp(context, 56));
        row.setPaddingRelative(ScreenUnits.dp(context, 4), ScreenUnits.dp(context, 8),
                ScreenUnits.dp(context, 4), ScreenUnits.dp(context, 8));
        row.setBackgroundResource(R.drawable.button_quiet);
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(clicked -> action.run());
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
        sheet.addView(row, fullWidth(0));
        return row;
    }

    private LinearLayout.LayoutParams fullWidth(int topMarginDp) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.topMargin = ScreenUnits.dp(context, topMarginDp);
        return layout;
    }

    /** The scrim covers the whole window, cutout band included. Only the sheet keeps clear of insets. */
    void applySafeArea(Rect safe) {
        FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) scroller.getLayoutParams();
        if (layout == null) return; // Called again once the scroller is attached.
        layout.leftMargin = safe.left;
        layout.rightMargin = safe.right;
        layout.topMargin = safe.top + ScreenUnits.dp(context, 48);
        scroller.setLayoutParams(layout);
        int side = ScreenUnits.dp(context, 24);
        sheet.setPadding(side, ScreenUnits.dp(context, 20), side, safe.bottom + ScreenUnits.dp(context, 16));
    }

    // The panel is modal and hides the document from accessibility, so focus must land inside it.
    @SuppressLint("AccessibilityFocus")
    void show(Optional<DocumentTitle> documentTitle, boolean canReturnToPreviousPosition) {
        title.setText(documentTitle.map(DocumentTitle::text).orElse(context.getString(R.string.app_name)));
        previousPosition.setVisibility(canReturnToPreviousPosition ? View.VISIBLE : View.GONE);
        scroller.scrollTo(0, 0);
        view.setVisibility(View.VISIBLE);
        resumeReading.requestFocus();
        title.post(() -> title.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
    }

    void hide() {
        view.setVisibility(View.GONE);
    }

    boolean isShown() {
        return view.getVisibility() == View.VISIBLE;
    }
}
