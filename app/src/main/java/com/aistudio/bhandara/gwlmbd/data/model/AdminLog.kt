package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class AdminLog(
    @DocumentId
    val id: String = "",
    val adminId: String = "",
    val action: String = "", // "approve", "reject", "edit", "archive", "meme_add", "meme_toggle", "report_resolve"
    val targetId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""
)
