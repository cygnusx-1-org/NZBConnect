package org.cygnusx1.nzbconnect.ui.queue

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import org.cygnusx1.nzbconnect.R
import org.cygnusx1.nzbconnect.domain.HistoryItem
import org.cygnusx1.nzbconnect.domain.QueueItem
import org.cygnusx1.nzbconnect.domain.QueueSnapshot
import org.cygnusx1.nzbconnect.domain.DownloadClientType
import org.cygnusx1.nzbconnect.domain.DownloadPriority
import org.cygnusx1.nzbconnect.domain.ServerInfo
import org.cygnusx1.nzbconnect.domain.ServerWarning
import org.cygnusx1.nzbconnect.domain.displayName
import org.cygnusx1.nzbconnect.ui.AppBrandTitle
import org.cygnusx1.nzbconnect.ui.formatHistoryDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onNavigateToSettings: () -> Unit, viewModel: QueueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSidebar by remember { mutableStateOf(false) }
    var showFinishActionDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSpeedLimitDialog by remember { mutableStateOf(false) }
    var showCustomSpeedDialog by remember { mutableStateOf(false) }
    var customSpeedInput by remember { mutableStateOf("") }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) viewModel.refreshHistory()
    }

    LaunchedEffect(showSidebar) {
        if (showSidebar) viewModel.loadServerInfo()
    }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AppBrandTitle("Downloads")
                            if (state.availableClients.size > 1) {
                                ServiceSelector(
                                    clientName = state.clientName,
                                    clients = state.availableClients,
                                    onSelect = viewModel::selectClient,
                                )
                            }
                        }
                    },
                    actions = {
                        state.snapshot?.let { snap ->
                            QueueStats(snap, onClick = { showSidebar = true })
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                state.snapshot?.let {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(if (it.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null) },
                                        text = { Text(if (it.paused) "Resume all" else "Pause all") },
                                        onClick = { viewModel.togglePauseAll(); menuExpanded = false },
                                    )
                                }
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, null) },
                                    text = { Text("Clear history") },
                                    onClick = { viewModel.clearHistory(); menuExpanded = false },
                                )
                                if (state.capabilities.finishAction) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, null) },
                                        text = { Text("Set on finish action") },
                                        onClick = { showFinishActionDialog = true; menuExpanded = false },
                                    )
                                }
                                if (state.capabilities.refreshFeeds) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.RssFeed, null) },
                                        text = { Text("Read all RSS feeds now") },
                                        onClick = { viewModel.refreshFeeds(); menuExpanded = false },
                                    )
                                }
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                                    text = { Text("Show server details") },
                                    onClick = { showSidebar = true; menuExpanded = false },
                                )
                                if (state.capabilities.restart) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.RestartAlt, null) },
                                        text = { Text("Restart ${state.clientName}") },
                                        onClick = { viewModel.restart(); menuExpanded = false },
                                    )
                                }
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Language, null) },
                                    text = { Text("View ${state.clientName} on web") },
                                    onClick = {
                                        val url = viewModel.webUrl
                                        if (url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        menuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    text = { Text("${state.clientName} settings") },
                                    onClick = { onNavigateToSettings(); menuExpanded = false },
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                DownloadsActionBar(
                    state = state,
                    currentPage = pagerState.currentPage,
                    onSort = { showSortDialog = true },
                    onToggleMultiSelect = viewModel::toggleHistoryMultiSelect,
                    onTogglePauseAll = viewModel::togglePauseAll,
                    onSpeedLimit = { showSpeedLimitDialog = true },
                    onDeleteSelected = viewModel::deleteSelectedHistory,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Queue") },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("History") },
                    )
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> PullToRefreshBox(
                            isRefreshing = state.refreshingQueue,
                            onRefresh = viewModel::refreshQueue,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            QueueTab(state, viewModel)
                        }
                        else -> PullToRefreshBox(
                            isRefreshing = state.loadingHistory,
                            onRefresh = viewModel::refreshHistory,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            HistoryTab(state, viewModel)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSidebar,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { showSidebar = false },
            )
        }

        AnimatedVisibility(
            visible = showSidebar,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd).statusBarsPadding(),
        ) {
            SabInfoSidebar(
                info = state.serverInfo,
                loading = state.serverInfoLoading,
                clientName = state.clientName,
                onDismiss = { showSidebar = false },
            )
        }

        if (showFinishActionDialog) {
            FinishActionDialog(
                onSelect = { action ->
                    viewModel.setFinishAction(action)
                    showFinishActionDialog = false
                },
                onDismiss = { showFinishActionDialog = false },
            )
        }

        if (showSortDialog) {
            SortDialog(
                currentPage = pagerState.currentPage,
                queueSort = state.queueSort,
                historySort = state.historySort,
                onQueueSort = { viewModel.setQueueSort(it); showSortDialog = false },
                onHistorySort = { viewModel.setHistorySort(it); showSortDialog = false },
                onDismiss = { showSortDialog = false },
            )
        }

        if (showSpeedLimitDialog) {
            SpeedLimitDialog(
                currentLimit = state.snapshot?.speedLimit ?: if (state.capabilities.speedLimitIsPercentage) 100 else 0,
                isPercentage = state.capabilities.speedLimitIsPercentage,
                onSelect = { value -> viewModel.setSpeedLimit(value); showSpeedLimitDialog = false },
                onCustom = { showSpeedLimitDialog = false; customSpeedInput = ""; showCustomSpeedDialog = true },
                onDismiss = { showSpeedLimitDialog = false },
            )
        }

        if (showCustomSpeedDialog) {
            val isPercentage = state.capabilities.speedLimitIsPercentage
            AlertDialog(
                onDismissRequest = { showCustomSpeedDialog = false },
                title = { Text("Custom Speed Limit") },
                text = {
                    OutlinedTextField(
                        value = customSpeedInput,
                        onValueChange = { customSpeedInput = it.filter { c -> c.isDigit() } },
                        label = { Text(if (isPercentage) "Percentage (1–100)" else "Limit (KB/s, 0 = unlimited)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = if (isPercentage) {
                            customSpeedInput.toIntOrNull()?.coerceIn(1, 100)
                        } else {
                            customSpeedInput.toIntOrNull()?.coerceAtLeast(0)
                        }
                        if (value != null) { viewModel.setSpeedLimit(value); showCustomSpeedDialog = false }
                    }) { Text("Set") }
                },
                dismissButton = { TextButton(onClick = { showCustomSpeedDialog = false }) { Text("Cancel") } },
            )
        }
    }
}

