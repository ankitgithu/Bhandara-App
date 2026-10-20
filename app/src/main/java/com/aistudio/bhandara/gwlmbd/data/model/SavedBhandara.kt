package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class SavedBhandara(
    @DocumentId
    val id: String = "",
    val bhandaraId: String = "",
    val userId: String = "",
    val savedAt: Long = System.currentTimeMillis()
)
