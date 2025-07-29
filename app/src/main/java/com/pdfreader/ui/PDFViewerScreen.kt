package com.pdfreader.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException // Import IOException for better error handling

// START: ADDED FOR ZOOM FEATURE IMPORTS
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
// END: ADDED FOR ZOOM FEATURE IMPORTS

@Composable
fun PDFViewerScreen(pdfUri: Uri?) {
    val context = LocalContext.current

    // Load PDF file from Uri or assets
    val pdfFile = remember(pdfUri) {
        when {
            pdfUri != null -> getPdfFileFromUri(context, pdfUri)
            else -> getPdfFileFromAssets(context, "sample.pdf")
        }
    }

    // --- CRASH FIX & RENDERER LIFECYCLE MANAGEMENT ---
    // Use a single DisposableEffect to manage PdfRenderer lifecycle
    val pdfRendererState = remember { mutableStateOf<PdfRenderer?>(null) }
    val fileDescriptorState = remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(pdfFile) {
        var newRenderer: PdfRenderer? = null
        var newFileDescriptor: ParcelFileDescriptor? = null

        if (pdfFile != null) {
            try {
                // Open ParcelFileDescriptor
                newFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                // Create PdfRenderer
                newRenderer = PdfRenderer(newFileDescriptor)
                
                // Update state variables
                pdfRendererState.value = newRenderer
                fileDescriptorState.value = newFileDescriptor

            } catch (e: Exception) {
                // Handle error opening PDF
                e.printStackTrace()
                pdfRendererState.value = null
                fileDescriptorState.value = null
            }
        } else {
            // If pdfFile is null, ensure renderer and descriptor are also null
            pdfRendererState.value = null
            fileDescriptorState.value = null
        }

        // Cleanup block for DisposableEffect
        onDispose {
            // Close resources safely and ensure they are nulled out
            pdfRendererState.value?.close()
            fileDescriptorState.value?.close()
            pdfRendererState.value = null
            fileDescriptorState.value = null
        }
    }

    val renderer = pdfRendererState.value // Use the value from the state
    val pageCount = renderer?.pageCount ?: 0
    var pageIndex by remember { mutableStateOf(0) }

    // START: ADDED FOR ZOOM FEATURE STATE
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, panChange, rotationChange ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f) // Limit zoom from 0.5x to 5x
        offset += panChange
        // rotationChange is ignored for PDF viewing
    }
    // END: ADDED FOR ZOOM FEATURE STATE

    Column(modifier = Modifier.fillMaxSize()) {
        if (renderer != null && pageCount > 0) {
            // Ensure pageIndex is always valid
            LaunchedEffect(pageCount) {
                if (pageIndex >= pageCount) {
                    pageIndex = pageCount - 1
                }
            }

            // --- QUALITY IMPROVEMENT ---
            // Render the current page to a Bitmap at a higher resolution
            val bitmap = remember(pageIndex, renderer) {
                // We use `renderer` directly here which is non-null due to the outer if-check
                val page = renderer.openPage(pageIndex)

                // Define a scaling factor for better quality
                val scaleFactor = 4f // You can adjust this: 2f for good, 3f for very good, 4f for excellent

                val bitmapWidth = (page.width * scaleFactor).toInt()
                val bitmapHeight = (page.height * scaleFactor).toInt()

                val bmp = Bitmap.createBitmap(
                    bitmapWidth,
                    bitmapHeight,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close() // Close the page after rendering!
                bmp
            }

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // START: ADDED FOR ZOOM FEATURE MODIFIERS
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = state)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scale = if (scale == 1f) 2f else 1f
                                offset = Offset.Zero
                            }
                        )
                    }
                    // END: ADDED FOR ZOOM FEATURE MODIFIERS
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        pageIndex = (pageIndex - 1).coerceAtLeast(0) 
                        // START: ADDED FOR ZOOM FEATURE RESET ON PAGE CHANGE
                        scale = 1f 
                        offset = Offset.Zero
                        // END: ADDED FOR ZOOM FEATURE RESET ON PAGE CHANGE
                    },
                    enabled = pageIndex > 0
                ) { Text("Previous") }
                Text(text = "Page ${pageIndex + 1} / $pageCount")
                Button(
                    onClick = { 
                        pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) 
                        // START: ADDED FOR ZOOM FEATURE RESET ON PAGE CHANGE
                        scale = 1f
                        offset = Offset.Zero
                        // END: ADDED FOR ZOOM FEATURE RESET ON PAGE CHANGE
                    },
                    enabled = pageIndex < pageCount - 1
                ) { Text("Next") }
            }
        } else {
            Text("PDF not found or could not be opened.", modifier = Modifier.padding(16.dp))
        }
    }
}

// Your existing utility functions remain the same
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
            e.printStackTrace() // Log the exception for debugging
            return null
        }
    }
    return file
}

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
        e.printStackTrace() // Log the exception for debugging
        return null
    }
}
