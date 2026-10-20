package com.aistudio.bhandara.gwlmbd.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val auth0UserId: String = "",
    val name: String = "",
    val email: String = "",
    val profilePicture: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val role: String = "user", // "user" or "admin"
    val admin: Boolean = false
) {
    fun isAdmin(): Boolean = role.equals("admin", ignoreCase = true) || admin
}
