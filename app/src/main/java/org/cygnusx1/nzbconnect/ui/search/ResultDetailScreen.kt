package org.cygnusx1.nzbconnect.ui.search

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.cygnusx1.nzbconnect.domain.DownloadClientType
import org.cygnusx1.nzbconnect.domain.SearchResult
import org.cygnusx1.nzbconnect.domain.displayName
import org.cygnusx1.nzbconnect.ui.formatAge
import org.cygnusx1.nzbconnect.ui.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultDetailScreen(
    result: SearchResult?,
    categories: List<String>,
    clientName: String,
    availableClients: List<DownloadClientType>,
    onLoadCategories: () -> Unit,
    onBack: () -> Unit,
    onSend: (type: DownloadClientType, category: String?) -> Unit,
) {
    if (result == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Details") }) }) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("Nothing selected", modifier = Modifier.align(Alignment.Center))
            }
        }
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    var category by remember { mutableStateOf<String?>(null) }
    var showSendMenu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onLoadCategories() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text(result.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                },
                actions = {
                    Box {
                        TextButton(
                            enabled = availableClients.isNotEmpty(),
                            onClick = {
                                if (availableClients.size == 1) {
                                    onSend(availableClients.first(), category)
                                } else {
                                    showSendMenu = true
                                }
                            },
                        ) {
                            val label = if (availableClients.size == 1) {
                                "SEND TO ${availableClients.first().displayName.uppercase()}"
                            } else {
                                "SEND TO…"
                            }
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = showSendMenu, onDismissRequest = { showSendMenu = false }) {
                            availableClients.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.displayName) },
                                    onClick = { showSendMenu = false; onSend(type, category) },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(formatSize(result.sizeBytes), fontWeight = FontWeight.Bold)
                Text(
                    formatAge(result.pubDateMillis),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Details") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("IMDb") })
            }
            when (tab) {
                0 -> DetailsTab(
                    result = result,
                    categories = categories,
                    clientName = clientName,
                    selectedCategory = category,
                    onCategoryChange = { category = it },
                )

                else -> ImdbTab(result = result)
            }
        }
    }
}

@Composable
private fun DetailsTab(
    result: SearchResult,
    categories: List<String>,
    clientName: String,
    selectedCategory: String?,
    onCategoryChange: (String?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DetailLine("Title", result.cleanTitle())
        result.displayYear()?.let { DetailLine("Year", it.toString()) }
        DetailLine("Release", result.title)
        DetailLine("Size", formatSize(result.sizeBytes))
        DetailLine("Age", formatAge(result.pubDateMillis))
        result.categoryName.takeIf { it.isNotBlank() }?.let { DetailLine("Category", it) }
        if (result.grabs > 0) DetailLine("Grabs", result.grabs.toString())
        DetailLine("Indexer", result.indexerName)

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        Text("$clientName category", style = MaterialTheme.typography.labelLarge)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selectedCategory ?: "Default category")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Default category") }, onClick = {
                    onCategoryChange(null); expanded = false
                })
                categories.forEach { cat ->
                    DropdownMenuItem(text = { Text(cat) }, onClick = {
                        onCategoryChange(cat); expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ImdbTab(result: SearchResult) {
    val url = result.imdbUrl()

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(url)
            }
        },
    )
}
