package com.example.lanman.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.lanman.ui.common.GlassButton
import com.example.lanman.ui.common.GlassTextField
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OnboardingScreen(
    onNavigateToHome: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val displayName = viewModel.displayName.value
    val errorMessage = viewModel.errorMessage.value
    val isSaving = viewModel.isSaving.value

    val hazeState = remember { HazeState() }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is OnboardingEvent.NavigateToHome -> {
                    onNavigateToHome()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .haze(state = hazeState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "LocalLink",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Enter a display name to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(48.dp))

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
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = ElectricCyan
                )
            } else {
                GlassButton(
                    text = "Get Started",
                    onClick = { viewModel.submitProfile() },

                    isPrimary = true,
                    enabled = displayName.isNotBlank()
                )
            }
        }
    }
}
