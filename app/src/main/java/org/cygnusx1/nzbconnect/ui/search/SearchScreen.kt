package org.cygnusx1.nzbconnect.ui.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private object SearchRoutes {
    const val INDEXERS = "indexers"
    const val CATEGORIES = "categories"
    const val RESULTS = "results"
    const val INPUT = "input"
    const val DETAIL = "detail"
}

/** Root of the Search tab: an nzb360-style indexer → category → results → detail flow. */
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = SearchRoutes.INDEXERS,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(SearchRoutes.INDEXERS) {
            SearchIndexersScreen(
                indexers = state.indexers,
                onSelect = { indexer ->
                    viewModel.selectIndexer(indexer)
                    navController.navigate(SearchRoutes.CATEGORIES)
                },
            )
        }
        composable(SearchRoutes.CATEGORIES) {
            SearchCategoriesScreen(
                state = state,
                onBack = { if (!viewModel.popCategory()) navController.popBackStack() },
                onCategory = { category ->
                    if (viewModel.hasChildren(category)) {
                        viewModel.drillInto(category)
                    } else {
                        viewModel.browseCategory(category)
                        navController.navigate(SearchRoutes.RESULTS)
                    }
                },
                onBrowseAll = state.categoryPath.lastOrNull()?.let { parent ->
                    {
                        viewModel.browseCategory(parent)
                        navController.navigate(SearchRoutes.RESULTS)
                    }
                },
                onSearch = {
                    viewModel.openSearchForCurrentLevel()
                    navController.navigate(SearchRoutes.INPUT)
                },
                onRefresh = viewModel::refreshCategories,
            )
        }
        composable(SearchRoutes.RESULTS) {
            SearchResultsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onConsumeMessage = viewModel::consumeMessage,
                onResultClick = { result ->
                    viewModel.selectResult(result)
                    navController.navigate(SearchRoutes.DETAIL)
                },
                onSearch = {
                    state.resultContext?.let { ctx ->
                        viewModel.openSearch(ctx.categoryId, ctx.breadcrumb)
                        navController.navigate(SearchRoutes.INPUT)
                    }
                },
                onSetSort = viewModel::setSort,
                onSetFilter = viewModel::setFilter,
                onClearFilter = viewModel::clearFilter,
                onReload = viewModel::reloadResults,
            )
        }
        composable(SearchRoutes.INPUT) {
            SearchInputScreen(
                scope = state.pendingScope,
                history = viewModel.recentSearches.collectAsStateWithLifecycle().value,
                onBack = { navController.popBackStack() },
                onSubmit = { query ->
                    viewModel.submitSearch(query)
                    navController.navigate(SearchRoutes.RESULTS) {
                        popUpTo(SearchRoutes.INPUT) { inclusive = true }
                    }
                },
                onDeleteHistory = viewModel::deleteHistory,
                onClearHistory = viewModel::clearHistory,
            )
        }
        composable(SearchRoutes.DETAIL) {
            ResultDetailScreen(
                result = state.selectedResult,
                categories = state.clientCategories,
                clientName = state.clientName,
                availableClients = state.availableClients,
                onLoadCategories = viewModel::loadClientCategories,
                onBack = { navController.popBackStack() },
                onSend = { type, category ->
                    viewModel.sendSelectedTo(type, category)
                    navController.popBackStack()
                },
            )
        }
    }
}
