package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class AppUpdateConfig(
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0",
    val apkUrl: String = "",
    val releaseNotes: String = "• सामान्य सुधार एवं प्रदर्शन में वृद्धि",
    val isMandatory: Boolean = false,
    val minimumSupportedVersionCode: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)
