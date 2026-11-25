package com.example.lanman.ui.qr

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
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
import com.example.lanman.ui.theme.LanmanTheme

@Composable
fun MyQrCodeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit = {},
    viewModel: MyQrCodeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LanmanTheme.colors

    // Keep screen awake at full brightness while showing a pairing QR code, restoring afterwards.
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
            .background(colors.background)
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
                        CircularProgressIndicator(color = colors.accent)
                    }

                    is MyQrCodeState.Error -> {
                        Spacer(modifier = Modifier.height(64.dp))
                        Text(
                            text = "Failed to load profile or generate QR code.",
                            color = colors.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    is MyQrCodeState.Generated -> {
                        Spacer(modifier = Modifier.height(8.dp))

                        ContactAvatar(
                            displayName = currentState.displayName,
                            size = 64.dp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = currentState.displayName.ifBlank { "Your code" },
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Have your contact scan this to pair on local Wi-Fi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.84f)
                                .aspectRatio(1f)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = currentState.bitmap.asImageBitmap(),
                                contentDescription = "My QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (!currentState.fingerprint.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            GlassSurface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "IDENTITY VERIFICATION CODE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.accent
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    SelectionContainer {
                                        Text(
                                            text = currentState.fingerprint,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = colors.textPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Your peer can confirm this matches what they scan",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Manual pairing code",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

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
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, currentState.payloadString)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(sendIntent, "Share pairing code"))
                                }
                            }) {
                                Text("Share code", color = colors.textSecondary)
                            }
                            TextButton(onClick = onNavigateToScanner) {
                                Text("Scan peer's QR", color = colors.accent)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
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
