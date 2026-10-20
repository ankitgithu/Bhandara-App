package com.aistudio.bhandara.gwlmbd.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aistudio.bhandara.gwlmbd.data.model.Bhandara
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.BhandaraCard
import com.aistudio.bhandara.gwlmbd.ui.components.EmptyStateView
import com.aistudio.bhandara.gwlmbd.ui.theme.DeepRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    currentCity: String,
    onBackClick: () -> Unit,
    onBhandaraClick: (String) -> Unit,
    repository: BhandaraRepository
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Bhandara>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedCityFilter by remember { mutableStateOf(currentCity) }

    fun performSearch(query: String, city: String) {
        scope.launch {
            isSearching = true
            searchResults = repository.searchBhandaras(query, city)
            isSearching = false
        }
    }

    LaunchedEffect(searchQuery, selectedCityFilter) {
        performSearch(searchQuery, selectedCityFilter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "भंडारा खोजो (नाम, मेला, इलाका)...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = DeepRed)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepRed,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
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
        ) {
            // City filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cityOptions = listOf(
                    "all" to "सभी शहर",
                    "gwalior" to "ग्वालियर",
                    "morena" to "मुरैना",
                    "bhind" to "भिंड"
                )

                cityOptions.forEach { (key, label) ->
                    val isSelected = selectedCityFilter.equals(key, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCityFilter = key },
                        label = { Text(text = label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DeepRed)
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = "अभी इधर कोई भंडारा नहीं मिला 😅",
                        description = "कुछ और नाम, इलाका या शहर बदलकर खोजें!"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchResults) { bhandara ->
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
