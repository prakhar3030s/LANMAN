package com.example.lanman.ui.qr

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.ui.common.ContactAvatar
import com.example.lanman.ui.common.GlassButton
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.MediaActions
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary

@Composable
fun MyQrCodeScreen(
    onNavigateBack: () -> Unit,
    viewModel: MyQrCodeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A pairing code is meant to be scanned by another phone, so hold the screen awake at full
    // brightness while it is on screen and restore the user's setting afterwards.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val previousBrightness = window?.attributes?.screenBrightness
        view.keepScreenOn = true
        window?.let { w ->
            val params = w.attributes
            params.screenBrightness = 1f
            w.attributes = params
        }
        onDispose {
            view.keepScreenOn = false
            window?.let { w ->
                val params = w.attributes
                params.screenBrightness = previousBrightness ?: -1f
                w.attributes = params
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LanmanTopBar(
            title = "My QR Code",
            onBack = onNavigateBack
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .contentWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val currentState = state) {
                    is MyQrCodeState.Loading -> {
                        Spacer(modifier = Modifier.height(64.dp))
                        CircularProgressIndicator(color = ElectricCyan)
                    }

                    is MyQrCodeState.Error -> {
                        Spacer(modifier = Modifier.height(64.dp))
                        Text(
                            text = "Failed to load profile or generate QR code.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    is MyQrCodeState.Generated -> {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Whose code this is, so a handed-over phone is unambiguous.
                        ContactAvatar(
                            displayName = currentState.displayName,
                            size = 64.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = currentState.displayName.ifBlank { "Your code" },
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Have your contact scan this to pair",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .aspectRatio(1f)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = currentState.bitmap.asImageBitmap(),
                                contentDescription = "My QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Or copy manual code:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            SelectionContainer {
                                Text(
                                    text = currentState.payloadString,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = TextPrimary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Equal widths: the two actions carry the same weight.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GlassButton(
                                text = "Copy Code",
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("LANMAN Code", currentState.payloadString)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                isPrimary = true,
                                modifier = Modifier.weight(1f)
                            )

                            GlassButton(
                                text = "Save Image",
                                onClick = {
                                    val saved = MediaActions.saveBitmapToPictures(
                                        context,
                                        currentState.bitmap,
                                        "lanman_qr.png"
                                    )
                                    Toast.makeText(
                                        context,
                                        if (saved) "QR code saved to Pictures/LANMAN" else "Could not save the QR code",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                isPrimary = false,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
