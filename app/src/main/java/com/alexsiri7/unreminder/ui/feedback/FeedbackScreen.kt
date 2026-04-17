package com.alexsiri7.unreminder.ui.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    screenshotPath: String,
    onNavigateBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSubmitted) {
        if (uiState.isSubmitted) onNavigateBack()
    }

    LaunchedEffect(uiState.tokenMissing) {
        if (uiState.tokenMissing) {
            snackbarHostState.showSnackbar("Feedback token not configured — copy logs to clipboard instead.")
            viewModel.consumeTokenMissing()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val screenshotBitmap = remember(screenshotPath) {
        val file = File(screenshotPath)
        if (file.exists()) android.graphics.BitmapFactory.decodeFile(screenshotPath) else null
    }

    val paletteColors = listOf(Color.Red, Color.Yellow, Color(0xFF4CAF50))
    var currentPathPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Send Feedback") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (screenshotBitmap != null) {
                    Image(
                        bitmap = screenshotBitmap.asImageBitmap(),
                        contentDescription = "Screenshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(uiState.isErasing, uiState.currentColor) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPathPoints = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    currentPathPoints = currentPathPoints + change.position
                                },
                                onDragEnd = {
                                    if (currentPathPoints.size > 1) {
                                        if (uiState.isErasing) {
                                            viewModel.clearPaths()
                                        } else {
                                            viewModel.addPath(
                                                DrawPath(
                                                    points = currentPathPoints,
                                                    color = uiState.currentColor,
                                                    canvasWidth = canvasSize.width,
                                                    canvasHeight = canvasSize.height
                                                )
                                            )
                                        }
                                        currentPathPoints = emptyList()
                                    }
                                }
                            )
                        }
                ) {
                    canvasSize = size
                    uiState.paths.forEach { path ->
                        path.points.zipWithNext { a, b ->
                            drawLine(
                                color = path.color,
                                start = a,
                                end = b,
                                strokeWidth = path.strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    currentPathPoints.zipWithNext { a, b ->
                        drawLine(
                            color = if (uiState.isErasing) Color.White else uiState.currentColor,
                            start = a,
                            end = b,
                            strokeWidth = 8f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                paletteColors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .then(
                                if (uiState.currentColor == color && !uiState.isErasing)
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { viewModel.selectColor(color) }
                    )
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { viewModel.toggleEraser() }) {
                    Text(if (uiState.isErasing) "Drawing" else "Eraser")
                }
                OutlinedButton(onClick = { viewModel.clearPaths() }) {
                    Text("Clear")
                }
            }

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("What happened?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            Button(
                onClick = { viewModel.submit(screenshotPath) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSubmitting
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send")
                }
            }
        }
    }
}
