package com.aistudio.bhandara.gwlmbd.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aistudio.bhandara.gwlmbd.data.model.Bhandara
import com.aistudio.bhandara.gwlmbd.data.model.Report
import com.aistudio.bhandara.gwlmbd.data.model.User
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.BhandaraCard
import com.aistudio.bhandara.gwlmbd.ui.components.DonationBottomSheet
import com.aistudio.bhandara.gwlmbd.ui.components.EmptyStateView
import com.aistudio.bhandara.gwlmbd.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    currentUser: User?,
    isGuest: Boolean,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAdminClick: () -> Unit,
    onBhandaraClick: (String) -> Unit,
    repository: BhandaraRepository
) {
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableIntStateOf(0) } // 0: जोड़े हुए, 1: रिपोर्ट्स
    var userSubmissions by remember { mutableStateOf<List<Bhandara>>(emptyList()) }
    var userReports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showDonationSheet by remember { mutableStateOf(false) }

    val sections = listOf("मेरे जोड़े हुए भंडारे", "मेरी रिपोर्ट्स")

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            isLoading = true
            userSubmissions = repository.getUserSubmittedBhandaras(currentUser.auth0UserId)
            userReports = repository.getUserReports(currentUser.email)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "मेरा प्रोफाइल",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isGuest || currentUser == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    title = "Login करके ये सुविधा इस्तेमाल करें",
                    description = "अपने जोड़े हुए भंडारे देखने और प्रोफाइल प्रबंधित करने के लिए Google से लॉगिन करें।",
                    icon = Icons.Default.AccountCircle,
                    onRetry = onLoginClick
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User Info Header Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Profile Avatar
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(DeepRed.copy(alpha = 0.1f))
                                    .border(2.dp, DeepRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentUser.profilePicture.isNotBlank()) {
                                    AsyncImage(
                                        model = currentUser.profilePicture,
                                        contentDescription = "Profile Picture",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = DeepRed,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Name with Secret Long-Press for Admin Panel
                            Text(
                                text = currentUser.name.ifBlank { "भक्त" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        if (currentUser.isAdmin()) {
                                            onAdminClick()
                                        }
                                    }
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = currentUser.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (currentUser.isAdmin()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DeepRed)
                                        .clickable { onAdminClick() }
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "★ व्यवस्थापक (Admin Panel) खोलें",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { showDonationSheet = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DeepRed.copy(alpha = 0.12f),
                                        contentColor = DeepRed
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(18.dp), tint = DeepRed)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Donate ❤️", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onLogoutClick,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DeepRed)
                                ) {
                                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("लॉगआउट")
                                }
                            }
                        }
                    }
                }

                // Section Tabs
                item {
                    TabRow(
                        selectedTabIndex = selectedSection,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = DeepRed
                    ) {
                        sections.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedSection == index,
                                onClick = { selectedSection = index },
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selectedSection == index) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }

                // Section Content
                if (selectedSection == 0) {
                    if (userSubmissions.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "आपने अभी कोई भंडारा नहीं जोड़ा है",
                                description = "अगर आप किसी भंडारे के बारे में जानते हैं, तो होम स्क्रीन पर '+' बटन दबाकर जोड़ें!"
                            )
                        }
                    } else {
                        items(userSubmissions) { b ->
                            BhandaraCard(
                                bhandara = b,
                                onClick = { onBhandaraClick(b.id) }
                            )
                        }
                    }
                } else {
                    if (userReports.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "कोई रिपोर्ट दर्ज नहीं है",
                                description = "अगर आपको किसी भंडारे की जानकारी में गड़बड़ी मिलती है, तो आप रिपोर्ट कर सकते हैं।"
                            )
                        }
                    } else {
                        items(userReports) { report ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "भंडारा: ${report.bhandaraName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "कारण: ${report.reason}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = DeepRed
                                    )
                                    if (report.description.isNotBlank()) {
                                        Text(
                                            text = "विवरण: ${report.description}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "स्थिति: ${if (report.status == "resolved") "समाधान हो गया ✓" else "समीक्षा जारी है ⏳"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (report.status == "resolved") StatusApproved else StatusPending
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDonationSheet) {
        DonationBottomSheet(
            onDismissRequest = { showDonationSheet = false }
        )
    }
}
