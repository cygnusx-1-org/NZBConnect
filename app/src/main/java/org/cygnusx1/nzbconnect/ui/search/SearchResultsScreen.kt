package org.cygnusx1.nzbconnect.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.cygnusx1.nzbconnect.R
import org.cygnusx1.nzbconnect.domain.SearchResult
import org.cygnusx1.nzbconnect.ui.formatAgeShort
import org.cygnusx1.nzbconnect.ui.formatSize

private val Badge4kColor = Color(0xFF6A4CC4)
private val BadgeHdrColor = Color(0xFF2E8B7F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onConsumeMessage: () -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onSearch: () -> Unit,
    onSetSort: (SortOption) -> Unit,
    onSetFilter: (ResultFilter) -> Unit,
    onClearFilter: () -> Unit,
    onReload: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(state.resultsLoading) {
        if (!state.resultsLoading) isRefreshing = false
    }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            onConsumeMessage()
        }
    }

    val ctx = state.resultContext
    val displayed = remember(state.page, state.filter, state.sort) {
        state.page?.results.orEmpty().applyFilterAndSort(state.filter, state.sort)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ctx?.indexer?.name ?: "Results",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(painterResource(R.drawable.ic_sort), contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onSetSort(option)
                                        sortMenuOpen = false
                                    },
                                    trailingIcon = {
                                        if (option == state.sort) {
                                            Icon(painterResource(R.drawable.ic_sort), contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { filterOpen = true }) {
                        Icon(
                            painterResource(R.drawable.ic_filter_list),
                            contentDescription = "Filter",
                            tint = if (state.filter.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onSearch) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                onReload()
            },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = (ctx?.breadcrumb ?: "").uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${formatCount(state.page?.total ?: displayed.size)} RESULTS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when {
                    state.resultsLoading -> Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    state.resultsError != null -> Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            state.resultsError,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    displayed.isEmpty() -> Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "No results",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        val rowIcon = categoryIcon(ctx?.breadcrumb)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            itemsIndexed(displayed, key = { i, r -> "$i-${r.guid}" }) { _, result ->
                                ResultRow(result = result, icon = rowIcon, onClick = { onResultClick(result) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (filterOpen) {
        FilterSheet(
            filter = state.filter,
            breadcrumb = ctx?.breadcrumb.orEmpty(),
            onApply = {
                onSetFilter(it)
                filterOpen = false
            },
            onClear = {
                onClearFilter()
                filterOpen = false
            },
            onDismiss = { filterOpen = false },
        )
    }
}

@Composable
private fun ResultRow(result: SearchResult, @DrawableRes icon: Int, onClick: () -> Unit) {
    val badges = badgesOf(result.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (result.posterUrl != null) {
            AsyncImage(
                model = result.posterUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 60.dp, height = 90.dp).clip(RoundedCornerShape(4.dp)),
            )
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatSize(result.sizeBytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (badges.is4k) QualityBadge("4K", Badge4kColor)
                    if (badges.isHdr) QualityBadge("HDR", BadgeHdrColor)
                }
                Text(
                    formatAgeShort(result.pubDateMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Leading icon for result rows, derived from the searched category breadcrumb. */
@DrawableRes
private fun categoryIcon(breadcrumb: String?): Int = categoryIconOrNull(breadcrumb.orEmpty().substringBefore(" > "))
    ?: R.drawable.ic_insert_drive_file

@Composable
private fun QualityBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun formatCount(n: Int): String = "%,d".format(n)
