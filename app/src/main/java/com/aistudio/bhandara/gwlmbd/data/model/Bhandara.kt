package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@IgnoreExtraProperties
data class Bhandara(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val city: String = "gwalior", // "gwalior", "morena", "bhind"
    val area: String = "",
    val landmark: String = "",
    val description: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val mapsUrl: String = "",
    val date: String = "", // "YYYY-MM-DD"
    val startTime: String = "", // "11:00 AM"
    val endTime: String = "", // "04:00 PM"
    val startDateTime: Long = 0L,
    val endDateTime: Long = 0L,
    val contactName: String = "",
    val contactPhone: String = "",
    val createdBy: String = "अनाम भक्त",
    val createdByAuth0Id: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val approvalStatus: String = "approved", // "approved", "pending", "rejected"
    val status: String = "active" // "active", "expired", "archived"
) {
    fun hasLocation(): Boolean {
        return latitude != 0.0 && longitude != 0.0
    }

    /**
     * Determines whether the Bhandara has concluded based on date and time in Asia/Kolkata timezone.
     */
    fun isExpired(): Boolean {
        // Explicitly archived or expired status
        if (status.equals("expired", ignoreCase = true) ||
            status.equals("archived", ignoreCase = true) ||
            status.equals("deleted", ignoreCase = true)
        ) {
            return true
        }

        val nowMillis = System.currentTimeMillis()

        // If explicit endDateTime timestamp exists in millis
        if (endDateTime > 0L) {
            return nowMillis > endDateTime
        }

        // Calculate end of event from date and endTime in Asia/Kolkata timezone
        if (date.isNotBlank()) {
            val eventEndMillis = parseEventEndMillis(date, endTime)
            if (eventEndMillis > 0L) {
                return nowMillis > eventEndMillis
            }
        }

        return false
    }

    /**
     * Backward-compatible approval check. Per requirements:
     * Bhandaras do NOT require admin approval before appearing.
     * Only explicitly rejected ones are excluded.
     */
    fun isVisiblePublicly(): Boolean {
        val isRejected = approvalStatus.equals("rejected", ignoreCase = true)
        return !isRejected && !isExpired()
    }

    /**
     * District/City matching logic.
     * Important requirement: Do not filter out records if district is missing.
     * Records with a valid district appear under the correct district.
     */
    fun matchesCity(filterCity: String): Boolean {
        val cleanFilter = filterCity.trim().lowercase()
        if (cleanFilter.isBlank() || cleanFilter == "all" || cleanFilter == "सभी") {
            return true
        }

        val selfCity = city.trim().lowercase()
        val selfDisplayName = getCityDisplayName().trim().lowercase()
        val selfArea = area.trim().lowercase()
        val selfAddress = address.trim().lowercase()

        // Match normalized key, hindi name, or if district is missing in document
        return selfCity == cleanFilter ||
               selfDisplayName == cleanFilter ||
               selfCity.contains(cleanFilter) ||
               cleanFilter.contains(selfCity) ||
               selfArea.contains(cleanFilter) ||
               selfAddress.contains(cleanFilter) ||
               selfCity.isBlank() // Missing district is never excluded
    }

    fun getCityDisplayName(): String {
        return when (city.lowercase().trim()) {
            "gwalior", "ग्वालियर" -> "ग्वालियर"
            "morena", "मुरैना" -> "मुरैना"
            "bhind", "भिंड" -> "भिंड"
            else -> if (city.isNotBlank()) city else "ग्वालियर"
        }
    }

    companion object {
        private val IST_TIMEZONE = TimeZone.getTimeZone("Asia/Kolkata")

        /**
         * Parses event end time in Asia/Kolkata. If endTime is missing, event expires at 23:59:59 IST of that date.
         */
        fun parseEventEndMillis(dateStr: String, endTimeStr: String): Long {
            return try {
                val cal = Calendar.getInstance(IST_TIMEZONE)

                // Try common date formats
                val dateFormats = listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd/MM/yyyy", "yyyy/MM/dd")
                var parsedDate: Date? = null
                for (fmt in dateFormats) {
                    try {
                        val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = IST_TIMEZONE }
                        sdf.isLenient = false
                        parsedDate = sdf.parse(dateStr.trim())
                        if (parsedDate != null) break
                    } catch (_: Exception) {}
                }

                if (parsedDate == null) return 0L
                cal.time = parsedDate

                val cleanTime = endTimeStr.trim().uppercase()
                if (cleanTime.isNotBlank()) {
                    // Try parsing 12-hour or 24-hour time
                    var timeParsed = false
                    val timeFormats = listOf(
                        "hh:mm a", "h:mm a", "hh:mma", "h:mma",
                        "HH:mm", "H:mm", "hh:mm:ss a"
                    )

                    for (tFmt in timeFormats) {
                        try {
                            val timeSdf = SimpleDateFormat(tFmt, Locale.US).apply { timeZone = IST_TIMEZONE }
                            val tDate = timeSdf.parse(cleanTime)
                            if (tDate != null) {
                                val tCal = Calendar.getInstance(IST_TIMEZONE).apply { time = tDate }
                                cal.set(Calendar.HOUR_OF_DAY, tCal.get(Calendar.HOUR_OF_DAY))
                                cal.set(Calendar.MINUTE, tCal.get(Calendar.MINUTE))
                                cal.set(Calendar.SECOND, 0)
                                cal.set(Calendar.MILLISECOND, 0)
                                timeParsed = true
                                break
                            }
                        } catch (_: Exception) {}
                    }

                    if (!timeParsed) {
                        // End of day fallback
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                    }
                } else {
                    // No end time specified: event is valid until end of the day
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                }

                cal.timeInMillis
            } catch (e: Exception) {
                0L
            }
        }
    }
}
