package com.jarvis.calendar

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class CalendarEvent(
    val id: Long, val title: String, val description: String?, val location: String?,
    val startTime: Long, val endTime: Long, val isAllDay: Boolean, val calendarName: String?
)

class CalendarManager(private val context: Context) {
    companion object {
        private const val TAG = "CalendarManager"
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    }

    fun hasPermission(): Boolean = context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun getTodayEvents(): List<CalendarEvent> {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        return getEvents(today.timeInMillis, tomorrow.timeInMillis)
    }

    suspend fun getUpcomingEvents(days: Int = 7): List<CalendarEvent> {
        val now = Calendar.getInstance()
        val future = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, days) }
        return getEvents(now.timeInMillis, future.timeInMillis)
    }

    suspend fun searchEvents(query: String): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEvent>()
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY, CalendarContract.Events.CALENDAR_DISPLAY_NAME)
        val selection = "${CalendarContract.Events.TITLE} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                events.add(CalendarEvent(
                    id = cursor.getLong(0), title = cursor.getString(1) ?: "Untitled",
                    description = cursor.getString(2), location = cursor.getString(3),
                    startTime = cursor.getLong(4), endTime = cursor.getLong(5),
                    isAllDay = cursor.getInt(6) == 1, calendarName = cursor.getString(7)
                ))
            }
        }
        events
    }

    private suspend fun getEvents(start: Long, end: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEvent>()
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY, CalendarContract.Events.CALENDAR_DISPLAY_NAME)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val selectionArgs = arrayOf(start.toString(), end.toString())

        context.contentResolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                events.add(CalendarEvent(
                    id = cursor.getLong(0), title = cursor.getString(1) ?: "Untitled",
                    description = cursor.getString(2), location = cursor.getString(3),
                    startTime = cursor.getLong(4), endTime = cursor.getLong(5),
                    isAllDay = cursor.getInt(6) == 1, calendarName = cursor.getString(7)
                ))
            }
        }
        events
    }

    fun formatEventForVoice(event: CalendarEvent): String = buildString {
        append(event.title)
        if (event.isAllDay) append(" all day") else append(" at ${timeFormat.format(event.startTime)}")
        if (!event.location.isNullOrBlank()) append(" at ${event.location}")
    }

    fun formatEventsSummary(events: List<CalendarEvent>): String {
        if (events.isEmpty()) return "No upcoming events found."
        return buildString {
            append("Found ${events.size} event${if (events.size > 1) "s" else ""}. ")
            events.take(5).forEachIndexed { i, e -> append("${i + 1}. ${formatEventForVoice(e)}. ") }
            if (events.size > 5) append("And ${events.size - 5} more.")
        }
    }
}
