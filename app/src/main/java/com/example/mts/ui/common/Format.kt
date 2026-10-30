package com.example.mts.ui.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun labelFormat(pattern: String) =
    SimpleDateFormat(pattern, Locale.forLanguageTag("id")).apply { timeZone = TimeZone.getTimeZone("UTC") }

fun parseIso(date: String): Date? = runCatching { synchronized(isoFormat) { isoFormat.parse(date) } }.getOrNull()

fun formatDateLabel(iso: String): String = parseIso(iso)?.let { labelFormat("d MMM yyyy").format(it) } ?: iso

fun formatDayLabel(iso: String): String = parseIso(iso)?.let { labelFormat("EEEE, d MMM").format(it) } ?: iso

fun millisToIso(millis: Long): String = synchronized(isoFormat) { isoFormat.format(Date(millis)) }

fun isoToMillis(iso: String): Long? = parseIso(iso)?.time

fun addDays(iso: String, days: Int): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    calendar.time = parseIso(iso) ?: Date()
    calendar.add(Calendar.DAY_OF_MONTH, days)
    return millisToIso(calendar.timeInMillis)
}

/** Drops needless decimals: 2.0 -> "2", 0.75 -> "0.75". */
fun formatQuantity(value: Double): String {
    val rounded = Math.round(value * 100) / 100.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
}
