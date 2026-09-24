package com.dicereligion.rateprince.ui.common

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dicereligion.rateprince.R

/** "2 days ago" / "just now", or null when [epochMillis] was never set. */
@Composable
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String? = when {
    epochMillis <= 0L -> null
    now - epochMillis < DateUtils.MINUTE_IN_MILLIS -> stringResource(R.string.time_just_now)
    else -> DateUtils.getRelativeTimeSpanString(epochMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