private enum class QueueItemSubmenu { NONE, PRIORITY, MOVE }

/** Per-queue-item overflow menu: set priority, set password, move, rename. */
@Composable
private fun QueueItemMenu(
    onSetPriority: (DownloadPriority) -> Unit,
    onSetPassword: (String) -> Unit,
    onRename: (String) -> Unit,
    currentName: String,
    onMoveTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveEnd: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var submenu by remember { mutableStateOf(QueueItemSubmenu.NONE) }
    var showPassword by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }

    fun close() { expanded = false; submenu = QueueItemSubmenu.NONE }

    Box {
        IconButton(
            onClick = { submenu = QueueItemSubmenu.NONE; expanded = true },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { close() }) {
            when (submenu) {
                QueueItemSubmenu.NONE -> {
                    DropdownMenuItem(
                        text = { Text("Set priority") },
                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        onClick = { submenu = QueueItemSubmenu.PRIORITY },
                    )
                    DropdownMenuItem(
                        text = { Text("Set password") },
                        onClick = { expanded = false; showPassword = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Move") },
                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        onClick = { submenu = QueueItemSubmenu.MOVE },
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { expanded = false; showRename = true },
                    )
                }
                QueueItemSubmenu.PRIORITY -> {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) },
                        text = { Text("Set Priority", fontWeight = FontWeight.Bold) },
                        onClick = { submenu = QueueItemSubmenu.NONE },
                    )
                    DownloadPriority.values().forEach { priority ->
                        DropdownMenuItem(
                            text = { Text(priority.label) },
                            onClick = { onSetPriority(priority); close() },
                        )
                    }
                }
                QueueItemSubmenu.MOVE -> {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) },
                        text = { Text("Move Options", fontWeight = FontWeight.Bold) },
                        onClick = { submenu = QueueItemSubmenu.NONE },
                    )
                    DropdownMenuItem(text = { Text("Move to top") }, onClick = { onMoveTop(); close() })
                    DropdownMenuItem(text = { Text("Move up 10") }, onClick = { onMoveUp(); close() })
                    DropdownMenuItem(text = { Text("Move down 10") }, onClick = { onMoveDown(); close() })
                    DropdownMenuItem(text = { Text("Move to end") }, onClick = { onMoveEnd(); close() })
                }
            }
        }
    }

    if (showPassword) {
        TextInputDialog(
            title = "Set password",
            label = "Password",
            initial = "",
            isPassword = true,
            onConfirm = { onSetPassword(it); showPassword = false },
            onDismiss = { showPassword = false },
        )
    }
    if (showRename) {
        TextInputDialog(
            title = "Rename",
            label = "New name",
            initial = currentName,
            isPassword = false,
            onConfirm = { onRename(it); showRename = false },
            onDismiss = { showRename = false },
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    isPassword: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Dropdown on the Downloads screen to choose which configured client to view. */
@Composable
private fun ServiceSelector(
    clientName: String,
    clients: List<DownloadClientType>,
    onSelect: (DownloadClientType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("$clientName ▾", fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            clients.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = { expanded = false; onSelect(type) },
                )
            }
        }
    }
}

