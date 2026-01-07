package com.example.anomess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anomess.data.Contact
import com.example.anomess.data.Message
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.ContentCopy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    contact: Contact,
    onBack: () -> Unit
) {
    // Selection State moved up for TopBar access
    val selectedIds = remember { mutableStateListOf<Int>() }
    // IDs currently being animated out before deletion
    val deletingIds = remember { mutableStateListOf<Int>() } 
    val selectionMode = selectedIds.isNotEmpty()

    // Back Handler: Undo selection if in selection mode, otherwise default back
    BackHandler(enabled = selectionMode) {
        selectedIds.clear()
    }
    BackHandler(enabled = !selectionMode, onBack = onBack)
    
    // Keyboard Controller
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // Observe contact changes for renaming updates
    val observedContact by viewModel.getContactFlow(contact.onionAddress).collectAsState(initial = contact)
    // Use observedContact instead of contact param for UI that needs updates
    val displayContact = observedContact ?: contact
    
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val connectionStatus by viewModel.torStatus.collectAsState()
    val myOnionAddress by viewModel.onionAddress.collectAsState()
    
    // Optimized: Collect pre-filtered flow from ViewModel (IO Thread)
    val messages by viewModel.getMessagesForContact(contact.onionAddress).collectAsState(initial = emptyList())
    val playingMessageId by viewModel.playingMessageId.collectAsState()
    
    // Sanitized address for display logic only
    val safeContactAddress = remember(contact.onionAddress) {
        contact.onionAddress.filter { it.isLetterOrDigit() || it == '.' }
    }
    
    var messageText by remember { mutableStateOf("") }
    var showLogs by remember { mutableStateOf(false) }
    var showSecurityInfo by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var lastMicClickTime by remember { mutableLongStateOf(0L) }

    // Selection State definitions moved up
    val launchTime = remember { System.currentTimeMillis() }
    // ...

    val focusRequester = remember { FocusRequester() }

    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.sendImage(context, uri, contact.onionAddress)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    // Helper to animate deletion
    fun triggerDelete(ids: List<Int>) {
        deletingIds.addAll(ids)
        // Clear selection immediately so UI exits selection mode
        selectedIds.clear() 
        coroutineScope.launch {
            kotlinx.coroutines.delay(400) // Wait for exit animation
            viewModel.deleteMessages(ids)
            deletingIds.removeAll(ids)
        }
    }

    Scaffold(
        topBar = {
// ...
// ...
            // Debug Log Dialog (Kept simple)
            if (showLogs) {
                // ... (existing log dialog)
                // Collect logs ONLY when dialog is open to improve performance
                val debugLogs by viewModel.debugLogs.collectAsState()
                
                AlertDialog(
                    onDismissRequest = { showLogs = false },
                    title = { Text("Debug Details") },
                    text = {
                        Column {
                            Text("My Address:", style = MaterialTheme.typography.labelMedium)
                            Text(myOnionAddress ?: "Generating...", style = MaterialTheme.typography.bodySmall)
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Logs:", style = MaterialTheme.typography.labelMedium)
                            Box(modifier = Modifier.height(200.dp).border(1.dp, MaterialTheme.colorScheme.outline).verticalScroll(rememberScrollState()).padding(4.dp)) {
                                Text(debugLogs, style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showLogs = false }) { Text("Close") } }
                )
            }

            // Security Info Dialog
            if (showSecurityInfo) {
                AlertDialog(
                    onDismissRequest = { showSecurityInfo = false },
                    title = { Text("Encryption Safety Number") },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("Verify this safety number with your contact to ensure your communication is secure.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Peer Key
                            Text("Contact's Identity:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (contact.publicKey != null) {
                                SelectionContainer {
                                    Text(
                                        text = contact.publicKey.chunked(4).joinToString(" "), 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            } else {
                                Text("No key established yet.", style = MaterialTheme.typography.bodyMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // My Key
                            Text("My Identity:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            val myKey = viewModel.getMyPublicKey() // Need to expose this in ViewModel
                            if (myKey != null) {
                                SelectionContainer {
                                    Text(
                                        text = myKey.chunked(4).joinToString(" "), 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            } else {
                                Text("Generating...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSecurityInfo = false }) { Text("Done") } },
                    icon = { Icon(Icons.Default.Fingerprint, contentDescription = null) }
                )
            }
// ...

            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size}", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                    },
                    actions = {
                        // Copy Option
                        if (selectedIds.size == 1) {
                            IconButton(onClick = {
                                val msg = messages.find { it.id == selectedIds.first() }
                                if (msg != null && msg.type == Message.TYPE_TEXT) {
                                    clipboardManager.setText(AnnotatedString(msg.content))
                                    selectedIds.clear()
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White)
                            }
                        }
                        // Delete Option
                        IconButton(onClick = {
                            triggerDelete(selectedIds.toList())
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { 
                        Column {
                            Text(displayContact.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (safeContactAddress.length > 15) "${safeContactAddress.take(12)}...onion" else safeContactAddress, 
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                // Rename Contact Option
                                DropdownMenuItem(
                                    text = { Text("Rename Contact") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showRenameDialog = true
                                    }
                                )

                                // Copy ID Option
                                DropdownMenuItem(
                                    text = { Text("Copy ID") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(contact.onionAddress))
                                        showMenu = false
                                    }
                                )

                                // Security Info Option
                                DropdownMenuItem(
                                    text = { Text("Safety Number") },
                                    leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                                    onClick = {
                                        showSecurityInfo = true
                                        showMenu = false
                                    }
                                )
                                
                                // Debug Info Toggle
                                DropdownMenuItem(
                                    text = { Text(if (showLogs) "Hide Debug Info" else "Show Debug Info") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        showLogs = !showLogs
                                        showMenu = false
                                    }
                                )

                                // Clear Chat Option
                                DropdownMenuItem(
                                    text = { Text("Clear Chat") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        val allIds = messages.map { it.id }
                                        triggerDelete(allIds)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    ) { padding ->
        // Rename Dialog
        if (showRenameDialog) {
            var newName by remember { mutableStateOf(displayContact.name ?: "") }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Contact") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                viewModel.updateContactName(contact.onionAddress, newName)
                                showRenameDialog = false
                            }
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                }
            )
        }
        
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // Connection Status (Subtle)
            if (connectionStatus != "Tor Connected") {
                 Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                     Text(
                        text = "Status: $connectionStatus", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                     )
                 }
            }

            // Chat Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No messages yet", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    val coroutineScope = rememberCoroutineScope()
                    val showScrollToBottom by remember {
                        derivedStateOf {
                            // Only show if we are NOT at the bottom
                            val layoutInfo = listState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            if (totalItems == 0) return@derivedStateOf false
                            
                            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                            lastVisibleItem == null || lastVisibleItem.index < totalItems - 1
                        }
                    }

                    // Auto-scroll to bottom when new messages arrive
                    // Auto-scroll to bottom when new messages arrive
                    var isInitialLoad by remember { mutableStateOf(true) }
                    
                     
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) {
                             if (isInitialLoad) {
                                 // Instant jump on first load (Entering chat)
                                 listState.scrollToItem(messages.size)
                                 isInitialLoad = false
                             } else {
                                 // With the new Layout-preserving animation, the space is reserved instantly.
                                 // So we can just scroll once to the new item.
                                 listState.animateScrollToItem(messages.size)
                             }
                        }
                    }
                    
                    // Mark messages as read continuously
                    LaunchedEffect(messages) {
                         if (messages.isNotEmpty()) {
                             // Only call DB if there are actual unread messages to prevent infinite loop
                             val hasUnread = messages.any { !it.isRead && !it.isMine }
                             if (hasUnread) {
                                viewModel.markAsRead(contact.onionAddress)
                             }
                         }
                    }

                    // Selection State removed from here (moved to top)
                    // val selectedIds... removed
                    // val selectionMode... removed
                    
                    // Image Preview State
                    var previewImageUrl by remember { mutableStateOf<String?>(null) }
                    
                    if (previewImageUrl != null) {
                        FullImageDialog(
                            imageUrl = previewImageUrl!!, 
                            onDismiss = { previewImageUrl = null },
                            onSave = {
                                viewModel.saveMediaToGallery(context, previewImageUrl!!)
                            }
                        )

                    }

                    // File Opening Logic
                    val onOpenFile: (String) -> Unit = { path ->
                        coroutineScope.launch {
                            val uri = viewModel.prepareFileForViewing(context, path)
                            if (uri != null) {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "*/*")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot open file", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Failed to load file", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.Bottom,
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            itemsIndexed(
                                items = messages, 
                                key = { _: Int, msg: Message -> msg.id }
                            ) { index, msg ->
                                // Date Header Logic (same)
                                val showHeader = if (index == 0) {
                                    true
                                } else {
                                    val prevMsg = messages[index - 1]
                                    !isSameDay(msg.timestamp, prevMsg.timestamp)
                                }

                                if (showHeader) {
                                    DateHeader(msg.timestamp)
                                }

                                Box(modifier = Modifier.animateItemPlacement()) {
                                    val isDeleting = deletingIds.contains(msg.id)
                                    // Logic to determine if message is "historical" (pre-session)
                                    // If timestamp is older than launchTime, it's history. 
                                    // We add a small buffer (5000ms) to account for initialization delays
                                    val isHistory = remember(msg.timestamp) { msg.timestamp < (launchTime - 5000) }
                                    
                                    // Track if this item has already appeared/animated to prevent re-animation on scroll
                                    // We default to 'true' (no animation) if it's history, 'false' (animate) if it's new
                                    val hasAppeared = androidx.compose.runtime.saveable.rememberSaveable(msg.id) { mutableStateOf(isHistory) }

                                    val visibleState = remember(msg.id) { 
                                        androidx.compose.animation.core.MutableTransitionState(hasAppeared.value).apply { 
                                            // If deleting, target is false. Otherwise target is true.
                                            targetState = !isDeleting
                                        } 
                                    }
                                    
                                    // Trigger exit animation if deleting
                                    LaunchedEffect(isDeleting) {
                                        if (isDeleting) visibleState.targetState = false
                                    }

                                    // Mark as appeared after first composition
                                    LaunchedEffect(Unit) {
                                        hasAppeared.value = true
                                    }

                                    androidx.compose.animation.AnimatedVisibility(
                                        visibleState = visibleState,
                                        // key fix: Use transitions that do NOT affect layout size (no expandVertically)
                                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + 
                                                androidx.compose.animation.slideInVertically(
                                                    animationSpec = androidx.compose.animation.core.tween(300),
                                                    initialOffsetY = { 40 } // Slide up from 40px down
                                                ),
                                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                    ) {
                                        MessageItem(
                                            message = msg,
                                            isSelected = selectedIds.contains(msg.id),
                                            selectionMode = selectionMode,
                                            onSelect = { id ->
                                                if (selectedIds.contains(id)) selectedIds.remove(id)
                                                else selectedIds.add(id)
                                            },
                                            onImageClick = { path ->
                                                previewImageUrl = path
                                            },
                                            onOpenFile = onOpenFile,
                                            onRetry = { message -> viewModel.retryMessage(message) },
                                            onReply = { message -> 
                                                replyingToMessage = message
                                                focusRequester.requestFocus()
                                                keyboardController?.show()
                                            },
                                            onReplyClick = { replyId ->
                                                val index = messages.indexOfFirst { it.id == replyId }
                                                com.example.anomess.network.TorManager.log("DEBUG: onReplyClick id=$replyId index=$index total=${messages.size}")
                                                if (index != -1) {
                                                    coroutineScope.launch {
                                                        try {
                                                            listState.animateScrollToItem(index)
                                                        } catch (e: Exception) {
                                                            com.example.anomess.network.TorManager.log("DEBUG: Scroll failed: ${e.message}")
                                                        }
                                                    }
                                                } else {
                                                    com.example.anomess.network.TorManager.log("DEBUG: Reply message not found in list")
                                                }
                                            },
                                            isPlaying = playingMessageId == msg.id,
                                            onPlayAudio = { path -> viewModel.playAudio(msg.id, path) },
                                            onStopAudio = { viewModel.stopAudio() }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            item {
                                Spacer(modifier = Modifier.height(1.dp))
                            }
                        }
                        
                        // Selection Toolbar Overlay REMOVED (Moved to TopBar)

                        // Scroll to Bottom FAB (Small)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollToBottom,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(messages.size)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Scroll Down", modifier = Modifier.rotate(-90f)) 
                            }
                        }
                    }
                }
            }

            // Reply Preview Bar
            if (replyingToMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Accent bar
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (replyingToMessage!!.isMine) "You" else contact.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = replyingToMessage!!.content.take(50) + if (replyingToMessage!!.content.length > 50) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { replyingToMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Reply")
                        }
                    }
                }
            }

            // Input Area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Transparent,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    // Attachment Menu State
                    var showAttachMenu by remember { mutableStateOf(false) }
                    
                    // Camera Logic
                    val cameraUri = remember { mutableStateOf<android.net.Uri?>(null) }
                    val takePictureLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.TakePicture()
                    ) { success ->
                        if (success && cameraUri.value != null) {
                            viewModel.sendImage(context, cameraUri.value!!, contact.onionAddress)
                        }
                    }
                    
                    // Permission Logic for Mic
                    var showPermissionRationale by remember { mutableStateOf(false) }
                    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted: Boolean ->
                        if (isGranted) {
                           // Permission granted, user can click mic again to record
                        } else {
                            showPermissionRationale = true
                        }
                    }

                    // Attach Button with Menu
                    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri: android.net.Uri? ->
                        if (uri != null) {
                            viewModel.sendFile(context, uri, contact.onionAddress)
                        }
                    }

                    Box {
                        IconButton(onClick = { showAttachMenu = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Attach", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Gallery") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Camera") },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    // Create temp file for camera
                                    val tmpFile = java.io.File.createTempFile("cam_", ".jpg", context.cacheDir)
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        tmpFile
                                    )
                                    cameraUri.value = uri
                                    takePictureLauncher.launch(uri)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("File") },
                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Filled.InsertDriveFile, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Message...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        maxLines = 4,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Voice State
                    var isRecording by remember { mutableStateOf(false) }
                    var voicePreviewFile by remember { mutableStateOf<java.io.File?>(null) }
                    var isPlayingPreview by remember { mutableStateOf(false) }
                    val previewPlayer = remember { android.media.MediaPlayer() }

                    // Cleanup on compose dispose
                    DisposableEffect(Unit) {
                        onDispose {
                            if (previewPlayer.isPlaying) previewPlayer.stop()
                            previewPlayer.release()
                        }
                    }

                    if (voicePreviewFile != null) {
                        // Voice Preview UI
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                                .padding(horizontal = 8.dp)
                        ) {
                            // 1. Delete
                            IconButton(onClick = {
                                voicePreviewFile = null
                                if (previewPlayer.isPlaying) previewPlayer.stop()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Discard", tint = MaterialTheme.colorScheme.error)
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            // 2. Play/Stop
                            IconButton(onClick = {
                                if (isPlayingPreview) {
                                    previewPlayer.pause()
                                    isPlayingPreview = false
                                } else {
                                    try {
                                        previewPlayer.reset()
                                        previewPlayer.setDataSource(voicePreviewFile!!.absolutePath)
                                        previewPlayer.prepare()
                                        previewPlayer.start()
                                        isPlayingPreview = true
                                        previewPlayer.setOnCompletionListener { 
                                            isPlayingPreview = false
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }) {
                                Icon(
                                    if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Preview",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.weight(1f))

                            // 3. Send
                            IconButton(
                                onClick = {
                                    val fileToSend = voicePreviewFile
                                    if (fileToSend != null) {
                                        viewModel.sendVoiceMessage(fileToSend, contact.onionAddress)
                                        voicePreviewFile = null
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send Voice", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else if (messageText.isBlank()) {
                         IconButton(
                            onClick = {
                                // Check Permission
                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                
                                if (!hasPermission) {
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    return@IconButton
                                }

                                if (isRecording) {
                                    // Stop and show Preview
                                    val file = viewModel.stopRecording(contact.onionAddress, autoSend = false)
                                    isRecording = false
                                    if (file != null) {
                                        voicePreviewFile = file
                                    }
                                } else {
                                    // Debounce / Safety check
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastMicClickTime > 500) {
                                        lastMicClickTime = currentTime
                                        try {
                                            viewModel.startRecording(context)
                                            isRecording = true
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Cannot start recording", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                if (isRecording) Icons.Default.Stop else Icons.Default.Mic, 
                                contentDescription = "Record Voice",
                                // Container is Green (Primary), Icon is Black
                                tint = if (isRecording) MaterialTheme.colorScheme.onError else Color.Black
                            )
                        }
                    } else {
                            IconButton(
                            onClick = {
                                val replyMsg = replyingToMessage
                                viewModel.sendMessage(
                                    contact.onionAddress, 
                                    messageText,
                                    replyToId = replyMsg?.id,
                                    replyToContent = replyMsg?.content?.take(100),
                                    // CRITICAL: Use the SENDER'S timestamp (senderTimestamp) if available (incoming msg),
                                    // otherwise fallback to local timestamp (outgoing msg).
                                    // This ensures we reference the message by the timestamp the creator knows.
                                    replyToTimestamp = replyMsg?.senderTimestamp ?: replyMsg?.timestamp,
                                    replyToSender = replyMsg?.senderOnionAddress
                                )
                                messageText = ""
                                replyingToMessage = null
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Send, 
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    if (showPermissionRationale) {
                        AlertDialog(
                            onDismissRequest = { showPermissionRationale = false },
                            title = { Text("Permission Required") },
                            text = { Text("This app needs microphone access to send voice messages. Please enable it.") },
                            confirmButton = {
                                TextButton(onClick = { 
                                    showPermissionRationale = false
                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }) { Text("Retry") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showPermissionRationale = false }) { Text("Cancel") }
                            }
                        )
                    }
                }
            }
            
            // Debug Log Dialog (Kept simple)
            if (showLogs) {
                // Collect logs ONLY when dialog is open to improve performance
                val debugLogs by viewModel.debugLogs.collectAsState()
                
                AlertDialog(
                    onDismissRequest = { showLogs = false },
                    title = { Text("Debug Details") },
                    text = {
                        Column {
                            Text("My Address:", style = MaterialTheme.typography.labelMedium)
                            Text(myOnionAddress ?: "Generating...", style = MaterialTheme.typography.bodySmall)
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Logs:", style = MaterialTheme.typography.labelMedium)
                            Box(modifier = Modifier.height(200.dp).border(1.dp, MaterialTheme.colorScheme.outline).verticalScroll(rememberScrollState()).padding(4.dp)) {
                                Text(debugLogs, style = MaterialTheme.typography.labelSmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    },
                    confirmButton = {
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        TextButton(onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(debugLogs))
                        }) {
                            Text("Copy Logs")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogs = false }) { Text("Close") }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: Message, 
    isSelected: Boolean, 
    selectionMode: Boolean,
    onSelect: (Int) -> Unit,
    onImageClick: (String) -> Unit,
    onOpenFile: (String) -> Unit,

    onRetry: (Message) -> Unit,
    onReply: (Message) -> Unit = {},
    onReplyClick: (Int) -> Unit = {},
    isPlaying: Boolean = false,
    onPlayAudio: (String) -> Unit = {},
    onStopAudio: () -> Unit = {}
) {
    val isMine = message.isMine
    val bubbleColor = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isMine) {
        androidx.compose.foundation.shape.RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        androidx.compose.foundation.shape.RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    
    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    val timeString = timeFormat.format(java.util.Date(message.timestamp))
    
    // Swipe state for reply gesture
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val swipeThreshold = 180f // Increased threshold for "longer" feel

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        // Reply Indicator (Behind the message)
        androidx.compose.animation.AnimatedVisibility(
            visible = offsetX.value > 40f,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
             Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
             ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Reply,
                    contentDescription = "Reply",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
             }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // Only allow right swipe (positive delta)
                        coroutineScope.launch {
                             // Resistance factor for better feel
                            val currentVal = offsetX.value
                            val resistance = 1f - (currentVal / swipeThreshold)
                            val actualDelta = delta * resistance.coerceAtLeast(0.5f)
                            val newOffset = (currentVal + actualDelta).coerceIn(0f, swipeThreshold)
                            offsetX.snapTo(newOffset)
                        }
                    },
                    onDragStopped = {
                        if (offsetX.value >= 100f) { // Trigger point
                            onReply(message)
                        }
                        // Animate back with bounce
                        coroutineScope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                )
                            )
                        } 
                    }
                ),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            shadowElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(
                    onClick = { 
                        if (selectionMode) {
                            onSelect(message.id)
                        } else if (message.type == Message.TYPE_IMAGE && message.mediaPath != null) {
                            onImageClick(message.mediaPath)
                        } else if (message.status == Message.STATUS_FAILED && isMine) {
                            onRetry(message)
                        }
                    },
                    onLongClick = {
                        onSelect(message.id)
                    }
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Show quoted reply if this message is a reply
                if (message.replyToContent != null) {
                    Surface(
                        color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                message.replyToMessageId?.let(onReplyClick)
                            }
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(30.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.replyToContent,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
                
                if (message.type == Message.TYPE_IMAGE && message.mediaPath != null) {
                    AsyncImage(
                        model = message.mediaPath,
                        contentDescription = "Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (message.type == Message.TYPE_AUDIO && message.mediaPath != null) {
                    // Audio UI using ViewModel callbacks
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    onStopAudio()
                                } else {
                                    onPlayAudio(message.mediaPath)
                                }
                            }
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Play/Stop Audio",
                                tint = textColor
                            )
                        }
                        Text(
                            if (isPlaying) "Playing..." else "Voice Message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                } else if (message.type == Message.TYPE_FILE && message.mediaPath != null) {
                    // File UI
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier
                            .clickable {
                                onOpenFile(message.mediaPath)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Icon(
                             androidx.compose.material.icons.Icons.Filled.InsertDriveFile, 
                             contentDescription = "File",
                             tint = textColor
                         )
                         Spacer(modifier = Modifier.width(8.dp))
                         Column {
                             Text(
                                 text = message.content.removePrefix("📎 "),
                                 style = MaterialTheme.typography.bodyMedium,
                                 color = textColor,
                                 maxLines = 1,
                                 overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                             )
                             Text(
                                 text = "Tap to open",
                                 style = MaterialTheme.typography.labelSmall,
                                 color = textColor.copy(alpha = 0.7f)
                             )
                         }
                    }
                } else {
                    Text(
                        text = message.content, 
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeString,
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        val statusIcon = when (message.status) {
                            Message.STATUS_SENDING -> Icons.Default.AccessTime
                            Message.STATUS_FAILED -> Icons.Default.Error
                            else -> if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done
                        }
                        
                        val statusTint = when (message.status) {
                            Message.STATUS_FAILED -> MaterialTheme.colorScheme.error
                            else -> if (message.isRead) Color(0xFF34B7F1) else textColor.copy(alpha = 0.7f)
                        }
                        
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = "Status",
                            tint = statusTint,
                            modifier = Modifier.size(16.dp).let { 
                                if (message.status == Message.STATUS_FAILED) it.clickable { onRetry(message) } else it 
                            }
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun DateHeader(timestamp: Long) {
    val dateString = remember(timestamp) {
        val date = java.util.Date(timestamp)
        val now = java.util.Date()
        val calendar = java.util.Calendar.getInstance()
        calendar.time = now
        
        val todayYear = calendar.get(java.util.Calendar.YEAR)
        val todayDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        calendar.time = date
        val msgYear = calendar.get(java.util.Calendar.YEAR)
        val msgDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        when {
            todayYear == msgYear && todayDay == msgDay -> "Today"
            todayYear == msgYear && todayDay - msgDay == 1 -> "Yesterday"
            else -> {
                val format = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
                format.format(date)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = dateString,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun isSameDay(t1: Long, t2: Long): Boolean {
    val c1 = java.util.Calendar.getInstance()
    val c2 = java.util.Calendar.getInstance()
    c1.timeInMillis = t1
    c2.timeInMillis = t2
    return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
           c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR)
}
