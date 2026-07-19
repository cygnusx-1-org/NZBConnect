package org.cygnusx1.nzbconnect.ui.search

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import org.cygnusx1.nzbconnect.domain.NewznabCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchCategoriesScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onCategory: (NewznabCategory) -> Unit,
    onBrowseAll: (() -> Unit)?,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val title = state.selectedIndexer?.name ?: "Categories"
    val breadcrumb = state.categoryPath.joinToString(" > ") { it.name }.ifBlank { "CATEGORIES" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Update categories")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = "Search")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = breadcrumb.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                state.categoriesLoading -> Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.categoriesError != null -> Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        state.categoriesError,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                else -> {
                    // Subcategories reuse their top-level category's icon; only the root
                    // level derives an icon per row from each category's own name.
                    val rootName = state.categoryPath.firstOrNull()?.name
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (onBrowseAll != null) {
                            item(key = "__all__") {
                                CategoryRow(
                                    name = "All",
                                    icon = iconFor(rootName ?: ""),
                                    onClick = onBrowseAll,
                                )
                            }
                        }
                        items(state.currentChildren, key = { it.id }) { category ->
                            CategoryRow(
                                name = category.name,
                                icon = iconFor(rootName ?: category.name),
                                onClick = { onCategory(category) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@DrawableRes
private fun iconFor(name: String): Int = categoryIconOrNull(name) ?: R.drawable.ic_category
