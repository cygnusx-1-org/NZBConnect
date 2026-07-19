package org.cygnusx1.nzbconnect.ui.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.cygnusx1.nzbconnect.R
import org.cygnusx1.nzbconnect.domain.Indexer
import org.cygnusx1.nzbconnect.ui.AppBrandTitle
import org.cygnusx1.nzbconnect.ui.IndexerLogo
import org.cygnusx1.nzbconnect.ui.IndexerPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchIndexersScreen(
    indexers: List<Indexer>,
    onSelect: (Indexer) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppBrandTitle("Search") },
            )
        },
    ) { padding ->
        if (indexers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "No enabled indexers. Add one under Settings.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(indexers, key = { it.id }) { indexer ->
                IndexerCard(indexer = indexer, onClick = { onSelect(indexer) })
            }
        }
    }
}

@Composable
private fun IndexerCard(indexer: Indexer, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val preset = IndexerPresets.presetFor(indexer.baseUrl)
            if (preset != null) {
                IndexerLogo(preset.logoRes, tint = preset.tintLogo, modifier = Modifier.size(40.dp))
            } else {
                Icon(
                    painterResource(R.drawable.ic_dns),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column {
                Text(
                    indexer.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    hostOf(indexer.baseUrl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').ifBlank { url }
