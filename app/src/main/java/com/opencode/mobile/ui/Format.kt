package com.opencode.mobile.ui

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

fun resetLabel(resetAt: Long, now: Long = System.currentTimeMillis()): String {
    val diff = resetAt - now
    if (diff <= 0) return "Resetting now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        days > 0 -> "Resets in ${days}d ${hours % 24}h"
        hours > 0 -> "Resets in ${hours}h ${minutes % 60}m"
        minutes < 1 -> "Resets in <1m"
        else -> "Resets in ${minutes}m"
    }
}

/** Sections of the chat list, newest first. */
enum class DateBucket(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("Previous 7 days"),
    OLDER("Older"),
}

fun dateBucket(
    timestamp: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): DateBucket {
    val day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0 -> DateBucket.TODAY
        daysAgo == 1L -> DateBucket.YESTERDAY
        daysAgo <= 7 -> DateBucket.WEEK
        else -> DateBucket.OLDER
    }
}
