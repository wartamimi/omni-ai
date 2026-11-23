package com.omniai.core.ui

import com.omniai.core.common.CostCalculator
import com.omniai.core.model.CurrencySettings
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    fun tokens(n: Int?): String = when {
        n == null -> "—"
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 10_000 -> "${n / 1000}K"
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1000.0)
        else -> n.toString()
    }

    fun context(n: Int?): String? = n?.let { "${tokens(it)} ctx" }

    fun money(usd: Double?, currency: CurrencySettings): String =
        usd?.let { CostCalculator.formatInDisplayCurrency(it, currency) } ?: "Unknown"

    fun pricePerMillion(usd: Double?): String = usd?.let { "$" + String.format(Locale.US, if (it < 1) "%.3f" else "%.2f", it) } ?: "?"

    fun time(ts: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ts))

    fun dateTime(ts: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

    fun latency(ms: Long?): String = when {
        ms == null -> "—"
        ms < 1000 -> "$ms ms"
        else -> String.format(Locale.US, "%.1f s", ms / 1000.0)
    }

    /** Groups for conversation lists: Today, Yesterday, Previous 7 days, Previous 30 days, Month Year. */
    fun dateGroup(ts: Long, now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val startToday = cal.timeInMillis
        val day = 24 * 3600 * 1000L
        return when {
            ts >= startToday -> "Today"
            ts >= startToday - day -> "Yesterday"
            ts >= startToday - 7 * day -> "Previous 7 days"
            ts >= startToday - 30 * day -> "Previous 30 days"
            else -> java.text.SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(ts))
        }
    }
}
