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
import java.io.IOException // Make sure this import is present for better error handling

@Composable
fun PDFViewerScreen(pdfUri: Uri?) {
    val context = LocalContext.current

    // Step 1: Determine the PDF file to load (from Uri or assets)
    val pdfFile = remember(pdfUri) {
        when {
            pdfUri != null -> getPdfFileFromUri(context, pdfUri)
            else -> getPdfFileFromAssets(context, "sample.pdf") // Loads a sample PDF if no URI is provided
        }
    }

    // Step 2: Open PdfRenderer and manage its lifecycle (CRASH FIX)
    // We use two mutable states to hold the renderer and its associated file descriptor.
    val pdfRendererState = remember { mutableStateOf<PdfRenderer?>(null) }
    val fileDescriptorState = remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    // DisposableEffect ensures proper opening and closing based on 'pdfFile'
    DisposableEffect(pdfFile) {
        var newRenderer: PdfRenderer? = null
        var newFileDescriptor: ParcelFileDescriptor? = null

        if (pdfFile != null) {
            try {
                // Open ParcelFileDescriptor in read-only mode
                newFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                // Create PdfRenderer using the file descriptor
                newRenderer = PdfRenderer(newFileDescriptor)
                
                // Update the state variables with the new instances
                pdfRendererState.value = newRenderer
                fileDescriptorState.value = newFileDescriptor

            } catch (e: Exception) {
                // Log any errors that occur during PDF opening
                e.printStackTrace()
                // Ensure states are nulled out on error
                pdfRendererState.value = null
                fileDescriptorState.value = null
            }
        } else {
            // If pdfFile is null, ensure renderer and descriptor states are also nulled
            pdfRendererState.value = null
            fileDescriptorState.value = null
        }

        // This 'onDispose' block is crucial for cleanup when the composable leaves composition
        // or 'pdfFile' changes, preventing the "Already closed" crash.
        onDispose {
            // Safely close the PdfRenderer and ParcelFileDescriptor if they exist
            pdfRendererState.value?.close()
            fileDescriptorState.value?.close()
            // Null out the states to prevent accidental re-use of closed objects
            pdfRendererState.value = null
            fileDescriptorState.value = null
        }
    }

    // Access the current PdfRenderer instance from the state
    val renderer = pdfRendererState.value
    val pageCount = renderer?.pageCount ?: 0
    var pageIndex by remember { mutableStateOf(0) } // State to track current page index

    Column(
        modifier = Modifier.fillMaxSize(),
        // Center content horizontally and vertically when no PDF is displayed
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (renderer != null && pageCount > 0) {
            // Ensure pageIndex is valid if pageCount changes (e.g., a different PDF is loaded)
            LaunchedEffect(pageCount) {
                if (pageIndex >= pageCount) {
                    pageIndex = pageCount - 1
                }
            }

            // Step 3: Render the current page to a Bitmap (QUALITY IMPROVEMENT)
            val bitmap = remember(pageIndex, renderer) {
                // Open the specific page from the renderer
                val page = renderer.openPage(pageIndex)

                // Define a scaling factor for better image quality.
                // A value of 3f means the bitmap will be 3 times larger in width/height
                // than the PDF's intrinsic page dimensions, resulting in sharper display.
                val scaleFactor = 3f 

                // Calculate the dimensions for the high-quality bitmap
                val bitmapWidth = (page.width * scaleFactor).toInt()
                val bitmapHeight = (page.height * scaleFactor).toInt()

                // Create the bitmap with the calculated dimensions and ARGB_8888 config for full color
                val bmp = Bitmap.createBitmap(
                    bitmapWidth,
                    bitmapHeight,
                    Bitmap.Config.ARGB_8888
                )
                // Render the PDF page onto the created bitmap
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close() // IMPORTANT: Close the page after rendering to release resources
                bmp // Return the rendered bitmap
            }

            // Display the rendered PDF page image
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes up remaining vertical space
            )

            // Navigation controls (Previous/Next buttons and page number)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, // Go to previous page, min 0
                    enabled = pageIndex > 0 // Disable if on the first page
                ) { Text("Previous") }
                
                Text(text = "Page ${pageIndex + 1} / $pageCount") // Display current page number
                
                Button(
                    onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) }, // Go to next page, max pageCount - 1
                    enabled = pageIndex < pageCount - 1 // Disable if on the last page
                ) { Text("Next") }
            }
        } else {
            // This block is executed when renderer is null (e.g., app launched directly or PDF failed to open).
            // The Text message has been REMOVED as per your request for a cleaner initial screen.
            // You can add a different message or UI here if desired, e.g.:
            // Text("Welcome! Open a PDF from your file manager.", modifier = Modifier.padding(16.dp))
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
            e.printStackTrace() // Print stack trace for debugging asset loading issues
            return null
        }
    }
    return file
}

// Utility function to get PDF File from a content Uri (e.g., from file manager)
fun getPdfFileFromUri(context: android.content.Context, uri: Uri): File? {
    return try {
        val file = File(context.cacheDir, "shared.pdf") // Temporary file name for shared PDFs
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        file
    } catch (e: Exception) {
        e.printStackTrace() // Print stack trace for debugging URI handling issues
        return null
    }
}
