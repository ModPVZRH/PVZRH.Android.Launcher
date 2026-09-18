package com.bepinex.android.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.bepinex.android.R
import com.bepinex.android.market.MarketApi
import com.bepinex.android.settings.AppSettings
import com.bepinex.android.market.MarketCategory
import com.bepinex.android.market.MarketMod
import com.bepinex.android.market.MarketTag
import com.bepinex.android.ui.components.plainTextFromMarkdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    onModClick: (String) -> Unit,
    gameVersion: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferChinese = remember { AppSettings.isChineseUi(context) }
    var mods by remember { mutableStateOf(MarketApi.cachedMods().orEmpty()) }
    var categories by remember { mutableStateOf(MarketApi.cachedCategories().orEmpty()) }
    var tags by remember { mutableStateOf(MarketApi.cachedTags().orEmpty()) }
    var isLoading by remember { mutableStateOf(mods.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(MarketBrowseState.searchExpanded) }
    var searchQuery by remember { mutableStateOf(MarketBrowseState.searchQuery) }
    var filter by remember { mutableStateOf(MarketBrowseState.filter) }
    var sort by remember { mutableStateOf(MarketBrowseState.sort) }
    var selectedAuthor by remember { mutableStateOf(MarketBrowseState.selectedAuthor) }
    var selectedCategoryId by remember { mutableStateOf(MarketBrowseState.selectedCategoryId) }
    var selectedTagId by remember { mutableStateOf(MarketBrowseState.selectedTagId) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
        }
    }

    LaunchedEffect(filter, sort, searchQuery, searchExpanded, selectedAuthor, selectedCategoryId, selectedTagId) {
        MarketBrowseState.filter = filter
        MarketBrowseState.sort = sort
        MarketBrowseState.searchQuery = searchQuery
        MarketBrowseState.searchExpanded = searchExpanded
        MarketBrowseState.selectedAuthor = selectedAuthor
        MarketBrowseState.selectedCategoryId = selectedCategoryId
        MarketBrowseState.selectedTagId = selectedTagId
    }

    fun loadMods(forceRefresh: Boolean) {
        scope.launch {
            if (forceRefresh) {
                isRefreshing = true
            } else if (mods.isEmpty()) {
                isLoading = true
            }
            loadFailed = false
            val result = withContext(Dispatchers.IO) {
                val loaded = MarketApi.loadMods(context, forceRefresh = forceRefresh)
                val loadedCategories = MarketApi.loadCategories(context, forceRefresh = forceRefresh)
                val loadedTags = MarketApi.loadTags(context, forceRefresh = forceRefresh)
                Triple(loaded, loadedCategories, loadedTags)
            }
            val loadedMods = result.first
            tags = result.third.ifEmpty {
                loadedMods.orEmpty().flatMap { it.tags }
                    .filter { it.name.isNotBlank() }
                    .distinctBy { it.id.ifEmpty { it.name } }
            }
            categories = result.second.ifEmpty {
                loadedMods.orEmpty()
                    .mapNotNull { mod ->
                        val id = mod.categoryId.trim()
                        val name = mod.categoryName.trim()
                        if (id.isEmpty() || name.isEmpty()) null
                        else MarketCategory(id = id, name = name)
                    }
                    .distinctBy { it.id }
            }
            if (loadedMods == null) {
                if (mods.isEmpty()) loadFailed = true
                if (forceRefresh) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.market_error),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                mods = loadedMods
                loadFailed = false
            }
            isLoading = false
            isRefreshing = false
        }
    }

    val statsRevision by MarketApi.statsRevision.collectAsState()
    LaunchedEffect(Unit) { loadMods(forceRefresh = false) }
    LaunchedEffect(statsRevision) {
        MarketApi.cachedMods()?.let { mods = it }
    }

    val showingAuthorDirectory = filter == MarketFilter.Author && selectedAuthor == null
    val unknownAuthor = stringResource(R.string.market_unknown_author)
    val filteredMods = remember(
        mods, searchQuery, filter, sort, selectedAuthor, selectedCategoryId, selectedTagId, preferChinese
    ) {
        val query = searchQuery.trim()
        mods.asSequence()
            .filter { mod ->
                selectedCategoryId.isNullOrBlank() || mod.categoryId == selectedCategoryId
            }
            .filter { mod ->
                selectedTagId.isNullOrBlank() ||
                    filter == MarketFilter.Author ||
                    mod.tags.any { it.id == selectedTagId || it.name == selectedTagId }
            }
            .filter { mod ->
                when (filter) {
                    MarketFilter.All -> true
                    MarketFilter.Featured -> mod.isFeatured
                    MarketFilter.Author ->
                        selectedAuthor == null || mod.displayAuthor.trim() == selectedAuthor
                }
            }
            .filter { mod ->
                if (showingAuthorDirectory || query.isEmpty()) true
                else mod.localizedName(preferChinese).contains(query, ignoreCase = true) ||
                    mod.modName.contains(query, ignoreCase = true) ||
                    mod.englishName.contains(query, ignoreCase = true) ||
                    mod.displayAuthor.contains(query, ignoreCase = true) ||
                    mod.modDescription.contains(query, ignoreCase = true)
            }
            .sortedWith(sort.comparator(preferChinese))
            .toList()
    }
    val authorDirectory = remember(mods, searchQuery, unknownAuthor) {
        val query = searchQuery.trim()
        mods.groupBy { it.displayAuthor.trim() }
            .map { (author, authorMods) -> author to authorMods.size }
            .filter { (author, _) ->
                val label = author.ifBlank { unknownAuthor }
                query.isEmpty() || label.contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareBy<Pair<String, Int>> { it.first.isBlank() }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first }
            )
    }
    val browsing = searchQuery.isBlank() &&
        filter == MarketFilter.All &&
        sort == MarketSort.Updated &&
        selectedCategoryId == null &&
        selectedTagId == null
    val featuredMods = remember(filteredMods, browsing) {
        if (browsing) filteredMods.filter { it.isFeatured } else emptyList()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.market_explore),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (!showingAuthorDirectory) Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.Sort,
                                contentDescription = stringResource(R.string.market_sort)
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            MarketSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    onClick = {
                                        sort = option
                                        sortMenuExpanded = false
                                    },
                                    trailingIcon = if (sort == option) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = stringResource(R.string.market_search)
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                ),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = searchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = {
                        Text(
                            stringResource(
                                if (showingAuthorDirectory) R.string.market_search_author
                                else R.string.market_search
                            )
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (!(isLoading && mods.isEmpty()) && !(loadFailed && mods.isEmpty())) {
                MarketFilterBar(
                    filter = filter,
                    tags = tags,
                    selectedTagId = selectedTagId,
                    onFilterChange = { next ->
                        if (next == MarketFilter.Author && filter == MarketFilter.Author) {
                            selectedAuthor = null
                        }
                        selectedTagId = null
                        filter = next
                    },
                    onTagSelect = { tagId ->
                        selectedAuthor = null
                        filter = MarketFilter.All
                        selectedTagId = tagId
                    }
                )
                if (categories.isNotEmpty() && !showingAuthorDirectory) {
                    MarketCategoryBar(
                        categories = categories,
                        selectedId = selectedCategoryId,
                        onSelect = { selectedCategoryId = it }
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { loadMods(forceRefresh = true) },
                modifier = Modifier.fillMaxSize()
            ) {
            when {
                isLoading && mods.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                loadFailed && mods.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.market_error),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { loadMods(forceRefresh = true) }) {
                                Text(stringResource(R.string.market_retry))
                            }
                        }
                    }
                }
                showingAuthorDirectory && authorDirectory.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.market_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                filteredMods.isEmpty() && !showingAuthorDirectory -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.market_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (showingAuthorDirectory) {
                            items(authorDirectory, key = { "author-dir-${it.first}" }) { (author, count) ->
                                MarketAuthorRow(
                                    author = author,
                                    count = count,
                                    onClick = { selectedAuthor = author }
                                )
                            }
                        } else {
                            if (browsing && featuredMods.isNotEmpty()) {
                                item(key = "hot_header") {
                                    MarketSectionHeader(
                                        title = stringResource(R.string.market_hot),
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                    )
                                }
                                item(key = "hot_row") {
                                    LazyRow(
                                        modifier = Modifier.nestedScroll(rememberConsumeHorizontalParentScroll()),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(featuredMods, key = { it.id }) { mod ->
                                            MarketHotCard(
                                                mod = mod,
                                                preferChinese = preferChinese,
                                                gameVersion = gameVersion,
                                                onClick = { onModClick(mod.id) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (filter == MarketFilter.Author && selectedAuthor != null) {
                                item(key = "author-back") {
                                    MarketAuthorModsHeader(
                                        author = selectedAuthor!!,
                                        count = filteredMods.size,
                                        onBack = { selectedAuthor = null }
                                    )
                                }
                            } else if (!browsing) {
                                item(key = "all_header") {
                                    MarketSectionHeader(
                                        title = if (searchQuery.isNotBlank()) {
                                            stringResource(R.string.market_search_results)
                                        } else {
                                            stringResource(R.string.market_result_count, filteredMods.size)
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            items(filteredMods, key = { it.id }) { mod ->
                                MarketModCard(
                                    mod = mod,
                                    preferChinese = preferChinese,
                                    gameVersion = gameVersion,
                                    onClick = { onModClick(mod.id) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollToTop,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 16.dp),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.modpack_scroll_to_top)
                            )
                        }
                    }
                    }
                }
            }
            }
        }
    }
}

private object MarketBrowseState {
    var filter: MarketFilter = MarketFilter.All
    var sort: MarketSort = MarketSort.Updated
    var searchQuery: String = ""
    var searchExpanded: Boolean = false
    var selectedAuthor: String? = null
    var selectedCategoryId: String? = null
    var selectedTagId: String? = null
}

private enum class MarketFilter(val labelRes: Int) {
    All(R.string.market_filter_all),
    Featured(R.string.market_filter_featured),
    Author(R.string.market_filter_author)
}

private enum class MarketSort(val labelRes: Int) {
    Updated(R.string.market_sort_updated),
    Created(R.string.market_sort_created),
    Downloads(R.string.market_sort_downloads),
    Views(R.string.market_sort_views),
    Name(R.string.market_sort_name);

    fun comparator(preferChinese: Boolean = true): Comparator<MarketMod> = when (this) {
        Updated -> compareByDescending<MarketMod> { parseMarketTime(it.timestamp) ?: 0L }
            .thenBy { it.id }
        Created -> compareByDescending<MarketMod> { parseMarketTime(it.createdAt) ?: 0L }
            .thenBy { it.id }
        Downloads -> compareByDescending<MarketMod> { it.downloadCount.toLongOrNull() ?: 0L }
            .thenBy { it.id }
        Views -> compareByDescending<MarketMod> { it.viewCount.toLongOrNull() ?: 0L }
            .thenBy { it.id }
        Name -> compareBy<MarketMod, String>(String.CASE_INSENSITIVE_ORDER) {
            it.localizedName(preferChinese)
        }.thenBy { it.id }
    }
}

@Composable
private fun rememberConsumeHorizontalParentScroll(): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset = Offset(x = available.x, y = 0f)

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(x = available.x, y = 0f)
        }
    }
}

