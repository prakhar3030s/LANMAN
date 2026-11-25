package com.prakhar.lanman.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.prakhar.lanman.ui.common.AppIcons
import com.prakhar.lanman.ui.common.GlassButton
import com.prakhar.lanman.ui.common.GlassSurface
import com.prakhar.lanman.ui.common.GlassTextField
import com.prakhar.lanman.ui.common.QrCodeIcon
import com.prakhar.lanman.ui.common.contentWidth
import com.prakhar.lanman.ui.theme.LanmanTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OnboardingScreen(
    onNavigateToHome: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val displayName = viewModel.displayName.value
    val errorMessage = viewModel.errorMessage.value
    val isSaving = viewModel.isSaving.value
    val colors = LanmanTheme.colors
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is OnboardingEvent.NavigateToHome -> onNavigateToHome()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "onboardingStep"
        ) { currentStep ->
            if (currentStep == 0) {
                Column(
                    modifier = Modifier
                        .contentWidth()
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(colors.accentTint),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Wifi,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "LANMAN",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Direct, encrypted messaging on your local Wi-Fi.\nNo cloud, no servers, no phone numbers.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        OnboardingPillar(
                            icon = AppIcons.Wifi,
                            title = "Zero Cloud Footprint",
                            description = "Messages, files, and voice notes travel directly device-to-device over your local network."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OnboardingPillar(
                            icon = Icons.Default.Lock,
                            title = "End-to-End Encrypted",
                            description = "Every message uses the Signal Protocol with forward secrecy and local key storage."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OnboardingPillar(
                            icon = null,
                            title = "QR-Verified Pairing",
                            description = "Pair with nearby peers by scanning their QR code and verifying safety numbers.",
                            customIcon = {
                                QrCodeIcon(contentDescription = "QR Pairing", tint = colors.accent)
                            }
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        GlassButton(
                            text = "Continue",
                            onClick = { step = 1 },
                            isPrimary = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .contentWidth()
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Choose your name",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "This name and your encryption keys are generated and kept strictly on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    GlassTextField(
                        value = displayName,
                        onValueChange = { viewModel.onDisplayNameChanged(it) },
                        placeholder = "Display Name",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isSaving) {
                                    viewModel.submitProfile()
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = colors.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${displayName.trim().length}/30",
                            color = if (displayName.trim().length > 30) colors.error else colors.textTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = colors.accent
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Generating cryptographic keys...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    } else {
                        GlassButton(
                            text = "Create Identity",
                            onClick = { viewModel.submitProfile() },
                            isPrimary = true,
                            enabled = displayName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { step = 0 }) {
                            Text("Back", color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPillar(
    icon: ImageVector?,
    title: String,
    description: String,
    customIcon: (@Composable () -> Unit)? = null
) {
    val colors = LanmanTheme.colors
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.accentTint),
                contentAlignment = Alignment.Center
            ) {
                if (customIcon != null) {
                    customIcon()
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}
