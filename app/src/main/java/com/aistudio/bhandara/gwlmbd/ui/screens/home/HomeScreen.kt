package com.aistudio.bhandara.gwlmbd.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.bhandara.gwlmbd.data.model.Bhandara
import com.aistudio.bhandara.gwlmbd.data.model.Meme
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.*
import com.aistudio.bhandara.gwlmbd.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    selectedCity: String,
    onCityChangeRequested: () -> Unit,
    onCityQuickFilter: (String) -> Unit,
    onSearchClick: () -> Unit,
    onBhandaraClick: (String) -> Unit,
    onAddBhandaraClick: () -> Unit,
    onAddMemeClick: (() -> Unit)? = null,
    isGuest: Boolean,
    repository: BhandaraRepository,
    currentTheme: String = "system",
    onThemeToggle: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var bhandaras by remember { mutableStateOf<List<Bhandara>>(repository.getCachedBhandarasForCity(selectedCity)) }
    var memes by remember { mutableStateOf<List<Meme>>(emptyList()) }
    var isLoading by remember { mutableStateOf(bhandaras.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLoginPrompt by remember { mutableStateOf(false) }
    var showDonationSheet by remember { mutableStateOf(false) }

    val tabs = listOf(
        "आज के भंडारे",
        "आने वाले भंडारे",
        "आसपास के भंडारे",
        "लोकप्रिय भंडारे",
        "हाल ही में जोड़े गए"
    )

    fun loadData() {
        scope.launch {
            val cached = repository.getCachedBhandarasForCity(selectedCity)
            if (cached.isNotEmpty()) {
                bhandaras = cached
                isLoading = false
            } else if (bhandaras.isEmpty()) {
                isLoading = true
            }
            errorMessage = null
            try {
                val fetched = repository.getActiveBhandaras(selectedCity)
                bhandaras = fetched
                memes = repository.getMemes(selectedCity)
            } catch (e: Exception) {
                if (bhandaras.isEmpty()) {
                    errorMessage = "भंडारे की जानकारी लोड नहीं हो पाई।"
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedCity) {
        val cached = repository.getCachedBhandarasForCity(selectedCity)
        if (cached.isNotEmpty()) {
            bhandaras = cached
            isLoading = false
        } else if (bhandaras.isEmpty()) {
            isLoading = true
        }
        try {
            repository.listenToActiveBhandaras(selectedCity).collect { updatedList ->
                bhandaras = updatedList
                isLoading = false
                errorMessage = null
            }
        } catch (_: Exception) {}
    }

    // Filter bhandaras according to selected tab
    val displayedBhandaras = remember(bhandaras, selectedTab) {
        when (selectedTab) {
            0 -> bhandaras.filter { !it.isExpired() } // Today / active
            1 -> bhandaras.filter { !it.isExpired() } // Upcoming
            2 -> bhandaras.filter { it.latitude != 0.0 } // Nearby (with GPS location)
            3 -> bhandaras.sortedByDescending { it.createdAt } // Popular / active
            4 -> bhandaras.sortedByDescending { it.createdAt } // Recently added
            else -> bhandaras
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddBhandaraClick,
                containerColor = DeepRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "भंडारा जोड़ें +",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 16:9 Banner Slider at the very top of Home Screen
            item(key = "home_top_banner_slider") {
                HomeBannerSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }

            // Top Section: City, Greeting, Donate & Theme Toggle
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "आज कहाँ भंडारा है?",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "राम-राम सा! चंबल के पावन भंडारे 🙏",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // "Donate ❤️" Button
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = DeepRed.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { showDonationSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Donate ❤️",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = DeepRed
                                    )
                                }
                            }

                            // Theme Toggle Button (☀️ / 🌙)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { onThemeToggle() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (currentTheme == "dark") Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = "Toggle Theme",
                                        tint = if (currentTheme == "dark") WarmGold else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // City selector badge
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SaffronOrange.copy(alpha = 0.12f),
                                modifier = Modifier.clickable { onCityChangeRequested() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = SaffronOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = when (selectedCity.lowercase()) {
                                            "gwalior" -> "ग्वालियर"
                                            "morena" -> "मुरैना"
                                            "bhind" -> "भिंड"
                                            else -> "ग्वालियर"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = SaffronOrange
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search Bar Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onSearchClick() }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "भंडारा खोजो (नाम, इलाका, मंदिर)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick City Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableCities.forEach { city ->
                            val isSelected = selectedCity.equals(city.key, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCityQuickFilter(city.key) },
                                label = {
                                    Text(
                                        text = city.hindiName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeepRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Local Meme Banner (Memes can only be added by Admin via Admin Panel)
                    MemeBanner(
                        memes = memes,
                        onAddMemeClick = null
                    )
                }
            }

            // Section Tabs
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs.size) { index ->
                        val isSelected = selectedTab == index
                        val bg = if (isSelected) DeepRed else MaterialTheme.colorScheme.surface
                        val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(bg)
                                .clickable { selectedTab = index }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tabs[index],
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }

            // Bhandaras List with Smooth City Transition
            if (isLoading && displayedBhandaras.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = DeepRed)
                    }
                }
            } else if (errorMessage != null && displayedBhandaras.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "भंडारे की जानकारी लोड नहीं हो पाई।",
                        description = "कृपया इंटरनेट कनेक्शन चेक करें और दोबारा कोशिश करें।",
                        buttonText = "फिर से कोशिश करें",
                        onRetry = { loadData() }
                    )
                }
            } else {
                item {
                    AnimatedContent(
                        targetState = Pair(selectedCity, displayedBhandaras),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 250)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 150))
                        },
                        label = "CitySwitchContent"
                    ) { (_, currentDisplayed) ->
                        if (currentDisplayed.isEmpty()) {
                            EmptyStateView(
                                title = "अभी इधर कोई भंडारा नहीं मिला 😋",
                                description = "अपने इलाके में कोई भंडारा होने वाला है तो नीचे दिए 'भंडारा जोड़ें +' बटन से जोड़ें!",
                                onRetry = { loadData() }
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                currentDisplayed.forEach { bhandara ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        BhandaraCard(
                                            bhandara = bhandara,
                                            onClick = { onBhandaraClick(bhandara.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLoginPrompt) {
        LoginPromptDialog(
            onLoginClick = {
                showLoginPrompt = false
                onAddBhandaraClick()
            },
            onDismiss = { showLoginPrompt = false }
        )
    }

    if (showDonationSheet) {
        DonationBottomSheet(
            onDismissRequest = { showDonationSheet = false }
        )
    }
}
