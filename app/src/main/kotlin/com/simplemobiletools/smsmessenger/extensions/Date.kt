package com.simplemobiletools.smsmessenger.extensions

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import org.joda.time.DateTime
import java.util.*

fun Date.format(pattern: String): String {
    return DateFormat.format(pattern, this).toString()
}

fun DateTime.humanize(context: Context, now: DateTime = DateTime.now(), pattern: String = "EEE, MMM dd, YYYY, hh:mm a"): String {
    return if (yearOfCentury().get() > now.yearOfCentury().get()) {
        toString(pattern)
    } else {
        DateUtils.getRelativeDateTimeString(context, millis, DateUtils.MINUTE_IN_MILLIS, DateUtils.DAY_IN_MILLIS, 0).toString()
    }
}
