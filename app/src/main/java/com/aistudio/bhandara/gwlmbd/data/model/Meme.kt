package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Meme(
    @DocumentId
    val id: String = "",
    val text: String = "",
    val city: String = "all", // "gwalior", "morena", "bhind", "all"
    val category: String = "Local", // "Local", "Funny", "Bhandara", "Other"
    val active: Boolean = true,
    val isActive: Boolean = true,
    val createdBy: String = "अनाम भक्त",
    val authorName: String = "",
    val imageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun isMemeActive(): Boolean {
        return active && isActive
    }

    fun getCityDisplayName(): String {
        return when (city.lowercase().trim()) {
            "gwalior", "ग्वालियर" -> "ग्वालियर"
            "morena", "मुरैना" -> "मुरैना"
            "bhind", "भिंड" -> "भिंड"
            else -> "सभी क्षेत्र"
        }
    }
}
