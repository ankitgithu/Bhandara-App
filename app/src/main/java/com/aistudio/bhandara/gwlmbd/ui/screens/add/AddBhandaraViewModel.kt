package com.aistudio.bhandara.gwlmbd.ui.screens.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.aistudio.bhandara.gwlmbd.data.model.Bhandara
import com.aistudio.bhandara.gwlmbd.data.model.User
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import java.text.SimpleDateFormat
import java.util.*

class AddBhandaraViewModel : ViewModel() {
    var name by mutableStateOf("")
    var city by mutableStateOf("gwalior")
    var area by mutableStateOf("")
    var landmark by mutableStateOf("")
    var description by mutableStateOf("")

    // Date & Time
    val defaultDateStr: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    var date by mutableStateOf(defaultDateStr)
    var startTime by mutableStateOf("11:00 AM")
    var endTime by mutableStateOf("04:00 PM")

    // Contact
    var contactName by mutableStateOf("")
    var contactPhone by mutableStateOf("")

    // Google Maps Location Link (Optional)
    var mapsUrl by mutableStateOf("")

    // Coordinates (Optional / Fallback)
    var latitude by mutableDoubleStateOf(0.0)
    var longitude by mutableDoubleStateOf(0.0)
    var address by mutableStateOf("")

    // UI state
    var isSubmitting by mutableStateOf(false)
    var showSuccessDialog by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun setLocation(lat: Double, lng: Double, addr: String, areaName: String) {
        latitude = lat
        longitude = lng
        address = addr
        if (mapsUrl.isBlank()) {
            mapsUrl = "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
        }
        if (area.isBlank() && areaName.isNotBlank()) {
            area = areaName
        }
    }

    fun initUser(user: User?) {
        if (contactName.isBlank() && user != null && user.name.isNotBlank()) {
            contactName = user.name
        }
    }

    suspend fun submit(repository: BhandaraRepository, currentUser: User?): Boolean {
        if (name.isBlank()) {
            errorMessage = "कृपया भंडारे का नाम दर्ज करें"
            return false
        }
        if (city.isBlank()) {
            errorMessage = "कृपया शहर चुनें"
            return false
        }
        if (date.isBlank()) {
            errorMessage = "कृपया दिनांक चुनें"
            return false
        }
        if (startTime.isBlank()) {
            errorMessage = "कृपया शुरू होने का समय चुनें"
            return false
        }

        isSubmitting = true
        errorMessage = null
        try {
            val ist = TimeZone.getTimeZone("Asia/Kolkata")
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ist }
            val parsedDate = sdf.parse(date) ?: Date()
            val startMillis = parsedDate.time
            val endMillis = Bhandara.parseEventEndMillis(date, endTime)

            val resolvedMapsUrl = when {
                mapsUrl.isNotBlank() -> mapsUrl.trim()
                latitude != 0.0 && longitude != 0.0 -> "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
                else -> ""
            }

            val newBhandara = Bhandara(
                name = name.trim(),
                city = city.trim(),
                area = area.trim().ifBlank { if (address.isNotBlank()) address else city },
                landmark = landmark.trim(),
                description = description.trim(),
                address = address.ifBlank { area },
                latitude = latitude,
                longitude = longitude,
                mapsUrl = resolvedMapsUrl,
                date = date,
                startTime = startTime,
                endTime = endTime,
                startDateTime = startMillis,
                endDateTime = if (endMillis > 0L) endMillis else (startMillis + 24 * 60 * 60 * 1000L),
                contactName = contactName.trim(),
                contactPhone = contactPhone.trim(),
                createdBy = if (contactName.isNotBlank()) contactName.trim() else (currentUser?.name?.ifBlank { "अनाम भक्त" } ?: "अनाम भक्त"),
                createdByAuth0Id = currentUser?.auth0UserId ?: "",
                approvalStatus = "approved", // Direct publication!
                status = "active"
            )

            repository.addBhandara(newBhandara)
            isSubmitting = false
            showSuccessDialog = true
            return true
        } catch (e: Exception) {
            isSubmitting = false
            errorMessage = "कुछ समस्या आ गई है। कृपया दोबारा कोशिश करें।"
            return false
        }
    }

    fun resetForm() {
        name = ""
        city = "gwalior"
        area = ""
        landmark = ""
        description = ""
        date = defaultDateStr
        startTime = "11:00 AM"
        endTime = "04:00 PM"
        contactName = ""
        contactPhone = ""
        mapsUrl = ""
        latitude = 0.0
        longitude = 0.0
        address = ""
        isSubmitting = false
        showSuccessDialog = false
        errorMessage = null
    }
}
