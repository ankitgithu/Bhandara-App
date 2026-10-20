package com.aistudio.bhandara.gwlmbd.ui.screens.meme

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.bhandara.gwlmbd.data.model.Meme
import com.aistudio.bhandara.gwlmbd.data.model.User
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemeScreen(
    currentUser: User?,
    repository: BhandaraRepository,
    onBackClick: () -> Unit,
    onMemeAddedSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var memeText by remember { mutableStateOf("") }
    var selectedCity by remember { mutableStateOf("all") }
    var selectedCategory by remember { mutableStateOf("Local") }
    var authorName by remember { mutableStateOf(currentUser?.name ?: "") }
    var imageUrl by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val cities = listOf(
        "all" to "सभी (All)",
        "gwalior" to "ग्वालियर",
        "morena" to "मुरैना",
        "bhind" to "भिंड"
    )

    val categories = listOf(
        "Local" to "स्थानीय अंचल",
        "Funny" to "मजेदार 😂",
        "Bhandara" to "भंडारा 😋",
        "Other" to "अन्य"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "नया Meme जोड़ें",
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
            // Header Hint Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SaffronOrange.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "😂",
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "ग्वालियर-चंबल अंचल के अनोखे और मजेदार स्थानीय मीम शेयर करें!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Meme Text (Required)
            OutlinedTextField(
                value = memeText,
                onValueChange = { memeText = it },
                label = { Text("Meme Text (मीम पंक्ति) *") },
                placeholder = { Text("जैसे: भंडारे को हमारा राष्ट्रीय भोजन घोषित किया जाना चाहिए 😂") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeepRed,
                    focusedLabelColor = DeepRed
                )
            )

            // City Selection (Optional, default: all)
            Text(
                text = "शहर चुनें (City)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cities.forEach { (key, label) ->
                    val isSelected = selectedCity == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCity = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Category Selection
            Text(
                text = "श्रेणी (Category)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { (key, label) ->
                    val isSelected = selectedCategory == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SaffronOrange,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Author Name (Optional)
            OutlinedTextField(
                value = authorName,
                onValueChange = { authorName = it },
                label = { Text("आपका नाम / लेखक (वैकल्पिक)") },
                placeholder = { Text("अनाम भक्त") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Image URL (Optional)
            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text("Image URL (वैकल्पिक)") },
                placeholder = { Text("https://example.com/meme.jpg (यदि कोई हो)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Error display if any
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button with double-tap prevention & loading state
            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    val trimmedText = memeText.trim()
                    if (trimmedText.isBlank()) {
                        Toast.makeText(context, "कृपया Meme का टेक्स्ट दर्ज करें", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
                    errorMessage = null

                    scope.launch {
                        try {
                            val newMeme = Meme(
                                text = trimmedText,
                                city = selectedCity,
                                category = selectedCategory,
                                active = true,
                                isActive = true,
                                createdBy = if (authorName.isNotBlank()) authorName.trim() else (currentUser?.name ?: "अनाम भक्त"),
                                authorName = authorName.trim(),
                                imageUrl = imageUrl.trim(),
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )

                            // Save to Firebase / cache safely
                            repository.addMeme(newMeme, currentUser?.auth0UserId)

                            isSubmitting = false
                            showSuccessDialog = true
                        } catch (e: Exception) {
                            isSubmitting = false
                            errorMessage = "कुछ समस्या आ गई है। कृपया दोबारा कोशिश करें।"
                            Toast.makeText(context, "कुछ समस्या आ गई है। कृपया दोबारा कोशिश करें।", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "जोड़ रहे हैं... ⏳",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Icon(imageVector = Icons.Default.AddComment, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Meme जोड़ें 😂",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Success State Dialog with Celebration Animation
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onMemeAddedSuccess()
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Meme successfully जुड़ गया 😂",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(StatusApproved.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = StatusApproved,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "आपका मीम तुरंत होम स्क्रीन और मीम सूची में दिखने लगा है!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onMemeAddedSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("बहुत बढ़िया 👍")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}
