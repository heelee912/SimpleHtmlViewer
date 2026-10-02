package com.triphtml.viewer;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Optional;

/**
 * The screen shown whenever no document is on screen: the first visit, opening a file and recovering
 * from a file that can no longer be read. Text is left-aligned and the single file action sits at the
 * bottom within thumb reach. The column scrolls with large fonts and short landscape screens.
 */
final class FileChoiceScreen {
    /** Why no document is showing. Each problem names its own headline, reason and recovery. */
    enum Problem {
        UNREADABLE(R.string.recovery_title, R.string.file_unavailable, true),
        PAGE_FAILED(R.string.page_failed_title, R.string.page_failed, true),
        PERMISSION_REFUSED(R.string.permission_title, R.string.permission_unavailable, true),
        PICKER_MISSING(R.string.picker_title, R.string.picker_unavailable, false);

        final int headline;
        final int reason;
        final boolean offersFileChoice;

        Problem(int headline, int reason, boolean offersFileChoice) {
            this.headline = headline;
            this.reason = reason;
            this.offersFileChoice = offersFileChoice;
        }
    }

    final LinearLayout view;
    private final ScrollView scroller;
    private final Context context;
    private final TextView fileName;
    private final TextView headline;
    private final TextView message;
    private final LinearLayout guide;
    private final Button chooseFile;
    private final Button retry;

    FileChoiceScreen(Context context, Runnable onChooseFile, Runnable onRetry) {
        this.context = context;
        view = new LinearLayout(context);
        view.setId(R.id.file_choice);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackgroundColor(context.getColor(R.color.surface));

        // Text scrolls with large fonts. The action footer stays pinned so the file button is never off screen.
        scroller = new ScrollView(context);
        scroller.setVerticalScrollBarEnabled(false);
        view.addView(scroller, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        BoundedColumn column = new BoundedColumn(context);
        column.setMaxWidthPx(ScreenUnits.dp(context, 560));
        column.setPadding(ScreenUnits.dp(context, 28), ScreenUnits.dp(context, 48),
                ScreenUnits.dp(context, 28), ScreenUnits.dp(context, 16));
        scroller.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        BoundedColumn footer = new BoundedColumn(context);
        footer.setMaxWidthPx(ScreenUnits.dp(context, 560));
        footer.setPadding(ScreenUnits.dp(context, 28), ScreenUnits.dp(context, 8),
                ScreenUnits.dp(context, 28), ScreenUnits.dp(context, 20));
        LinearLayout.LayoutParams footerLayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        footerLayout.gravity = Gravity.CENTER_HORIZONTAL;
        view.addView(footer, footerLayout);

        fileName = text(14, R.color.ink_muted, false);
        fileName.setSingleLine(true);
        fileName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        column.addView(fileName, fullWidth(0));

        headline = text(28, R.color.ink, true);
        headline.setLineSpacing(0, 1.15f);
        if (Build.VERSION.SDK_INT >= 28) headline.setAccessibilityHeading(true);
        column.addView(headline, fullWidth(6));

        message = text(16, R.color.ink_muted, false);
        message.setId(R.id.document_status);
        message.setLineSpacing(0, 1.25f);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        column.addView(message, fullWidth(12));

        guide = new LinearLayout(context);
        guide.setOrientation(LinearLayout.VERTICAL);
        addGuideItem(R.string.welcome_tools_label, R.string.welcome_tools);
        addGuideItem(R.string.welcome_resume_label, R.string.welcome_resume);
        column.addView(guide, fullWidth(36));

        chooseFile = new Button(context);
        chooseFile.setId(R.id.choose_document);
        styleButton(chooseFile, R.drawable.button_primary, R.color.on_ink);
        chooseFile.setOnClickListener(clicked -> onChooseFile.run());
        footer.addView(chooseFile, fullWidth(0));

        retry = new Button(context);
        retry.setId(R.id.retry_document);
        retry.setText(R.string.retry);
        styleButton(retry, R.drawable.button_quiet, R.color.ink);
        retry.setOnClickListener(clicked -> onRetry.run());
        footer.addView(retry, fullWidth(4));

        hide();
    }

    private TextView text(float sizeSp, int color, boolean medium) {
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setTextColor(context.getColor(color));
        if (medium) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        KoreanLineBreaks.keepWordsWhole(text);
        return text;
    }

    private void addGuideItem(int label, int body) {
        TextView title = text(13, R.color.accent, true);
        title.setText(label);
        guide.addView(title, fullWidth(guide.getChildCount() == 0 ? 0 : 20));
        TextView detail = text(16, R.color.ink, false);
        detail.setText(body);
        detail.setLineSpacing(0, 1.25f);
        guide.addView(detail, fullWidth(4));
    }

    private void styleButton(Button button, int background, int textColor) {
        button.setBackgroundResource(background);
        button.setStateListAnimator(null);
        button.setTextColor(context.getColor(textColor));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setAllCaps(false);
        int minimum = ScreenUnits.dp(context, 56);
        button.setMinHeight(minimum);
        button.setMinimumHeight(minimum);
    }

    private LinearLayout.LayoutParams fullWidth(int topMarginDp) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layout.topMargin = ScreenUnits.dp(context, topMarginDp);
        return layout;
    }

    void showWelcome() {
        present(Optional.empty(), R.string.welcome_title);
        message.setVisibility(View.GONE);
        guide.setVisibility(View.VISIBLE);
        chooseFile.setText(R.string.choose_document);
        chooseFile.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
    }

    void showLoading(Optional<DocumentTitle> title) {
        present(title, R.string.loading);
        message.setVisibility(View.GONE);
        guide.setVisibility(View.GONE);
        chooseFile.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
    }

    void showProblem(Problem problem, Optional<DocumentTitle> title, boolean canRetry) {
        present(title, problem.headline);
        message.setText(problem.reason);
        message.setVisibility(View.VISIBLE);
        guide.setVisibility(View.GONE);
        chooseFile.setText(R.string.choose_again);
        chooseFile.setVisibility(problem.offersFileChoice ? View.VISIBLE : View.GONE);
        retry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
    }

    private void present(Optional<DocumentTitle> title, int headlineText) {
        fileName.setText(title.map(DocumentTitle::text).orElse(""));
        fileName.setVisibility(title.isPresent() ? View.VISIBLE : View.GONE);
        headline.setText(headlineText);
        scroller.scrollTo(0, 0);
        view.setVisibility(View.VISIBLE);
    }

    void hide() {
        view.setVisibility(View.GONE);
    }

    boolean isShown() {
        return view.getVisibility() == View.VISIBLE;
    }
}
