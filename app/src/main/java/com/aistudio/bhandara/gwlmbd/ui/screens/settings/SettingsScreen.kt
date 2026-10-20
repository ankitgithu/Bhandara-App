package com.aistudio.bhandara.gwlmbd.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.bhandara.gwlmbd.data.model.AppUpdateConfig
import com.aistudio.bhandara.gwlmbd.data.model.User
import com.aistudio.bhandara.gwlmbd.data.preferences.AppPreferences
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.AppUpdateDialog
import com.aistudio.bhandara.gwlmbd.ui.components.CitySelectorDialog
import com.aistudio.bhandara.gwlmbd.ui.components.DonationBottomSheet
import com.aistudio.bhandara.gwlmbd.ui.theme.DeepRed
import com.aistudio.bhandara.gwlmbd.util.AppUpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    currentUser: User?,
    isGuest: Boolean,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onThemeChanged: (String) -> Unit,
    onCityChanged: (String) -> Unit,
    repository: BhandaraRepository? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentTheme by remember { mutableStateOf(preferences.themeSetting) }
    var currentCity by remember { mutableStateOf(preferences.selectedCity) }
    var notifEnabled by remember { mutableStateOf(preferences.notificationsEnabled) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDonationSheet by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdateConfig by remember { mutableStateOf<AppUpdateConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "सेटिंग्स (Settings)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Setting Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "थीम (Theme)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val themeOptions = listOf(
                        Triple("light", "☀️ लाइट (Light)", "दिन के समय स्पष्ट दृश्यता"),
                        Triple("dark", "🌙 डार्क (Dark)", "रात में आँखों को आराम"),
                        Triple("system", "⚙️ सिस्टम डिफ़ॉल्ट", "फ़ोन की सेटिंग के अनुसार")
                    )

                    themeOptions.forEach { (key, label, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentTheme = key
                                    preferences.themeSetting = key
                                    onThemeChanged(key)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == key,
                                onClick = {
                                    currentTheme = key
                                    preferences.themeSetting = key
                                    onThemeChanged(key)
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = DeepRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // City Setting Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCityDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "पसंदीदा शहर",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = preferences.getCityHindiName(currentCity),
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeepRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // Notifications Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "भंडारा सूचनाएं (Notifications)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "नए भंडारे जोड़े जाने पर सूचना प्राप्त करें",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notifEnabled,
                        onCheckedChange = {
                            notifEnabled = it
                            preferences.notificationsEnabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = DeepRed, checkedTrackColor = DeepRed.copy(alpha = 0.3f))
                    )
                }
            }

            // About, Terms, Privacy Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAboutDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("भंडारा ऐप के बारे में (About)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null)
                    }

                    Divider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDonationSheet = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = DeepRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Donate for Developer ❤️", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = DeepRed)
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = DeepRed)
                    }

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("नियम व शर्तें (Terms)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("सामुदायिक सेवा", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("गोपनीयता नीति (Privacy)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("सुरक्षित व निजी", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ऐप वर्ज़न (App Version)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        val installedName = AppUpdateManager.getInstalledVersionName(context)
                        val installedCode = AppUpdateManager.getInstalledVersionCode(context)
                        Text("v$installedName ($installedCode) • चंबल अंचल", style = MaterialTheme.typography.bodySmall, color = DeepRed, fontWeight = FontWeight.Bold)
                    }

                    Divider()

                    // Check for Updates action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isCheckingUpdate) {
                                if (repository == null) {
                                    Toast.makeText(context, "सर्वर से संपर्क नहीं हो सका", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                scope.launch {
                                    isCheckingUpdate = true
                                    try {
                                        val config = repository.getAppUpdateConfig()
                                        val installedCode = AppUpdateManager.getInstalledVersionCode(context)
                                        val installedName = AppUpdateManager.getInstalledVersionName(context)
                                        if (config != null && config.latestVersionCode > installedCode) {
                                            availableUpdateConfig = config
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "आप पहले से नवीनतम वर्ज़न (v$installedName) पर हैं! 🎉",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "अपडेट चेक नहीं हो सका, कृपया इंटरनेट चेक करें।",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isCheckingUpdate = false
                                    }
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = DeepRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "अपडेट चेक करें (Check for Update)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DeepRed
                            )
                        }
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = DeepRed
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = DeepRed
                            )
                        }
                    }
                }
            }

            // Login / Logout Action Button
            if (isGuest || currentUser == null) {
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
                ) {
                    Icon(imageVector = Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continue with Google (लॉगिन करें)")
                }
            } else {
                OutlinedButton(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepRed)
                ) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("लॉगआउट करें (${currentUser.name})")
                }
            }
        }
    }

    if (showCityDialog) {
        CitySelectorDialog(
            selectedCity = currentCity,
            onCitySelected = { newCity ->
                currentCity = newCity
                preferences.selectedCity = newCity
                onCityChanged(newCity)
            },
            onDismiss = { showCityDialog = false }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("भंडारा — ग्वालियर, मुरैना, भिंड", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "यह ऐप चंबल-ग्वालियर अंचल के श्रद्धालुओं और आमजन के लिए बनाया गया एक निःशुल्क सामुदायिक मंच है, जहाँ लोग अपने आसपास होने वाले पावन भंडारों की जानकारी प्राप्त कर सकते हैं और नए भंडारे जोड़ सकते हैं।\n\nराम-राम सा! 🙏"
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("धन्यवाद")
                }
            }
        )
    }

    if (showDonationSheet) {
        DonationBottomSheet(
            onDismissRequest = { showDonationSheet = false }
        )
    }

    if (availableUpdateConfig != null) {
        AppUpdateDialog(
            config = availableUpdateConfig!!,
            currentVersionName = AppUpdateManager.getInstalledVersionName(context),
            onDismiss = { availableUpdateConfig = null }
        )
    }
}
