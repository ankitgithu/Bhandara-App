package com.aistudio.bhandara.gwlmbd.ui.screens.admin

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.bhandara.gwlmbd.data.model.*
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.AppUpdateDialog
import com.aistudio.bhandara.gwlmbd.ui.components.EmptyStateView
import com.aistudio.bhandara.gwlmbd.ui.theme.*
import com.aistudio.bhandara.gwlmbd.util.AppUpdateManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    currentUser: User?,
    onBackClick: () -> Unit,
    repository: BhandaraRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Secure verification check: User must be authenticated and have role == "admin"
    if (currentUser == null || !currentUser.isAdmin()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("प्रवेश निषेध") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    title = "प्रशासनिक अनुमति आवश्यक है",
                    description = "यह भाग केवल अधिकृत व्यवस्थापकों के लिए है।"
                )
            }
        }
        return
    }

    var selectedAdminTab by remember { mutableIntStateOf(0) }
    val adminTabs = listOf("सक्रिय भंडारे", "समाप्त भंडारे", "मीम प्रबंधन", "शिकायतें", "APK वर्ज़न")

    var activeList by remember { mutableStateOf<List<Bhandara>>(emptyList()) }
    var expiredList by remember { mutableStateOf<List<Bhandara>>(emptyList()) }
    var memeList by remember { mutableStateOf<List<Meme>>(emptyList()) }
    var reportList by remember { mutableStateOf<List<Report>>(emptyList()) }
    var appUpdateConfig by remember { mutableStateOf(AppUpdateConfig()) }

    var isLoading by remember { mutableStateOf(false) }
    var isSavingConfig by remember { mutableStateOf(false) }
    var showUpdatePreviewDialog by remember { mutableStateOf(false) }
    var bhandaraToDelete by remember { mutableStateOf<Bhandara?>(null) }
    var reportToDelete by remember { mutableStateOf<Report?>(null) }

    var showAddMemeDialog by remember { mutableStateOf(false) }
    var newMemeText by remember { mutableStateOf("") }
    var newMemeCity by remember { mutableStateOf("all") }

    fun refreshAll() {
        scope.launch {
            isLoading = true
            try {
                activeList = repository.getAllActiveBhandarasForAdmin()
                expiredList = repository.getAllExpiredBhandarasForAdmin()
                memeList = repository.getAllMemesForAdmin()
                reportList = repository.getAllReports()
                val updateCfg = repository.getAppUpdateConfig()
                if (updateCfg != null) {
                    appUpdateConfig = updateCfg
                }
            } catch (e: Exception) {
                // Keep resilient
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        launch {
            try {
                val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
                if (uid != null) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("admins").document(uid).set(
                        mapOf(
                            "auth0UserId" to currentUser.auth0UserId,
                            "email" to currentUser.email,
                            "name" to currentUser.name,
                            "role" to "admin",
                            "admin" to true,
                            "updatedAt" to System.currentTimeMillis()
                        )
                    ).await()
                }
            } catch (_: Exception) {}
        }
        refreshAll()
        launch {
            repository.listenToAllActiveBhandarasForAdmin().collect {
                activeList = it
            }
        }
        launch {
            repository.listenToAllExpiredBhandarasForAdmin().collect {
                expiredList = it
            }
        }
        launch {
            repository.listenToReports().collect {
                reportList = it
            }
        }
        launch {
            repository.listenToMemesForAdmin().collect {
                memeList = it
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "व्यवस्थापक नियंत्रण (Admin Panel)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "व्यवस्थापक: ${currentUser.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshAll() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (selectedAdminTab == 2) { // Memes tab
                FloatingActionButton(
                    onClick = { showAddMemeDialog = true },
                    containerColor = DeepRed,
                    contentColor = Color.White
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Meme")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SaffronOrange.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${activeList.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SaffronOrange)
                        Text(text = "सक्रिय भंडारे", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${expiredList.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(text = "समाप्त", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = DeepRed.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${reportList.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DeepRed)
                        Text(text = "रिपोर्ट्स", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = WarmGold.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "${memeList.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = WarmGold)
                        Text(text = "मीम्स", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Tabs Row
            ScrollableTabRow(
                selectedTabIndex = selectedAdminTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = DeepRed,
                edgePadding = 16.dp
            ) {
                adminTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedAdminTab == index,
                        onClick = { selectedAdminTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedAdminTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DeepRed)
                }
            } else {
                when (selectedAdminTab) {
                    // 0: Active Bhandaras (All active documents from Firestore)
                    0 -> {
                        if (activeList.isEmpty()) {
                            EmptyStateView(
                                title = "कोई सक्रिय भंडारा नहीं मिला",
                                description = "डेटाबेस में सक्रिय भंडारे यहाँ दिखेंगे।"
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DeepRed.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "कुल भंडारे: ${activeList.size} (Firestore लाइव डेटा)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = DeepRed,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                items(activeList) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.name.ifBlank { "अनाम भंडारा" },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DeepRed
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = StatusApproved.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = "सक्रिय ✓",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = StatusApproved,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "ज़िला: ${item.getCityDisplayName()} | क्षेत्र: ${item.area.ifBlank { "उपलब्ध नहीं" }}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (item.address.isNotBlank()) {
                                                Text(text = "पता: ${item.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (item.landmark.isNotBlank()) {
                                                Text(text = "लैंडमार्क: ${item.landmark}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "दिनांक: ${item.date} (${item.startTime}${if (item.endTime.isNotBlank()) " - ${item.endTime}" else ""})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = SaffronOrange,
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            if (item.contactPhone.isNotBlank() || item.contactName.isNotBlank()) {
                                                Text(
                                                    text = "संपर्क: ${item.contactName} (${item.contactPhone})",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            val formattedCreated = formatTimestamp(item.createdAt)
                                            Text(
                                                text = "जोड़ा गया: ${item.createdBy} • $formattedCreated",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Map Button (Only shown if map link exists!)
                                                val mapLink = when {
                                                    item.mapsUrl.isNotBlank() -> item.mapsUrl.trim()
                                                    item.latitude != 0.0 && item.longitude != 0.0 -> "https://www.google.com/maps/search/?api=1&query=${item.latitude},${item.longitude}"
                                                    else -> null
                                                }
                                                if (mapLink != null) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            try {
                                                                val cleanUrl = if (!mapLink.startsWith("http://") && !mapLink.startsWith("https://")) "https://$mapLink" else mapLink
                                                                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl))
                                                                context.startActivity(mapIntent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "मैप खोलने में असमर्थ", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text("📍 Map पर देखें", style = MaterialTheme.typography.labelMedium)
                                                    }
                                                }

                                                // Call Button
                                                if (item.contactPhone.isNotBlank()) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.contactPhone}"))
                                                            context.startActivity(callIntent)
                                                        },
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }

                                                // Archive / Expire Button
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                repository.archiveBhandara(item.id, currentUser.auth0UserId)
                                                                refreshAll()
                                                                Toast.makeText(context, "भंडारा समाप्त चिह्नित किया गया", Toast.LENGTH_SHORT).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "अपडेट विफल: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("समाप्त करें", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                                                }

                                                // Admin Delete Button
                                                OutlinedButton(
                                                    onClick = { bhandaraToDelete = item },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRejected),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusRejected.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("हटाएं", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 1: Expired / Historical Bhandaras
                    1 -> {
                        if (expiredList.isEmpty()) {
                            EmptyStateView(
                                title = "कोई समाप्त भंडारा नहीं मिला",
                                description = "समय पूरा होने के बाद भंडारे यहाँ इतिहास में सुरक्षित रहते हैं।"
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Text(
                                        text = "कुल समाप्त भंडारे: ${expiredList.size}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                items(expiredList) { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(text = item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                Text(text = "समाप्त", style = MaterialTheme.typography.labelSmall, color = StatusExpired)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(text = "दिनांक: ${item.date} | शहर: ${item.getCityDisplayName()}", style = MaterialTheme.typography.bodySmall)
                                            Text(text = "स्थान: ${item.area}", style = MaterialTheme.typography.bodySmall)

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                OutlinedButton(
                                                    onClick = { bhandaraToDelete = item },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRejected),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusRejected.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("हटाएं", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2: Manage Memes
                    2 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(memeList) { meme ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = meme.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = "क्षेत्र: ${meme.city.uppercase()}", style = MaterialTheme.typography.labelSmall, color = SaffronOrange)
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = meme.active && meme.isActive,
                                                onCheckedChange = { active ->
                                                    scope.launch {
                                                        try {
                                                            repository.toggleMeme(meme.id, active, currentUser.auth0UserId)
                                                            refreshAll()
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "अपडेट नहीं हो पाया", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            )
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            repository.deleteMeme(meme.id, currentUser.auth0UserId)
                                                            refreshAll()
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "हटाया नहीं जा सका", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = StatusRejected)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3: Reports
                    3 -> {
                        if (reportList.isEmpty()) {
                            EmptyStateView(
                                title = "कोई शिकायत नहीं है",
                                description = "सभी रिपोर्टों का समाधान हो चुका है।"
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(reportList, key = { it.id }) { report ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            // Header: Report ID and Status Badge
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Report #${report.id.takeLast(6).uppercase()}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )

                                                val statusColor = when (report.status.lowercase()) {
                                                    "resolved" -> StatusApproved
                                                    "reviewed" -> Color(0xFF0288D1)
                                                    else -> StatusPending
                                                }
                                                val statusText = when (report.status.lowercase()) {
                                                    "resolved" -> "Resolved (हल हुआ)"
                                                    "reviewed" -> "Reviewed (समीक्षित)"
                                                    else -> "Pending (लंबित)"
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = statusColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = statusText,
                                                        color = statusColor,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Bhandara Name
                                            Text(
                                                text = "भंडारा: ${report.bhandaraName.ifBlank { report.bhandaraId }}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            // Reason
                                            Text(
                                                text = "कारण: ${report.reason}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = DeepRed
                                            )

                                            // Description if available
                                            if (report.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "विवरण: ${report.description}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            // City if available
                                            if (report.city.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "शहर: ${report.city}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            // Reporter info
                                            val reporterInfo = when {
                                                report.reporterName.isNotBlank() && report.reporterEmail.isNotBlank() ->
                                                    "${report.reporterName} (${report.reporterEmail})"
                                                report.reporterName.isNotBlank() -> report.reporterName
                                                report.reporterEmail.isNotBlank() -> report.reporterEmail
                                                report.reportedBy.isNotBlank() -> report.reportedBy
                                                else -> "अनाम यूज़र"
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "रिपोर्टकर्ता: $reporterInfo",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            // Date
                                            Text(
                                                text = "समय: ${formatTimestamp(report.createdAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )

                                            Spacer(modifier = Modifier.height(10.dp))
                                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Status switch chips & Delete button
                                            Text(
                                                text = "स्थिति बदलें (Change Status):",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    listOf("Pending", "Reviewed", "Resolved").forEach { statusOption ->
                                                        val isSelected = report.status.equals(statusOption, ignoreCase = true)
                                                        FilterChip(
                                                            selected = isSelected,
                                                            onClick = {
                                                                if (!isSelected) {
                                                                    scope.launch {
                                                                        try {
                                                                            repository.updateReportStatus(report.id, statusOption, currentUser.auth0UserId)
                                                                            Toast.makeText(context, "स्थिति बदली: $statusOption", Toast.LENGTH_SHORT).show()
                                                                        } catch (e: Exception) {
                                                                            Toast.makeText(context, "स्थिति बदलने में त्रुटि: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            label = {
                                                                Text(
                                                                    text = when (statusOption) {
                                                                        "Pending" -> "लंबित"
                                                                        "Reviewed" -> "समीक्षित"
                                                                        "Resolved" -> "हल हुआ"
                                                                        else -> statusOption
                                                                    },
                                                                    style = MaterialTheme.typography.labelSmall
                                                                )
                                                            },
                                                            colors = FilterChipDefaults.filterChipColors(
                                                                selectedContainerColor = when (statusOption) {
                                                                    "Resolved" -> StatusApproved
                                                                    "Reviewed" -> Color(0xFF0288D1)
                                                                    else -> StatusPending
                                                                },
                                                                selectedLabelColor = Color.White
                                                            )
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = { reportToDelete = report }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Report",
                                                        tint = StatusRejected
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4: App Update Management (APK Versioning)
                    4 -> {
                        val installedCode = AppUpdateManager.getInstalledVersionCode(context)
                        val installedName = AppUpdateManager.getInstalledVersionName(context)

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Current device version info card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "वर्तमान डिवाइस वर्ज़न",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "v$installedName (Code: $installedCode)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (appUpdateConfig.latestVersionCode > installedCode) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = if (appUpdateConfig.latestVersionCode > installedCode) "नया अपडेट उपलब्ध" else "अप-टू-डेट ✓",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (appUpdateConfig.latestVersionCode > installedCode) SaffronOrange else StatusApproved,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Firestore Remote Configuration Form
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "रिमोट अपडेट कॉन्फ़िगरेशन (Firestore)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepRed
                                    )
                                    Text(
                                        text = "Firestore संग्रह: app_config / दस्तावेज़: app_update",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    HorizontalDivider()

                                    // Latest Version Code
                                    OutlinedTextField(
                                        value = appUpdateConfig.latestVersionCode.toString(),
                                        onValueChange = { newVal ->
                                            val parsed = newVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                            appUpdateConfig = appUpdateConfig.copy(latestVersionCode = parsed)
                                        },
                                        label = { Text("नवीनतम Version Code (उदा. 2)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    // Latest Version Name
                                    OutlinedTextField(
                                        value = appUpdateConfig.latestVersionName,
                                        onValueChange = { newVal ->
                                            appUpdateConfig = appUpdateConfig.copy(latestVersionName = newVal)
                                        },
                                        label = { Text("नवीनतम Version Name (उदा. 1.1)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    // APK URL
                                    OutlinedTextField(
                                        value = appUpdateConfig.apkUrl,
                                        onValueChange = { newVal ->
                                            appUpdateConfig = appUpdateConfig.copy(apkUrl = newVal)
                                        },
                                        label = { Text("APK डाउनलोड URL (Direct Link)") },
                                        placeholder = { Text("https://example.com/bhandara-v1.1.apk") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    // Release Notes
                                    OutlinedTextField(
                                        value = appUpdateConfig.releaseNotes,
                                        onValueChange = { newVal ->
                                            appUpdateConfig = appUpdateConfig.copy(releaseNotes = newVal)
                                        },
                                        label = { Text("रिलीज़ नोट्स (हिंदी में)") },
                                        placeholder = { Text("• नया Map सुधार\n• Meme Add बग फिक्स\n• इन-ऐप अपडेट सिस्टम") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )

                                    // Mandatory Update Toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "अनिवार्य अपडेट (Mandatory Update)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "चालू करने पर यूज़र बिना अपडेट किए ऐप इस्तेमाल नहीं कर सकेंगे",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = appUpdateConfig.isMandatory,
                                            onCheckedChange = { isChecked ->
                                                appUpdateConfig = appUpdateConfig.copy(isMandatory = isChecked)
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = DeepRed, checkedTrackColor = DeepRed.copy(alpha = 0.5f))
                                        )
                                    }

                                    // Minimum Supported Version Code
                                    OutlinedTextField(
                                        value = appUpdateConfig.minimumSupportedVersionCode.toString(),
                                        onValueChange = { newVal ->
                                            val parsed = newVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                                            appUpdateConfig = appUpdateConfig.copy(minimumSupportedVersionCode = parsed)
                                        },
                                        label = { Text("न्यूनतम समर्थित Version Code (Minimum Supported)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Save Button
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isSavingConfig = true
                                                try {
                                                    repository.setAppUpdateConfig(appUpdateConfig, currentUser.auth0UserId)
                                                    Toast.makeText(context, "अपडेट सेटिंग्स Firestore में सुरक्षित कर दी गईं! ✓", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "सेव नहीं हो सका: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                } finally {
                                                    isSavingConfig = false
                                                }
                                            }
                                        },
                                        enabled = !isSavingConfig,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
                                    ) {
                                        if (isSavingConfig) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                        } else {
                                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Firestore में सेव करें (Save Config)")
                                        }
                                    }

                                    // Preview Dialog Button
                                    OutlinedButton(
                                        onClick = { showUpdatePreviewDialog = true },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Visibility, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("अपडेट डायलॉग प्रीव्यू करें (Preview Dialog)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Meme Dialog
    if (showAddMemeDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemeDialog = false },
            title = { Text("नया स्थानीय मीम जोड़ें") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newMemeText,
                        onValueChange = { newMemeText = it },
                        label = { Text("मीम पंक्ति (हिंदी में)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Text("शहर चुनें:", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to "सभी", "gwalior" to "ग्वालियर", "morena" to "मुरैना", "bhind" to "भिंड").forEach { (k, v) ->
                            FilterChip(
                                selected = newMemeCity == k,
                                onClick = { newMemeCity = k },
                                label = { Text(v, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val text = newMemeText.trim()
                        if (text.isNotBlank()) {
                            scope.launch {
                                try {
                                    val meme = Meme(
                                        text = text,
                                        city = newMemeCity,
                                        active = true,
                                        isActive = true,
                                        createdBy = currentUser.name
                                    )
                                    repository.addMeme(meme, currentUser.auth0UserId)
                                    showAddMemeDialog = false
                                    newMemeText = ""
                                    refreshAll()
                                    Toast.makeText(context, "Meme successfully जुड़ गया 😂", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "कुछ समस्या आ गई है। कृपया दोबारा कोशिश करें।", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
                ) {
                    Text("जोड़ें")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemeDialog = false }) {
                    Text("रद्द करें")
                }
            }
        )
    }

    // Update Dialog Preview
    if (showUpdatePreviewDialog) {
        AppUpdateDialog(
            config = appUpdateConfig,
            currentVersionName = AppUpdateManager.getInstalledVersionName(context),
            onDismiss = { showUpdatePreviewDialog = false }
        )
    }

    // Delete Bhandara Confirmation Dialog
    if (bhandaraToDelete != null) {
        val target = bhandaraToDelete!!
        AlertDialog(
            onDismissRequest = { bhandaraToDelete = null },
            title = {
                Text(
                    text = "क्या आप इस भंडारे को हटाना चाहते हैं?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "\"${target.name.ifBlank { "यह भंडारा" }}\" को डेटाबेस से स्थायी रूप से हटा दिया जाएगा।",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = target
                        bhandaraToDelete = null
                        scope.launch {
                            try {
                                // Immediately remove from active & expired lists in memory
                                activeList = activeList.filter { it.id != toDelete.id }
                                expiredList = expiredList.filter { it.id != toDelete.id }

                                repository.deleteBhandara(toDelete.id, currentUser.auth0UserId)
                                Toast.makeText(context, "भंडारा सफलतापूर्वक हटा दिया गया।", Toast.LENGTH_SHORT).show()
                                refreshAll()
                            } catch (e: Exception) {
                                val errorMsg = if (e.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ||
                                                   e.message?.contains("permission-denied", ignoreCase = true) == true) {
                                    "अनुमति अस्वीकृत (Permission Denied): केवल अधिकृत एडमिन ही भंडारा हटा सकते हैं।"
                                } else {
                                    "हटाने में समस्या आई: ${e.message}"
                                }
                                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                                refreshAll()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRejected)
                ) {
                    Text("हटाएं", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { bhandaraToDelete = null }
                ) {
                    Text("रद्द करें")
                }
            }
        )
    }

    // Delete Report Confirmation Dialog
    if (reportToDelete != null) {
        val target = reportToDelete!!
        AlertDialog(
            onDismissRequest = { reportToDelete = null },
            title = {
                Text(
                    text = "क्या आप यह शिकायत हटाना चाहते हैं?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "\"${target.bhandaraName.ifBlank { "यह रिपोर्ट" }}\" से संबंधित शिकायत को हटा दिया जाएगा।",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = target
                        reportToDelete = null
                        scope.launch {
                            try {
                                reportList = reportList.filter { it.id != toDelete.id }
                                repository.deleteReport(toDelete.id, currentUser.auth0UserId)
                                Toast.makeText(context, "रिपोर्ट सफलतापूर्वक हटा दी गई।", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "हटाने में समस्या आई: ${e.message}", Toast.LENGTH_SHORT).show()
                                refreshAll()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRejected)
                ) {
                    Text("हटाएं", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { reportToDelete = null }) {
                    Text("रद्द करें")
                }
            }
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "हाल ही में"
    return try {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }
        sdf.format(Date(millis))
    } catch (_: Exception) {
        "हाल ही में"
    }
}
