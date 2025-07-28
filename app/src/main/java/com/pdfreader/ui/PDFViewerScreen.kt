package com.pdfreader.ui

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

    // Open PdfRenderer and manage its lifecycle
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    LaunchedEffect(pdfFile) {
        renderer?.close() // close previous renderer if any
        renderer = pdfFile?.let {
            try {
                ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)?.let { pfd ->
                    PdfRenderer(pfd)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    DisposableEffect(renderer) {
        onDispose {
            renderer?.close()
        }
    }

    var pageIndex by remember { mutableStateOf(0) }
    val pageCount = renderer?.pageCount ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        if (renderer != null && pageCount > 0) {
            // FIX STARTS HERE: Create a local, immutable copy
            val currentRenderer = renderer!! // We can use !! here because we just checked it's not null
                                            // Or use `requireNotNull` for a more explicit check and error
                                            // val currentRenderer = requireNotNull(renderer) { "Renderer should not be null here" }

            val bitmap = remember(pageIndex, currentRenderer) { // Use currentRenderer here
                val page = currentRenderer.openPage(pageIndex) // Use currentRenderer
                val bmp = android.graphics.Bitmap.createBitmap(
                    page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888
                )
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                    enabled = pageIndex > 0
                ) { Text("Previous") }
                Text(text = "Page ${pageIndex + 1} / $pageCount")
                Button(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                    enabled = pageIndex < pageCount - 1
                ) { Text("Next") }
            }
        } else {
            Text("PDF not found or could not be opened.", modifier = Modifier.padding(16.dp))
        }
    }
}

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
        null
    }
}
