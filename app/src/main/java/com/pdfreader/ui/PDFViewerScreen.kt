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

// START: ADDED FOR PASSWORD HANDLING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment // Required for centering loading/error states
// END: ADDED FOR PASSWORD HANDLING

// START: ADDED FOR PASSWORD HANDLING (PDF Load State)
sealed interface PdfLoadState {
    data object Loading : PdfLoadState
    data class Success(val renderer: PdfRenderer, val fileDescriptor: ParcelFileDescriptor) : PdfLoadState
    data object PasswordRequired : PdfLoadState
    data class Error(val message: String) : PdfLoadState
}
// END: ADDED FOR PASSWORD HANDLING (PDF Load State)

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

    // START: ADDED FOR PASSWORD HANDLING (NEW STATE VARIABLES)
    val pdfLoadState = remember { mutableStateOf<PdfLoadState>(PdfLoadState.Loading) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    // END: ADDED FOR PASSWORD HANDLING (NEW STATE VARIABLES)

    // START: ADDED FOR PASSWORD HANDLING (LOAD PDF FUNCTION)
    val loadPdf: (String?) -> Unit = { password ->
        coroutineScope.launch(Dispatchers.IO) {
            pdfLoadState.value = PdfLoadState.Loading // Set to loading
            var newFileDescriptor: ParcelFileDescriptor? = null
            var newRenderer: PdfRenderer? = null
            try {
                if (pdfFile == null) {
                    pdfLoadState.value = PdfLoadState.Error("PDF file not found for loading.")
                    return@launch
                }
                newFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                
                newRenderer = try {
                    if (password != null) {
                        PdfRenderer(newFileDescriptor, password)
                    } else {
                        PdfRenderer(newFileDescriptor)
                    }
                } catch (e: SecurityException) {
                    // PDF is password protected or incorrect password
                    throw e // Re-throw to be caught by the outer catch block
                }
                
                pdfLoadState.value = PdfLoadState.Success(newRenderer, newFileDescriptor)
                showPasswordDialog = false // Hide dialog on success
                passwordError = null // Clear any password errors

            } catch (e: SecurityException) {
                e.printStackTrace()
                if (password == null) { // First attempt, no password given
                    showPasswordDialog = true
                    passwordError = null
                    pdfLoadState.value = PdfLoadState.PasswordRequired
                } else { // Password was given, but it was incorrect
                    passwordError = "Incorrect password."
                    showPasswordDialog = true
                    pdfLoadState.value = PdfLoadState.PasswordRequired
                }
            } catch (e: Exception) {
                e.printStackTrace()
                pdfLoadState.value = PdfLoadState.Error("Error opening PDF: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }
    // END: ADDED FOR PASSWORD HANDLING (LOAD PDF FUNCTION)

    // START: ADDED FOR PASSWORD HANDLING (LAUNCHED EFFECT FOR INITIAL LOAD)
    LaunchedEffect(pdfFile) {
        if (pdfFile != null) {
            loadPdf(null) // Try loading without password first
        } else {
            pdfLoadState.value = PdfLoadState.Error("PDF not found or could not be opened.")
        }
    }
    // END: ADDED FOR PASSWORD HANDLING (LAUNCHED EFFECT FOR INITIAL LOAD)

    // --- CRASH FIX & RENDERER LIFECYCLE MANAGEMENT ---
    // START: REPLACED ORIGINAL DisposableEffect FOR PASSWORD HANDLING
    DisposableEffect(pdfLoadState.value) {
        val currentLoadState = pdfLoadState.value
        onDispose {
            if (currentLoadState is PdfLoadState.Success) {
                currentLoadState.renderer.close()
                currentLoadState.fileDescriptor.close()
            }
            if (showPasswordDialog) { // Ensure dialog state is reset on dispose
                showPasswordDialog = false
            }
        }
    }
    // END: REPLACED ORIGINAL DisposableEffect FOR PASSWORD HANDLING

    // Moved these states outside the `when` block to be accessible.
    val renderer = remember(pdfLoadState.value) { 
        if (pdfLoadState.value is PdfLoadState.Success) (pdfLoadState.value as PdfLoadState.Success).renderer else null 
    }
    val pageCount = renderer?.pageCount ?: 0
    var pageIndex by remember { mutableStateOf(0) } // This needs to be mutable and updated by buttons

    // START: ADDED FOR ZOOM FEATURE STATE
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // START: ADDED FOR ZOOM/PAN RESET ON PAGE/LOAD STATE CHANGE
    LaunchedEffect(pdfLoadState.value, pageIndex) {
        if (pdfLoadState.value is PdfLoadState.Success) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    // END: ADDED FOR ZOOM/PAN RESET ON PAGE/LOAD STATE CHANGE

    val state = rememberTransformableState { zoomChange, panChange, rotationChange ->
        scale = (scale * zoomChange).coerceIn(0.5f, 5f) // Limit zoom from 0.5x to 5x
        // START: GAIN SPEED WHILE MOVING IN ZOOMED PDF (FASTER)
        offset += panChange / scale
        // END: GAIN SPEED WHILE MOVING IN ZOOMED PDF (FASTER)
        // rotationChange is ignored for PDF viewing
    }
    // END: ADDED FOR ZOOM FEATURE STATE

    // START: MODIFIED Column to use WHEN for PdfLoadState
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val currentLoadState = pdfLoadState.value) {
            PdfLoadState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Loading PDF...")
            }
            is PdfLoadState.Success -> {
                // Ensure pageIndex is always valid
                LaunchedEffect(pageCount) {
                    if (pageIndex >= pageCount) {
                        pageIndex = pageCount - 1
                    }
                }

                // --- QUALITY IMPROVEMENT ---
                // Render the current page to a Bitmap at a higher resolution
                val bitmap = remember(pageIndex, renderer, scale) { // ADDED 'scale' to key for dynamic quality
                    // We use `renderer` directly here which is non-null due to the outer if-check
                    val page = renderer.openPage(pageIndex)

                    // Define a scaling factor for better quality (adjusted from 5f for better starting point)
                    val baseRenderScale = 6f // Original was 5f. 6f offers better balance.

                    // Render at a resolution that supports current zoom level
                    val renderScale = if (scale > 1f) baseRenderScale * scale else baseRenderScale

                    val bitmapWidth = (page.width * renderScale).toInt()
                    val bitmapHeight = (page.height * renderScale).toInt()

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
                            // Zoom/Pan reset is now handled by LaunchedEffect(pdfLoadState.value, pageIndex)
                        },
                        enabled = pageIndex > 0
                    ) { Text("Previous") }
                    Text(text = "Page ${pageIndex + 1} / $pageCount")
                    Button(
                        onClick = { 
                            pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) 
                            // Zoom/Pan reset is now handled by LaunchedEffect(pdfLoadState.value, pageIndex)
                        },
                        enabled = pageIndex < pageCount - 1
                    ) { Text("Next") }
                }
            }
            PdfLoadState.PasswordRequired -> {
                // Text indicating password is required; the dialog will overlay this.
                Text("Please enter password to view this PDF.")
            }
            is PdfLoadState.Error -> {
                Text(
                    text = currentLoadState.message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
    // END: MODIFIED Column to use WHEN for PdfLoadState

    // START: ADDED FOR PASSWORD HANDLING (PASSWORD DIALOG)
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { /* User must input password or cancel */ },
            title = { Text("Enter Password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    passwordError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { loadPdf(passwordInput) }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                Button(onClick = { 
                    showPasswordDialog = false 
                    pdfLoadState.value = PdfLoadState.Error("PDF requires password and was cancelled.")
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    // END: ADDED FOR PASSWORD HANDLING (PASSWORD DIALOG)
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
