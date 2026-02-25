package com.zynerio.bibliotecajelly

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.zynerio.bibliotecajelly.data.MovieEntity
import com.zynerio.bibliotecajelly.data.SeriesEntity
import com.zynerio.bibliotecajelly.data.ListDisplayMode
import com.zynerio.bibliotecajelly.data.LibrarySortMode
import com.zynerio.bibliotecajelly.data.MovieDetailsSyncMode
import com.zynerio.bibliotecajelly.data.ClearDataScope
import com.zynerio.bibliotecajelly.ui.LibraryTab
import com.zynerio.bibliotecajelly.ui.MainUiState
import com.zynerio.bibliotecajelly.ui.MainViewModel
import com.zynerio.bibliotecajelly.ui.TechnicalFilterType
import com.zynerio.bibliotecajelly.ui.theme.BibliotecaJellyTheme

private const val PROJECT_GITHUB_URL = "https://github.com/zynerio/BibliotecaJelly"

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BibliotecaJellyTheme {
                BibliotecaJellyApp(viewModel = viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val mode = viewModel.uiState.value.config.autoSyncMode
        if (mode == com.zynerio.bibliotecajelly.data.AutoSyncMode.OnClose) {
            val request =
                androidx.work.OneTimeWorkRequestBuilder<com.zynerio.bibliotecajelly.data.sync.JellyfinSyncWorker>()
                    .build()
            androidx.work.WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    "jellyfin_manual_sync",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }
}

@Composable
fun BibliotecaJellyApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val series by viewModel.series.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val totalMovies by viewModel.totalMoviesCount.collectAsState()
    val totalSeries by viewModel.totalSeriesCount.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        if (!isConfigured) {
            ConfigScreen(
                uiState = uiState,
                onServerChanged = viewModel::onServerAddressChanged,
                onPortChanged = viewModel::onPortChanged,
                onUsernameChanged = viewModel::onUsernameChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onApiKeyChanged = viewModel::onApiKeyChanged,
                onAutoSyncModeChanged = viewModel::onAutoSyncModeChanged,
                onMovieDetailsSyncModeChanged = viewModel::onMovieDetailsSyncModeChanged,
                onListDisplayModeChanged = viewModel::onListDisplayModeChanged,
                onDownloadPostersOfflineChanged = viewModel::onDownloadPostersOfflineChanged,
                onClearLocalData = viewModel::clearLocalData,
                onTestConnection = viewModel::testConnection,
                onSaveAndValidate = viewModel::saveAndValidateConfig,
                onCancel = viewModel::cancelConfigChanges,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LibraryScreen(
                uiState = uiState,
                movies = movies,
                series = series,
                totalMovies = totalMovies,
                totalSeries = totalSeries,
                onSearchChanged = viewModel::onSearchQueryChanged,
                onTabSelected = viewModel::onTabSelected,
                onSyncAll = viewModel::triggerManualSync,
                onSyncMovies = viewModel::triggerMoviesSync,
                onSyncSeries = viewModel::triggerSeriesSync,
                onSyncFastAll = viewModel::triggerFastSync,
                onSyncFastMovies = viewModel::triggerFastMoviesSync,
                onSyncFastSeries = viewModel::triggerFastSeriesSync,
                onSyncDetailsAll = viewModel::triggerDetailsSync,
                onSyncDetailsMovies = viewModel::triggerDetailsMoviesSync,
                onSyncDetailsSeries = viewModel::triggerDetailsSeriesSync,
                onCancelSync = viewModel::cancelSync,
                onOpenConfig = viewModel::openConfigScreen,
                onGenreSelected = viewModel::onGenreSelected,
                onClearGenreFilter = viewModel::clearGenreFilter,
                onTechnicalFilterSelected = viewModel::onTechnicalFilterSelected,
                onSortModeSelected = viewModel::onSortModeSelected,
                onFavoriteFilterToggled = viewModel::onFavoriteFilterToggled,
                onMovieFavoriteToggled = viewModel::onMovieFavoriteToggled,
                onSeriesFavoriteToggled = viewModel::onSeriesFavoriteToggled,
                onMarkNovedadesAsSeen = viewModel::markNovedadesAsSeen,
                onClearTechnicalFilter = viewModel::clearTechnicalFilter,
                onClearAllFilters = viewModel::clearAllActiveFilters,
                onClearAllFiltersAcrossTabs = viewModel::clearAllFiltersAcrossTabs,
                onMovieDetailsRefresh = viewModel::refreshMovieDetails,
                onSeriesDetailsRefresh = viewModel::refreshSeriesDetails,
                seriesDetailsProvider = { seriesId ->
                    viewModel.seriesSeasons(seriesId)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    uiState: MainUiState,
    onServerChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onAutoSyncModeChanged: (com.zynerio.bibliotecajelly.data.AutoSyncMode) -> Unit,
    onMovieDetailsSyncModeChanged: (MovieDetailsSyncMode) -> Unit,
    onListDisplayModeChanged: (ListDisplayMode) -> Unit,
    onDownloadPostersOfflineChanged: (Boolean) -> Unit,
    onClearLocalData: (ClearDataScope) -> Unit,
    onTestConnection: ((Boolean, String) -> Unit) -> Unit,
    onSaveAndValidate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = uiState.config
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appVersion = remember(context) {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull().orEmpty().ifBlank { "1.3" }
    }
    val showClearDialog = remember { mutableStateOf(false) }
    val clearConfirmText = remember { mutableStateOf("") }
    val clearScope = remember { mutableStateOf(ClearDataScope.All) }
    val showClearSuccess = remember { mutableStateOf(false) }
    val clearSuccessText = remember { mutableStateOf("") }
    val optionsTabIndex = remember { mutableIntStateOf(0) }
    val showConnectionDialog = remember { mutableStateOf(false) }
    val connectionDialogTitle = remember { mutableStateOf("") }
    val connectionDialogMessage = remember { mutableStateOf("") }
    val showSyncHistoryDialog = remember { mutableStateOf(false) }

    LaunchedEffect(showClearSuccess.value) {
        if (showClearSuccess.value) {
            delay(6000L)
            showClearSuccess.value = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Configuración del servidor Jellyfin",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            TabRow(selectedTabIndex = optionsTabIndex.intValue) {
                Tab(
                    selected = optionsTabIndex.intValue == 0,
                    onClick = { optionsTabIndex.intValue = 0 },
                    text = { Text("Servidor") }
                )
                Tab(
                    selected = optionsTabIndex.intValue == 1,
                    onClick = { optionsTabIndex.intValue = 1 },
                    text = { Text("Sincronización") }
                )
                Tab(
                    selected = optionsTabIndex.intValue == 2,
                    onClick = { optionsTabIndex.intValue = 2 },
                    text = { Text("Datos") }
                )
            }

            if (optionsTabIndex.intValue == 0) {
                TextField(
                    value = config.serverAddress,
                    onValueChange = onServerChanged,
                    label = { Text("Dirección del servidor (IP o dominio)") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.port,
                    onValueChange = onPortChanged,
                    label = { Text("Puerto") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.username,
                    onValueChange = onUsernameChanged,
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.password,
                    onValueChange = onPasswordChanged,
                    label = { Text("Contraseña") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text("API key (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        onTestConnection { success, message ->
                            connectionDialogTitle.value = if (success) "Conexión correcta" else "Error de conexión"
                            connectionDialogMessage.value = message
                            showConnectionDialog.value = true
                        }
                    },
                    enabled = !config.isValidating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Probar conexión")
                }
            }

            if (optionsTabIndex.intValue == 1) {
                Text(
                    text = "Sincronización automática",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = config.autoSyncMode == com.zynerio.bibliotecajelly.data.AutoSyncMode.OnStart,
                        onClick = { onAutoSyncModeChanged(com.zynerio.bibliotecajelly.data.AutoSyncMode.OnStart) }
                    )
                    Text("Al iniciar la aplicación")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = config.autoSyncMode == com.zynerio.bibliotecajelly.data.AutoSyncMode.OnClose,
                        onClick = { onAutoSyncModeChanged(com.zynerio.bibliotecajelly.data.AutoSyncMode.OnClose) }
                    )
                    Text("Al cerrar la aplicación")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = config.autoSyncMode == com.zynerio.bibliotecajelly.data.AutoSyncMode.Manual,
                        onClick = { onAutoSyncModeChanged(com.zynerio.bibliotecajelly.data.AutoSyncMode.Manual) }
                    )
                    Text("Solo manual")
                }

                Text(
                    text = "Detalles de películas",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = config.movieDetailsSyncMode == MovieDetailsSyncMode.All,
                        onClick = { onMovieDetailsSyncModeChanged(MovieDetailsSyncMode.All) }
                    )
                    Text("Todos (más completo, más lento)")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = config.movieDetailsSyncMode == MovieDetailsSyncMode.RecentOnly,
                        onClick = { onMovieDetailsSyncModeChanged(MovieDetailsSyncMode.RecentOnly) }
                    )
                    Text("Solo recientes (más rápido)")
                }

                Text(
                    text = "Vista de biblioteca",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = config.listDisplayMode == ListDisplayMode.Infinite,
                        onClick = { onListDisplayModeChanged(ListDisplayMode.Infinite) }
                    )
                    Text("Listado infinito")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = config.listDisplayMode == ListDisplayMode.Paged50,
                        onClick = { onListDisplayModeChanged(ListDisplayMode.Paged50) }
                    )
                    Text("Paginado (50 por página, swipe lateral)")
                }

                Text(
                    text = "Portadas offline",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.downloadPostersOffline,
                        onCheckedChange = { onDownloadPostersOfflineChanged(it) }
                    )
                    Text("Descargar portadas para uso sin conexión")
                }
            }

            if (optionsTabIndex.intValue == 2) {
                Text(
                    text = config.databaseSizeText,
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = config.postersSizeText,
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedButton(
                    onClick = { showSyncHistoryDialog.value = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Historial de sincronización")
                }
            }

            if (config.validationError != null) {
                Text(
                    text = config.validationError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !config.isValidating,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancelar")
            }
            Button(
                onClick = onSaveAndValidate,
                enabled = !config.isValidating,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (config.isValidating) "Validando..." else "Guardar y validar")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                showClearDialog.value = true
                showClearSuccess.value = false
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Borrar datos sincronizados",
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "v$appVersion",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.size(8.dp))
            IconButton(onClick = { uriHandler.openUri(PROJECT_GITHUB_URL) }) {
                Icon(
                    painter = painterResource(id = R.drawable.github),
                    contentDescription = "Abrir GitHub",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showClearSuccess.value) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = clearSuccessText.value,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (showClearDialog.value) {
            AlertDialog(
                onDismissRequest = {
                    showClearDialog.value = false
                    clearConfirmText.value = ""
                    clearScope.value = ClearDataScope.All
                },
                confirmButton = {
                    val enabled = clearConfirmText.value.equals("BORRAR", ignoreCase = true)
                    Button(
                        onClick = {
                            showClearDialog.value = false
                            clearConfirmText.value = ""
                            val selectedScope = clearScope.value
                            onClearLocalData(clearScope.value)
                            clearScope.value = ClearDataScope.All
                            clearSuccessText.value = when (selectedScope) {
                                ClearDataScope.All -> "Se borraron películas y series sincronizadas."
                                ClearDataScope.Movies -> "Se borraron solo las películas sincronizadas."
                                ClearDataScope.Series -> "Se borraron solo las series sincronizadas."
                            }
                            showClearSuccess.value = true
                        },
                        enabled = enabled
                    ) {
                        Text("Borrar")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showClearDialog.value = false
                        clearConfirmText.value = ""
                        clearScope.value = ClearDataScope.All
                    }) {
                        Text("Cancelar")
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Selecciona qué datos sincronizados quieres borrar.")

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = clearScope.value == ClearDataScope.All,
                                onClick = { clearScope.value = ClearDataScope.All }
                            )
                            Text("Todo")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = clearScope.value == ClearDataScope.Movies,
                                onClick = { clearScope.value = ClearDataScope.Movies }
                            )
                            Text("Solo películas")
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = clearScope.value == ClearDataScope.Series,
                                onClick = { clearScope.value = ClearDataScope.Series }
                            )
                            Text("Solo series")
                        }

                        TextField(
                            value = clearConfirmText.value,
                            onValueChange = { clearConfirmText.value = it },
                            label = { Text("Escribe BORRAR para confirmar") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }

        if (showConnectionDialog.value) {
            AlertDialog(
                onDismissRequest = { showConnectionDialog.value = false },
                confirmButton = {
                    Button(onClick = { showConnectionDialog.value = false }) {
                        Text("Cerrar")
                    }
                },
                title = { Text(connectionDialogTitle.value) },
                text = { Text(connectionDialogMessage.value) }
            )
        }

        if (showSyncHistoryDialog.value) {
            AlertDialog(
                onDismissRequest = { showSyncHistoryDialog.value = false },
                confirmButton = {
                    Button(onClick = { showSyncHistoryDialog.value = false }) {
                        Text("Cerrar")
                    }
                },
                title = { Text("Historial de sincronización") },
                text = {
                    if (uiState.sync.syncHistory.isEmpty()) {
                        Text("Sin historial todavía")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            uiState.sync.syncHistory.take(20).forEach { entry ->
                                Text(
                                    text = entry,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: MainUiState,
    movies: List<MovieEntity>,
    series: List<SeriesEntity>,
    totalMovies: Int,
    totalSeries: Int,
    onSearchChanged: (String) -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onSyncAll: () -> Unit,
    onSyncMovies: () -> Unit,
    onSyncSeries: () -> Unit,
    onSyncFastAll: () -> Unit,
    onSyncFastMovies: () -> Unit,
    onSyncFastSeries: () -> Unit,
    onSyncDetailsAll: () -> Unit,
    onSyncDetailsMovies: () -> Unit,
    onSyncDetailsSeries: () -> Unit,
    onCancelSync: () -> Unit,
    onOpenConfig: () -> Unit,
    onGenreSelected: (String) -> Unit,
    onClearGenreFilter: () -> Unit,
    onTechnicalFilterSelected: (TechnicalFilterType, String) -> Unit,
    onSortModeSelected: (LibrarySortMode) -> Unit,
    onFavoriteFilterToggled: () -> Unit,
    onMovieFavoriteToggled: (String, Boolean) -> Unit,
    onSeriesFavoriteToggled: (String, Boolean) -> Unit,
    onMarkNovedadesAsSeen: () -> Unit,
    onClearTechnicalFilter: () -> Unit,
    onClearAllFilters: () -> Unit,
    onClearAllFiltersAcrossTabs: () -> Unit,
    onMovieDetailsRefresh: suspend (String) -> Unit,
    onSeriesDetailsRefresh: suspend (String) -> Unit,
    seriesDetailsProvider: (String) -> kotlinx.coroutines.flow.Flow<List<com.zynerio.bibliotecajelly.data.SeasonEntity>>,
    modifier: Modifier = Modifier
) {
    val sync = uiState.sync
    val library = uiState.library

    val selectedMovieId = remember { mutableStateOf<String?>(null) }
    val movieRefreshInProgress = remember { mutableStateOf(false) }
    val movieRefreshingId = remember { mutableStateOf<String?>(null) }
    val selectedSeries = remember { mutableStateOf<SeriesEntity?>(null) }
    val seriesRefreshInProgress = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val showSyncDialog = remember { mutableStateOf(false) }
    val showSyncHistoryDialog = remember { mutableStateOf(false) }

    val genreFilters = if (library.selectedTab == LibraryTab.Movies) {
        library.selectedMovieGenres
    } else {
        library.selectedSeriesGenres
    }

    val technicalFilters = if (library.selectedTab == LibraryTab.Movies) {
        library.selectedMovieTechnicalFilters
    } else {
        library.selectedSeriesTechnicalFilters
    }

    val activeFiltersCount = buildList {
        if (library.searchQuery.isNotBlank()) add("search")
        if (genreFilters.isNotEmpty()) add("genre")
        if (technicalFilters.isNotEmpty()) add("technical")
        if (library.showOnlyMovieFavorites || library.showOnlySeriesFavorites) add("favorites")
    }.size

    val genreFilteredMovies = if (library.selectedMovieGenres.isEmpty()) {
        movies
    } else {
        movies.filter { movie ->
            movie.genres.any { genre ->
                library.selectedMovieGenres.any { selected ->
                    genre.equals(selected, ignoreCase = true)
                }
            }
        }
    }

    val filteredMovies = if (library.selectedMovieTechnicalFilters.isEmpty()) {
        genreFilteredMovies
    } else {
        genreFilteredMovies.filter { movie ->
            library.selectedMovieTechnicalFilters.all { (type, selectedValues) ->
                if (selectedValues.isEmpty()) {
                    true
                } else {
                    val value = when (type) {
                        TechnicalFilterType.Quality -> movie.quality
                        TechnicalFilterType.Format -> movie.format
                        TechnicalFilterType.Resolution -> movie.resolution
                    }
                    value != null && selectedValues.any { it.equals(value, ignoreCase = true) }
                }
            }
        }
    }

    val genreFilteredSeries = if (library.selectedSeriesGenres.isEmpty()) {
        series
    } else {
        series.filter { item ->
            item.genres.any { genre ->
                library.selectedSeriesGenres.any { selected ->
                    genre.equals(selected, ignoreCase = true)
                }
            }
        }
    }

    val filteredSeries = if (library.selectedSeriesTechnicalFilters.isEmpty()) {
        genreFilteredSeries
    } else {
        val matchedIds = library.selectedSeriesTechnicalMatchedIds
        if (matchedIds == null) {
            genreFilteredSeries
        } else {
            genreFilteredSeries.filter { it.id in matchedIds }
        }
    }

    val favoriteFilteredMovies = if (library.showOnlyMovieFavorites) {
        filteredMovies.filter { it.isFavorite }
    } else {
        filteredMovies
    }

    val favoriteFilteredSeries = if (library.showOnlySeriesFavorites) {
        filteredSeries.filter { it.isFavorite }
    } else {
        filteredSeries
    }

    val activeSortMode = if (library.selectedTab == LibraryTab.Movies) {
        library.movieSortMode
    } else {
        library.seriesSortMode
    }

    val sortedMovies = when (library.movieSortMode) {
        LibrarySortMode.Alphabetical -> favoriteFilteredMovies.sortedBy { it.title.lowercase() }
        LibrarySortMode.RecentlyAdded -> favoriteFilteredMovies.sortedWith(
            compareByDescending<MovieEntity> { it.createdUtcMillis ?: Long.MIN_VALUE }
                .thenBy { it.title.lowercase() }
        )
    }

    val sortedSeries = when (library.seriesSortMode) {
        LibrarySortMode.Alphabetical -> favoriteFilteredSeries.sortedBy { it.title.lowercase() }
        LibrarySortMode.RecentlyAdded -> favoriteFilteredSeries.sortedWith(
            compareByDescending<SeriesEntity> { it.createdUtcMillis ?: Long.MIN_VALUE }
                .thenBy { it.title.lowercase() }
        )
    }

    val activeFavoriteOnly = if (library.selectedTab == LibraryTab.Movies) {
        library.showOnlyMovieFavorites
    } else {
        library.showOnlySeriesFavorites
    }

    val currentItemsMissingCreatedDate = if (library.selectedTab == LibraryTab.Movies) {
        sortedMovies.count { it.createdUtcMillis == null }
    } else {
        sortedSeries.count { it.createdUtcMillis == null }
    }

    val currentItemsTotal = if (library.selectedTab == LibraryTab.Movies) {
        sortedMovies.size
    } else {
        sortedSeries.size
    }

    val novedadesMoviesCount = sortedMovies.count {
        val created = it.createdUtcMillis ?: return@count false
        created > library.lastSeenEpochMillis
    }

    val novedadesSeriesCount = sortedSeries.count {
        val created = it.createdUtcMillis ?: return@count false
        created > library.lastSeenEpochMillis
    }

    val currentNovedadesCount = if (library.selectedTab == LibraryTab.Movies) {
        novedadesMoviesCount
    } else {
        novedadesSeriesCount
    }
    val currentNovedadesLabel = if (currentNovedadesCount > 99) "99+" else currentNovedadesCount.toString()

    val movieNovedadesLabel = if (novedadesMoviesCount > 99) "99+" else novedadesMoviesCount.toString()
    val seriesNovedadesLabel = if (novedadesSeriesCount > 99) "99+" else novedadesSeriesCount.toString()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text("Biblioteca Jelly")
            },
            actions = {
                Button(onClick = onOpenConfig) {
                    Text("Configurar")
                }
                Button(onClick = { showSyncDialog.value = true }) {
                    Text("Sincronizar")
                }
            }
        )

        if (sync.isSyncing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                LinearProgressIndicator(
                    progress = {
                        if (sync.total > 0) {
                            sync.processed.toFloat() / sync.total.toFloat()
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = sync.phaseText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (sync.total > 0) {
                                "${((sync.processed * 100f) / sync.total).toInt().coerceIn(0, 100)}% (${sync.processed}/${sync.total})"
                            } else {
                                "0% (0/0)"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = onCancelSync) {
                        Text("Parar")
                    }
                }
            }
        }

        if (sync.lastError != null) {
            val isCancelNotice = sync.lastError.startsWith("Sincronización cancelada")
            Text(
                text = sync.lastError,
                color = if (isCancelNotice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        TabRow(
            selectedTabIndex = if (library.selectedTab == LibraryTab.Movies) 0 else 1
        ) {
            Tab(
                selected = library.selectedTab == LibraryTab.Movies,
                onClick = { onTabSelected(LibraryTab.Movies) },
                text = {
                    Text(
                        if (novedadesMoviesCount > 0) {
                            "Películas ($movieNovedadesLabel)"
                        } else {
                            "Películas"
                        }
                    )
                }
            )
            Tab(
                selected = library.selectedTab == LibraryTab.Series,
                onClick = { onTabSelected(LibraryTab.Series) },
                text = {
                    Text(
                        if (novedadesSeriesCount > 0) {
                            "Series ($seriesNovedadesLabel)"
                        } else {
                            "Series"
                        }
                    )
                }
            )
        }

        val currentCount =
            if (library.selectedTab == LibraryTab.Movies) sortedMovies.size else sortedSeries.size
        val totalCount =
            if (library.selectedTab == LibraryTab.Movies) totalMovies else totalSeries

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (library.selectedTab == LibraryTab.Movies) {
                    "Películas: mostrando $currentCount de $totalCount"
                } else {
                    "Series: mostrando $currentCount de $totalCount"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (currentNovedadesCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Novedades desde última visita: $currentNovedadesLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Marcar vistas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onMarkNovedadesAsSeen() }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = library.searchQuery,
                onValueChange = onSearchChanged,
                label = { Text("Buscar") },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Filtros activos: $activeFiltersCount",
                style = MaterialTheme.typography.bodySmall,
                color = if (activeFiltersCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.combinedClickable(
                    enabled = activeFiltersCount > 0,
                    onClick = { onClearAllFilters() },
                    onLongClick = { onClearAllFiltersAcrossTabs() }
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Orden:",
                style = MaterialTheme.typography.bodySmall
            )
            FilterChip(
                selected = activeSortMode == LibrarySortMode.Alphabetical,
                onClick = { onSortModeSelected(LibrarySortMode.Alphabetical) },
                modifier = Modifier.height(32.dp),
                label = {
                    Text(
                        text = "A-Z",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            FilterChip(
                selected = activeSortMode == LibrarySortMode.RecentlyAdded,
                onClick = { onSortModeSelected(LibrarySortMode.RecentlyAdded) },
                modifier = Modifier.height(32.dp),
                label = {
                    Text(
                        text = "Últimos añadidos",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            FilterChip(
                selected = activeFavoriteOnly,
                onClick = onFavoriteFilterToggled,
                modifier = Modifier.height(32.dp),
                label = {
                    Text(
                        text = "Favoritos",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }

        if (
            activeSortMode == LibrarySortMode.RecentlyAdded &&
            currentItemsTotal > 0 &&
            currentItemsMissingCreatedDate > 0
        ) {
            val hintText = if (currentItemsMissingCreatedDate == currentItemsTotal) {
                "Sin fecha de alta disponible todavía. Sincroniza para completar metadatos."
            } else {
                "$currentItemsMissingCreatedDate elementos sin fecha de alta se muestran al final."
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = "Mantén pulsado en 'Filtros activos' para limpiar filtros de películas y series.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (genreFilters.isNotEmpty() || technicalFilters.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (genreFilters.isNotEmpty()) {
                        Text(
                            text = "Géneros: ${genreFilters.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (technicalFilters.isNotEmpty()) {
                        technicalFilters.forEach { (type, values) ->
                            val techLabel = when (type) {
                                TechnicalFilterType.Quality -> "Calidad"
                                TechnicalFilterType.Format -> "Formato"
                                TechnicalFilterType.Resolution -> "Resolución"
                            }
                            Text(
                                text = "$techLabel: ${values.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (genreFilters.isNotEmpty()) {
                        Text(
                            text = "Limpiar género",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onClearGenreFilter() }
                        )
                    }
                    if (technicalFilters.isNotEmpty()) {
                        Text(
                            text = "Limpiar detalles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onClearTechnicalFilter() }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (library.selectedTab == LibraryTab.Movies) {
                if (uiState.config.listDisplayMode == ListDisplayMode.Paged50) {
                    MoviesPagedGrid(
                        movies = sortedMovies,
                        onMovieClick = { selectedMovieId.value = it.id },
                        onFavoriteToggle = onMovieFavoriteToggled,
                        onGenreClick = onGenreSelected,
                        onTechnicalFilterClick = onTechnicalFilterSelected,
                        refreshingMovieId = movieRefreshingId.value
                    )
                } else {
                    MoviesGrid(
                        movies = sortedMovies,
                        onMovieClick = { selectedMovieId.value = it.id },
                        onFavoriteToggle = onMovieFavoriteToggled,
                        onGenreClick = onGenreSelected,
                        onTechnicalFilterClick = onTechnicalFilterSelected,
                        refreshingMovieId = movieRefreshingId.value
                    )
                }
            } else {
                val onSeriesSelected: (SeriesEntity) -> Unit = { s ->
                    selectedSeries.value = s
                }

                if (uiState.config.listDisplayMode == ListDisplayMode.Paged50) {
                    SeriesPagedList(
                        series = sortedSeries,
                        onSeriesClick = onSeriesSelected,
                        onFavoriteToggle = onSeriesFavoriteToggled,
                        onGenreClick = onGenreSelected
                    )
                } else {
                    SeriesList(
                        series = sortedSeries,
                        onSeriesClick = onSeriesSelected,
                        onFavoriteToggle = onSeriesFavoriteToggled,
                        onGenreClick = onGenreSelected
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (sync.isServerActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                )
                Text(
                    text = sync.serverStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sync.isServerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = sync.lastSyncText?.let { "Última actualización: $it" }
                    ?: "Última actualización: pendiente",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showSyncHistoryDialog.value = true }
            )
        }

        selectedMovieId.value?.let { movieId ->
            val selectedMovie = movies.firstOrNull { it.id == movieId }
            if (selectedMovie != null) {
                MovieDetailDialog(
                    movie = selectedMovie,
                    onFavoriteToggle = { isFavorite ->
                        onMovieFavoriteToggled(selectedMovie.id, isFavorite)
                    },
                    isRefreshing = movieRefreshInProgress.value,
                    onRefresh = {
                        if (!movieRefreshInProgress.value) {
                            movieRefreshInProgress.value = true
                            movieRefreshingId.value = movieId
                            scope.launch {
                                try {
                                    onMovieDetailsRefresh(movieId)
                                } finally {
                                    movieRefreshInProgress.value = false
                                    movieRefreshingId.value = null
                                }
                            }
                        }
                    },
                    onDismiss = { selectedMovieId.value = null }
                )
            }
        }

        selectedSeries.value?.let { s ->
            val selectedSeriesLatest = series.firstOrNull { it.id == s.id } ?: s
            val detailsFlow = remember(selectedSeriesLatest.id) { seriesDetailsProvider(selectedSeriesLatest.id) }
            val seasons = detailsFlow.collectAsState(initial = emptyList()).value
            SeriesDetailDialog(
                series = selectedSeriesLatest,
                onFavoriteToggle = { isFavorite ->
                    onSeriesFavoriteToggled(selectedSeriesLatest.id, isFavorite)
                },
                seasons = seasons,
                isRefreshing = seriesRefreshInProgress.value,
                onRefresh = {
                    if (!seriesRefreshInProgress.value) {
                        seriesRefreshInProgress.value = true
                        scope.launch {
                            onSeriesDetailsRefresh(selectedSeriesLatest.id)
                            seriesRefreshInProgress.value = false
                        }
                    }
                },
                onDismiss = { selectedSeries.value = null }
            )
        }

        if (showSyncDialog.value) {
            AlertDialog(
                onDismissRequest = { showSyncDialog.value = false },
                confirmButton = {},
                dismissButton = {},
                text = {
                    Column {
                        Text(
                            "Aviso: colecciones grandes pueden tardar varios minutos en sincronizar.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("¿Qué deseas sincronizar?")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncAll()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Todo")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncMovies()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Solo películas")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncSeries()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Solo series")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Modo rápido (primera pasada)")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncFastAll()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rápido: Todo")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncFastMovies()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rápido: Solo películas")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncFastSeries()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rápido: Solo series")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Solo detalles (sin catálogo)")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncDetailsAll()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Detalles: Todo")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncDetailsMovies()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Detalles: Solo películas")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncDetailsSeries()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Detalles: Solo series")
                        }
                    }
                }
            )
        }

        if (showSyncHistoryDialog.value) {
            AlertDialog(
                onDismissRequest = { showSyncHistoryDialog.value = false },
                confirmButton = {
                    Button(onClick = { showSyncHistoryDialog.value = false }) {
                        Text("Cerrar")
                    }
                },
                title = { Text("Historial de sincronización") },
                text = {
                    if (sync.syncHistory.isEmpty()) {
                        Text("Sin historial todavía")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            sync.syncHistory.take(20).forEach { entry ->
                                Text(
                                    text = entry,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoviesPagedGrid(
    movies: List<MovieEntity>,
    onMovieClick: (MovieEntity) -> Unit,
    onFavoriteToggle: (String, Boolean) -> Unit,
    onGenreClick: (String) -> Unit,
    onTechnicalFilterClick: (TechnicalFilterType, String) -> Unit,
    refreshingMovieId: String?
) {
    val pages = remember(movies) { movies.chunked(50) }
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${pagerState.currentPage + 1}/$pageCount",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageItems = pages.getOrNull(pageIndex).orEmpty()
            if (pageItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin resultados")
                }
            } else {
                MoviesGrid(
                    movies = pageItems,
                    onMovieClick = onMovieClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onGenreClick = onGenreClick,
                    onTechnicalFilterClick = onTechnicalFilterClick,
                    refreshingMovieId = refreshingMovieId
                )
            }
        }
    }
}

@Composable
fun MoviesGrid(
    movies: List<MovieEntity>,
    onMovieClick: (MovieEntity) -> Unit,
    onFavoriteToggle: (String, Boolean) -> Unit,
    onGenreClick: (String) -> Unit,
    onTechnicalFilterClick: (TechnicalFilterType, String) -> Unit,
    refreshingMovieId: String?
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val windowWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val windowHeightDp = with(density) { windowInfo.containerSize.height.toDp() }
    val isLandscape = windowWidthDp > windowHeightDp
    val isTablet = windowWidthDp >= 600.dp
    val gridMinSize = when {
        isTablet && isLandscape -> 220.dp
        isTablet -> 200.dp
        isLandscape -> 180.dp
        else -> 170.dp
    }
    val cardHeight = when {
        isTablet && isLandscape -> 420.dp
        isTablet -> 400.dp
        isLandscape -> 330.dp
        else -> 340.dp
    }
    val posterHeight = when {
        isTablet && isLandscape -> 220.dp
        isTablet -> 210.dp
        isLandscape -> 175.dp
        else -> 180.dp
    }
    val responsiveTitleSmall = if (isTablet) {
        MaterialTheme.typography.titleMedium
    } else {
        MaterialTheme.typography.titleSmall
    }
    val responsiveBodySmall = if (isTablet) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodySmall
    }
    val technicalLineSpacing = if (isTablet) 4.dp else 2.dp
    val technicalTitleTopPadding = if (isTablet) 4.dp else 0.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = gridMinSize),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(movies) { movie ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .clickable { onMovieClick(movie) },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    Box {
                        PosterImage(
                            imageUrl = movie.posterUrl,
                            contentDescription = movie.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(posterHeight)
                        )
                        IconButton(
                            onClick = { onFavoriteToggle(movie.id, !movie.isFavorite) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = if (movie.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (movie.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                                tint = if (movie.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Text(
                        text = movie.title,
                        style = responsiveTitleSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (refreshingMovieId == movie.id) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .padding(bottom = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Actualizando...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (movie.genres.isEmpty()) {
                        Text(
                            text = "Géneros: -",
                            style = responsiveBodySmall,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 6.dp)
                        )
                    } else {
                        Text(
                            text = "Géneros:",
                            style = responsiveBodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        movie.genres.take(2).forEach { genre ->
                            Text(
                                text = genre,
                                style = responsiveBodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable { onGenreClick(genre) }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    val hasTechnicalInfo = !movie.quality.isNullOrBlank() ||
                        !movie.format.isNullOrBlank() ||
                        !movie.resolution.isNullOrBlank()
                    if (hasTechnicalInfo) {
                        Text(
                            text = "Detalles técnicos:",
                            style = responsiveBodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .padding(top = technicalTitleTopPadding)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(technicalLineSpacing),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            movie.quality?.takeIf { it.isNotBlank() }?.let { quality ->
                                Text(
                                    text = "Calidad: $quality",
                                    style = responsiveBodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        onTechnicalFilterClick(TechnicalFilterType.Quality, quality)
                                    }
                                )
                            }
                            movie.format?.takeIf { it.isNotBlank() }?.let { format ->
                                Text(
                                    text = "Formato: $format",
                                    style = responsiveBodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        onTechnicalFilterClick(TechnicalFilterType.Format, format)
                                    }
                                )
                            }
                            movie.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                                Text(
                                    text = "Resolución: $resolution",
                                    style = responsiveBodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        onTechnicalFilterClick(TechnicalFilterType.Resolution, resolution)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesList(
    series: List<SeriesEntity>,
    onSeriesClick: (SeriesEntity) -> Unit,
    onFavoriteToggle: (String, Boolean) -> Unit,
    onGenreClick: (String) -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val windowWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val windowHeightDp = with(density) { windowInfo.containerSize.height.toDp() }
    val isLandscape = windowWidthDp > windowHeightDp
    val isTablet = windowWidthDp >= 600.dp
    val posterWidth = when {
        isTablet && isLandscape -> 130.dp
        isTablet -> 120.dp
        isLandscape -> 84.dp
        else -> 92.dp
    }
    val posterHeight = when {
        isTablet && isLandscape -> 195.dp
        isTablet -> 180.dp
        isLandscape -> 126.dp
        else -> 138.dp
    }
    val responsiveBodySmall = if (isTablet) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodySmall
    }
    val useVerticalCardLayout = !isTablet && !isLandscape

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(series) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeriesClick(item) },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (useVerticalCardLayout) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box {
                            PosterImage(
                                imageUrl = item.posterUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                            )
                            IconButton(
                                onClick = { onFavoriteToggle(item.id, !item.isFavorite) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (item.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                                    tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.genres.isNotEmpty()) {
                            Text(
                                text = "Géneros: ${item.genres.take(3).joinToString()}",
                                style = responsiveBodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    item.genres.firstOrNull()?.let { onGenreClick(it) }
                                }
                            )
                        }
                        Text(
                            text = "Temporadas: ${item.totalSeasons}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Episodios: ${item.totalEpisodes}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Ver detalle por temporada y actualizar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(width = posterWidth, height = posterHeight)
                        ) {
                            PosterImage(
                                imageUrl = item.posterUrl,
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                            IconButton(
                                onClick = { onFavoriteToggle(item.id, !item.isFavorite) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (item.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                                    tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f),
                            verticalArrangement = Arrangement.Top
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (item.genres.isNotEmpty()) {
                                Text(
                                    text = "Géneros:",
                                    style = responsiveBodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                item.genres.take(3).forEach { genre ->
                                    Text(
                                        text = genre,
                                        style = responsiveBodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { onGenreClick(genre) }
                                    )
                                }
                            }
                            Text(
                                text = "Temporadas: ${item.totalSeasons}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Episodios: ${item.totalEpisodes}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ver detalle por temporada y actualizar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesPagedList(
    series: List<SeriesEntity>,
    onSeriesClick: (SeriesEntity) -> Unit,
    onFavoriteToggle: (String, Boolean) -> Unit,
    onGenreClick: (String) -> Unit
) {
    val pages = remember(series) { series.chunked(50) }
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${pagerState.currentPage + 1}/$pageCount",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageItems = pages.getOrNull(pageIndex).orEmpty()
            if (pageItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin resultados")
                }
            } else {
                SeriesList(
                    series = pageItems,
                    onSeriesClick = onSeriesClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onGenreClick = onGenreClick
                )
            }
        }
    }
}

@Composable
fun PosterImage(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = false
            }
        },
        modifier = modifier.clipToBounds(),
        update = { imageView ->
            imageView.contentDescription = contentDescription
            Glide.with(imageView)
                .load(imageUrl)
                .centerCrop()
                .placeholder(R.drawable.sin_imagen)
                .error(R.drawable.sin_imagen)
                .fallback(R.drawable.sin_imagen)
                .into(imageView)
        }
    )
}

@Composable
fun MovieDetailDialog(
    movie: MovieEntity,
    onFavoriteToggle: (Boolean) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onFavoriteToggle(!movie.isFavorite) }) {
                    Icon(
                        imageVector = if (movie.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (movie.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (movie.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isRefreshing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Actualizando información de la película...")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Formato: ${movie.format ?: "-"}")
                Text("Calidad: ${movie.quality ?: "-"}")
                Text("Resolución: ${movie.resolution ?: "-"}")
                Text("Bitrate: ${movie.bitrateMbps?.let { "%.2f Mbps".format(it) } ?: "-"}")
                val durationText = movie.durationMinutes?.let { totalMinutes ->
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    if (hours > 0) {
                        "${hours}h ${minutes}min"
                    } else {
                        "${minutes}min"
                    }
                } ?: "-"
                Text("Duración: $durationText")
                Text("Tamaño: ${movie.sizeGb?.let { "%.2f GB".format(it) } ?: "-"}")
                Text("Audios: ${if (movie.audioLanguages.isEmpty()) "-" else movie.audioLanguages.joinToString()}")
                Text("Subtítulos: ${if (movie.subtitleLanguages.isEmpty()) "-" else movie.subtitleLanguages.joinToString()}")
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRefreshing) "Actualizando..." else "Actualizar esta película")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun SeriesDetailDialog(
    series: SeriesEntity,
    onFavoriteToggle: (Boolean) -> Unit,
    seasons: List<com.zynerio.bibliotecajelly.data.SeasonEntity>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isRefreshing) {
                onDismiss()
            }
        },
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onFavoriteToggle(!series.isFavorite) }) {
                    Icon(
                        imageVector = if (series.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (series.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (series.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Temporadas: ${series.totalSeasons}")
                Text("Episodios: ${series.totalEpisodes}")

                if (series.genres.isNotEmpty()) {
                    Text(
                        text = "Géneros: ${series.genres.joinToString()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (isRefreshing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Actualizando información de temporadas y episodios...")
                    }
                }

                Text(
                    text = "Detalle por temporada:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
                seasons.sortedBy { it.seasonNumber }.forEach { season ->
                    Text(
                        text = "T${season.seasonNumber}: ${season.episodeCount} eps",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    season.quality?.takeIf { it.isNotBlank() }?.let {
                        Text("Calidad: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    season.format?.takeIf { it.isNotBlank() }?.let {
                        Text("Formato: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    season.resolution?.takeIf { it.isNotBlank() }?.let {
                        Text("Resolución: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (seasons.isEmpty()) {
                    Text(
                        text = "No hay detalle de temporadas todavía. Usa Actualizar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRefreshing) "Actualizando..." else "Actualizar esta serie")
                }
                Text(
                    text = "Puedes cerrar cuando termine la actualización.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                enabled = !isRefreshing
            ) {
                Text("Cerrar")
            }
        }
    )
}
