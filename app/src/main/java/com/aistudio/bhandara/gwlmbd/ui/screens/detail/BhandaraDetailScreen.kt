package com.aistudio.bhandara.gwlmbd.ui.screens.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.bhandara.gwlmbd.data.model.Bhandara
import com.aistudio.bhandara.gwlmbd.data.model.Report
import com.aistudio.bhandara.gwlmbd.data.model.User
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.LoginPromptDialog
import com.aistudio.bhandara.gwlmbd.ui.theme.*
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BhandaraDetailScreen(
    bhandaraId: String,
    currentUser: User?,
    isGuest: Boolean,
    onBackClick: () -> Unit,
    onRequireLogin: () -> Unit,
    repository: BhandaraRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bhandara by remember { mutableStateOf<Bhandara?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaved by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showLoginPrompt by remember { mutableStateOf(false) }

    // Report reasons
    val reportReasons = listOf(
        "गलत Location",
        "गलत Date",
        "गलत Time",
        "Contact गलत है",
        "भंडारा समाप्त हो चुका है",
        "अन्य"
    )
    var selectedReason by remember { mutableStateOf(reportReasons[0]) }
    var reportDesc by remember { mutableStateOf("") }
    var isReporting by remember { mutableStateOf(false) }

    LaunchedEffect(bhandaraId) {
        isLoading = true
        val b = repository.getBhandaraById(bhandaraId)
        bhandara = b
        if (currentUser != null && b != null) {
            isSaved = repository.isBhandaraSaved(b.id, currentUser.auth0UserId)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "भंडारा विवरण",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Save / Bookmark button
                    IconButton(
                        onClick = {
                            if (isGuest || currentUser == null) {
                                showLoginPrompt = true
                            } else {
                                scope.launch {
                                    val b = bhandara ?: return@launch
                                    if (isSaved) {
                                        repository.unsaveBhandara(b.id, currentUser.auth0UserId)
                                        isSaved = false
                                        Toast.makeText(context, "सेव से हटा दिया गया", Toast.LENGTH_SHORT).show()
                                    } else {
                                        repository.saveBhandara(b.id, currentUser.auth0UserId)
                                        isSaved = true
                                        Toast.makeText(context, "भंडारा सुरक्षित सेव किया गया!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save",
                            tint = if (isSaved) DeepRed else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Report button
                    IconButton(onClick = { showReportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Report",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = DeepRed)
            }
        } else if (bhandara == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("भंडारे की जानकारी नहीं मिल सकी।")
            }
        } else {
            val item = bhandara!!
            val isExpired = item.isExpired()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Expired Warning Banner (Section 31 requirement!)
                if (isExpired) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = DeepRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ये भंडारा अब समाप्त हो चुका है",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DeepRed
                            )
                        }
                    }
                }

                // Map Preview or Google Maps Link Banner
                val mapLink = when {
                    item.mapsUrl.isNotBlank() -> item.mapsUrl.trim()
                    item.latitude != 0.0 && item.longitude != 0.0 -> "https://www.google.com/maps/search/?api=1&query=${item.latitude},${item.longitude}"
                    else -> null
                }

                if (item.latitude != 0.0 && item.longitude != 0.0) {
                    val position = remember { LatLng(item.latitude, item.longitude) }
                    val cameraState = rememberCameraPositionState {
                        this.position = CameraPosition.fromLatLngZoom(position, 16f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState,
                            properties = MapProperties(mapType = MapType.SATELLITE),
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                scrollGesturesEnabled = false,
                                zoomGesturesEnabled = false,
                                tiltGesturesEnabled = false,
                                rotationGesturesEnabled = false
                            )
                        ) {
                            Marker(
                                state = MarkerState(position = position),
                                title = item.name,
                                snippet = item.area
                            )
                        }

                        // Open Map button overlay
                        Button(
                            onClick = {
                                try {
                                    val cleanUrl = if (mapLink != null && !mapLink.startsWith("http://") && !mapLink.startsWith("https://")) {
                                        "https://$mapLink"
                                    } else {
                                        mapLink ?: "https://www.google.com/maps/search/?api=1&query=${item.latitude},${item.longitude}"
                                    }
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "मैप खोलने में असमर्थ", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.7f))
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("📍 Map पर देखें", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else if (item.mapsUrl.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable {
                                try {
                                    val raw = item.mapsUrl.trim()
                                    val cleanUrl = if (!raw.startsWith("http://") && !raw.startsWith("https://")) "https://$raw" else raw
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "मैप खोलने में असमर्थ", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DeepRed.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = DeepRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Maps लोकेशन उपलब्ध",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepRed
                                )
                                Text(
                                    text = "सीधे गूगल मैप्स में नेविगेट करने हेतु टैप करें",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = DeepRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Name & City
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Locality & Landmark
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "${item.area} (${item.getCityDisplayName()})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (item.landmark.isNotBlank()) {
                                Text(
                                    text = "पहचान: ${item.landmark}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.address.isNotBlank() && item.address != item.area) {
                                Text(
                                    text = item.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Date & Time Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = DeepRed, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "दिनांक", style = MaterialTheme.typography.labelSmall)
                                Text(text = item.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }

                            Divider(modifier = Modifier.height(40.dp).width(1.dp))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = WarmGold, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "समय", style = MaterialTheme.typography.labelSmall)
                                Text(text = "${item.startTime} - ${item.endTime}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Description
                    if (item.description.isNotBlank()) {
                        Column {
                            Text(
                                text = "भंडारे के बारे में",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    // Contact Info
                    if (item.contactName.isNotBlank() || item.contactPhone.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "संपर्क सूत्र",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.contactName.isNotBlank()) {
                                        Text(text = item.contactName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                    if (item.contactPhone.isNotBlank()) {
                                        Text(text = item.contactPhone, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                if (item.contactPhone.isNotBlank()) {
                                    FilledTonalButton(
                                        onClick = {
                                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.contactPhone}")))
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SaffronOrange.copy(alpha = 0.15f), contentColor = SaffronOrange)
                                    ) {
                                        Icon(imageVector = Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("कॉल करें")
                                    }
                                }
                            }
                        }
                    }

                    // Big Directions / Map Button ("📍 Map पर देखें")
                    // Only shown if a location link exists; omitted safely if none exists!
                    if (mapLink != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                try {
                                    val cleanUrl = if (!mapLink.startsWith("http://") && !mapLink.startsWith("https://")) {
                                        "https://$mapLink"
                                    } else {
                                        mapLink
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "मैप खोलने में असमर्थ", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
                        ) {
                            Text(
                                text = "📍 Map पर देखें",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Report Dialog
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = {
                Text(
                    text = "गलत जानकारी की रिपोर्ट करें",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("कारण चुनें:", style = MaterialTheme.typography.labelMedium)
                    reportReasons.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedReason == r,
                                onClick = { selectedReason = r }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = r, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    OutlinedTextField(
                        value = reportDesc,
                        onValueChange = { reportDesc = it },
                        label = { Text("अतिरिक्त विवरण (वैकल्पिक)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val b = bhandara ?: return@Button
                        if (isReporting) return@Button
                        isReporting = true
                        scope.launch {
                            try {
                                val report = Report(
                                    bhandaraId = b.id,
                                    bhandaraName = b.name,
                                    reportedBy = currentUser?.email?.ifBlank { currentUser.name } ?: "Guest",
                                    reporterName = currentUser?.name ?: "Guest",
                                    reporterEmail = currentUser?.email ?: "",
                                    city = b.city,
                                    reason = selectedReason,
                                    description = reportDesc.trim(),
                                    createdAt = System.currentTimeMillis(),
                                    status = "Pending"
                                )
                                repository.reportBhandara(report)
                                isReporting = false
                                showReportDialog = false
                                reportDesc = ""
                                Toast.makeText(context, "Report सफलतापूर्वक भेज दी गई।", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                isReporting = false
                                Toast.makeText(context, "Report भेजने में समस्या: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isReporting,
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
                ) {
                    if (isReporting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Report भेजी जा रही है...")
                    } else {
                        Text("रिपोर्ट भेजें")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("रद्द करें")
                }
            }
        )
    }

    if (showLoginPrompt) {
        LoginPromptDialog(
            onLoginClick = {
                showLoginPrompt = false
                onRequireLogin()
            },
            onDismiss = { showLoginPrompt = false }
        )
    }
}
