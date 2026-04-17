package com.alexsiri7.unreminder.ui.location

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    existingLabel: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: MapPickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) { viewModel.initialize(existingLabel) }

    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (existingLabel == null) "Add location" else "Edit location") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        sheetPeekHeight = 200.dp,
        sheetContent = {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Home, Gym, Office") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Radius: ${uiState.radiusM.toInt()} m",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.radiusM,
                    onValueChange = viewModel::updateRadius,
                    valueRange = 50f..500f,
                    steps = 89,  // 90 positions at 5 m increments (50, 55, ..., 500)
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { viewModel.save(onComplete = onNavigateBack) },
                    enabled = uiState.name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
                Text(
                    "\u00a9 OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    ) { paddingValues ->
        if (uiState.centerReady) {
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().load(
                        ctx,
                        ctx.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                    )
                    MapView(ctx).also { mv ->
                        mapViewRef.value = mv
                        mv.setTileSource(TileSourceFactory.MAPNIK)
                        mv.setMultiTouchControls(true)
                        mv.controller.setZoom(15.0)
                        mv.controller.setCenter(
                            GeoPoint(uiState.initialCenterLat, uiState.initialCenterLng)
                        )
                        val marker = Marker(mv)
                        marker.position = GeoPoint(uiState.lat, uiState.lng)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.isDraggable = true
                        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker) {}
                            override fun onMarkerDragEnd(marker: Marker) {
                                viewModel.updatePin(
                                    marker.position.latitude,
                                    marker.position.longitude
                                )
                            }
                            override fun onMarkerDragStart(marker: Marker) {}
                        })
                        mv.overlays.add(marker)
                        mv.overlays.add(object : org.osmdroid.views.overlay.Overlay() {
                            override fun onLongPress(
                                e: android.view.MotionEvent,
                                mapView: MapView
                            ): Boolean {
                                val projection = mapView.projection
                                val gp = projection.fromPixels(
                                    e.x.toInt(),
                                    e.y.toInt()
                                ) as GeoPoint
                                marker.position = gp
                                viewModel.updatePin(gp.latitude, gp.longitude)
                                mapView.invalidate()
                                return true
                            }
                        })
                    }
                },
                update = { mv ->
                    mv.overlays.removeIf { it is Polygon }
                    val circle = Polygon(mv)
                    circle.points = Polygon.pointsAsCircle(
                        GeoPoint(uiState.lat, uiState.lng),
                        uiState.radiusM.toDouble()
                    )
                    circle.fillColor = 0x220000FF           // ARGB: alpha=0x22 (~13% opaque), blue
                    circle.strokeColor = 0xFF0000FF.toInt() // ARGB: fully opaque blue
                    circle.strokeWidth = 2f
                    mv.overlays.add(0, circle)
                    mv.invalidate()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef.value?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.onDetach()
        }
    }
}
