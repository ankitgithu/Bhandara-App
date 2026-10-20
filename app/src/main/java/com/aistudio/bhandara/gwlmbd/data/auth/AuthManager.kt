package com.aistudio.bhandara.gwlmbd.data.auth

import android.content.Context
import android.util.Log
import com.aistudio.bhandara.gwlmbd.R
import com.aistudio.bhandara.gwlmbd.data.model.User
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.authentication.storage.CredentialsManagerException
import com.auth0.android.authentication.storage.SecureCredentialsManager
import com.auth0.android.authentication.storage.SharedPreferencesStorage
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {
    private val tag = "AuthManager"

    private val account: Auth0 by lazy {
        Auth0.getInstance(
            context.getString(R.string.com_auth0_client_id),
            context.getString(R.string.com_auth0_domain)
        )
    }

    private val credentialsManager: SecureCredentialsManager by lazy {
        SecureCredentialsManager(context, account, SharedPreferencesStorage(context))
    }

    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        _isLoading.value = true
        credentialsManager.getCredentials(object : Callback<Credentials, CredentialsManagerException> {
            override fun onSuccess(result: Credentials) {
                val user = result.user
                val sub = user.getId() ?: ""
                val name = user.name ?: user.nickname ?: "भक्त"
                val email = user.email ?: ""
                val picture = user.pictureURL ?: ""

                syncUserWithFirestore(sub, name, email, picture)
            }

            override fun onFailure(error: CredentialsManagerException) {
                Log.d(tag, "No active session or session expired: ${error.message}")
                _currentUser.value = null
                _isLoading.value = false
            }
        })
    }

    fun loginWithGoogle(
        activityContext: Context,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val scheme = context.getString(R.string.com_auth0_scheme)
        _isLoading.value = true

        WebAuthProvider.login(account)
            .withScheme(scheme)
            .withScope("openid profile email offline_access")
            .withConnection("google-oauth2") // Direct Google authentication through Auth0
            .start(activityContext, object : Callback<Credentials, AuthenticationException> {
                override fun onSuccess(result: Credentials) {
                    credentialsManager.saveCredentials(result)
                    val user = result.user
                    val sub = user.getId() ?: ""
                    val name = user.name ?: user.nickname ?: "भक्त"
                    val email = user.email ?: ""
                    val picture = user.pictureURL ?: ""

                    syncUserWithFirestore(sub, name, email, picture, onComplete = onSuccess)
                }

                override fun onFailure(error: AuthenticationException) {
                    _isLoading.value = false
                    Log.e(tag, "Auth0 login failed: ${error.message}", error)
                    val friendlyMsg = if (error.isCanceled) {
                        "लॉगिन रद्द कर दिया गया"
                    } else {
                        "लॉगिन नहीं हो पाया, कृपया फिर कोशिश करें।"
                    }
                    _authError.value = friendlyMsg
                    onError(friendlyMsg)
                }
            })
    }

    fun logout(
        activityContext: Context,
        onComplete: () -> Unit
    ) {
        val scheme = context.getString(R.string.com_auth0_scheme)
        WebAuthProvider.logout(account)
            .withScheme(scheme)
            .start(activityContext, object : Callback<Void?, AuthenticationException> {
                override fun onSuccess(result: Void?) {
                    credentialsManager.clearCredentials()
                    _currentUser.value = null
                    onComplete()
                }

                override fun onFailure(error: AuthenticationException) {
                    Log.w(tag, "Auth0 logout warning: ${error.message}")
                    credentialsManager.clearCredentials()
                    _currentUser.value = null
                    onComplete()
                }
            })
    }

    private fun syncUserWithFirestore(
        sub: String,
        name: String,
        email: String,
        picture: String,
        onComplete: ((User) -> Unit)? = null
    ) {
        if (sub.isBlank()) {
            _isLoading.value = false
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val docRef = firestore.collection("users").document(sub)
                val snapshot = docRef.get().await()

                val isPrimaryOwner = email.equals("shatak1432@gmail.com", ignoreCase = true)
                val userObj: User
                if (snapshot.exists()) {
                    var existingRole = snapshot.getString("role") ?: "user"
                    val isAdminBool = snapshot.getBoolean("admin") ?: false
                    if (isPrimaryOwner || isAdminBool || existingRole.equals("admin", ignoreCase = true)) {
                        existingRole = "admin"
                    }
                    userObj = User(
                        auth0UserId = sub,
                        name = name.ifBlank { snapshot.getString("name") ?: "भक्त" },
                        email = email.ifBlank { snapshot.getString("email") ?: "" },
                        profilePicture = picture.ifBlank { snapshot.getString("profilePicture") ?: "" },
                        role = existingRole,
                        admin = (existingRole == "admin"),
                        createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    docRef.update(
                        mapOf(
                            "name" to userObj.name,
                            "email" to userObj.email,
                            "profilePicture" to userObj.profilePicture,
                            "role" to userObj.role,
                            "admin" to userObj.admin,
                            "updatedAt" to userObj.updatedAt
                        )
                    ).await()
                } else {
                    val roleToSet = if (isPrimaryOwner) "admin" else "user"
                    userObj = User(
                        auth0UserId = sub,
                        name = name,
                        email = email,
                        profilePicture = picture,
                        role = roleToSet,
                        admin = (roleToSet == "admin"),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    docRef.set(userObj).await()
                }

                _currentUser.value = userObj
                _isLoading.value = false
                onComplete?.invoke(userObj)
            } catch (e: Exception) {
                Log.e(tag, "Error syncing user with Firestore: ${e.message}", e)
                // Fallback local user object so app continues working
                val fallbackUser = User(
                    auth0UserId = sub,
                    name = name,
                    email = email,
                    profilePicture = picture,
                    role = "user"
                )
                _currentUser.value = fallbackUser
                _isLoading.value = false
                onComplete?.invoke(fallbackUser)
            }
        }
    }
}