@Composable
private fun DownloadsActionBar(
    state: QueueUiState,
    currentPage: Int,
    onSort: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onTogglePauseAll: () -> Unit,
    onSpeedLimit: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.historyMultiSelect && currentPage == 1) {
                IconButton(onClick = onToggleMultiSelect) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                }
                Text(
                    text = "${state.selectedHistoryIds.size} selected",
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(
                    onClick = onDeleteSelected,
                    enabled = state.selectedHistoryIds.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete selected",
                        tint = if (state.selectedHistoryIds.isNotEmpty())
                            MaterialTheme.colorScheme.error
                        else
                            LocalContentColor.current.copy(alpha = 0.38f),
                    )
                }
            } else {
                IconButton(onClick = onSort) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                if (currentPage == 1) {
                    IconButton(onClick = onToggleMultiSelect) {
                        Icon(
                            Icons.Filled.SelectAll,
                            contentDescription = "Multi-select",
                            tint = if (state.historyMultiSelect)
                                MaterialTheme.colorScheme.primary
                            else
                                LocalContentColor.current,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                val paused = state.snapshot?.paused == true
                IconButton(onClick = onTogglePauseAll) {
                    Icon(
                        imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (paused) "Resume all" else "Pause all",
                        tint = if (paused) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
                val isPercentage = state.capabilities.speedLimitIsPercentage
                val speedLimit = state.snapshot?.speedLimit ?: if (isPercentage) 100 else 0
                val isLimited = if (isPercentage) speedLimit != 100 else speedLimit > 0
                BadgedBox(
                    badge = {
                        if (isLimited) {
                            val label = if (isPercentage) {
                                "$speedLimit%"
                            } else if (speedLimit >= 1024) {
                                "${speedLimit / 1024}M"
                            } else {
                                "${speedLimit}K"
                            }
                            Badge { Text(label, style = MaterialTheme.typography.labelSmall) }
                        }
                    },
                ) {
                    IconButton(onClick = onSpeedLimit) {
                        Icon(Icons.Filled.Speed, contentDescription = "Speed limit")
                    }
                }
            }
        }
    }
}

@Composable
private fun SortDialog(
    currentPage: Int,
    queueSort: QueueSortOrder,
    historySort: HistorySortOrder,
    onQueueSort: (QueueSortOrder) -> Unit,
    onHistorySort: (HistorySortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column {
                if (currentPage == 0) {
                    QueueSortOrder.entries.forEach { order ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onQueueSort(order) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = queueSort == order, onClick = { onQueueSort(order) })
                            Text(order.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                } else {
                    HistorySortOrder.entries.forEach { order ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHistorySort(order) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = historySort == order, onClick = { onHistorySort(order) })
                            Text(order.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SpeedLimitDialog(
    currentLimit: Int,
    isPercentage: Boolean,
    onSelect: (Int) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    // SAB: percentages of the configured max. NZBGet: absolute KB/s (0 = unlimited).
    val presets = if (isPercentage) listOf(100, 95, 80, 60, 40, 20) else listOf(0, 51200, 20480, 10240, 5120, 1024)
    fun label(value: Int): String = when {
        isPercentage && value == 100 -> "100% (unlimited)"
        isPercentage -> "$value%"
        value == 0 -> "Unlimited"
        value >= 1024 -> "${value / 1024} MB/s"
        else -> "$value KB/s"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speed Limit") },
        text = {
            Column {
                Text(
                    "Current: ${label(currentLimit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                presets.forEach { preset ->
                    Text(
                        text = label(preset),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(preset) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (currentLimit == preset) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentLimit == preset) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                }
                Text(
                    text = "Custom…",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCustom() }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QueueTab(state: QueueUiState, viewModel: QueueViewModel) {
    var localItems by remember { mutableStateOf(state.sortedQueueItems) }
    var isAnyDragging by remember { mutableStateOf(false) }

    LaunchedEffect(state.sortedQueueItems) {
        if (!isAnyDragging) localItems = state.sortedQueueItems
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        localItems = localItems.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    when {
        state.snapshot == null && state.error != null -> Centered(state.error!!, isError = true)
        state.snapshot == null -> Centered("Loading…")
        localItems.isEmpty() -> Centered("Queue is empty")
        else -> LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(localItems, key = { it.id }) { item ->
                ReorderableItem(reorderState, key = item.id) { isDragging ->
                    QueueRow(
                        item = item,
                        isDragging = isDragging,
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStarted = { isAnyDragging = true },
                            onDragStopped = {
                                isAnyDragging = false
                                val newIdx = localItems.indexOfFirst { it.id == item.id }
                                if (newIdx >= 0) viewModel.moveItem(item.id, newIdx)
                            },
                        ),
                        onPause = { viewModel.pauseItem(item.id) },
                        onResume = { viewModel.resumeItem(item.id) },
                        onDelete = { viewModel.deleteItem(item.id, deleteFiles = true) },
                        onSetPriority = { priority -> viewModel.setItemPriority(item.id, priority) },
                        onSetPassword = { password -> viewModel.setItemPassword(item.id, item.name, password) },
                        onRename = { newName -> viewModel.renameItem(item.id, newName) },
                        onMoveTop = { viewModel.moveItemToTop(item.id) },
                        onMoveUp = { viewModel.moveItemUp(item.id) },
                        onMoveDown = { viewModel.moveItemDown(item.id) },
                        onMoveEnd = { viewModel.moveItemToEnd(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isDragging: Boolean,
    dragHandleModifier: Modifier,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onSetPriority: (DownloadPriority) -> Unit,
    onSetPassword: (String) -> Unit,
    onRename: (String) -> Unit,
    onMoveTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveEnd: () -> Unit,
) {
    val paused = item.status.equals("paused", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
                Text(
                    item.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (paused) PausedAmber else Color.Unspecified,
                )
                LinearProgressIndicator(
                    progress = { item.percentage / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = if (paused) PausedAmber else MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val meta = buildList {
                        add("${item.percentage}%")
                        add("${item.sizeLeft} left")
                        if (item.timeLeft.isNotBlank() && item.timeLeft != "0:00:00") add(item.timeLeft)
                        if (item.category.isNotBlank()) add(item.category)
                        add(item.status)
                    }.joinToString("  •  ")
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Row {
                        IconButton(
                            onClick = if (paused) onResume else onPause,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (paused) "Resume" else "Pause",
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                        QueueItemMenu(
                            onSetPriority = onSetPriority,
                            onSetPassword = onSetPassword,
                            onRename = onRename,
                            currentName = item.name,
                            onMoveTop = onMoveTop,
                            onMoveUp = onMoveUp,
                            onMoveDown = onMoveDown,
                            onMoveEnd = onMoveEnd,
                        )
                    }
                }
            }
            val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(
                modifier = dragHandleModifier
                    .fillMaxHeight()
                    .width(20.dp),
            ) {
                val dotR = 2.dp.toPx()
                val colGap = 5.5.dp.toPx()
                val rowGap = 10.dp.toPx()
                val vertPad = 14.dp.toPx()
                val startX = size.width / 2f - colGap / 2f
                var y = vertPad
                while (y <= size.height - vertPad) {
                    drawCircle(dotColor, dotR, Offset(startX, y))
                    drawCircle(dotColor, dotR, Offset(startX + colGap, y))
                    y += rowGap
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(state: QueueUiState, viewModel: QueueViewModel) {
    val items = state.sortedHistoryItems
    when {
        state.loadingHistory && items.isEmpty() -> Centered("Loading…")
        items.isEmpty() -> Centered("No history")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { item ->
                HistoryRow(
                    item = item,
                    multiSelect = state.historyMultiSelect,
                    selected = item.id in state.selectedHistoryIds,
                    onSelect = { viewModel.toggleHistoryItemSelection(item.id) },
                    onDelete = { viewModel.deleteHistoryItem(item.id) },
                    onDeleteWithFiles = { viewModel.deleteHistoryItem(item.id, deleteFiles = true) },
                )
            }
        }
    }
}

private val StatusGreen = Color(0xFF66BB6A)
private val DateAmber = Color(0xFFFFB300)
private val PausedAmber = Color(0xFFFFC107)

@Composable
private fun statusColor(status: String): Color = when {
    status.equals("Completed", ignoreCase = true) -> StatusGreen
    status.equals("Failed", ignoreCase = true) -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    multiSelect: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onDeleteWithFiles: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showRemoveSubmenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (multiSelect) Modifier.clickable { onSelect() } else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiSelect) {
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.status,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor(item.status),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.size,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val date = formatHistoryDate(item.completedMillis)
                        if (date.isNotBlank()) {
                            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                date,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DateAmber,
                            )
                        }
                    }
                }
                item.failMessage.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (!multiSelect) {
                Box {
                    IconButton(onClick = { showRemoveSubmenu = false; menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false; showRemoveSubmenu = false },
                    ) {
                        if (!showRemoveSubmenu) {
                            DropdownMenuItem(
                                text = { Text("Copy title") },
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(item.name))
                                    menuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove") },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                                onClick = { showRemoveSubmenu = true },
                            )
                        } else {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) },
                                text = { Text("Remove Options", fontWeight = FontWeight.Bold) },
                                onClick = { showRemoveSubmenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from history") },
                                onClick = { onDelete(); menuExpanded = false; showRemoveSubmenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove and delete files") },
                                onClick = { onDeleteWithFiles(); menuExpanded = false; showRemoveSubmenu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinishActionDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        "" to "Nothing",
        "pause" to "Pause",
        "shutdown" to "Shutdown",
        "hibernate" to "Hibernate",
        "suspend" to "Suspend",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("On Finish") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QueueStats(snapshot: QueueSnapshot, onClick: () -> Unit) {
    val isActive = !snapshot.paused && snapshot.speed.isNotBlank() && snapshot.speed != "0"
    val speedText = when {
        snapshot.paused -> "Paused"
        !isActive -> "Idle"
        else -> snapshot.speed
    }
    val accentColor = when {
        snapshot.paused -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier.padding(end = 4.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = speedText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )
        // Show a dash for zero/blank values (idle) instead of "0:00:00" / "0 B", like NZBGet.
        fun dashIfEmpty(v: String): String =
            if (v.isBlank() || v == "0" || v == "0 B" || v == "0:00:00") "---" else v
        val eta = dashIfEmpty(snapshot.timeLeft)
        val size = dashIfEmpty(snapshot.sizeLeft)
        Text(
            text = if (isActive && size != "---") "$eta  •  $size left"
                   else "$eta  •  $size",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SabInfoSidebar(info: ServerInfo?, loading: Boolean, clientName: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.85f)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        tonalElevation = 8.dp,
    ) {
        if (loading && info == null) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        } else if (info == null) {
            Box(Modifier.fillMaxSize()) {
                Text("No data", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    if (clientName == "SABnzbd") {
                        Image(
                            painter = painterResource(R.drawable.sabnzbd_logo),
                            contentDescription = "SABnzbd",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2175f / 606f)
                                .padding(bottom = 12.dp),
                        )
                    } else {
                        Text(
                            clientName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Today" to info.downloadToday, "This Week" to info.downloadWeek,
                               "This Month" to info.downloadMonth, "Total" to info.downloadTotal)
                            .forEach { (label, value) ->
                                StatCell(label = label, value = value, modifier = Modifier.weight(1f))
                            }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("On Finish" to info.onFinish, "Fr. Space" to info.freeSpace, "Uptime" to info.uptime)
                            .forEach { (label, value) ->
                                StatCell(label = label, value = value, modifier = Modifier.weight(1f))
                            }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Logs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (info.warnings.isEmpty()) {
                    item {
                        Text(
                            "No log entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(info.warnings) { warning ->
                        WarningCard(warning)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WarningCard(warning: ServerWarning) {
    val formattedTime = warning.time.toLongOrNull()
        ?.let { formatHistoryDate(it * 1000) }
        ?: warning.time.ifBlank { null }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(warning.text, style = MaterialTheme.typography.bodySmall)
            if (formattedTime != null) {
                Text(
                    formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Centered(text: String, isError: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
