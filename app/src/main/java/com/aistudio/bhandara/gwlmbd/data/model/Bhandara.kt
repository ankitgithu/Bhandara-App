package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

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
    val status: String = "active" // "active", "expired"
) {
    fun hasLocation(): Boolean {
        return latitude != 0.0 && longitude != 0.0
    }

    fun isExpired(): Boolean {
        if (status == "expired") return true
        if (endDateTime > 0L) {
            return System.currentTimeMillis() > endDateTime
        }
        return false
    }

    fun isApproved(): Boolean {
        return approvalStatus == "approved"
    }

    fun getCityDisplayName(): String {
        return when (city.lowercase().trim()) {
            "gwalior", "ग्वालियर" -> "ग्वालियर"
            "morena", "मुरैना" -> "मुरैना"
            "bhind", "भिंड" -> "भिंड"
            else -> city
        }
    }
}
