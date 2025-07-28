package com.pdfreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pdfreader.ui.PDFViewerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Try to get PDF URI from Intent
        val pdfUri: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if ("application/pdf" == intent.type) intent.getParcelableExtra(Intent.EXTRA_STREAM) else null
            }
            else -> null
        }

        setContent {
            PDFViewerScreen(context = this, pdfUri = pdfUri)
        }
    }
}