@Composable
private fun MarketFilterBar(
    filter: MarketFilter,
    tags: List<MarketTag>,
    selectedTagId: String?,
    onFilterChange: (MarketFilter) -> Unit,
    onTagSelect: (String?) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        selectedLabelColor = MaterialTheme.colorScheme.primary
    )
    val allSelected = filter == MarketFilter.All && selectedTagId == null

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(rememberConsumeHorizontalParentScroll()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        item(key = "filter-all") {
            FilterChip(
                selected = allSelected,
                onClick = { onFilterChange(MarketFilter.All) },
                label = { Text(stringResource(R.string.market_filter_all)) },
                colors = chipColors,
                border = marketChipBorder(allSelected)
            )
        }
        item(key = "filter-featured") {
            FilterChip(
                selected = filter == MarketFilter.Featured,
                onClick = { onFilterChange(MarketFilter.Featured) },
                label = { Text(stringResource(R.string.market_filter_featured)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = chipColors,
                border = marketChipBorder(filter == MarketFilter.Featured)
            )
        }
        item(key = "filter-author") {
            FilterChip(
                selected = filter == MarketFilter.Author,
                onClick = { onFilterChange(MarketFilter.Author) },
                label = { Text(stringResource(R.string.market_filter_author)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = chipColors,
                border = marketChipBorder(filter == MarketFilter.Author)
            )
        }
        items(tags, key = { "tag-${it.id.ifEmpty { it.name }}" }) { tag ->
            val selected = selectedTagId != null &&
                (tag.id == selectedTagId || tag.name == selectedTagId)
            val tagColor = remember(tag.color) { parseMarketTagColor(tag.color) }
            FilterChip(
                selected = selected,
                onClick = { onTagSelect(if (selected) null else tag.id.ifEmpty { tag.name }) },
                label = { Text(tag.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = (tagColor ?: MaterialTheme.colorScheme.primary)
                        .copy(alpha = 0.16f),
                    selectedLabelColor = tagColor ?: MaterialTheme.colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = (tagColor ?: MaterialTheme.colorScheme.primary)
                        .copy(alpha = 0.45f)
                )
            )
        }
    }
}

@Composable
private fun marketChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant,
    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
)

@Composable
private fun MarketCategoryBar(
    categories: List<MarketCategory>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
        selectedLabelColor = MaterialTheme.colorScheme.secondary
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(rememberConsumeHorizontalParentScroll()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp)
    ) {
        item(key = "cat-all") {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.market_category_all)) },
                colors = chipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedId == null,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedId == category.id,
                onClick = { onSelect(if (selectedId == category.id) null else category.id) },
                label = { Text(category.name) },
                colors = chipColors,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedId == category.id,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                )
            )
        }
    }
}

