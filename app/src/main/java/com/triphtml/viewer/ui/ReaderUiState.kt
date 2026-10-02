package com.triphtml.viewer.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.triphtml.viewer.R
import com.triphtml.viewer.domain.DocumentTitle

/** Why no document is on screen. Each problem names its headline, its reason and whether choosing again helps. */
enum class FileProblem(@StringRes val headline: Int, @StringRes val reason: Int, val offersFileChoice: Boolean) {
    UNREADABLE(R.string.recovery_title, R.string.file_unavailable, true),
    PAGE_FAILED(R.string.page_failed_title, R.string.page_failed, true),
    PERMISSION_REFUSED(R.string.permission_title, R.string.permission_unavailable, true),
    PICKER_MISSING(R.string.picker_title, R.string.picker_unavailable, false),
}

sealed interface ReaderScreen {
    /** First visit: nothing has been chosen yet. */
    data object Welcome : ReaderScreen

    /** The document fills the screen. */
    data object Reading : ReaderScreen

    data class Problem(val problem: FileProblem, val canRetry: Boolean) : ReaderScreen
}

/** Everything the screens show. The activity changes it; Compose redraws from it. */
@Stable
class ReaderUiState {
    var screen: ReaderScreen by mutableStateOf(ReaderScreen.Welcome)
    var title: DocumentTitle? by mutableStateOf(null)

    /** A newly chosen file is being checked before it replaces anything. */
    var opening: Boolean by mutableStateOf(false)

    /** The page itself is loading. */
    var loading: Boolean by mutableStateOf(false)

    /** The page's own background, used for the band beside a camera cutout. */
    var pageColor: Int? by mutableStateOf(null)

    var toolsOpen: Boolean by mutableStateOf(false)
    var canReturnToPreviousPlace: Boolean by mutableStateOf(false)

    val isReading: Boolean get() = screen == ReaderScreen.Reading
}

/** What the screens can ask for. */
interface ReaderActions {
    fun chooseFile()
    fun refresh()
    fun returnToPreviousPlace()
    fun openTools()
    fun closeTools()
    suspend fun hasPageDialog(): Boolean
    suspend fun dismissPageDialog(): Boolean
}
