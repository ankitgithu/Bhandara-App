package com.aistudio.bhandara.gwlmbd

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.aistudio.bhandara.gwlmbd.data.auth.AuthManager
import com.aistudio.bhandara.gwlmbd.data.preferences.AppPreferences
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.navigation.BhandaraNavGraph
import com.aistudio.bhandara.gwlmbd.ui.theme.BhandaraTheme
import com.google.android.gms.maps.MapsInitializer

class MainActivity : ComponentActivity() {

    private val preferences: AppPreferences by lazy {
        AppPreferences(this)
    }

    private val authManager: AuthManager by lazy {
        AuthManager(this)
    }

    private val repository: BhandaraRepository by lazy {
        BhandaraRepository()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            var themeState by remember { mutableStateOf(preferences.themeSetting) }

            BhandaraTheme(themeSetting = themeState) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BhandaraNavGraph(
                        preferences = preferences,
                        authManager = authManager,
                        repository = repository,
                        onLoginRequested = {
                            try {
                                authManager.loginWithGoogle(
                                    activityContext = this@MainActivity,
                                    onSuccess = { user ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            "स्वागत है, ${user.name}! 🙏",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onError = { errorMsg ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            errorMsg,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "लॉगिन नहीं हो सका, कृपया फिर प्रयास करें", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLogoutRequested = {
                            try {
                                authManager.logout(this@MainActivity) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "लॉगआउट संपन्न हुआ",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "लॉगआउट संपन्न हुआ", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onThemeChanged = { newTheme ->
                            preferences.themeSetting = newTheme
                            themeState = newTheme
                        }
                    )
                }
            }
        }
    }
}