@Composable
private fun MarketAuthorRow(
    author: String,
    count: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = author.ifBlank { stringResource(R.string.market_unknown_author) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.market_result_count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketAuthorModsHeader(
    author: String,
    count: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.market_all_authors),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = author.ifBlank { stringResource(R.string.market_unknown_author) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.market_result_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarketSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private const val ListDescriptionMaxChars = 36

private fun ellipsizeDescription(text: String, maxChars: Int): String {
    val collapsed = plainTextFromMarkdown(text).ifBlank { text.trim() }.replace(Regex("\\s+"), " ")
    if (collapsed.length <= maxChars) return collapsed
    return collapsed.take(maxChars).trimEnd() + "..."
}

@Composable
private fun MarketHotCard(
    mod: MarketMod,
    preferChinese: Boolean,
    gameVersion: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                ModIcon(
                    url = mod.iconUrl,
                    name = mod.localizedName(preferChinese),
                    modifier = Modifier.size(76.dp)
                )
                if (mod.isFeatured) {
                    Icon(
                        imageVector = Icons.Filled.Whatshot,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = mod.localizedName(preferChinese),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            if (mod.supportedVersions.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                MarketCompatibleLabel(
                    versions = mod.supportedVersions,
                    gameVersion = gameVersion,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MarketModCard(
    mod: MarketMod,
    preferChinese: Boolean,
    gameVersion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModIcon(
                url = mod.iconUrl,
                name = mod.localizedName(preferChinese),
                modifier = Modifier.size(58.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mod.localizedName(preferChinese),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (mod.isFeatured) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    MarketTagChip(mod)
                }
                Text(
                    text = buildString {
                        append(mod.displayAuthor.ifBlank { stringResource(R.string.market_unknown_author) })
                        if (mod.categoryName.isNotBlank()) {
                            append(" · ")
                            append(mod.categoryName)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (mod.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    MarketApiTagRow(tags = mod.tags, maxCount = 3)
                }
                val description = ellipsizeDescription(mod.modDescription, ListDescriptionMaxChars)
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatDownloadCount(mod.downloadCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = relativeTimeLabel(mod.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (mod.supportedVersions.isNotBlank()) {
                        Spacer(Modifier.width(12.dp))
                        MarketCompatibleLabel(
                            versions = mod.supportedVersions,
                            gameVersion = gameVersion,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarketCompatibleLabel(
    versions: String,
    gameVersion: String,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelSmall
) {
    if (versions.isBlank()) return
    val compatible = gameVersion.isBlank() ||
        com.bepinex.android.modpack.ModpackManager.isGameVersionCompatible(versions, gameVersion)
    Text(
        text = stringResource(R.string.market_compatible_version, versions),
        style = style,
        color = if (compatible) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal val MarketCardShape = RoundedCornerShape(18.dp)

@Composable
internal fun marketCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface
)

@Composable
internal fun marketCardElevation() = CardDefaults.cardElevation(defaultElevation = 0.dp)

@Composable
internal fun MarketApiTagRow(
    tags: List<MarketTag>,
    maxCount: Int = 4
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tags.take(maxCount).forEach { tag ->
            MarketApiTagChip(tag)
        }
    }
}

@Composable
internal fun MarketApiTagChip(tag: MarketTag) {
    val parsed = remember(tag.color) { parseMarketTagColor(tag.color) }
    val container = parsed?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.secondaryContainer
    val content = parsed ?: MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        shape = RoundedCornerShape(50),
        color = container
    ) {
        Text(
            text = tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

private fun parseMarketTagColor(raw: String): androidx.compose.ui.graphics.Color? {
    val value = raw.trim()
    if (value.isEmpty()) return null
    return runCatching {
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(value))
    }.getOrNull()
}

@Composable
internal fun MarketTagChip(mod: MarketMod) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content, border) = when {
        mod.isPreposition -> Quad(
            stringResource(R.string.market_tag_preposition),
            scheme.primary.copy(alpha = 0.14f),
            scheme.primary,
            scheme.primary.copy(alpha = 0.35f)
        )
        mod.isModpack -> Quad(
            stringResource(R.string.market_tag_modpack),
            scheme.primary.copy(alpha = 0.10f),
            scheme.primary,
            scheme.primary.copy(alpha = 0.28f)
        )
        else -> Quad(
            stringResource(R.string.market_tag_mod),
            scheme.surface,
            scheme.onSurfaceVariant,
            scheme.outlineVariant
        )
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        border = BorderStroke(1.dp, border)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
internal fun ModIcon(
    url: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { MarketIconCache.get(it) }) }

    LaunchedEffect(url) {
        val target = url?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { loadRemoteBitmap(target) }
        if (loaded != null) {
            MarketIconCache.put(target, loaded)
            bitmap = loaded
        }
    }

    val context = LocalContext.current
    val fallbackIcon = remember(context) {
        context.packageManager
            .getApplicationIcon(context.packageName)
            .toBitmap()
            .asImageBitmap()
    }
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Image(
            bitmap = bitmap ?: fallbackIcon,
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
internal fun relativeTimeLabel(raw: String): String {
    val parsed = remember(raw) { parseMarketTime(raw) } ?: return raw.take(10).ifBlank { raw }
    val minutes = ((System.currentTimeMillis() - parsed) / 60_000L).toInt().coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.market_time_just_now)
        minutes < 60 -> stringResource(R.string.market_time_minutes, minutes)
        minutes < 60 * 24 -> stringResource(R.string.market_time_hours, minutes / 60)
        else -> stringResource(R.string.market_time_days, minutes / (60 * 24))
    }
}

internal fun formatDownloadCount(raw: String): String {
    val number = raw.toLongOrNull() ?: return raw.ifBlank { "0" }
    return DecimalFormat("#,###").format(number)
}

private fun parseMarketTime(raw: String): Long? {
    if (raw.isBlank()) return null
    val patterns = arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getDefault()
                isLenient = false
            }.parse(raw.take(19))?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

internal object MarketIconCache {
    private const val MAX_ENTRIES = 64
    private val cache = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(url: String): ImageBitmap? = cache[url]

    @Synchronized
    fun put(url: String, bitmap: ImageBitmap) {
        cache[url] = bitmap
    }
}

private fun loadRemoteBitmap(url: String): ImageBitmap? {
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PVZRH-Launcher/1.0")
        }
        if (conn.responseCode !in 200..299) return null
        conn.inputStream.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}
