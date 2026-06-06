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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.cygnusx1.nzbconnect.R
import org.cygnusx1.nzbconnect.domain.HistoryItem
import org.cygnusx1.nzbconnect.domain.QueueItem
import org.cygnusx1.nzbconnect.domain.QueueSnapshot
import org.cygnusx1.nzbconnect.domain.SabInfo
import org.cygnusx1.nzbconnect.domain.SabWarning
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

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) viewModel.refreshHistory()
    }

    LaunchedEffect(showSidebar) {
        if (showSidebar) viewModel.loadSabInfo()
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
                        Text(
                            "Downloads",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
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
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, null) },
                                    text = { Text("Set on finish action") },
                                    onClick = { showFinishActionDialog = true; menuExpanded = false },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.RssFeed, null) },
                                    text = { Text("Read all RSS feeds now") },
                                    onClick = { viewModel.readRssNow(); menuExpanded = false },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                                    text = { Text("Show server details") },
                                    onClick = { showSidebar = true; menuExpanded = false },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.RestartAlt, null) },
                                    text = { Text("Restart SABnzbd") },
                                    onClick = { viewModel.restartSabnzbd(); menuExpanded = false },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Language, null) },
                                    text = { Text("View SABnzbd on web") },
                                    onClick = {
                                        val url = viewModel.sabWebUrl
                                        if (url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        menuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    text = { Text("SABnzbd settings") },
                                    onClick = { onNavigateToSettings(); menuExpanded = false },
                                )
                            }
                        }
                    },
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
                info = state.sabInfo,
                loading = state.sabInfoLoading,
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
    }
}

@Composable
private fun QueueTab(state: QueueUiState, viewModel: QueueViewModel) {
    val snapshot = state.snapshot
    when {
        snapshot == null && state.error != null -> Centered(state.error!!, isError = true)
        snapshot == null -> Centered("Loading…")
        snapshot.items.isEmpty() -> Centered("Queue is empty")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(snapshot.items, key = { it.id }) { item ->
                QueueRow(
                    item = item,
                    onPause = { viewModel.pauseItem(item.id) },
                    onResume = { viewModel.resumeItem(item.id) },
                    onDelete = { viewModel.deleteItem(item.id, deleteFiles = true) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { item.percentage / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${item.percentage}%  •  ${item.sizeLeft} left  •  ${item.status}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    val paused = item.status.equals("paused", ignoreCase = true)
                    IconButton(onClick = if (paused) onResume else onPause) {
                        Icon(
                            imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) "Resume" else "Pause",
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(state: QueueUiState, viewModel: QueueViewModel) {
    when {
        state.loadingHistory && state.history.isEmpty() -> Centered("Loading…")
        state.history.isEmpty() -> Centered("No history")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.history, key = { it.id }) { item ->
                HistoryRow(
                    item = item,
                    onDelete = { viewModel.deleteHistoryItem(item.id) },
                    onDeleteWithFiles = { viewModel.deleteHistoryItem(item.id, deleteFiles = true) },
                )
            }
        }
    }
}

private val StatusGreen = Color(0xFF66BB6A)
private val DateAmber = Color(0xFFFFB300)

@Composable
private fun statusColor(status: String): Color = when {
    status.equals("Completed", ignoreCase = true) -> StatusGreen
    status.equals("Failed", ignoreCase = true) -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun HistoryRow(item: HistoryItem, onDelete: () -> Unit, onDeleteWithFiles: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showRemoveSubmenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        val eta = snapshot.timeLeft.ifBlank { "---" }
        val size = snapshot.sizeLeft.ifBlank { "---" }
        Text(
            text = if (isActive && snapshot.sizeLeft.isNotBlank()) "$eta  •  $size left"
                   else "$eta  •  $size",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SabInfoSidebar(info: SabInfo?, loading: Boolean, onDismiss: () -> Unit) {
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
                    Image(
                        painter = painterResource(R.drawable.sabnzbd_logo),
                        contentDescription = "SABnzbd",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2175f / 606f)
                            .padding(bottom = 12.dp),
                    )
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
private fun WarningCard(warning: SabWarning) {
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
