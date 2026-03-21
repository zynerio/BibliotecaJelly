package com.zynerio.bibliotecajelly

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.zynerio.bibliotecajelly.data.LibraryViewInfo
import com.zynerio.bibliotecajelly.data.MovieEntity
import com.zynerio.bibliotecajelly.data.OtherMediaEntity
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
    val others by viewModel.others.collectAsState()
    val libraryViews by viewModel.libraryViews.collectAsState()
    val libraryCoverOverrides by viewModel.libraryCoverOverrides.collectAsState()
    val showLibraryCoverHint by viewModel.showLibraryCoverHint.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val totalMovies by viewModel.totalMoviesCount.collectAsState()
    val totalSeries by viewModel.totalSeriesCount.collectAsState()
    val totalOthers by viewModel.totalOthersCount.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val installedVersionName = remember(context) {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull().orEmpty().ifBlank { "desconocida" }
    }

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
                onShowFilePathChanged = viewModel::onShowFilePathChanged,
                onLibrariesAdvancedViewChanged = viewModel::onLibrariesAdvancedViewChanged,
                onClearSyncHistory = viewModel::clearSyncHistory,
                onClearLocalData = viewModel::clearLocalData,
                onTestConnection = viewModel::testConnection,
                onSave = viewModel::saveConfig,
                onCancel = viewModel::cancelConfigChanges,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LibraryScreen(
                uiState = uiState,
                movies = movies,
                series = series,
                others = others,
                libraryViews = libraryViews,
                libraryCoverOverrides = libraryCoverOverrides,
                showLibraryCoverHint = showLibraryCoverHint,
                totalMovies = totalMovies,
                totalSeries = totalSeries,
                totalOthers = totalOthers,
                onSearchChanged = viewModel::onSearchQueryChanged,
                onTabSelected = viewModel::onTabSelected,
                onSyncAll = viewModel::triggerManualSync,
                onSyncMovies = viewModel::triggerMoviesSync,
                onSyncSeries = viewModel::triggerSeriesSync,
                onSyncOthers = viewModel::triggerOthersSync,
                onSyncRecentMovies = viewModel::triggerRecentMoviesSync,
                onSyncRecentSeries = viewModel::triggerRecentSeriesSync,
                onSyncRecentOthers = viewModel::triggerRecentOthersSync,
                onSyncFastAll = viewModel::triggerFastSync,
                onSyncFastMovies = viewModel::triggerFastMoviesSync,
                onSyncFastSeries = viewModel::triggerFastSeriesSync,
                onSyncFastOthers = viewModel::triggerFastOthersSync,
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
                onMarkNovedadesAsSeen = { markUntilEpochMillis ->
                    viewModel.markNovedadesAsSeen(markUntilEpochMillis)
                },
                onClearTechnicalFilter = viewModel::clearTechnicalFilter,
                onClearAllFilters = viewModel::clearAllActiveFilters,
                onClearAllFiltersAcrossTabs = viewModel::clearAllFiltersAcrossTabs,
                onMovieDetailsRefresh = viewModel::refreshMovieDetails,
                onSeriesDetailsRefresh = viewModel::refreshSeriesDetails,
                onClearSyncHistory = viewModel::clearSyncHistory,
                onLibraryCoverSelected = viewModel::setLibraryCoverOverride,
                onLibraryCoverReset = viewModel::clearLibraryCoverOverride,
                onDismissLibraryCoverHint = viewModel::dismissLibraryCoverHint,
                seriesDetailsProvider = { seriesId ->
                    viewModel.seriesSeasons(seriesId)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (uiState.update.showDialog) {
        AlertDialog(
            onDismissRequest = viewModel::postponeUpdateDialog,
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                val latestVersion = uiState.update.latestVersion.orEmpty().ifBlank { "-" }
                Text(
                    stringResource(
                        R.string.update_available_message,
                        latestVersion,
                        installedVersionName
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val url = uiState.update.releaseUrl
                    if (!url.isNullOrBlank()) {
                        uriHandler.openUri(url)
                    }
                    viewModel.dismissUpdateDialog()
                }) {
                    Text(stringResource(R.string.download))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::postponeUpdateDialog) {
                    Text(stringResource(R.string.later))
                }
            }
        )
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
    onShowFilePathChanged: (Boolean) -> Unit,
    onLibrariesAdvancedViewChanged: (Boolean) -> Unit,
    onClearSyncHistory: () -> Unit,
    onClearLocalData: (ClearDataScope) -> Unit,
    onTestConnection: ((Boolean, String) -> Unit) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config = uiState.config
    val context = LocalContext.current
    val activity = context as? Activity
    val uriHandler = LocalUriHandler.current
    val appVersion = remember(context) {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull().orEmpty().ifBlank { "1.6" }
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
    val pressBackAgainMessage = stringResource(R.string.press_back_again_to_exit)
    val lastBackPressAt = remember { mutableLongStateOf(0L) }

    BackHandler {
        when {
            showClearDialog.value -> {
                showClearDialog.value = false
                clearConfirmText.value = ""
                clearScope.value = ClearDataScope.All
            }

            showConnectionDialog.value -> showConnectionDialog.value = false
            showSyncHistoryDialog.value -> showSyncHistoryDialog.value = false
            config.hasSavedConfig -> onCancel()
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt.longValue < 2000L) {
                    activity?.finish()
                } else {
                    lastBackPressAt.longValue = now
                    Toast.makeText(context, pressBackAgainMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
                text = stringResource(R.string.server_config_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            TabRow(selectedTabIndex = optionsTabIndex.intValue) {
                Tab(
                    selected = optionsTabIndex.intValue == 0,
                    onClick = { optionsTabIndex.intValue = 0 },
                    text = { Text(stringResource(R.string.server)) }
                )
                Tab(
                    selected = optionsTabIndex.intValue == 1,
                    onClick = { optionsTabIndex.intValue = 1 },
                    text = { Text(stringResource(R.string.sync)) }
                )
                Tab(
                    selected = optionsTabIndex.intValue == 2,
                    onClick = { optionsTabIndex.intValue = 2 },
                    text = { Text(stringResource(R.string.data)) }
                )
            }

            if (optionsTabIndex.intValue == 0) {
                TextField(
                    value = config.serverAddress,
                    onValueChange = onServerChanged,
                    label = { Text(stringResource(R.string.server_address_label)) },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.port,
                    onValueChange = onPortChanged,
                    label = { Text(stringResource(R.string.port)) },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.username,
                    onValueChange = onUsernameChanged,
                    label = { Text(stringResource(R.string.username)) },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.password,
                    onValueChange = onPasswordChanged,
                    label = { Text(stringResource(R.string.password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text(stringResource(R.string.api_key_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        onTestConnection { success, message ->
                            connectionDialogTitle.value = if (success) {
                                context.getString(R.string.connection_ok)
                            } else {
                                context.getString(R.string.connection_error)
                            }
                            connectionDialogMessage.value = message
                            showConnectionDialog.value = true
                        }
                    },
                    enabled = !config.isValidating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.test_connection))
                }
            }

            if (optionsTabIndex.intValue == 1) {
                Text(
                    text = stringResource(R.string.auto_sync_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = config.autoSyncMode == com.zynerio.bibliotecajelly.data.AutoSyncMode.OnStart,
                        onClick = { onAutoSyncModeChanged(com.zynerio.bibliotecajelly.data.AutoSyncMode.OnStart) }
                    )
                    Text(stringResource(R.string.auto_sync_on_start))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = config.autoSyncMode == com.zynerio.bibliotecajelly.data.AutoSyncMode.OnClose,
                        onClick = { onAutoSyncModeChanged(com.zynerio.bibliotecajelly.data.AutoSyncMode.OnClose) }
                    )
                    Text(stringResource(R.string.auto_sync_on_close))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = config.autoSyncMode == com.zynerio.bibliotecajelly.data.AutoSyncMode.Manual,
                        onClick = { onAutoSyncModeChanged(com.zynerio.bibliotecajelly.data.AutoSyncMode.Manual) }
                    )
                    Text(stringResource(R.string.auto_sync_manual_only))
                }

                Text(
                    text = stringResource(R.string.movie_details_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = config.movieDetailsSyncMode == MovieDetailsSyncMode.All,
                        onClick = { onMovieDetailsSyncModeChanged(MovieDetailsSyncMode.All) }
                    )
                    Text(stringResource(R.string.movie_details_all))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = config.movieDetailsSyncMode == MovieDetailsSyncMode.RecentOnly,
                        onClick = { onMovieDetailsSyncModeChanged(MovieDetailsSyncMode.RecentOnly) }
                    )
                    Text(stringResource(R.string.movie_details_recent))
                }

                Text(
                    text = stringResource(R.string.library_view_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = config.listDisplayMode == ListDisplayMode.Infinite,
                        onClick = { onListDisplayModeChanged(ListDisplayMode.Infinite) }
                    )
                    Text(stringResource(R.string.list_infinite))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(
                        selected = config.listDisplayMode == ListDisplayMode.Paged50,
                        onClick = { onListDisplayModeChanged(ListDisplayMode.Paged50) }
                    )
                    Text(stringResource(R.string.list_paged))
                }

                Text(
                    text = stringResource(R.string.offline_posters_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.downloadPostersOffline,
                        onCheckedChange = { onDownloadPostersOfflineChanged(it) }
                    )
                    Text(stringResource(R.string.download_posters_offline))
                }

                Text(
                    text = stringResource(R.string.details_info_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.showFilePath,
                        onCheckedChange = { onShowFilePathChanged(it) }
                    )
                    Text(stringResource(R.string.show_file_path_details))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.librariesAdvancedView,
                        onCheckedChange = { onLibrariesAdvancedViewChanged(it) }
                    )
                    Text(stringResource(R.string.advanced_libraries_view))
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
                    Text(stringResource(R.string.sync_history))
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
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onSave,
                enabled = !config.isValidating,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (config.isValidating) {
                        stringResource(R.string.validating)
                    } else {
                        stringResource(R.string.save)
                    }
                )
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
                text = stringResource(R.string.clear_synced_data),
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
                    contentDescription = stringResource(R.string.open_github),
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
                    val enabled = clearConfirmText.value.equals(
                        context.getString(R.string.delete_confirm_word),
                        ignoreCase = true
                    )
                    Button(
                        onClick = {
                            showClearDialog.value = false
                            clearConfirmText.value = ""
                            val selectedScope = clearScope.value
                            onClearLocalData(clearScope.value)
                            clearScope.value = ClearDataScope.All
                            clearSuccessText.value = when (selectedScope) {
                                ClearDataScope.All -> context.getString(R.string.clear_success_all)
                                ClearDataScope.Movies -> context.getString(R.string.clear_success_movies)
                                ClearDataScope.Series -> context.getString(R.string.clear_success_series)
                            }
                            showClearSuccess.value = true
                        },
                        enabled = enabled
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showClearDialog.value = false
                        clearConfirmText.value = ""
                        clearScope.value = ClearDataScope.All
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.clear_data_select_scope))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = clearScope.value == ClearDataScope.All,
                                onClick = { clearScope.value = ClearDataScope.All }
                            )
                            Text(stringResource(R.string.all))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = clearScope.value == ClearDataScope.Movies,
                                onClick = { clearScope.value = ClearDataScope.Movies }
                            )
                            Text(stringResource(R.string.movies_only))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(
                                selected = clearScope.value == ClearDataScope.Series,
                                onClick = { clearScope.value = ClearDataScope.Series }
                            )
                            Text(stringResource(R.string.series_only))
                        }

                        TextField(
                            value = clearConfirmText.value,
                            onValueChange = { clearConfirmText.value = it },
                            label = { Text(stringResource(R.string.type_delete_confirm)) },
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
                        Text(stringResource(R.string.close))
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
                        Text(stringResource(R.string.close))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            onClearSyncHistory()
                            showSyncHistoryDialog.value = false
                        }
                    ) {
                        Text(
                            stringResource(R.string.clear_history),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                title = { Text(stringResource(R.string.sync_history)) },
                text = {
                    if (uiState.sync.syncHistory.isEmpty()) {
                        Text(stringResource(R.string.empty_history))
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.sync.syncHistory.forEach { entry ->
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
    others: List<OtherMediaEntity>,
    libraryViews: List<LibraryViewInfo>,
    libraryCoverOverrides: Map<String, String>,
    showLibraryCoverHint: Boolean,
    totalMovies: Int,
    totalSeries: Int,
    totalOthers: Int,
    onSearchChanged: (String) -> Unit,
    onTabSelected: (LibraryTab) -> Unit,
    onSyncAll: () -> Unit,
    onSyncMovies: () -> Unit,
    onSyncSeries: () -> Unit,
    onSyncOthers: () -> Unit,
    onSyncRecentMovies: () -> Unit,
    onSyncRecentSeries: () -> Unit,
    onSyncRecentOthers: () -> Unit,
    onSyncFastAll: () -> Unit,
    onSyncFastMovies: () -> Unit,
    onSyncFastSeries: () -> Unit,
    onSyncFastOthers: () -> Unit,
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
    onMarkNovedadesAsSeen: (Long?) -> Unit,
    onClearTechnicalFilter: () -> Unit,
    onClearAllFilters: () -> Unit,
    onClearAllFiltersAcrossTabs: () -> Unit,
    onMovieDetailsRefresh: suspend (String) -> Unit,
    onSeriesDetailsRefresh: suspend (String) -> Unit,
    onClearSyncHistory: () -> Unit,
    onLibraryCoverSelected: (String, String) -> Unit,
    onLibraryCoverReset: (String) -> Unit,
    onDismissLibraryCoverHint: () -> Unit,
    seriesDetailsProvider: (String) -> kotlinx.coroutines.flow.Flow<List<com.zynerio.bibliotecajelly.data.SeasonEntity>>,
    modifier: Modifier = Modifier
) {
    val sync = uiState.sync
    val library = uiState.library
    val context = LocalContext.current
    val activity = context as? Activity
    val pressBackAgainMessage = stringResource(R.string.press_back_again_to_exit)
    val lastBackPressAt = remember { mutableLongStateOf(0L) }

    val selectedMovieId = remember { mutableStateOf<String?>(null) }
    val movieRefreshInProgress = remember { mutableStateOf(false) }
    val movieRefreshingId = remember { mutableStateOf<String?>(null) }
    val selectedSeries = remember { mutableStateOf<SeriesEntity?>(null) }
    val seriesRefreshInProgress = remember { mutableStateOf(false) }
    val selectedOtherGroupType = remember { mutableStateOf<String?>(null) }
    val selectedOtherLibraryFilter = remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val showSyncDialog = remember { mutableStateOf(false) }
    val showSyncHistoryDialog = remember { mutableStateOf(false) }
    val selectedAdvancedLibrary = remember { mutableStateOf<String?>(null) }
    val selectedLibraryForCoverDialog = remember { mutableStateOf<String?>(null) }

    val isAdvancedMode = uiState.config.librariesAdvancedView
    val isAdvancedOverview = isAdvancedMode && selectedAdvancedLibrary.value.isNullOrBlank()
    val isAdvancedLibraryView = isAdvancedMode && !selectedAdvancedLibrary.value.isNullOrBlank()

    LaunchedEffect(isAdvancedMode) {
        if (!isAdvancedMode) {
            selectedAdvancedLibrary.value = null
        }
    }

    val genreFilters = when (library.selectedTab) {
        LibraryTab.Movies -> library.selectedMovieGenres
        LibraryTab.Series -> library.selectedSeriesGenres
        LibraryTab.Others -> emptySet()
    }

    val technicalFilters = when (library.selectedTab) {
        LibraryTab.Movies -> library.selectedMovieTechnicalFilters
        LibraryTab.Series -> library.selectedSeriesTechnicalFilters
        LibraryTab.Others -> emptyMap()
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

    val sortedOthers = others.sortedWith(
        compareByDescending<OtherMediaEntity> { it.createdUtcMillis ?: Long.MIN_VALUE }
            .thenBy { it.title.lowercase() }
    )

    val unassignedLibraryLabel = stringResource(R.string.library_unassigned)

    val movieLibrariesCount = sortedMovies
        .groupingBy { it.libraryName ?: unassignedLibraryLabel }
        .eachCount()
    val seriesLibrariesCount = sortedSeries
        .groupingBy { it.libraryName ?: unassignedLibraryLabel }
        .eachCount()
    val otherLibrariesCount = sortedOthers
        .groupingBy { it.libraryName ?: unassignedLibraryLabel }
        .eachCount()
    val allLibraries = (movieLibrariesCount.keys + seriesLibrariesCount.keys + otherLibrariesCount.keys)
        .distinct()
        .filter { it != unassignedLibraryLabel }
        .sorted()
    val libraryPosterUrls = remember(libraryViews, sortedMovies, sortedSeries) {
        buildMap {
            libraryViews.forEach { libraryView ->
                if (libraryView.imageUrl != null) {
                    put(libraryView.name, libraryView.imageUrl)
                }
            }
            sortedMovies.forEach { movie ->
                val libraryName = movie.libraryName
                if (!libraryName.isNullOrBlank() && !containsKey(libraryName) && !movie.posterUrl.isNullOrBlank()) {
                    put(libraryName, movie.posterUrl)
                }
            }
            sortedSeries.forEach { item ->
                val libraryName = item.libraryName
                if (!libraryName.isNullOrBlank() && !containsKey(libraryName) && !item.posterUrl.isNullOrBlank()) {
                    put(libraryName, item.posterUrl)
                }
            }
        }
    }
    val effectiveLibraryPosterUrls = remember(libraryPosterUrls, libraryCoverOverrides) {
        buildMap {
            putAll(libraryPosterUrls)
            libraryCoverOverrides.forEach { (libraryName, imageUrl) ->
                put(libraryName, imageUrl)
            }
        }
    }
    val libraryPosterCandidates = remember(sortedMovies, sortedSeries) {
        buildMap {
            val grouped = linkedMapOf<String, LinkedHashSet<String>>()
            sortedMovies.forEach { movie ->
                val libraryName = movie.libraryName
                val posterUrl = movie.posterUrl
                if (!libraryName.isNullOrBlank() && !posterUrl.isNullOrBlank()) {
                    grouped.getOrPut(libraryName) { linkedSetOf() }.add(posterUrl)
                }
            }
            sortedSeries.forEach { item ->
                val libraryName = item.libraryName
                val posterUrl = item.posterUrl
                if (!libraryName.isNullOrBlank() && !posterUrl.isNullOrBlank()) {
                    grouped.getOrPut(libraryName) { linkedSetOf() }.add(posterUrl)
                }
            }
            grouped.forEach { (libraryName, urls) ->
                put(libraryName, urls.take(18))
            }
        }
    }

    val selectedLibraryName = selectedAdvancedLibrary.value
    val selectedLibraryMoviesCount = selectedLibraryName?.let { movieLibrariesCount[it] ?: 0 } ?: 0
    val selectedLibrarySeriesCount = selectedLibraryName?.let { seriesLibrariesCount[it] ?: 0 } ?: 0
    val selectedLibraryOthersCount = selectedLibraryName?.let { otherLibrariesCount[it] ?: 0 } ?: 0

    LaunchedEffect(
        isAdvancedMode,
        selectedLibraryName,
        selectedLibraryMoviesCount,
        selectedLibrarySeriesCount,
        selectedLibraryOthersCount,
        library.selectedTab
    ) {
        if (isAdvancedMode && !selectedLibraryName.isNullOrBlank()) {
            when {
                selectedLibraryMoviesCount == 0 && selectedLibrarySeriesCount > 0 && library.selectedTab != LibraryTab.Series -> {
                    onTabSelected(LibraryTab.Series)
                }

                selectedLibrarySeriesCount == 0 && selectedLibraryMoviesCount > 0 && library.selectedTab != LibraryTab.Movies -> {
                    onTabSelected(LibraryTab.Movies)
                }

                selectedLibraryMoviesCount == 0 && selectedLibrarySeriesCount == 0 && selectedLibraryOthersCount > 0 && library.selectedTab != LibraryTab.Others -> {
                    onTabSelected(LibraryTab.Others)
                }
            }
        }
    }

    val visibleMovies = when {
        !isAdvancedMode -> sortedMovies
        selectedLibraryName.isNullOrBlank() -> emptyList()
        else -> sortedMovies.filter { (it.libraryName ?: unassignedLibraryLabel) == selectedLibraryName }
    }

    val visibleSeries = when {
        !isAdvancedMode -> sortedSeries
        selectedLibraryName.isNullOrBlank() -> emptyList()
        else -> sortedSeries.filter { (it.libraryName ?: unassignedLibraryLabel) == selectedLibraryName }
    }

    val visibleOthers = when {
        !isAdvancedMode -> sortedOthers
        selectedLibraryName.isNullOrBlank() -> emptyList()
        else -> sortedOthers.filter { (it.libraryName ?: unassignedLibraryLabel) == selectedLibraryName }
    }

    val otherLibraryOptions = remember(visibleOthers, unassignedLibraryLabel) {
        visibleOthers
            .map { it.libraryName ?: unassignedLibraryLabel }
            .distinct()
            .sorted()
    }

    LaunchedEffect(library.selectedTab, isAdvancedMode, selectedLibraryName, otherLibraryOptions) {
        if (library.selectedTab != LibraryTab.Others) {
            selectedOtherLibraryFilter.value = null
        } else {
            val currentFilter = selectedOtherLibraryFilter.value
            if (currentFilter != null && currentFilter !in otherLibraryOptions) {
                selectedOtherLibraryFilter.value = null
            }
        }
    }

    val filteredVisibleOthers = selectedOtherLibraryFilter.value?.let { selectedLibrary ->
        visibleOthers.filter { (it.libraryName ?: unassignedLibraryLabel) == selectedLibrary }
    } ?: visibleOthers

    val activeFavoriteOnly = when (library.selectedTab) {
        LibraryTab.Movies -> library.showOnlyMovieFavorites
        LibraryTab.Series -> library.showOnlySeriesFavorites
        LibraryTab.Others -> false
    }

    val currentItemsMissingCreatedDate = when (library.selectedTab) {
        LibraryTab.Movies -> visibleMovies.count { it.createdUtcMillis == null }
        LibraryTab.Series -> visibleSeries.count { it.createdUtcMillis == null }
        LibraryTab.Others -> filteredVisibleOthers.count { it.createdUtcMillis == null }
    }

    val currentItemsTotal = when (library.selectedTab) {
        LibraryTab.Movies -> visibleMovies.size
        LibraryTab.Series -> visibleSeries.size
        LibraryTab.Others -> filteredVisibleOthers.size
    }

    val novedadesMoviesCount = visibleMovies.count {
        val created = it.createdUtcMillis ?: return@count false
        created > library.lastSeenEpochMillis
    }

    val novedadesSeriesCount = visibleSeries.count {
        val created = it.createdUtcMillis ?: return@count false
        created > library.lastSeenEpochMillis
    }

    val novedadesOthersCount = filteredVisibleOthers.count {
        val created = it.createdUtcMillis ?: return@count false
        created > library.lastSeenEpochMillis
    }

    val currentNovedadesCount = when (library.selectedTab) {
        LibraryTab.Movies -> novedadesMoviesCount
        LibraryTab.Series -> novedadesSeriesCount
        LibraryTab.Others -> novedadesOthersCount
    }
    val currentMaxCreatedEpochMillis = when (library.selectedTab) {
        LibraryTab.Movies -> visibleMovies.mapNotNull { it.createdUtcMillis }.maxOrNull()
        LibraryTab.Series -> visibleSeries.mapNotNull { it.createdUtcMillis }.maxOrNull()
        LibraryTab.Others -> filteredVisibleOthers.mapNotNull { it.createdUtcMillis }.maxOrNull()
    }
    val currentNovedadesLabel = if (currentNovedadesCount > 99) "99+" else currentNovedadesCount.toString()

    val movieNovedadesLabel = if (novedadesMoviesCount > 99) "99+" else novedadesMoviesCount.toString()
    val seriesNovedadesLabel = if (novedadesSeriesCount > 99) "99+" else novedadesSeriesCount.toString()
    val othersNovedadesLabel = if (novedadesOthersCount > 99) "99+" else novedadesOthersCount.toString()

    BackHandler {
        when {
            selectedLibraryForCoverDialog.value != null -> selectedLibraryForCoverDialog.value = null
            selectedMovieId.value != null -> selectedMovieId.value = null
            selectedSeries.value != null -> selectedSeries.value = null
            selectedOtherGroupType.value != null -> selectedOtherGroupType.value = null
            showSyncDialog.value -> showSyncDialog.value = false
            showSyncHistoryDialog.value -> showSyncHistoryDialog.value = false
            isAdvancedLibraryView -> selectedAdvancedLibrary.value = null
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressAt.longValue < 2000L) {
                    activity?.finish()
                } else {
                    lastBackPressAt.longValue = now
                    Toast.makeText(context, pressBackAgainMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.app_title))
            },
            actions = {
                Button(onClick = onOpenConfig) {
                    Text(stringResource(R.string.configure))
                }
                Button(onClick = { showSyncDialog.value = true }) {
                    Text(stringResource(R.string.sync))
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
                        Text(stringResource(R.string.stop))
                    }
                }
            }
        }

        if (sync.lastError != null) {
            val isCancelNotice = sync.lastError.startsWith(stringResource(R.string.sync_cancelled_prefix))
            Text(
                text = sync.lastError,
                color = if (isCancelNotice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        if (!isAdvancedMode) {
            TabRow(
                selectedTabIndex = when (library.selectedTab) {
                    LibraryTab.Movies -> 0
                    LibraryTab.Series -> 1
                    LibraryTab.Others -> 2
                }
            ) {
                Tab(
                    selected = library.selectedTab == LibraryTab.Movies,
                    onClick = { onTabSelected(LibraryTab.Movies) },
                    text = {
                        Text(
                            if (novedadesMoviesCount > 0) {
                                stringResource(R.string.movies_tab_count, movieNovedadesLabel)
                            } else {
                                stringResource(R.string.movies)
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
                                stringResource(R.string.series_tab_count, seriesNovedadesLabel)
                            } else {
                                stringResource(R.string.series)
                            }
                        )
                    }
                )
                Tab(
                    selected = library.selectedTab == LibraryTab.Others,
                    onClick = { onTabSelected(LibraryTab.Others) },
                    text = {
                        Text(
                            if (novedadesOthersCount > 0) {
                                stringResource(R.string.others_tab_count, othersNovedadesLabel)
                            } else {
                                stringResource(R.string.others)
                            }
                        )
                    }
                )
            }
        } else if (isAdvancedLibraryView) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "< ${stringResource(R.string.all_libraries)}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { selectedAdvancedLibrary.value = null }
                )
                Text(
                    text = selectedLibraryName.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val hasMovies = selectedLibraryMoviesCount > 0
            val hasSeries = selectedLibrarySeriesCount > 0
            val hasOthers = selectedLibraryOthersCount > 0
            if (listOf(hasMovies, hasSeries, hasOthers).count { it } > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = library.selectedTab == LibraryTab.Movies,
                        onClick = { onTabSelected(LibraryTab.Movies) },
                        enabled = hasMovies,
                        label = {
                            Text("${stringResource(R.string.movies)} ($selectedLibraryMoviesCount)")
                        }
                    )
                    FilterChip(
                        selected = library.selectedTab == LibraryTab.Series,
                        onClick = { onTabSelected(LibraryTab.Series) },
                        enabled = hasSeries,
                        label = {
                            Text("${stringResource(R.string.series)} ($selectedLibrarySeriesCount)")
                        }
                    )
                    FilterChip(
                        selected = library.selectedTab == LibraryTab.Others,
                        onClick = { onTabSelected(LibraryTab.Others) },
                        enabled = hasOthers,
                        label = {
                            Text("${stringResource(R.string.others)} ($selectedLibraryOthersCount)")
                        }
                    )
                }
            }
        }

        val currentCount = when (library.selectedTab) {
            LibraryTab.Movies -> sortedMovies.size
            LibraryTab.Series -> sortedSeries.size
            LibraryTab.Others -> sortedOthers.size
        }
        val totalCount = when (library.selectedTab) {
            LibraryTab.Movies -> totalMovies
            LibraryTab.Series -> totalSeries
            LibraryTab.Others -> totalOthers
        }

        if (!isAdvancedOverview) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (library.selectedTab) {
                        LibraryTab.Movies -> stringResource(R.string.movies_showing, currentCount, totalCount)
                        LibraryTab.Series -> stringResource(R.string.series_showing, currentCount, totalCount)
                        LibraryTab.Others -> stringResource(R.string.others_showing, currentCount, totalCount)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (!isAdvancedOverview && currentNovedadesCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.new_since_last_visit, currentNovedadesLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.mark_seen),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onMarkNovedadesAsSeen(currentMaxCreatedEpochMillis) }
                )
            }
        }

        if (!isAdvancedOverview && library.selectedTab != LibraryTab.Others) {
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
                    label = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.active_filters, activeFiltersCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activeFiltersCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.combinedClickable(
                        enabled = activeFiltersCount > 0,
                        onClick = { onClearAllFilters() },
                        onLongClick = { onClearAllFiltersAcrossTabs() }
                    )
                )
            }
        }

        if (!isAdvancedOverview) {
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
                            text = stringResource(R.string.sort_az),
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
                            text = stringResource(R.string.recently_added),
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
                            text = stringResource(R.string.favorites),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        if (!isAdvancedOverview && library.selectedTab == LibraryTab.Others && otherLibraryOptions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.libraries_label)}:",
                    style = MaterialTheme.typography.bodySmall
                )
                FilterChip(
                    selected = selectedOtherLibraryFilter.value == null,
                    onClick = { selectedOtherLibraryFilter.value = null },
                    label = { Text(stringResource(R.string.all_libraries)) }
                )
                otherLibraryOptions.forEach { libraryName ->
                    FilterChip(
                        selected = selectedOtherLibraryFilter.value == libraryName,
                        onClick = { selectedOtherLibraryFilter.value = libraryName },
                        label = { Text(libraryName) }
                    )
                }
            }
        }

        if (
            !isAdvancedOverview &&
            library.selectedTab != LibraryTab.Others &&
            activeSortMode == LibrarySortMode.RecentlyAdded &&
            currentItemsTotal > 0 &&
            currentItemsMissingCreatedDate > 0
        ) {
            val hintText = if (currentItemsMissingCreatedDate == currentItemsTotal) {
                stringResource(R.string.no_created_date_all)
            } else {
                stringResource(R.string.no_created_date_partial, currentItemsMissingCreatedDate)
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

        if (!isAdvancedOverview && library.selectedTab != LibraryTab.Others) {
            Text(
                text = stringResource(R.string.hold_filters_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (isAdvancedOverview && showLibraryCoverHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.library_cover_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onDismissLibraryCoverHint) {
                    Text(stringResource(R.string.got_it))
                }
            }
        }

        if (!isAdvancedOverview && (genreFilters.isNotEmpty() || technicalFilters.isNotEmpty())) {
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
                            text = stringResource(R.string.label_genres, genreFilters.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (technicalFilters.isNotEmpty()) {
                        technicalFilters.forEach { (type, values) ->
                            val techLabel = when (type) {
                                TechnicalFilterType.Quality -> stringResource(R.string.tech_quality)
                                TechnicalFilterType.Format -> stringResource(R.string.tech_format)
                                TechnicalFilterType.Resolution -> stringResource(R.string.tech_resolution)
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
                            text = stringResource(R.string.clear_genre),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onClearGenreFilter() }
                        )
                    }
                    if (technicalFilters.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.clear_details),
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
            if (isAdvancedOverview) {
                AdvancedLibrariesGrid(
                    libraries = allLibraries,
                    posterUrls = effectiveLibraryPosterUrls,
                    movieCounts = movieLibrariesCount,
                    seriesCounts = seriesLibrariesCount,
                    otherCounts = otherLibrariesCount,
                    onOpenLibrary = { libraryName ->
                        selectedAdvancedLibrary.value = libraryName
                        val moviesCount = movieLibrariesCount[libraryName] ?: 0
                        val seriesCount = seriesLibrariesCount[libraryName] ?: 0
                        val othersCount = otherLibrariesCount[libraryName] ?: 0
                        when {
                            moviesCount > 0 -> onTabSelected(LibraryTab.Movies)
                            seriesCount > 0 -> onTabSelected(LibraryTab.Series)
                            othersCount > 0 -> onTabSelected(LibraryTab.Others)
                        }
                    },
                    onEditLibraryCover = { libraryName ->
                        onDismissLibraryCoverHint()
                        selectedLibraryForCoverDialog.value = libraryName
                    }
                )
            } else {
                if (library.selectedTab == LibraryTab.Movies) {
                    if (uiState.config.listDisplayMode == ListDisplayMode.Paged50) {
                        MoviesPagedGrid(
                            movies = visibleMovies,
                            onMovieClick = { selectedMovieId.value = it.id },
                            onFavoriteToggle = onMovieFavoriteToggled,
                            onGenreClick = onGenreSelected,
                            onTechnicalFilterClick = onTechnicalFilterSelected,
                            refreshingMovieId = movieRefreshingId.value
                        )
                    } else {
                        MoviesGrid(
                            movies = visibleMovies,
                            onMovieClick = { selectedMovieId.value = it.id },
                            onFavoriteToggle = onMovieFavoriteToggled,
                            onGenreClick = onGenreSelected,
                            onTechnicalFilterClick = onTechnicalFilterSelected,
                            refreshingMovieId = movieRefreshingId.value
                        )
                    }
                } else if (library.selectedTab == LibraryTab.Series) {
                    val onSeriesSelected: (SeriesEntity) -> Unit = { s ->
                        selectedSeries.value = s
                    }

                    if (uiState.config.listDisplayMode == ListDisplayMode.Paged50) {
                        SeriesPagedList(
                            series = visibleSeries,
                            onSeriesClick = onSeriesSelected,
                            onFavoriteToggle = onSeriesFavoriteToggled,
                            onGenreClick = onGenreSelected
                        )
                    } else {
                        SeriesList(
                            series = visibleSeries,
                            onSeriesClick = onSeriesSelected,
                            onFavoriteToggle = onSeriesFavoriteToggled,
                            onGenreClick = onGenreSelected
                        )
                    }
                } else {
                    OtherMediaTypeList(
                        items = filteredVisibleOthers,
                        onOpenType = { mediaType ->
                            selectedOtherGroupType.value = mediaType
                        }
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
                text = sync.lastSyncText?.let { stringResource(R.string.last_update, it) }
                    ?: stringResource(R.string.last_update_pending),
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
                    showFilePath = uiState.config.showFilePath,
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
                showFilePath = uiState.config.showFilePath,
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

        selectedOtherGroupType.value?.let { mediaType ->
            val groupItems = filteredVisibleOthers.filter { it.mediaType.equals(mediaType, ignoreCase = true) }
            OtherMediaItemsDialog(
                mediaType = mediaType,
                items = groupItems,
                onDismiss = { selectedOtherGroupType.value = null }
            )
        }

        if (showSyncDialog.value) {
            AlertDialog(
                onDismissRequest = { showSyncDialog.value = false },
                confirmButton = {},
                dismissButton = {},
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.sync_warning_large_collections),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(stringResource(R.string.what_to_sync))
                        Button(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncAll()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.all))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    showSyncDialog.value = false
                                    onSyncMovies()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.movies))
                            }
                            Button(
                                onClick = {
                                    showSyncDialog.value = false
                                    onSyncSeries()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.series))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncOthers()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.others))
                        }

                        Text(stringResource(R.string.recently_added))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showSyncDialog.value = false
                                    onSyncRecentMovies()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.movies))
                            }
                            OutlinedButton(
                                onClick = {
                                    showSyncDialog.value = false
                                    onSyncRecentSeries()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.series))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncRecentOthers()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.others))
                        }

                        Text(stringResource(R.string.fast))
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncFastAll()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.all))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showSyncDialog.value = false
                                    onSyncFastMovies()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.movies))
                            }
                            OutlinedButton(
                                onClick = {
                                    showSyncDialog.value = false
                                    onSyncFastSeries()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.series))
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncFastOthers()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.others))
                        }

                        Text(stringResource(R.string.details_no_catalog))
                        OutlinedButton(
                            onClick = {
                                showSyncDialog.value = false
                                onSyncDetailsAll()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.all))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showSyncDialog.value = false
                                    onSyncDetailsMovies()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.movies))
                            }
                            OutlinedButton(
                                onClick = {
                                    showSyncDialog.value = false
                                    onSyncDetailsSeries()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.series))
                            }
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
                        Text(stringResource(R.string.close))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            onClearSyncHistory()
                            showSyncHistoryDialog.value = false
                        }
                    ) {
                        Text(stringResource(R.string.clear_history), color = MaterialTheme.colorScheme.error)
                    }
                },
                title = { Text(stringResource(R.string.sync_history)) },
                text = {
                    if (sync.syncHistory.isEmpty()) {
                        Text(stringResource(R.string.empty_history))
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sync.syncHistory.forEach { entry ->
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

    selectedLibraryForCoverDialog.value?.let { libraryName ->
        LibraryCoverPickerDialog(
            libraryName = libraryName,
            currentImageUrl = effectiveLibraryPosterUrls[libraryName],
            candidatePosterUrls = libraryPosterCandidates[libraryName].orEmpty(),
            onDismiss = { selectedLibraryForCoverDialog.value = null },
            onRestoreAutomatic = {
                onLibraryCoverReset(libraryName)
                selectedLibraryForCoverDialog.value = null
            },
            onSelectPoster = { imageUrl ->
                onLibraryCoverSelected(libraryName, imageUrl)
                selectedLibraryForCoverDialog.value = null
            }
        )
    }
}

@Composable
private fun AdvancedLibrariesGrid(
    libraries: List<String>,
    posterUrls: Map<String, String?>,
    movieCounts: Map<String, Int>,
    seriesCounts: Map<String, Int>,
    otherCounts: Map<String, Int>,
    onOpenLibrary: (String) -> Unit,
    onEditLibraryCover: (String) -> Unit
) {
    if (libraries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(libraries) { libraryName ->
            val posterUrl = posterUrls[libraryName]
            val movies = movieCounts[libraryName] ?: 0
            val series = seriesCounts[libraryName] ?: 0
            val others = otherCounts[libraryName] ?: 0
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenLibrary(libraryName) },
                        onLongClick = { onEditLibraryCover(libraryName) }
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PosterImage(
                        imageUrl = posterUrl,
                        contentDescription = libraryName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = libraryName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${stringResource(R.string.movies)}: $movies",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${stringResource(R.string.series)}: $series",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${stringResource(R.string.others)}: $others",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherMediaTypeList(
    items: List<OtherMediaEntity>,
    onOpenType: (String) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val grouped = items.groupBy { it.mediaType.lowercase() }
    val videos = grouped["video"].orEmpty()
    val photos = grouped["photo"].orEmpty()
    val cards = listOf(
        stringResource(R.string.videos) to videos,
        stringResource(R.string.images) to photos
    ).filter { it.second.isNotEmpty() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(cards) { (label, groupItems) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val mediaType = groupItems.firstOrNull()?.mediaType ?: return@clickable
                        onOpenType(mediaType)
                    },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.items_count, groupItems.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OtherMediaItemsDialog(
    mediaType: String,
    items: List<OtherMediaEntity>,
    onDismiss: () -> Unit
) {
    val searchQuery = remember(mediaType) { mutableStateOf("") }
    val recentFirst = remember(mediaType) { mutableStateOf(false) }

    val typeLabel = if (mediaType.equals("photo", ignoreCase = true)) {
        stringResource(R.string.images)
    } else {
        stringResource(R.string.videos)
    }

    val filteredItems = remember(items, searchQuery.value) {
        val query = searchQuery.value.trim()
        if (query.isBlank()) {
            items
        } else {
            items.filter { it.title.contains(query, ignoreCase = true) }
        }
    }

    val displayedItems = remember(filteredItems, recentFirst.value) {
        if (recentFirst.value) {
            filteredItems.sortedWith(
                compareByDescending<OtherMediaEntity> { it.createdUtcMillis ?: Long.MIN_VALUE }
                    .thenBy { it.title.lowercase() }
            )
        } else {
            filteredItems.sortedBy { it.title.lowercase() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.other_items_dialog_title, typeLabel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = searchQuery.value,
                    onValueChange = { searchQuery.value = it },
                    label = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !recentFirst.value,
                        onClick = { recentFirst.value = false },
                        label = { Text(stringResource(R.string.sort_az)) }
                    )
                    FilterChip(
                        selected = recentFirst.value,
                        onClick = { recentFirst.value = true },
                        label = { Text(stringResource(R.string.recently_added)) }
                    )
                }

                if (displayedItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(displayedItems) { item ->
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun LibraryCoverPickerDialog(
    libraryName: String,
    currentImageUrl: String?,
    candidatePosterUrls: List<String>,
    onDismiss: () -> Unit,
    onRestoreAutomatic: () -> Unit,
    onSelectPoster: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.library_cover_dialog_title, libraryName))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!currentImageUrl.isNullOrBlank()) {
                    PosterImage(
                        imageUrl = currentImageUrl,
                        contentDescription = libraryName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
                if (candidatePosterUrls.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_cover_no_candidates),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.library_cover_choose_manual),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(candidatePosterUrls) { posterUrl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPoster(posterUrl) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                PosterImage(
                                    imageUrl = posterUrl,
                                    contentDescription = libraryName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRestoreAutomatic) {
                Text(stringResource(R.string.library_cover_restore_automatic))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
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
                    Text(stringResource(R.string.no_results))
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
                                contentDescription = if (movie.isFavorite) {
                                    stringResource(R.string.remove_from_favorites)
                                } else {
                                    stringResource(R.string.add_to_favorites)
                                },
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
                                text = stringResource(R.string.updating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (movie.genres.isEmpty()) {
                        Text(
                            text = stringResource(R.string.genres_none),
                            style = responsiveBodySmall,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 6.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.genres_title),
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
                            text = stringResource(R.string.technical_details),
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
                                    text = stringResource(R.string.label_quality, quality),
                                    style = responsiveBodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        onTechnicalFilterClick(TechnicalFilterType.Quality, quality)
                                    }
                                )
                            }
                            movie.format?.takeIf { it.isNotBlank() }?.let { format ->
                                Text(
                                    text = stringResource(R.string.label_format, format),
                                    style = responsiveBodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        onTechnicalFilterClick(TechnicalFilterType.Format, format)
                                    }
                                )
                            }
                            movie.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                                Text(
                                    text = stringResource(R.string.label_resolution, resolution),
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
                                    contentDescription = if (item.isFavorite) {
                                        stringResource(R.string.remove_from_favorites)
                                    } else {
                                        stringResource(R.string.add_to_favorites)
                                    },
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
                                text = stringResource(R.string.label_genres, item.genres.take(3).joinToString()),
                                style = responsiveBodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    item.genres.firstOrNull()?.let { onGenreClick(it) }
                                }
                            )
                        }
                        Text(
                            text = stringResource(R.string.label_seasons, item.totalSeasons),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.label_episodes, item.totalEpisodes),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.view_season_details),
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
                                    contentDescription = if (item.isFavorite) {
                                        stringResource(R.string.remove_from_favorites)
                                    } else {
                                        stringResource(R.string.add_to_favorites)
                                    },
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
                                    text = stringResource(R.string.genres_title),
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
                    Text(stringResource(R.string.no_results))
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
    showFilePath: Boolean,
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
                        contentDescription = if (movie.isFavorite) {
                            stringResource(R.string.remove_from_favorites)
                        } else {
                            stringResource(R.string.add_to_favorites)
                        },
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
                        Text(stringResource(R.string.movie_updating_info))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(stringResource(R.string.label_format, movie.format ?: "-"))
                Text(stringResource(R.string.label_quality, movie.quality ?: "-"))
                Text(stringResource(R.string.label_resolution, movie.resolution ?: "-"))
                movie.productionYear?.let { Text(stringResource(R.string.label_year, it)) }
                Text(
                    stringResource(
                        R.string.label_bitrate,
                        movie.bitrateMbps?.let { "%.2f Mbps".format(it) } ?: "-"
                    )
                )
                val durationText = movie.durationMinutes?.let { totalMinutes ->
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    if (hours > 0) {
                        "${hours}h ${minutes}min"
                    } else {
                        "${minutes}min"
                    }
                } ?: "-"
                Text(stringResource(R.string.label_duration, durationText))
                Text(
                    stringResource(
                        R.string.label_size,
                        movie.sizeGb?.let { "%.2f GB".format(it) } ?: "-"
                    )
                )
                Text(
                    stringResource(
                        R.string.label_audio,
                        if (movie.audioLanguages.isEmpty()) "-" else movie.audioLanguages.joinToString()
                    )
                )
                Text(
                    stringResource(
                        R.string.label_subtitles,
                        if (movie.subtitleLanguages.isEmpty()) "-" else movie.subtitleLanguages.joinToString()
                    )
                )
                if (showFilePath && !movie.filePath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.label_path, movie.filePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isRefreshing) {
                            stringResource(R.string.updating)
                        } else {
                            stringResource(R.string.update_this_movie)
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun SeriesDetailDialog(
    series: SeriesEntity,
    showFilePath: Boolean,
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
                        contentDescription = if (series.isFavorite) {
                            stringResource(R.string.remove_from_favorites)
                        } else {
                            stringResource(R.string.add_to_favorites)
                        },
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
                Text(stringResource(R.string.label_seasons, series.totalSeasons))
                Text(stringResource(R.string.label_episodes, series.totalEpisodes))
                series.productionYear?.let { Text(stringResource(R.string.label_year, it)) }

                if (series.genres.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.label_genres, series.genres.joinToString()),
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
                        Text(stringResource(R.string.series_updating_info))
                    }
                }

                Text(
                    text = stringResource(R.string.season_detail_title),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                )
                seasons.sortedBy { it.seasonNumber }.forEach { season ->
                    Text(
                        text = stringResource(R.string.season_item_short, season.seasonNumber, season.episodeCount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    season.quality?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.label_quality, it), style = MaterialTheme.typography.bodySmall)
                    }
                    season.format?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.label_format, it), style = MaterialTheme.typography.bodySmall)
                    }
                    season.resolution?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.label_resolution, it), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (seasons.isEmpty()) {
                    Text(
                        text = stringResource(R.string.series_no_season_details),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (showFilePath && !series.filePath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.label_path, series.filePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isRefreshing) {
                            stringResource(R.string.updating)
                        } else {
                            stringResource(R.string.update_this_series)
                        }
                    )
                }
                Text(
                    text = stringResource(R.string.close_when_done),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                enabled = !isRefreshing
            ) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
