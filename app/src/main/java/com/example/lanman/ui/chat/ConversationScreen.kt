package com.example.lanman.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.ui.chat.components.MessageCard
import com.example.lanman.ui.common.GlassTextField
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val contactPresence by viewModel.contactPresence.collectAsStateWithLifecycle()
    val isTransferActive by viewModel.isTransferActive.collectAsStateWithLifecycle()
    
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    
    var inputText by remember { mutableStateOf("") }
    var previousMessageCount by remember { mutableIntStateOf(0) }
    var showAttachMenu by remember { mutableStateOf(false) }

    // Pickers
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendFile(uri)
        }
    }
    
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.sendFile(uri)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.setActive(true)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                viewModel.setActive(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setActive(false)
        }
    }

    // Auto-scroll logic: If at bottom and new message arrives, snap to bottom
    LaunchedEffect(messages.size) {
        if (messages.size > previousMessageCount) {
            val isAtBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == (listState.layoutInfo.totalItemsCount - 1)
            if (isAtBottom || messages.lastOrNull()?.message?.direction == MessageDirection.OUTGOING) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
        previousMessageCount = messages.size
    }

    // Read receipts marking logic
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.collect { visibleItems ->
            val visibleMessages = visibleItems.mapNotNull {
                val index = it.index
                if (index in messages.indices) messages[index] else null
            }
            viewModel.onMessagesVisible(visibleMessages)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(viewModel.errorFlow) {
        viewModel.errorFlow.collect { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier
            .fillMaxSize()
            .haze(hazeState),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = contactPresence?.contact?.displayName ?: "Unknown",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            text = if (contactPresence?.isOnline == true) "Online" else "Offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (contactPresence?.isOnline == true) ElectricCyan else TextSecondary
                        )
                    }
                }

                // Messages List
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start the conversation",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(
                                items = messages,
                                key = { _, uiModel: MessageUiModel -> uiModel.message.id }
                            ) { index, uiModel: MessageUiModel ->
                                val previousMessage = if (index > 0) messages[index - 1] else null
                                
                                val currentDateStr = formatMessageDate(uiModel.message.timestamp)
                                val showDateSeparator = previousMessage == null || 
                                    currentDateStr != formatMessageDate(previousMessage.message.timestamp)
                                
                                if (showDateSeparator) {
                                    DateSeparator(dateStr = currentDateStr)
                                }
                                
                                val showHeader = shouldShowHeader(uiModel.message, previousMessage?.message)
                                
                                if (showHeader) {
                                    MessageClusterHeader(message = uiModel.message)
                                }
                                
                                MessageCard(
                                    uiModel = uiModel,
                                )
                            }
                        }
                        
                        // Floating Sticky Date Header
                        val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                        val isScrolling = listState.isScrollInProgress
                        
                        var showFloatingDate by remember { mutableStateOf(false) }
                        LaunchedEffect(isScrolling) {
                            if (isScrolling) {
                                showFloatingDate = true
                            } else {
                                kotlinx.coroutines.delay(1500)
                                showFloatingDate = false
                            }
                        }
                        
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showFloatingDate,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        ) {
                            if (messages.isNotEmpty() && firstVisibleIndex < messages.size) {
                                val topMessage = messages[firstVisibleIndex].message
                                DateSeparator(dateStr = formatMessageDate(topMessage.timestamp))
                            }
                        }
                    }
                }

                // Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        enabled = !isTransferActive
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Add,
                            contentDescription = "Attach",
                            tint = if (isTransferActive) TextSecondary.copy(alpha = 0.5f) else TextPrimary
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photo / Video", color = TextPrimary) },
                            onClick = {
                                showAttachMenu = false
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                    )
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Document", color = TextPrimary) },
                            onClick = {
                                showAttachMenu = false
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                GlassTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = "Message...",

                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(ElectricCyan.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = ElectricCyan
                    )
                }
            }
        }
    }
}
}

private fun shouldShowHeader(current: Message, previous: Message?): Boolean {
    if (previous == null) return true
    if (current.direction != previous.direction) return true
    
    // Group consecutive messages by same direction within 5 minutes (300,000 ms)
    val timeDiff = current.timestamp - previous.timestamp
    return timeDiff > 300_000
}

@Composable
fun MessageClusterHeader(message: Message) {
    val alignment = if (message.direction == MessageDirection.OUTGOING) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = alignment
    ) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        Text(
            text = formatter.format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
fun DateSeparator(dateStr: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Text(
                text = dateStr,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
        }
    }
}

fun formatMessageDate(timestamp: Long): String {
    val messageDate = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    val today = java.time.LocalDate.now()
    
    return when {
        messageDate == today -> "Today"
        messageDate == today.minusDays(1) -> "Yesterday"
        else -> {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
            messageDate.format(formatter)
        }
    }
}
