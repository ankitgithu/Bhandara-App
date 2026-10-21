package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Report(
    @DocumentId
    val id: String = "",
    val bhandaraId: String = "",
    val bhandaraName: String = "",
    val reason: String = "", // गलत Location, गलत Date, गलत Time, Contact गलत है, भंडारा समाप्त हो चुका है, अन्य
    val description: String = "",
    val reportedBy: String = "",
    val reporterName: String = "",
    val reporterEmail: String = "",
    val city: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "Pending" // Pending, Reviewed, Resolved
)
