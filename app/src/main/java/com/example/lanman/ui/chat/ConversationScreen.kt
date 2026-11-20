package com.example.lanman.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.ui.chat.components.MessageCard
import com.example.lanman.ui.chat.components.VoiceRecordButton
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.common.EmptyState
import com.example.lanman.ui.common.GlassDialog
import com.example.lanman.ui.common.GlassDialogText
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.GlassTextField
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.common.pressable
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val contactPresence by viewModel.contactPresence.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var previousMessageCount by remember { mutableIntStateOf(0) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<MessageUiModel?>(null) }

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

    // Opening a chat must land on the newest message, not the oldest one. The list starts at
    // index 0, so jump to the end once the thread has loaded.
    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (!didInitialScroll && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            didInitialScroll = true
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
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.errorFlow) {
        viewModel.errorFlow.collect { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        // The bottom is handled below by imePadding, so the keyboard and the composer do not
        // stack a navigation-bar gap between them.
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        )
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .contentWidth()
                    .fillMaxHeight()
                    // The whole screen lifts once when the keyboard opens, so the composer stays
                    // glued to it instead of the window panning the header off-screen.
                    .imePadding()
            ) {
                LanmanTopBar(
                    title = contactPresence?.contact?.displayName ?: "Conversation",
                    subtitle = if (contactPresence?.isOnline == true) "Online" else "Offline",
                    subtitleColor = if (contactPresence?.isOnline == true) ElectricCyan else TextSecondary,
                    onBack = onNavigateBack,
                    avatarName = contactPresence?.contact?.displayName,
                    avatarOnline = contactPresence?.isOnline == true
                )

                // Messages List
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = AppIcons.Chat,
                            title = "Start the conversation",
                            message = "Messages are sent over your local network, end to end encrypted."
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
                            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                            // Short threads sit on the composer instead of floating at the top.
                            verticalArrangement = Arrangement.Bottom
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

                                MessageCard(
                                    uiModel = uiModel,
                                    onRetry = {
                                        viewModel.retryMessage(uiModel.message.id, uiModel.fileTransfer?.id)
                                    },
                                    onCancel = {
                                        viewModel.cancelTransfer(uiModel.message.id, uiModel.fileTransfer?.id)
                                    },
                                    onLongPress = { actionTarget = uiModel }
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
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach a photo or document",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    GlassTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = "Message...",
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(inputText)
                                inputText = ""
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
                    } else {
                        VoiceRecordButton(
                            onRecordComplete = { file, durationMs ->
                                // The provider throws if the file is outside every configured root,
                                // so never let that escape from the recording callback.
                                val uri = runCatching {
                                    androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                }.getOrNull()
                                if (uri == null) {
                                    file.delete()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Could not attach the voice recording")
                                    }
                                } else {
                                    viewModel.sendVoiceMessage(uri = uri, durationMs = durationMs)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAttachMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAttachMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Attach",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                SheetAction(
                    icon = AppIcons.Image,
                    label = "Photo or video",
                    onClick = {
                        showAttachMenu = false
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }
                )
                SheetAction(
                    icon = AppIcons.InsertDriveFile,
                    label = "Document",
                    onClick = {
                        showAttachMenu = false
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )
            }
        }
    }

    actionTarget?.let { target ->
        val isText = target.message.type == com.example.lanman.domain.model.MessageType.TEXT
        GlassDialog(
            onDismissRequest = { actionTarget = null },
            title = if (isText) "Message" else "Attachment",
            confirmLabel = "Close",
            onConfirm = { actionTarget = null },
            dismissLabel = "Cancel"
        ) {
            if (isText) {
                GlassDialogText("Copy the text of this message to the clipboard?")
            } else {
                GlassDialogText("Use the bubble actions to open, share or save this attachment.")
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (isText) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "Message",
                                    String(target.message.content, StandardCharsets.UTF_8)
                                )
                            )
                            android.widget.Toast.makeText(context, "Message copied", android.widget.Toast.LENGTH_SHORT).show()
                            actionTarget = null
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(AppIcons.ContentCopy, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Copy text", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ElectricCyan)
        Spacer(modifier = Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}

@Composable
fun DateSeparator(dateStr: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(shape = RoundedCornerShape(12.dp)) {
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
