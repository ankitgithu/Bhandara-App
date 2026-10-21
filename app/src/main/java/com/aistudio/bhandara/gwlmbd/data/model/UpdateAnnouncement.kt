package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UpdateAnnouncement(
    val message: String = "भंडारा App का नया Update आ गया है 🚀\nUpdate डाउनलोड करें और नए भंडारे, नए features और बेहतर experience का मज़ा लें।",
    val updateLink: String = "https://t.me/your_channel",
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
