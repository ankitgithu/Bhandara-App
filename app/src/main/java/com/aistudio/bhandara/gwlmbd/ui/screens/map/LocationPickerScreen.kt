package com.aistudio.bhandara.gwlmbd.ui.screens.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aistudio.bhandara.gwlmbd.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    initialLatitude: Double = 26.2183, // Gwalior by default
    initialLongitude: Double = 78.1828,
    onLocationConfirmed: (lat: Double, lng: Double, address: String, area: String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val defaultLocation = remember {
        val lat = if (initialLatitude != 0.0) initialLatitude else 26.2183
        val lng = if (initialLongitude != 0.0) initialLongitude else 78.1828
        LatLng(lat, lng)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 17f)
    }

    // DEFAULT MAP TYPE: SATELLITE VIEW (as required)
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }
    var currentAddress by remember { mutableStateOf("स्थान लोड हो रहा है...") }
    var currentArea by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }

    // Geocode center location as user moves the map underneath the pin
    LaunchedEffect(cameraPositionState.position.target) {
        val target = cameraPositionState.position.target
        isGeocoding = true
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.forLanguageTag("hi"))
                val addresses = geocoder.getFromLocation(target.latitude, target.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    currentAddress = addr.getAddressLine(0) ?: "${String.format(Locale.US, "%.5f, %.5f", target.latitude, target.longitude)}"
                    currentArea = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: ""
                } else {
                    currentAddress = "स्थान: ${String.format(Locale.US, "%.5f, %.5f", target.latitude, target.longitude)}"
                    currentArea = ""
                }
            } catch (e: Exception) {
                currentAddress = "स्थान: ${String.format(Locale.US, "%.5f, %.5f", target.latitude, target.longitude)}"
                currentArea = ""
            } finally {
                isGeocoding = false
            }
        }
    }

    // Permission launcher for "मेरी Location"
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(loc.latitude, loc.longitude),
                                    18f
                                )
                            )
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Handled
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen Native Google Map
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = mapType,
                isMyLocationEnabled = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                rotationGesturesEnabled = true,
                tiltGesturesEnabled = true,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true,
                myLocationButtonEnabled = false
            )
        )

        // Center Fixed Pin Indicator (User moves map underneath marker)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 40.dp), // offset slightly so tip of pin touches exact center
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Target Pin",
                    tint = DeepRed,
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(10.dp, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
        }

        // Top App Bar with Back and 3-Way Map Switcher (Satellite, Hybrid, Normal)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.size(46.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // 3-Way Map Mode Toggle: Satellite | Hybrid | Normal
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Satellite
                    FilledTonalButton(
                        onClick = { mapType = MapType.SATELLITE },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (mapType == MapType.SATELLITE) DeepRed else Color.Transparent,
                            contentColor = if (mapType == MapType.SATELLITE) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "🛰️ सैटेलाइट", style = MaterialTheme.typography.labelSmall)
                    }

                    // Hybrid
                    FilledTonalButton(
                        onClick = { mapType = MapType.HYBRID },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (mapType == MapType.HYBRID) DeepRed else Color.Transparent,
                            contentColor = if (mapType == MapType.HYBRID) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "🌐 हाइब्रिड", style = MaterialTheme.typography.labelSmall)
                    }

                    // Normal
                    FilledTonalButton(
                        onClick = { mapType = MapType.NORMAL },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (mapType == MapType.NORMAL) DeepRed else Color.Transparent,
                            contentColor = if (mapType == MapType.NORMAL) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "🗺️ सामान्य", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Floating Action Buttons on Right: My Location, Zoom In, Zoom Out
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // "मेरी Location" Button
            FloatingActionButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                        fusedClient.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(loc.latitude, loc.longitude),
                                            18f
                                        )
                                    )
                                }
                            }
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = SaffronOrange,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "मेरी Location")
            }

            // Zoom In
            FloatingActionButton(
                onClick = {
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out
            FloatingActionButton(
                onClick = {
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }

        // Bottom Confirmation Card (Selected Location / Address / "यह Location चुनें")
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = DeepRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Selected Location (चुना गया स्थान)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = currentAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val target = cameraPositionState.position.target
                        onLocationConfirmed(target.latitude, target.longitude, currentAddress, currentArea)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepRed)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "यह Location चुनें",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
