package com.aistudio.bhandara.gwlmbd.ui.screens.add

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.bhandara.gwlmbd.data.model.User
import com.aistudio.bhandara.gwlmbd.data.repository.BhandaraRepository
import com.aistudio.bhandara.gwlmbd.ui.components.availableCities
import com.aistudio.bhandara.gwlmbd.ui.theme.*
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBhandaraScreen(
    viewModel: AddBhandaraViewModel,
    currentUser: User?,
    onBackClick: () -> Unit,
    onOpenMapPicker: (lat: Double, lng: Double) -> Unit,
    repository: BhandaraRepository,
    onSuccessSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUser) {
        viewModel.initUser(currentUser)
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "नया भंडारा जोड़ें",
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
            // Bhandara Name (Mandatory)
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("भंडारे का नाम * (जैसे: अचलेश्वर महादेव भंडारा)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeepRed,
                    focusedLabelColor = DeepRed
                )
            )

            // City Selection Chips (Mandatory)
            Text(
                text = "शहर चुनें *",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableCities.forEach { c ->
                    val isSelected = viewModel.city.equals(c.key, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.city = c.key },
                        label = { Text(c.hindiName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DeepRed,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Location Picker Section (OPTIONAL)
            val hasLocation = viewModel.latitude != 0.0 && viewModel.longitude != 0.0
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMapPicker(viewModel.latitude, viewModel.longitude) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasLocation) DeepRed.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (hasLocation) DeepRed else MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hasLocation) Icons.Default.LocationOn else Icons.Default.AddLocationAlt,
                                contentDescription = null,
                                tint = if (hasLocation) DeepRed else SaffronOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasLocation) "स्थान चुना गया ✓" else "नक्शे पर स्थान चुनें (वैकल्पिक)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (hasLocation) DeepRed else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (viewModel.address.isNotBlank()) {
                                viewModel.address
                            } else if (hasLocation) {
                                "${String.format(Locale.US, "%.5f", viewModel.latitude)}, ${String.format(Locale.US, "%.5f", viewModel.longitude)}"
                            } else {
                                "सटीक मैप लोकेशन हेतु टैप करें (सैटेलाइट / नक्शा)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Open Map",
                        tint = DeepRed
                    )
                }
            }

            // Area / Locality
            OutlinedTextField(
                value = viewModel.area,
                onValueChange = { viewModel.area = it },
                label = { Text("इलाका / मोहल्ला (जैसे: महाराज बाड़ा, किला गेट, मुरार)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeepRed,
                    focusedLabelColor = DeepRed
                )
            )

            // Landmark
            OutlinedTextField(
                value = viewModel.landmark,
                onValueChange = { viewModel.landmark = it },
                label = { Text("पहचान / नज़दीकी स्थान (जैसे: हनुमान मंदिर के सामने)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Description
            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text("विवरण (किस उपलक्ष्य में भंडारा है, प्रसादी विवरण)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                shape = RoundedCornerShape(12.dp)
            )

            // Date Picker Row (Mandatory)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val c = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    viewModel.date = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                },
                                c.get(Calendar.YEAR),
                                c.get(Calendar.MONTH),
                                c.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("दिनांक *", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = DeepRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = viewModel.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Start Time (Mandatory)
                OutlinedCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    val ampm = if (h >= 12) "PM" else "AM"
                                    val displayH = if (h > 12) h - 12 else if (h == 0) 12 else h
                                    viewModel.startTime = String.format(Locale.US, "%02d:%02d %s", displayH, min, ampm)
                                },
                                11,
                                0,
                                false
                            ).show()
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("शुरू समय *", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = SaffronOrange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = viewModel.startTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // End Time
                OutlinedCard(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            TimePickerDialog(
                                context,
                                { _, h, min ->
                                    val ampm = if (h >= 12) "PM" else "AM"
                                    val displayH = if (h > 12) h - 12 else if (h == 0) 12 else h
                                    viewModel.endTime = String.format(Locale.US, "%02d:%02d %s", displayH, min, ampm)
                                },
                                16,
                                0,
                                false
                            ).show()
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("समाप्ति समय", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = WarmGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = viewModel.endTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Contact Info (Optional)
            Text(
                text = "संपर्क जानकारी (वैकल्पिक)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = viewModel.contactName,
                onValueChange = { viewModel.contactName = it },
                label = { Text("आयोजक का नाम (वैकल्पिक)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = viewModel.contactPhone,
                onValueChange = { viewModel.contactPhone = it },
                label = { Text("मोबाइल नंबर (वैकल्पिक - कॉल करने हेतु)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button
            Button(
                onClick = {
                    scope.launch {
                        viewModel.submit(repository, currentUser)
                    }
                },
                enabled = !viewModel.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
            ) {
                if (viewModel.isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "प्रकाशित हो रहा है... ⏳",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = "भंडारा प्रकाशित करें 🚩",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    if (viewModel.showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.resetForm()
                onSuccessSubmitted()
            },
            title = {
                Text(
                    text = "जय श्री राम! 🙏",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "आपका भंडारा सफलतापूर्वक प्रकाशित हो गया है और अब सभी भक्तों को सूची में दिखाई दे रहा है।",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetForm()
                        onSuccessSubmitted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
                ) {
                    Text("ठीक है")
                }
            }
        )
    }
}
