package com.pdfreader.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// --- IMPORTS FOR ZOOM/PAN AND DOUBLE TAP ---
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures // <--- NEW IMPORT
import androidx.compose.ui.input.pointer.pointerInput // <--- NEW IMPORT
// --- END IMPORTS ---

@Composable
fun PDFViewerScreen(pdfUri: Uri?) {
    val context = LocalContext.current

    val pdfFile = remember(pdfUri) {
        when {
            pdfUri != null -> getPdfFileFromUri(context, pdfUri)
            else -> getPdfFileFromAssets(context, "sample.pdf") // Default to sample.pdf
        }
    }

    val pdfRendererState = remember { mutableStateOf<PdfRenderer?>(null) }
    val fileDescriptorState = remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(pdfFile) {
        var newRenderer: PdfRenderer? = null
        var newFileDescriptor: ParcelFileDescriptor? = null

        if (pdfFile != null) {
            try {
                newFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                newRenderer = PdfRenderer(newFileDescriptor)
                
                pdfRendererState.value = newRenderer
                fileDescriptorState.value = newFileDescriptor

            } catch (e: Exception) {
                e.printStackTrace()
                pdfRendererState.value = null
                fileDescriptorState.value = null
            }
        } else {
            pdfRendererState.value = null
            fileDescriptorState.value = null
        }

        onDispose {
            pdfRendererState.value?.close()
            fileDescriptorState.value?.close()
            pdfRendererState.value = null
            fileDescriptorState.value = null
        }
    }

    val renderer = pdfRendererState.value
    val pageCount = renderer?.pageCount ?: 0
    var pageIndex by remember { mutableStateOf(0) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, panChange, rotationChange ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f) // Limit zoom from 0.5x to 5x
        offset += panChange
        // rotationChange is ignored for PDF viewing
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (renderer != null && pageCount > 0) {
            LaunchedEffect(pageCount) {
                if (pageIndex >= pageCount) {
                    pageIndex = pageCount - 1
                }
            }

            // Render the current page to a Bitmap (higher quality)
            val bitmap = remember(pageIndex, renderer) {
                val page = renderer.openPage(pageIndex)
                val scaleFactor = 3f 
                val bmp = Bitmap.createBitmap(
                    (page.width * scaleFactor).toInt(),
                    (page.height * scaleFactor).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            }

            // Display the rendered PDF page image with zoom/pan capabilities
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer( // Apply zoom and pan here
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = state) // Attach transformable modifier for pinch/pan gestures
                    .pointerInput(Unit) { // <--- ADDED FOR DOUBLE TAP ZOOM
                        detectTapGestures(
                            onDoubleTap = { tapOffset -> // 'tapOffset' is the position of the double tap
                                scale = if (scale == 1f) 2f else 1f // Toggle between 1x and 2x zoom
                                offset = Offset.Zero // Reset pan to center
                                // For more advanced double tap zoom, you could calculate a pan offset
                                // to center the exact point that was double-tapped.
                            }
                        )
                    }
            )

            // Navigation controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        pageIndex = (pageIndex - 1).coerceAtLeast(0) 
                        // Reset zoom/pan when changing pages
                        scale = 1f 
                        offset = Offset.Zero
                    },
                    enabled = pageIndex > 0
                ) { Text("Previous") }
                
                Text(text = "Page ${pageIndex + 1} / $pageCount")
                
                Button(
                    onClick = { 
                        pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1)
                        // Reset zoom/pan when changing pages
                        scale = 1f
                        offset = Offset.Zero
                    },
                    enabled = pageIndex < pageCount - 1
                ) { Text("Next") }
            }
        } else {
            Text(
                text = "PDF not found or could not be opened.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// Utility function to get PDF File from assets folder
fun getPdfFileFromAssets(context: android.content.Context, assetName: String): File? {
    val file = File(context.cacheDir, assetName)
    if (!file.exists()) {
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    return file
}

// Utility function to get PDF File from a content Uri
fun getPdfFileFromUri(context: android.content.Context, uri: Uri): File? {
    return try {
        val file = File(context.cacheDir, "shared.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
