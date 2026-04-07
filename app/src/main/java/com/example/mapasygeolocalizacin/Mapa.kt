package com.example.mapasygeolocalizacin

import android.Manifest
import android.location.Geocoder
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mapasygeolocalizacin.api.RetrofitClient
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RouteMapScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Configuración base de OSMDroid (Lógica intacta)
    Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
    Configuration.getInstance().userAgentValue = context.packageName

    // Estados de la UI (Lógica intacta)
    var homeAddressInput by remember { mutableStateOf("") }
    var homeLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var routePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var mapViewReference by remember { mutableStateOf<MapView?>(null) }

    // ¡IMPORTANTE! Tu API Key original
    val apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImQxYmQ5MzVmZTc2YzRhMmJiYTE2OWExZGM5MmU1NjJmIiwiaCI6Im11cm11cjY0In0="

    // Permisos (Lógica intacta)
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    LaunchedEffect(Unit) {
        if (!locationPermissions.allPermissionsGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        if (locationPermissions.allPermissionsGranted) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = GeoPoint(location.latitude, location.longitude)
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    // Función para centrar el mapa desde el botón flotante
    fun centerMapOn(point: GeoPoint) {
        mapViewReference?.controller?.animateTo(point)
        mapViewReference?.controller?.setZoom(15.0)
    }

    // Función principal (Lógica intacta)
    fun findAddressAndRoute(address: String) {
        if (address.isBlank()) {
            Toast.makeText(context, "Por favor, ingresa una dirección", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentLocation == null) {
            Toast.makeText(context, "Aún buscando tu ubicación actual GPS... Espera un momento.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(context, "Buscando dirección...", Toast.LENGTH_SHORT).show()

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName(address, 1)

            if (addresses.isNullOrEmpty()) {
                Toast.makeText(context, "No se encontró la dirección en el mapa", Toast.LENGTH_SHORT).show()
                return
            }

            val addr = addresses[0]
            homeLocation = GeoPoint(addr.latitude, addr.longitude)
            Toast.makeText(context, "Casa encontrada. Calculando ruta...", Toast.LENGTH_SHORT).show()

            coroutineScope.launch {
                try {
                    val startStr = "${currentLocation!!.longitude},${currentLocation!!.latitude}"
                    val endStr = "${homeLocation!!.longitude},${homeLocation!!.latitude}"

                    val response = RetrofitClient.api.getRoute(apiKey, startStr, endStr)

                    val coords = response.features.firstOrNull()?.geometry?.coordinates
                    if (coords != null) {
                        routePoints = coords.map { GeoPoint(it[1], it[0]) }
                        Toast.makeText(context, "¡Ruta trazada exitosamente!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No se pudo trazar una ruta por calles", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error de red o API Key inválida", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error en el Geocoder", Toast.LENGTH_SHORT).show()
        }
    }

    // --- NUEVO DISEÑO DE UI ---
    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // Botón Flotante para centrar en tu ubicación
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { currentLocation?.let { centerMapOn(it) } },
                    containerColor = Color(0xFF006064), // Cyan oscuro
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Mi ubicación")
                }
            },
            // Panel inferior para la búsqueda
            bottomBar = {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    elevation = CardDefaults.cardElevation(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Configurar Destino",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF006064)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = homeAddressInput,
                            onValueChange = { homeAddressInput = it },
                            label = { Text("Dirección de tu casa") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = Color.Gray) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { findAddressAndRoute(homeAddressInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006064))
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Trazar Ruta", fontSize = 16.sp)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                // El mapa de fondo
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            mapViewReference = this // Guardar referencia para el botón flotante
                        }
                    },
                    update = { mapView ->
                        mapView.overlays.clear()

                        // Marcador de la Casa
                        homeLocation?.let { loc ->
                            val homeMarker = Marker(mapView).apply {
                                position = loc
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Casa"
                            }
                            mapView.overlays.add(homeMarker)
                        }

                        // Marcador de tu Ubicación Actual
                        currentLocation?.let { loc ->
                            val currentMarker = Marker(mapView).apply {
                                position = loc
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Mi Ubicación"
                            }
                            mapView.overlays.add(currentMarker)

                            // Solo centramos en tu ubicación si aún no hay una ruta dibujada
                            if (routePoints.isEmpty()) {
                                mapView.controller.animateTo(loc)
                            }
                        }

                        // Polilínea de la ruta
                        if (routePoints.isNotEmpty()) {
                            val polyline = Polyline(mapView).apply {
                                setPoints(routePoints)
                                outlinePaint.color = android.graphics.Color.rgb(0, 151, 167) // Cian medio
                                outlinePaint.strokeWidth = 12f
                            }
                            mapView.overlays.add(polyline)

                            // Centrar la cámara un poco hacia la ruta trazada
                            mapView.controller.animateTo(routePoints.first())
                            mapView.controller.setZoom(14.5)
                        }

                        mapView.invalidate()
                    }
                )
            }
        }
    }
}