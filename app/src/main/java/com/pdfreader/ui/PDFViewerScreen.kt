package com.pdfreader.ui

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

@Composable
fun PDFViewerScreen(context: Context, pdfUri: Uri?) {
    val pdfFile = remember(pdfUri) {
        when {
            pdfUri != null -> getPdfFileFromUri(context, pdfUri)
            else -> getPdfFileFromAssets(context, "sample.pdf")
        }
    }
    val renderer = remember(pdfFile) {
        pdfFile?.let {
            ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY)?.let { pfd ->
                PdfRenderer(pfd)
            }
        }
    }

    var pageIndex by remember { mutableStateOf(0) }
    val pageCount = renderer?.pageCount ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        if (renderer != null && pageCount > 0) {
            val page = renderer.openPage(pageIndex)
            val bitmap = android.graphics.Bitmap.createBitmap(
                page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }) { Text("Previous") }
                Text(text = "Page ${pageIndex + 1} / $pageCount")
                Button(onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }) { Text("Next") }
            }
        } else {
            Text("PDF not found or could not be opened.", modifier = Modifier.padding(16.dp))
        }
    }
}

fun getPdfFileFromAssets(context: Context, assetName: String): File? {
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

fun getPdfFileFromUri(context: Context, uri: Uri): File? {
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
