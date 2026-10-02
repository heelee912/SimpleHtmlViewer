package com.triphtml.viewer.document

import android.content.Context
import androidx.core.content.edit
import com.triphtml.viewer.domain.DocumentTitle

/** Where the reader was: the place inside the document plus its scroll offset. */
data class ReadingPlace(val url: String?, val scrollY: Int)

/**
 * Remembers the last document and where reading stopped, so the app reopens straight into it.
 * File name and keys are the same as in 1.x, so updating keeps the user's place.
 */
class ReadingMemory(context: Context) {
    private val preferences = context.getSharedPreferences("document", Context.MODE_PRIVATE)

    val documentUri: String? get() = preferences.getString(KEY_URI, null)

    val title: DocumentTitle? get() = DocumentTitle.fromDisplayName(preferences.getString(KEY_TITLE, null))

    val place: ReadingPlace get() = ReadingPlace(preferences.getString(KEY_URL, null), preferences.getInt(KEY_SCROLL, 0))

    /** A newly chosen document starts at its top. */
    fun rememberDocument(uri: String, title: DocumentTitle?) = preferences.edit {
        putString(KEY_URI, uri)
        if (title != null) putString(KEY_TITLE, title.text) else remove(KEY_TITLE)
        remove(KEY_URL)
        putInt(KEY_SCROLL, 0)
    }

    fun rememberPlace(place: ReadingPlace) = preferences.edit {
        putString(KEY_URL, place.url)
        putInt(KEY_SCROLL, place.scrollY)
        remove(LEGACY_KEY_HIDDEN) // 1.x toolbar state. Reading is always the default now.
    }

    private companion object {
        const val KEY_URI = "uri"
        const val KEY_TITLE = "title"
        const val KEY_URL = "url"
        const val KEY_SCROLL = "scroll"
        const val LEGACY_KEY_HIDDEN = "hidden"
    }
}
