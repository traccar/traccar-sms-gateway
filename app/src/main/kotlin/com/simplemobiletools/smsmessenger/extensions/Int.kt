package com.simplemobiletools.smsmessenger.extensions

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Time
import com.simplemobiletools.commons.extensions.getTimeFormat
import java.util.*

fun Int.formatTime(context: Context): String {
    val cal = Calendar.getInstance(Locale.ENGLISH)
    cal.timeInMillis = this * 1000L
    return DateFormat.format("${context.getTimeFormat()}", cal).toString()
}

// if the given date is today, we show only the time. Else we show only the date
fun Int.formatDateOrTime(context: Context): String {
    val cal = Calendar.getInstance(Locale.ENGLISH)
    cal.timeInMillis = this * 1000L

    return if (DateUtils.isToday(this * 1000L)) {
        DateFormat.format(context.getTimeFormat(), cal).toString()
    } else {
        var format = context.config.dateFormat
        if (isThisYear()) {
            format = format.replace("y", "").trim().trim('-').trim('.').trim('/')
        }

        DateFormat.format(format, cal).toString()
    }
}

fun Int.isThisYear(): Boolean {
    val time = Time()
    time.set(this * 1000L)

    val thenYear = time.year
    time.set(System.currentTimeMillis())

    return (thenYear == time.year)
}
