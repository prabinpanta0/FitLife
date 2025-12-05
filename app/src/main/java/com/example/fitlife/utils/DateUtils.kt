package com.example.fitlife.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val shortDayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun getDayName(dayOfWeek: Int): String {
        return if (dayOfWeek in 0..6) dayNames[dayOfWeek] else "Unscheduled"
    }

    fun getShortDayName(dayOfWeek: Int): String {
        return if (dayOfWeek in 0..6) shortDayNames[dayOfWeek] else "N/A"
    }

    fun getCurrentDayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-based (Sunday = 0)
    }

    fun formatDate(timestamp: Long, pattern: String = "MMM dd, yyyy"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long, pattern: String = "HH:mm"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    fun getDaysOfWeek(): List<Pair<Int, String>> {
        return (0..6).map { it to getDayName(it) }
    }
}
