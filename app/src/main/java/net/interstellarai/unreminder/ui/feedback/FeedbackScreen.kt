package net.interstellarai.unreminder.ui.feedback

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val AnnotationRed = Color(0xFFE53935)
private val AnnotationYellow = Color(0xFFFDD835)
private val AnnotationGreen = Color(0xFF43A047)

data class StrokePath(val path: Path, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    screenshotBitmap: Bitmap?,
    onNavigateBack: () -> Unit,
    viewModel: FeedbackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(screenshotBitmap) {
        screenshotBitmap?.let { viewModel.setScreenshot(it) }
    }

    LaunchedEffect(uiState.submitted) {
        if (uiState.submitted) onNavigateBack()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val strokes = remember { mutableStateListOf<StrokePath>() }
    var currentColor by remember { mutableStateOf(AnnotationRed) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Feedback") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (screenshotBitmap != null) {
                val aspectRatio = screenshotBitmap.width.toFloat() / screenshotBitmap.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .aspectRatio(aspectRatio)
                        .clipToBounds()
                        .onSizeChanged { canvasSize = it }
                ) {
                    Image(
                        bitmap = screenshotBitmap.asImageBitmap(),
                        contentDescription = "Screenshot",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPath?.lineTo(change.position.x, change.position.y)
                                        // Create a new Path instance to trigger Compose recomposition —
                                        // Path has no structural equality, so mutating in place is
                                        // invisible to state tracking and the canvas would not redraw.
                                        currentPath = currentPath?.let { Path().apply { addPath(it) } }
                                    },
                                    onDragEnd = {
                                        currentPath?.let { strokes.add(StrokePath(it, currentColor)) }
                                        currentPath = null
                                    }
                                )
                            }
                    ) {
                        for (stroke in strokes) {
                            drawPath(stroke.path, stroke.color, style = Stroke(width = 6f))
                        }
                        currentPath?.let {
                            drawPath(it, currentColor, style = Stroke(width = 6f))
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(AnnotationRed, AnnotationYellow, AnnotationGreen).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (currentColor == color)
                                        Modifier
                                            .border(3.dp, Color.Black, CircleShape)
                                            .padding(2.dp)
                                            .border(3.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { currentColor = color }
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    TextButton(onClick = { strokes.clear() }) {
                        Text("Clear All")
                    }
                }
            }

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("What happened?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Local flag to disable the button immediately on click, before the ViewModel's
            // isSubmitting is set (which only happens once the coroutine starts executing).
            // This closes the double-tap race window between click and submit().
            var isLaunching by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    if (isLaunching) return@onClick
                    isLaunching = true
                    val bmpSnapshot = screenshotBitmap
                    val strokesSnapshot = strokes.toList()
                    val canvasSizeSnapshot = canvasSize
                    coroutineScope.launch(Dispatchers.Default) {
                        try {
                            val annotationBitmap = if (bmpSnapshot != null && canvasSizeSnapshot.width > 0) {
                                val annBitmap = Bitmap.createBitmap(bmpSnapshot.width, bmpSnapshot.height, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(annBitmap)
                                val scaleX = bmpSnapshot.width.toFloat() / canvasSizeSnapshot.width
                                val scaleY = bmpSnapshot.height.toFloat() / canvasSizeSnapshot.height
                                canvas.scale(scaleX, scaleY)
                                val paint = android.graphics.Paint().apply {
                                    style = android.graphics.Paint.Style.STROKE
                                    strokeWidth = 6f
                                    isAntiAlias = true
                                }
                                for (stroke in strokesSnapshot) {
                                    paint.color = stroke.color.toArgb()
                                    canvas.drawPath(stroke.path.asAndroidPath(), paint)
                                }
                                annBitmap
                            } else {
                                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                            }
                            viewModel.submit(annotationBitmap)
                        } catch (e: OutOfMemoryError) {
                            // Bitmap allocation failed before submit() was called — surface the
                            // error through the ViewModel so the snackbar flow fires normally.
                            viewModel.onAnnotationBitmapOom()
                        } finally {
                            isLaunching = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSubmitting && !isLaunching
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Send")
                }
            }
        }
    }
}
