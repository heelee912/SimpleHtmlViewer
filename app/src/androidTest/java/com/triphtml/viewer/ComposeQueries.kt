package com.triphtml.viewer

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText

/** Counts instead of asserting, so waits can poll for things that appear or disappear. */
fun ComposeTestRule.onAllNodesWithTagCount(tag: String): Int = onAllNodesWithTag(tag).fetchSemanticsNodes().size

fun ComposeTestRule.onAllNodesWithTextCount(text: String): Int = onAllNodesWithText(text).fetchSemanticsNodes().size
