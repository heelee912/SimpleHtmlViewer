package com.triphtml.viewer;

import android.graphics.text.LineBreakConfig;
import android.os.Build;
import android.widget.TextView;
import java.util.Locale;

/**
 * Korean wraps between syllables by default. Phrase style keeps words whole, but Android applies it
 * by text locale. The strings exist only in Korean, so the locale is fixed even on an English phone.
 */
final class KoreanLineBreaks {
    private KoreanLineBreaks() { }

    static void keepWordsWhole(TextView text) {
        text.setTextLocale(Locale.KOREAN);
        if (Build.VERSION.SDK_INT >= 33) text.setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
    }
}
