package com.aistudio.bhandara.gwlmbd.ui.screens.saved

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.bhandara.gwlmbd.data.model.Bhandara
import com.aistudio.bhandara.gwlmbd.data.model.User
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.BhandaraCard
import com.aistudio.bhandara.gwlmbd.ui.components.EmptyStateView
import com.aistudio.bhandara.gwlmbd.ui.theme.DeepRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedBhandarasScreen(
    currentUser: User?,
    isGuest: Boolean,
    onLoginClick: () -> Unit,
    onBhandaraClick: (String) -> Unit,
    repository: BhandaraRepository
) {
    val scope = rememberCoroutineScope()
    var savedList by remember { mutableStateOf<List<Bhandara>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            isLoading = true
            savedList = repository.getSavedBhandaras(currentUser.auth0UserId)
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "सेव किए गए भंडारे",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                    description = "अपने पसंदीदा भंडारों को बुकमार्क करने के लिए Google से लॉगिन करें।",
                    icon = Icons.Default.Lock,
                    onRetry = onLoginClick
                )
            }
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = DeepRed)
            }
        } else if (savedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    title = "कोई सुरक्षित भंडारा नहीं है",
                    description = "होम स्क्रीन से किसी भी भंडारे के विवरण में जाकर बुकमार्क आइकन दबाएँ!",
                    icon = Icons.Default.BookmarkBorder
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedList) { bhandara ->
                    BhandaraCard(
                        bhandara = bhandara,
                        onClick = { onBhandaraClick(bhandara.id) }
                    )
                }
            }
        }
    }
}
