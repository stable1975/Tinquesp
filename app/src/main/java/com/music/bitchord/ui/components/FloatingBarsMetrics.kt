package com.music.bitchord.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * How tall the floating bars (mini-player and tab bar) are, measured from the
 * foot of the screen to the top of the highest one. Pages that float a button
 * above the bars read this to sit the same distance from them as the bars sit
 * from each other.
 */
object FloatingBarsMetrics {
    var heightPx by mutableIntStateOf(0)
}
