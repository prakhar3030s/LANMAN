package com.example.lanman.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.domain.model.MessageRequest
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.common.ContactAvatar
import com.example.lanman.ui.common.EmptyState
import com.example.lanman.ui.common.GlassButton
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun MessageRequestsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MessageRequestsViewModel = hiltViewModel()
) {
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LanmanTopBar(
                title = "Message Requests",
                subtitle = when {
                    requests.isEmpty() -> "Nothing pending"
                    requests.size == 1 -> "1 request"
                    else -> "${requests.size} requests"
                },
                onBack = onNavigateBack
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                if (requests.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = AppIcons.MarkEmailRead,
                            title = "No pending requests",
                            message = "Messages from people you have not paired with will wait here for review."
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .contentWidth()
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(requests, key = { it.contactId }) { request ->
                            MessageRequestItem(
                                request = request,
                                onAccept = { viewModel.acceptRequest(request) },
                                onReject = {
                                    viewModel.rejectRequest(request)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Request removed",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoReject()
                                        }
                                    }
                                },
                                onBlock = { viewModel.blockRequest(request) }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
fun MessageRequestItem(
    request: MessageRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onBlock: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(displayName = request.displayName, size = 42.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Wants to message you",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = request.firstMessageText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBlock) {
                    Text(text = "Block", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onReject) {
                    Text(text = "Decline", color = TextSecondary)
                }
                Spacer(modifier = Modifier.weight(1f))
                GlassButton(
                    text = "Accept",
                    onClick = onAccept,
                    isPrimary = true,
                    modifier = Modifier.widthIn(min = 128.dp)
                )
            }
        }
    }
}
