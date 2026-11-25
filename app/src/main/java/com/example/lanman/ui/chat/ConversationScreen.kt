package com.example.lanman.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageType
import com.example.lanman.ui.chat.components.MessageCard
import com.example.lanman.ui.chat.components.VoiceRecordButton
import com.example.lanman.ui.chat.components.formatSize
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.common.EmptyState
import com.example.lanman.ui.common.GlassButton
import com.example.lanman.ui.common.GlassDialog
import com.example.lanman.ui.common.GlassDialogText
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.GlassTextField
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.MediaActions
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.common.pressable
import com.example.lanman.ui.theme.LanmanTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToContactDetail: (String) -> Unit = {},
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val contactPresence by viewModel.contactPresence.collectAsStateWithLifecycle()
    val colors = LanmanTheme.colors

    val listState = rememberLazyListState()
    val context = LocalContext.current

    var inputText by remember { mutableStateOf("") }
    var previousMessageCount by remember { mutableIntStateOf(0) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<MessageUiModel?>(null) }
    var previewImageTransfer by remember { mutableStateOf<FileTransfer?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.sendFile(uri)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.sendFile(uri)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
                viewModel.setActive(true)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isResumed = false
                viewModel.setActive(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setActive(false)
        }
    }

    var didInitialScroll by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (!didInitialScroll && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            didInitialScroll = true
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.size > previousMessageCount) {
            val isAtBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ==
                (listState.layoutInfo.totalItemsCount - 1)
            if (isAtBottom || messages.lastOrNull()?.message?.direction == MessageDirection.OUTGOING) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
        previousMessageCount = messages.size
    }

    val messagesState = rememberUpdatedState(messages)
    val isResumedState = rememberUpdatedState(isResumed)
    LaunchedEffect(listState) {
        snapshotFlow {
            if (!isResumedState.value) {
                emptyList()
            } else {
                val snapshot = messagesState.value
                listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    snapshot.getOrNull(info.index)
                }
            }
        }.collect { visibleMessages ->
            if (visibleMessages.isNotEmpty()) {
                viewModel.onMessagesVisible(visibleMessages)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.errorFlow) {
        viewModel.errorFlow.collect { errorMessage ->
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            messages.isNotEmpty() &&
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) < messages.lastIndex - 1
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        )
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(colors.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .contentWidth()
                    .fillMaxHeight()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                val peerName = contactPresence?.contact?.displayName ?: "Conversation"
                val isOnline = contactPresence?.isOnline == true

                LanmanTopBar(
                    title = peerName,
                    subtitle = when {
                        contactPresence == null -> "Contact unavailable"
                        isOnline -> "Online on Wi-Fi"
                        else -> "Offline \u00B7 Messages queued locally"
                    },
                    subtitleColor = if (isOnline) colors.accent else colors.textSecondary,
                    onBack = onNavigateBack,
                    avatarName = contactPresence?.contact?.displayName,
                    avatarOnline = isOnline,
                    onTitleClick = if (contactPresence != null) {
                        { onNavigateToContactDetail(viewModel.contactId) }
                    } else null,
                    actions = {
                        if (contactPresence != null) {
                            IconButton(onClick = { onNavigateToContactDetail(viewModel.contactId) }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Contact details and safety number",
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    }
                )

                if (contactPresence != null && !isOnline) {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        tint = colors.surfaceGlass
                    ) {
                        Text(
                            text = "$peerName is offline. New messages stay encrypted here and send automatically when nearby on Wi-Fi.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

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
                            message = "Messages are encrypted end-to-end and travel directly over your local Wi-Fi."
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
                                    onLongPress = { actionTarget = uiModel },
                                    onPreviewImage = { transfer -> previewImageTransfer = transfer }
                                )
                            }
                        }

                        val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
                        val isScrolling = listState.isScrollInProgress

                        var showFloatingDate by remember { mutableStateOf(false) }
                        LaunchedEffect(isScrolling) {
                            if (isScrolling) {
                                showFloatingDate = true
                            } else {
                                delay(1500)
                                showFloatingDate = false
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showFloatingDate,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            if (messages.isNotEmpty() && firstVisibleIndex < messages.size) {
                                val topMessage = messages[firstVisibleIndex].message
                                DateSeparator(dateStr = formatMessageDate(topMessage.timestamp))
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollToBottom,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(messages.lastIndex)
                                    }
                                },
                                containerColor = colors.surfaceElevated,
                                contentColor = colors.accent,
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Jump to newest message"
                                )
                            }
                        }
                    }
                }

                // Composer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(
                        onClick = { showAttachMenu = true },
                        enabled = contactPresence != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Attach a photo or document",
                            tint = if (contactPresence != null) colors.textPrimary else colors.textTertiary
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    GlassTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = "Message...",
                        singleLine = false,
                        maxLines = 4,
                        enabled = contactPresence != null,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotEmpty()) {
                                    viewModel.sendMessage(text)
                                    inputText = ""
                                }
                            },
                            enabled = contactPresence != null,
                            modifier = Modifier
                                .size(48.dp)
                                .background(colors.accentTint, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = colors.accent
                            )
                        }
                    } else if (contactPresence != null) {
                        VoiceRecordButton(
                            onRecordComplete = { file, durationMs ->
                                val uri = runCatching {
                                    FileProvider.getUriForFile(
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
                            },
                            onRecordingHint = { hint ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(hint)
                                }
                            },
                            onPermissionDenied = { permanentlyDenied ->
                                coroutineScope.launch {
                                    if (permanentlyDenied) {
                                        val res = snackbarHostState.showSnackbar(
                                            message = "Microphone permission is required for voice messages",
                                            actionLabel = "Settings",
                                            duration = SnackbarDuration.Long
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            val intent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", context.packageName, null)
                                            )
                                            context.startActivity(intent)
                                        }
                                    } else {
                                        snackbarHostState.showSnackbar("Microphone permission is needed to record voice notes")
                                    }
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
            containerColor = colors.surfaceElevated
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = "Attach",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                SheetAction(
                    icon = AppIcons.Image,
                    label = "Photo or video",
                    onClick = {
                        showAttachMenu = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
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
        val isText = target.message.type == MessageType.TEXT
        GlassDialog(
            onDismissRequest = { actionTarget = null },
            title = if (isText) "Message" else "Attachment",
            confirmLabel = if (isText) "Copy text" else "Got it",
            onConfirm = {
                if (isText) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            "Message",
                            String(target.message.content, StandardCharsets.UTF_8)
                        )
                    )
                    Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                }
                actionTarget = null
            },
            dismissLabel = if (isText) "Cancel" else null
        ) {
            if (isText) {
                GlassDialogText("Copy the text of this message to the clipboard?")
            } else {
                GlassDialogText("Long-press completed attachments to open, share, or save them.")
            }
        }
    }

    previewImageTransfer?.let { transfer ->
        val model: Any = if (transfer.localUri.startsWith("content://")) {
            Uri.parse(transfer.localUri)
        } else {
            File(transfer.localUri)
        }
        BackHandler { previewImageTransfer = null }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AsyncImage(
                model = model,
                contentDescription = transfer.fileName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 64.dp),
                contentScale = ContentScale.Fit
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { previewImageTransfer = null }) {
                    Icon(Icons.Default.Close, contentDescription = "Close preview", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Text(
                        text = formatSize(transfer.fileSizeBytes),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassButton(
                    text = "Share",
                    onClick = {
                        if (!MediaActions.shareFile(context, transfer.localUri, transfer.mimeType)) {
                            Toast.makeText(context, "Could not share this image", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                GlassButton(
                    text = "Save",
                    onClick = {
                        val saved = MediaActions.saveToDownloads(
                            context,
                            transfer.localUri,
                            transfer.fileName,
                            transfer.mimeType
                        )
                        Toast.makeText(
                            context,
                            if (saved) "Saved to Downloads/LANMAN" else "Could not save this image",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    isPrimary = true,
                    modifier = Modifier.weight(1f)
                )
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
    val colors = LanmanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.accent)
        Spacer(modifier = Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
    }
}

@Composable
fun DateSeparator(dateStr: String) {
    val colors = LanmanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            shape = RoundedCornerShape(12.dp),
            tint = colors.surfaceCard
        ) {
            Text(
                text = dateStr,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }
    }
}

fun formatMessageDate(timestamp: Long): String {
    val messageDate = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val today = LocalDate.now()

    return when {
        messageDate == today -> "Today"
        messageDate == today.minusDays(1) -> "Yesterday"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
            messageDate.format(formatter)
        }
    }
}
