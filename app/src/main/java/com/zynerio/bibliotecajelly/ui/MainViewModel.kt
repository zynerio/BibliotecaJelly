package com.zynerio.bibliotecajelly.ui

import android.app.Application
import android.annotation.SuppressLint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zynerio.bibliotecajelly.R
import com.zynerio.bibliotecajelly.data.AutoSyncMode
import com.zynerio.bibliotecajelly.data.ConnectionResult
import com.zynerio.bibliotecajelly.data.JellyfinRepository
import com.zynerio.bibliotecajelly.data.LibraryViewInfo
import com.zynerio.bibliotecajelly.data.MovieEntity
import com.zynerio.bibliotecajelly.data.OtherMediaEntity
import com.zynerio.bibliotecajelly.data.SeriesEntity
import com.zynerio.bibliotecajelly.data.SeasonEntity
import com.zynerio.bibliotecajelly.data.ServiceLocator
import com.zynerio.bibliotecajelly.data.MovieDetailsSyncMode
import com.zynerio.bibliotecajelly.data.ListDisplayMode
import com.zynerio.bibliotecajelly.data.LibrarySortMode
import com.zynerio.bibliotecajelly.data.ClearDataScope
import com.zynerio.bibliotecajelly.data.DuplicateReportFormat
import com.zynerio.bibliotecajelly.data.SyncResult
import com.zynerio.bibliotecajelly.data.SyncScope
import com.zynerio.bibliotecajelly.data.SyncProgressPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LibraryTab {
    Movies,
    Series,
    Others
}

enum class TechnicalFilterType {
    Quality,
    Format,
    Resolution
}

enum class DuplicateMatchMode {
    Flexible,
    Balanced,
    Strict
}

data class ConfigUiState(
    val serverAddress: String = "",
    val port: String = "8096",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val isValidating: Boolean = false,
    val isExportingReport: Boolean = false,
    val validationError: String? = null,
    val hasSavedConfig: Boolean = false,
    val isConfigured: Boolean = false,
    val databaseSizeText: String = "",
    val databaseUsagePercent: Float? = null,
    val postersSizeText: String = "",
    val postersUsagePercent: Float? = null,
    val downloadPostersOffline: Boolean = false,
    val showFilePath: Boolean = false,
    val librariesAdvancedView: Boolean = false,
    val reconcileDeletedOnRecentSync: Boolean = false,
    val reportMovieDuplicateMatchMode: DuplicateMatchMode = DuplicateMatchMode.Strict,
    val reportSeriesDuplicateMatchMode: DuplicateMatchMode = DuplicateMatchMode.Strict,
    val connectedServerVersion: String? = null,
    val autoSyncMode: AutoSyncMode = AutoSyncMode.OnStart,
    val movieDetailsSyncMode: MovieDetailsSyncMode = MovieDetailsSyncMode.All,
    val listDisplayMode: ListDisplayMode = ListDisplayMode.Infinite
)

data class SyncUiState(
    val isSyncing: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val phaseText: String = "",
    val serverStatusText: String = "",
    val isServerActive: Boolean = false,
    val lastSyncText: String? = null,
    val lastError: String? = null,
    val syncHistory: List<String> = emptyList()
)

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.Movies,
    val searchQuery: String = "",
    val lastSeenEpochMillis: Long = 0L,
    val movieSortMode: LibrarySortMode = LibrarySortMode.Alphabetical,
    val seriesSortMode: LibrarySortMode = LibrarySortMode.Alphabetical,
    val showOnlyMovieFavorites: Boolean = false,
    val showOnlySeriesFavorites: Boolean = false,
    val showOnlyMovieDuplicates: Boolean = false,
    val showOnlySeriesDuplicates: Boolean = false,
    val movieDuplicateMatchMode: DuplicateMatchMode = DuplicateMatchMode.Strict,
    val seriesDuplicateMatchMode: DuplicateMatchMode = DuplicateMatchMode.Strict,
    val selectedMovieGenres: Set<String> = emptySet(),
    val selectedSeriesGenres: Set<String> = emptySet(),
    val selectedMovieTechnicalFilters: Map<TechnicalFilterType, Set<String>> = emptyMap(),
    val selectedSeriesTechnicalFilters: Map<TechnicalFilterType, Set<String>> = emptyMap(),
    val selectedSeriesTechnicalMatchedIds: Set<String>? = null
)

data class MainUiState(
    val config: ConfigUiState = ConfigUiState(),
    val sync: SyncUiState = SyncUiState(),
    val library: LibraryUiState = LibraryUiState(),
    val update: UpdateUiState = UpdateUiState()
)

data class UpdateUiState(
    val showDialog: Boolean = false,
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
    val releaseTag: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private data class ConfigDraftSnapshot(
        val serverAddress: String,
        val port: String,
        val username: String,
        val password: String,
        val apiKey: String,
        val hasSavedConfig: Boolean,
        val downloadPostersOffline: Boolean,
        val showFilePath: Boolean,
        val librariesAdvancedView: Boolean,
        val reconcileDeletedOnRecentSync: Boolean,
        val reportMovieDuplicateMatchMode: DuplicateMatchMode,
        val reportSeriesDuplicateMatchMode: DuplicateMatchMode,
        val connectedServerVersion: String?,
        val autoSyncMode: AutoSyncMode,
        val movieDetailsSyncMode: MovieDetailsSyncMode,
        val listDisplayMode: ListDisplayMode
    )

    private val repository: JellyfinRepository =
        ServiceLocator.provideRepository(application)

    private val _uiState = MutableStateFlow(
        MainUiState(
            config = ConfigUiState(
                databaseSizeText = application.getString(R.string.db_size_calculating),
                postersSizeText = application.getString(R.string.posters_size_calculating)
            ),
            sync = SyncUiState(
                phaseText = application.getString(R.string.phase_syncing_catalog),
                serverStatusText = application.getString(R.string.server_not_checked)
            )
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val searchQueryMovies = MutableStateFlow("")
    private val searchQuerySeries = MutableStateFlow("")
    private val searchQueryOthers = MutableStateFlow("")
    private val _libraryViews = MutableStateFlow<List<LibraryViewInfo>>(emptyList())
    val libraryViews: StateFlow<List<LibraryViewInfo>> = _libraryViews.asStateFlow()
    private val _libraryCoverOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val libraryCoverOverrides: StateFlow<Map<String, String>> = _libraryCoverOverrides.asStateFlow()
    private val _showLibraryCoverHint = MutableStateFlow(false)
    val showLibraryCoverHint: StateFlow<Boolean> = _showLibraryCoverHint.asStateFlow()

    val movies: StateFlow<List<MovieEntity>> =
        searchQueryMovies.flatMapLatest { query ->
            repository.searchMovies(query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val series: StateFlow<List<SeriesEntity>> =
        searchQuerySeries.flatMapLatest { query ->
            repository.searchSeries(query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val others: StateFlow<List<OtherMediaEntity>> =
        searchQueryOthers.flatMapLatest { query ->
            repository.searchOthers(query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val totalMoviesCount: StateFlow<Int> =
        repository.movies.map { it.size }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val totalSeriesCount: StateFlow<Int> =
        repository.series.map { it.size }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val totalOthersCount: StateFlow<Int> =
        repository.others.map { it.size }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val isConfigured: StateFlow<Boolean> =
        uiState.map { it.config.isConfigured }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private var currentSyncJob: Job? = null
    private var committedConfigSnapshot = snapshotFromConfig(_uiState.value.config)
    private var configEntrySyncSnapshot: SyncUiState? = null
    private val partialSyncWarning =
        application.getString(R.string.warning_partial_sync)
    private val installedVersionName: String by lazy {
        runCatching {
            getApplication<Application>()
                .packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
                .versionName
        }.getOrNull().orEmpty().ifBlank { "0.0" }
    }

    private fun snapshotFromConfig(config: ConfigUiState): ConfigDraftSnapshot = ConfigDraftSnapshot(
        serverAddress = config.serverAddress,
        port = config.port,
        username = config.username,
        password = config.password,
        apiKey = config.apiKey,
        hasSavedConfig = config.hasSavedConfig,
        downloadPostersOffline = config.downloadPostersOffline,
        showFilePath = config.showFilePath,
        librariesAdvancedView = config.librariesAdvancedView,
        reconcileDeletedOnRecentSync = config.reconcileDeletedOnRecentSync,
        reportMovieDuplicateMatchMode = config.reportMovieDuplicateMatchMode,
        reportSeriesDuplicateMatchMode = config.reportSeriesDuplicateMatchMode,
        connectedServerVersion = config.connectedServerVersion,
        autoSyncMode = config.autoSyncMode,
        movieDetailsSyncMode = config.movieDetailsSyncMode,
        listDisplayMode = config.listDisplayMode
    )

    private fun commitConfigSnapshot(config: ConfigUiState = _uiState.value.config) {
        committedConfigSnapshot = snapshotFromConfig(config)
    }

    private fun restoreCommittedConfig(isConfigured: Boolean) {
        val snapshot = committedConfigSnapshot
        val current = _uiState.value.config
        _uiState.value = _uiState.value.copy(
            config = current.copy(
                serverAddress = snapshot.serverAddress,
                port = snapshot.port,
                username = snapshot.username,
                password = snapshot.password,
                apiKey = snapshot.apiKey,
                isValidating = false,
                isExportingReport = false,
                validationError = null,
                hasSavedConfig = snapshot.hasSavedConfig,
                isConfigured = isConfigured,
                downloadPostersOffline = snapshot.downloadPostersOffline,
                showFilePath = snapshot.showFilePath,
                librariesAdvancedView = snapshot.librariesAdvancedView,
                reconcileDeletedOnRecentSync = snapshot.reconcileDeletedOnRecentSync,
                reportMovieDuplicateMatchMode = snapshot.reportMovieDuplicateMatchMode,
                reportSeriesDuplicateMatchMode = snapshot.reportSeriesDuplicateMatchMode,
                connectedServerVersion = snapshot.connectedServerVersion,
                autoSyncMode = snapshot.autoSyncMode,
                movieDetailsSyncMode = snapshot.movieDetailsSyncMode,
                listDisplayMode = snapshot.listDisplayMode
            )
        )
    }

    init {
        viewModelScope.launch {
            val config = repository.getServerConfig()
            val lastSync = repository.getLastSyncEpochMillis()
            val autoSyncMode = repository.getAutoSyncMode()
            val movieDetailsSyncMode = repository.getMovieDetailsSyncMode()
            val listDisplayMode = repository.getListDisplayMode()
            val offlinePostersEnabled = repository.getOfflinePostersEnabled()
            val showFilePath = repository.getShowFilePath()
            val librariesAdvancedView = repository.getLibrariesAdvancedView()
            val reconcileDeletedOnRecentSync = repository.getReconcileDeletedOnRecentSync()
            _libraryCoverOverrides.value = repository.getLibraryCoverOverrides()
            _showLibraryCoverHint.value = !repository.isLibraryCoverHintDismissed()
            val movieSortMode = repository.getMovieSortMode()
            val seriesSortMode = repository.getSeriesSortMode()
            val now = System.currentTimeMillis()
            val lastSeenEpochMillis = repository.getLibraryLastSeenMillis() ?: now.also {
                repository.setLibraryLastSeenMillis(it)
            }

            if (config != null) {
                val parsed = runCatching { URI(config.baseUrl) }.getOrNull()
                val address = parsed?.host
                    ?: config.baseUrl
                        .removeSuffix("/")
                        .removePrefix("http://")
                        .removePrefix("https://")
                        .substringBefore(":")
                        .substringBefore("/")
                val port = parsed?.port?.takeIf { it > 0 }?.toString() ?: "8096"

                _uiState.value = _uiState.value.copy(
                    config = _uiState.value.config.copy(
                        serverAddress = address,
                        port = port,
                        username = config.username.orEmpty(),
                        password = config.password.orEmpty(),
                        apiKey = config.apiKey.orEmpty(),
                        hasSavedConfig = true,
                        isConfigured = true,
                        downloadPostersOffline = offlinePostersEnabled,
                        showFilePath = showFilePath,
                        librariesAdvancedView = librariesAdvancedView,
                        reconcileDeletedOnRecentSync = reconcileDeletedOnRecentSync,
                        autoSyncMode = autoSyncMode,
                        movieDetailsSyncMode = movieDetailsSyncMode,
                        listDisplayMode = listDisplayMode
                    ),
                    library = _uiState.value.library.copy(
                        lastSeenEpochMillis = lastSeenEpochMillis,
                        movieSortMode = movieSortMode,
                        seriesSortMode = seriesSortMode
                    ),
                    sync = _uiState.value.sync.copy(
                        lastSyncText = lastSync?.let { formatLastSync(it) },
                        syncHistory = repository.getSyncHistory()
                    )
                )

                commitConfigSnapshot(_uiState.value.config)
                configEntrySyncSnapshot = _uiState.value.sync.copy()

                updateServerStatus()
                loadLibraryViews()

                if (autoSyncMode == AutoSyncMode.OnStart) {
                    triggerStartupSync()
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    config = _uiState.value.config.copy(
                        downloadPostersOffline = offlinePostersEnabled,
                        showFilePath = showFilePath,
                        librariesAdvancedView = librariesAdvancedView,
                        reconcileDeletedOnRecentSync = reconcileDeletedOnRecentSync,
                        autoSyncMode = autoSyncMode,
                        movieDetailsSyncMode = movieDetailsSyncMode,
                        listDisplayMode = listDisplayMode
                    ),
                    library = _uiState.value.library.copy(
                        lastSeenEpochMillis = lastSeenEpochMillis,
                        movieSortMode = movieSortMode,
                        seriesSortMode = seriesSortMode
                    )
                )
                commitConfigSnapshot(_uiState.value.config)
                configEntrySyncSnapshot = _uiState.value.sync.copy()
            }

            refreshDatabaseSize()
            checkForAppUpdate()
        }
    }

    private suspend fun loadLibraryViews() {
        _libraryViews.value = repository.getLibraryViews()
    }

    fun setLibraryCoverOverride(libraryName: String, imageUrl: String) {
        viewModelScope.launch {
            repository.setLibraryCoverOverride(libraryName, imageUrl)
            _libraryCoverOverrides.value = repository.getLibraryCoverOverrides()
        }
    }

    fun clearLibraryCoverOverride(libraryName: String) {
        viewModelScope.launch {
            repository.clearLibraryCoverOverride(libraryName)
            _libraryCoverOverrides.value = repository.getLibraryCoverOverrides()
        }
    }

    fun dismissLibraryCoverHint() {
        viewModelScope.launch {
            repository.setLibraryCoverHintDismissed(true)
            _showLibraryCoverHint.value = false
        }
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(
            update = _uiState.value.update.copy(showDialog = false)
        )
    }

    fun postponeUpdateDialog() {
        val releaseTag = _uiState.value.update.releaseTag
        dismissUpdateDialog()
        if (releaseTag.isNullOrBlank()) return

        viewModelScope.launch {
            repository.setDismissedReleaseTag(releaseTag)
        }
    }

    fun onServerAddressChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(
                serverAddress = value,
                connectedServerVersion = null
            )
        )
    }

    fun onPortChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(
                port = value,
                connectedServerVersion = null
            )
        )
    }

    fun onUsernameChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(username = value)
        )
    }

    fun onPasswordChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(password = value)
        )
    }

    fun onApiKeyChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(apiKey = value)
        )
    }

    fun onAutoSyncModeChanged(mode: AutoSyncMode) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(autoSyncMode = mode)
        )
    }

    fun onMovieDetailsSyncModeChanged(mode: MovieDetailsSyncMode) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(movieDetailsSyncMode = mode)
        )
    }

    fun onListDisplayModeChanged(mode: ListDisplayMode) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(listDisplayMode = mode)
        )
    }

    fun onDownloadPostersOfflineChanged(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(downloadPostersOffline = enabled)
        )
    }

    fun onShowFilePathChanged(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(showFilePath = enabled)
        )
    }

    fun onLibrariesAdvancedViewChanged(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(librariesAdvancedView = enabled)
        )
    }

    fun onReconcileDeletedOnRecentSyncChanged(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(reconcileDeletedOnRecentSync = enabled)
        )
    }

    fun onReportMovieDuplicateMatchModeChanged(mode: DuplicateMatchMode) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(reportMovieDuplicateMatchMode = mode)
        )
    }

    fun onReportSeriesDuplicateMatchModeChanged(mode: DuplicateMatchMode) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(reportSeriesDuplicateMatchMode = mode)
        )
    }

    fun onSearchQueryChanged(value: String) {
        val currentTab = _uiState.value.library.selectedTab
        when (currentTab) {
            LibraryTab.Movies -> searchQueryMovies.value = value
            LibraryTab.Series -> searchQuerySeries.value = value
            LibraryTab.Others -> searchQueryOthers.value = value
        }

        _uiState.value = _uiState.value.copy(
            library = _uiState.value.library.copy(searchQuery = value)
        )
    }

    fun onTabSelected(tab: LibraryTab) {
        val newQuery = when (tab) {
            LibraryTab.Movies -> searchQueryMovies.value
            LibraryTab.Series -> searchQuerySeries.value
            LibraryTab.Others -> searchQueryOthers.value
        }

        _uiState.value = _uiState.value.copy(
            library = _uiState.value.library.copy(
                selectedTab = tab,
                searchQuery = newQuery
            )
        )
    }

    fun onGenreSelected(genre: String) {
        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = when (library.selectedTab) {
                LibraryTab.Movies -> {
                    val next = library.selectedMovieGenres.toMutableSet().apply {
                        if (any { it.equals(genre, ignoreCase = true) }) {
                            removeAll { it.equals(genre, ignoreCase = true) }
                        } else {
                            add(genre)
                        }
                    }
                    library.copy(selectedMovieGenres = next)
                }

                LibraryTab.Series -> {
                    val next = library.selectedSeriesGenres.toMutableSet().apply {
                        if (any { it.equals(genre, ignoreCase = true) }) {
                            removeAll { it.equals(genre, ignoreCase = true) }
                        } else {
                            add(genre)
                        }
                    }
                    library.copy(selectedSeriesGenres = next)
                }

                LibraryTab.Others -> library
            }
        )
    }

    fun clearGenreFilter() {
        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = when (library.selectedTab) {
                LibraryTab.Movies -> library.copy(selectedMovieGenres = emptySet())
                LibraryTab.Series -> library.copy(selectedSeriesGenres = emptySet())
                LibraryTab.Others -> library
            }
        )
    }

    fun onTechnicalFilterSelected(type: TechnicalFilterType, value: String) {
        val normalizedValue = value.trim()
        if (normalizedValue.isBlank() || normalizedValue == "-") {
            return
        }

        val library = _uiState.value.library
        when (library.selectedTab) {
            LibraryTab.Movies -> {
                val currentValues = library.selectedMovieTechnicalFilters[type].orEmpty()
                val nextValues = currentValues.toMutableSet().apply {
                    if (any { it.equals(normalizedValue, ignoreCase = true) }) {
                        removeAll { it.equals(normalizedValue, ignoreCase = true) }
                    } else {
                        add(normalizedValue)
                    }
                }
                val nextFilters = library.selectedMovieTechnicalFilters.toMutableMap().apply {
                    if (nextValues.isEmpty()) {
                        remove(type)
                    } else {
                        put(type, nextValues)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    library = library.copy(selectedMovieTechnicalFilters = nextFilters)
                )
            }

            LibraryTab.Series -> {
                val currentValues = library.selectedSeriesTechnicalFilters[type].orEmpty()
                val nextValues = currentValues.toMutableSet().apply {
                    if (any { it.equals(normalizedValue, ignoreCase = true) }) {
                        removeAll { it.equals(normalizedValue, ignoreCase = true) }
                    } else {
                        add(normalizedValue)
                    }
                }
                val nextFilters = library.selectedSeriesTechnicalFilters.toMutableMap().apply {
                    if (nextValues.isEmpty()) {
                        remove(type)
                    } else {
                        put(type, nextValues)
                    }
                }
                viewModelScope.launch {
                    val matchedIds = resolveSeriesTechnicalMatchedIds(nextFilters)
                    _uiState.value = _uiState.value.copy(
                        library = _uiState.value.library.copy(
                            selectedSeriesTechnicalFilters = nextFilters,
                            selectedSeriesTechnicalMatchedIds = matchedIds
                        )
                    )
                }
            }

            LibraryTab.Others -> Unit
        }
    }

    fun onSortModeSelected(mode: LibrarySortMode) {
        val library = _uiState.value.library
        when (library.selectedTab) {
            LibraryTab.Movies -> {
                _uiState.value = _uiState.value.copy(
                    library = library.copy(movieSortMode = mode)
                )
                viewModelScope.launch {
                    repository.setMovieSortMode(mode)
                }
            }

            LibraryTab.Series -> {
                _uiState.value = _uiState.value.copy(
                    library = library.copy(seriesSortMode = mode)
                )
                viewModelScope.launch {
                    repository.setSeriesSortMode(mode)
                }
            }

            LibraryTab.Others -> Unit
        }
    }

    fun onFavoriteFilterToggled() {
        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = when (library.selectedTab) {
                LibraryTab.Movies -> library.copy(
                    showOnlyMovieFavorites = !library.showOnlyMovieFavorites
                )

                LibraryTab.Series -> library.copy(
                    showOnlySeriesFavorites = !library.showOnlySeriesFavorites
                )

                LibraryTab.Others -> library
            }
        )
    }

    fun onDuplicateFilterToggled() {
        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = when (library.selectedTab) {
                LibraryTab.Movies -> library.copy(
                    showOnlyMovieDuplicates = !library.showOnlyMovieDuplicates
                )

                LibraryTab.Series -> library.copy(
                    showOnlySeriesDuplicates = !library.showOnlySeriesDuplicates
                )

                LibraryTab.Others -> library
            }
        )
    }

    fun onDuplicateMatchModeSelected(mode: DuplicateMatchMode) {
        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = when (library.selectedTab) {
                LibraryTab.Movies -> library.copy(movieDuplicateMatchMode = mode)
                LibraryTab.Series -> library.copy(seriesDuplicateMatchMode = mode)
                LibraryTab.Others -> library
            }
        )
    }

    fun onMovieFavoriteToggled(movieId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setMovieFavorite(movieId, isFavorite)
        }
    }

    fun onSeriesFavoriteToggled(seriesId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setSeriesFavorite(seriesId, isFavorite)
        }
    }

    fun markNovedadesAsSeen(markUntilEpochMillis: Long? = null) {
        val now = System.currentTimeMillis()
        val target = maxOf(now, markUntilEpochMillis ?: now)
        _uiState.value = _uiState.value.copy(
            library = _uiState.value.library.copy(lastSeenEpochMillis = target)
        )
        viewModelScope.launch {
            repository.setLibraryLastSeenMillis(target)
        }
    }

    fun clearTechnicalFilter() {
        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = when (library.selectedTab) {
                LibraryTab.Movies -> library.copy(
                    selectedMovieTechnicalFilters = emptyMap()
                )

                LibraryTab.Series -> library.copy(
                    selectedSeriesTechnicalFilters = emptyMap(),
                    selectedSeriesTechnicalMatchedIds = null
                )

                LibraryTab.Others -> library
            }
        )
    }

    fun clearAllActiveFilters() {
        val library = _uiState.value.library
        when (library.selectedTab) {
            LibraryTab.Movies -> {
                searchQueryMovies.value = ""
                _uiState.value = _uiState.value.copy(
                    library = library.copy(
                        searchQuery = "",
                        showOnlyMovieFavorites = false,
                        showOnlyMovieDuplicates = false,
                        selectedMovieGenres = emptySet(),
                        selectedMovieTechnicalFilters = emptyMap()
                    )
                )
            }

            LibraryTab.Series -> {
                searchQuerySeries.value = ""
                _uiState.value = _uiState.value.copy(
                    library = library.copy(
                        searchQuery = "",
                        showOnlySeriesFavorites = false,
                        showOnlySeriesDuplicates = false,
                        selectedSeriesGenres = emptySet(),
                        selectedSeriesTechnicalFilters = emptyMap(),
                        selectedSeriesTechnicalMatchedIds = null
                    )
                )
            }

            LibraryTab.Others -> {
                searchQueryOthers.value = ""
                _uiState.value = _uiState.value.copy(
                    library = library.copy(searchQuery = "")
                )
            }
        }
    }

    fun clearAllFiltersAcrossTabs() {
        searchQueryMovies.value = ""
        searchQuerySeries.value = ""
        searchQueryOthers.value = ""

        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = library.copy(
                searchQuery = "",
                showOnlyMovieFavorites = false,
                showOnlySeriesFavorites = false,
                showOnlyMovieDuplicates = false,
                showOnlySeriesDuplicates = false,
                selectedMovieGenres = emptySet(),
                selectedSeriesGenres = emptySet(),
                selectedMovieTechnicalFilters = emptyMap(),
                selectedSeriesTechnicalFilters = emptyMap(),
                selectedSeriesTechnicalMatchedIds = null
            )
        )
    }

    fun openConfigScreen() {
        configEntrySyncSnapshot = _uiState.value.sync.copy()
        restoreCommittedConfig(isConfigured = false)
        viewModelScope.launch {
            refreshDatabaseSize()
        }
    }

    fun cancelConfigChanges() {
        restoreCommittedConfig(isConfigured = true)
        configEntrySyncSnapshot?.let { previousSync ->
            _uiState.value = _uiState.value.copy(sync = previousSync)
        }
    }

    fun clearLocalData(scope: ClearDataScope) {
        viewModelScope.launch {
            repository.clearLocalData(scope)
            refreshDatabaseSize()
            _uiState.value = _uiState.value.copy(
                sync = _uiState.value.sync.copy(
                    lastSyncText = null,
                    lastError = null
                )
            )
        }
    }

    fun clearSyncHistory() {
        viewModelScope.launch {
            repository.clearSyncHistory()
            _uiState.value = _uiState.value.copy(
                sync = _uiState.value.sync.copy(syncHistory = emptyList())
            )
        }
    }

    fun exportDuplicateReport(
        format: DuplicateReportFormat,
        movieMode: DuplicateMatchMode,
        seriesMode: DuplicateMatchMode,
        onResult: (Boolean, String, File?) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                config = _uiState.value.config.copy(isExportingReport = true)
            )

            val library = _uiState.value.library
            val exported = repository.exportDuplicateReport(
                format = format,
                movieModeName = movieMode.name,
                seriesModeName = seriesMode.name
            )

            _uiState.value = _uiState.value.copy(
                config = _uiState.value.config.copy(isExportingReport = false)
            )

            if (exported != null) {
                onResult(
                    true,
                    getApplication<Application>().getString(
                        R.string.duplicate_report_export_success,
                        exported.absolutePath
                    ),
                    exported
                )
            } else {
                onResult(
                    false,
                    getApplication<Application>().getString(R.string.duplicate_report_export_error),
                    null
                )
            }
        }
    }

    suspend fun refreshSeriesDetails(seriesId: String) {
        try {
            repository.refreshSeriesDetails(seriesId)
        } catch (_: Exception) {
        }
    }

    suspend fun refreshMovieDetails(movieId: String) {
        try {
            repository.refreshMovieDetails(movieId)
        } catch (_: Exception) {
        }
    }

    fun saveConfig() {
        val state = _uiState.value.config
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                config = state.copy(
                    isValidating = true,
                    validationError = null
                ),
                sync = _uiState.value.sync.copy(lastError = null)
            )

            repository.saveServerConfig(
                serverAddress = state.serverAddress,
                port = state.port,
                username = state.username.ifBlank { null },
                password = state.password.ifBlank { null },
                apiKey = state.apiKey.ifBlank { null }
            )

            repository.setAutoSyncMode(state.autoSyncMode)
            repository.setMovieDetailsSyncMode(state.movieDetailsSyncMode)
            repository.setListDisplayMode(state.listDisplayMode)
            repository.setOfflinePostersEnabled(state.downloadPostersOffline)
            repository.setShowFilePath(state.showFilePath)
            repository.setLibrariesAdvancedView(state.librariesAdvancedView)
            repository.setReconcileDeletedOnRecentSync(state.reconcileDeletedOnRecentSync)

            _uiState.value = _uiState.value.copy(
                config = _uiState.value.config.copy(
                    isValidating = false,
                    validationError = null,
                    hasSavedConfig = true,
                    isConfigured = true
                )
            )

            val status = withTimeoutOrNull(12_000L) {
                repository.checkServerStatus()
            } ?: ConnectionResult.NetworkError(getApplication<Application>().getString(R.string.timeout_validating_server))

            when (status) {
                ConnectionResult.Success -> {
                    val version = withTimeoutOrNull(8_000L) {
                        repository.getServerVersion()
                    }
                    _uiState.value = _uiState.value.copy(
                        config = _uiState.value.config.copy(
                            connectedServerVersion = version
                                ?: getApplication<Application>().getString(R.string.server_version_unknown)
                        ),
                        sync = _uiState.value.sync.copy(
                            serverStatusText = getApplication<Application>().getString(R.string.server_active),
                            isServerActive = true,
                            lastError = null
                        )
                    )

                    if (state.apiKey.isBlank()) {
                        when (val auth = withTimeoutOrNull(15_000L) {
                            repository.authenticateAndValidateConnection()
                        } ?: ConnectionResult.NetworkError(getApplication<Application>().getString(R.string.timeout_validating_credentials))) {
                            ConnectionResult.Success -> Unit
                            is ConnectionResult.AuthFailure -> {
                                _uiState.value = _uiState.value.copy(
                                    sync = _uiState.value.sync.copy(
                                        serverStatusText = getApplication<Application>().getString(R.string.server_active_unauth),
                                        isServerActive = true,
                                        lastError = auth.message
                                    )
                                )
                            }

                            is ConnectionResult.NetworkError -> {
                                _uiState.value = _uiState.value.copy(
                                    sync = _uiState.value.sync.copy(
                                        serverStatusText = getApplication<Application>().getString(R.string.server_active_unauth),
                                        isServerActive = true,
                                        lastError = auth.message
                                    )
                                )
                            }

                            is ConnectionResult.UnknownError -> {
                                _uiState.value = _uiState.value.copy(
                                    sync = _uiState.value.sync.copy(
                                        serverStatusText = getApplication<Application>().getString(R.string.server_active_unauth),
                                        isServerActive = true,
                                        lastError = auth.message
                                    )
                                )
                            }
                        }
                    }

                    loadLibraryViews()
                }

                is ConnectionResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(
                        config = _uiState.value.config.copy(connectedServerVersion = null),
                        sync = _uiState.value.sync.copy(
                            serverStatusText = getApplication<Application>().getString(R.string.server_inactive),
                            isServerActive = false,
                            lastError = status.message
                        )
                    )
                }

                is ConnectionResult.AuthFailure -> {
                    _uiState.value = _uiState.value.copy(
                        config = _uiState.value.config.copy(connectedServerVersion = null),
                        sync = _uiState.value.sync.copy(
                            serverStatusText = getApplication<Application>().getString(R.string.server_config_incomplete),
                            isServerActive = false,
                            lastError = status.message
                        )
                    )
                }

                is ConnectionResult.UnknownError -> {
                    _uiState.value = _uiState.value.copy(
                        config = _uiState.value.config.copy(connectedServerVersion = null),
                        sync = _uiState.value.sync.copy(
                            serverStatusText = getApplication<Application>().getString(R.string.server_unknown_state),
                            isServerActive = false,
                            lastError = status.message
                        )
                    )
                }
            }

            commitConfigSnapshot(_uiState.value.config)
            configEntrySyncSnapshot = _uiState.value.sync.copy()
        }
    }

    fun triggerManualSync() {
        triggerSyncWithScope(SyncScope.All)
    }

    fun triggerMoviesSync() {
        triggerSyncWithScope(SyncScope.Movies)
    }

    fun triggerSeriesSync() {
        triggerSyncWithScope(SyncScope.Series)
    }

    fun triggerOthersSync() {
        triggerSyncWithScope(SyncScope.Others)
    }

    fun triggerRecentMoviesSync() {
        triggerSyncWithScope(scope = SyncScope.Movies, onlyRecentAdded = true)
    }

    fun triggerRecentSeriesSync() {
        triggerSyncWithScope(scope = SyncScope.Series, onlyRecentAdded = true)
    }

    fun triggerRecentOthersSync() {
        triggerSyncWithScope(scope = SyncScope.Others, onlyRecentAdded = true)
    }

    fun triggerFastSync() {
        triggerFastSyncWithScope(SyncScope.All)
    }

    fun triggerFastMoviesSync() {
        triggerFastSyncWithScope(SyncScope.Movies)
    }

    fun triggerFastSeriesSync() {
        triggerFastSyncWithScope(SyncScope.Series)
    }

    fun triggerFastOthersSync() {
        triggerFastSyncWithScope(SyncScope.Others)
    }

    fun triggerDetailsSync() {
        triggerDetailsSyncWithScope(SyncScope.All)
    }

    fun triggerDetailsMoviesSync() {
        triggerDetailsSyncWithScope(SyncScope.Movies)
    }

    fun triggerDetailsSeriesSync() {
        triggerDetailsSyncWithScope(SyncScope.Series)
    }

    private fun triggerSyncWithScope(scope: SyncScope, onlyRecentAdded: Boolean = false) {
        currentSyncJob?.cancel()
        currentSyncJob = viewModelScope.launch {
            try {
                if (!ensureServerAvailableBeforeSync()) {
                    currentSyncJob = null
                    return@launch
                }

                val forceFullMovies = !onlyRecentAdded && (scope == SyncScope.All || scope == SyncScope.Movies)
                val forceFullSeries = !onlyRecentAdded && (scope == SyncScope.All || scope == SyncScope.Series)
                val forceFullOthers = !onlyRecentAdded && (scope == SyncScope.All || scope == SyncScope.Others)

                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = true,
                        processed = 0,
                        total = 0,
                        phaseText = if (onlyRecentAdded) {
                            getApplication<Application>().getString(R.string.phase_syncing_recent)
                        } else if (forceFullMovies) {
                            getApplication<Application>().getString(R.string.phase_refreshing_movies_full)
                        } else {
                            getApplication<Application>().getString(R.string.phase_syncing_catalog)
                        },
                        lastError = null
                    )
                )

                val result = repository.syncIncremental(
                    scope = scope,
                    forceFullMovies = forceFullMovies,
                    forceFullSeries = forceFullSeries,
                    forceFullOthers = forceFullOthers,
                    reconcileDeletedOnRecent = onlyRecentAdded && _uiState.value.config.reconcileDeletedOnRecentSync,
                    modeLabel = if (onlyRecentAdded) {
                        getApplication<Application>().getString(R.string.mode_recently_added)
                    } else {
                        getApplication<Application>().getString(R.string.mode_normal)
                    }
                ) { processed, total, phase ->
                    val phaseText = if (forceFullMovies && phase == SyncProgressPhase.FetchingMoviesCatalog) {
                        getApplication<Application>().getString(R.string.phase_refreshing_movies_full)
                    } else {
                        phase.toUiText()
                    }
                    _uiState.value = _uiState.value.copy(
                        sync = _uiState.value.sync.copy(
                            isSyncing = true,
                            processed = processed,
                            total = total,
                            phaseText = phaseText
                        )
                    )
                }

                val lastSync = repository.getLastSyncEpochMillis()
                val syncHistory = repository.getSyncHistory()

                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        serverStatusText = getApplication<Application>().getString(R.string.server_active),
                        isServerActive = true,
                        phaseText = getApplication<Application>().getString(R.string.phase_sync_completed),
                        lastSyncText = lastSync?.let { formatLastSync(it) },
                        syncHistory = syncHistory,
                        lastError = when (result) {
                            SyncResult.Success -> null
                            is SyncResult.NetworkError -> result.message
                            is SyncResult.UnknownError -> result.message
                        }
                    )
                )

                if (result == SyncResult.Success) {
                    loadLibraryViews()
                }
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        phaseText = getApplication<Application>().getString(R.string.phase_sync_cancelled)
                    )
                )
            } finally {
                currentSyncJob = null
            }
        }
    }

    private fun triggerFastSyncWithScope(scope: SyncScope) {
        currentSyncJob?.cancel()
        currentSyncJob = viewModelScope.launch {
            try {
                if (!ensureServerAvailableBeforeSync()) {
                    currentSyncJob = null
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = true,
                        processed = 0,
                        total = 0,
                        phaseText = getApplication<Application>().getString(R.string.phase_syncing_catalog),
                        lastError = null
                    )
                )

                val result = repository.syncFast(scope) { processed, total, phase ->
                    _uiState.value = _uiState.value.copy(
                        sync = _uiState.value.sync.copy(
                            isSyncing = true,
                            processed = processed,
                            total = total,
                            phaseText = phase.toUiText()
                        )
                    )
                }

                val lastSync = repository.getLastSyncEpochMillis()
                val syncHistory = repository.getSyncHistory()

                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        serverStatusText = getApplication<Application>().getString(R.string.server_active),
                        isServerActive = true,
                        phaseText = getApplication<Application>().getString(R.string.phase_sync_completed),
                        lastSyncText = lastSync?.let { formatLastSync(it) },
                        syncHistory = syncHistory,
                        lastError = when (result) {
                            SyncResult.Success -> null
                            is SyncResult.NetworkError -> result.message
                            is SyncResult.UnknownError -> result.message
                        }
                    )
                )

                if (result == SyncResult.Success) {
                    loadLibraryViews()
                }
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        phaseText = getApplication<Application>().getString(R.string.phase_sync_cancelled)
                    )
                )
            } finally {
                currentSyncJob = null
            }
        }
    }

    private fun triggerDetailsSyncWithScope(scope: SyncScope) {
        currentSyncJob?.cancel()
        currentSyncJob = viewModelScope.launch {
            try {
                if (!ensureServerAvailableBeforeSync()) {
                    currentSyncJob = null
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = true,
                        processed = 0,
                        total = 0,
                        phaseText = getApplication<Application>().getString(R.string.phase_syncing_details_only),
                        lastError = null
                    )
                )

                val result = repository.syncDetailsOnly(scope) { processed, total, phase ->
                    _uiState.value = _uiState.value.copy(
                        sync = _uiState.value.sync.copy(
                            isSyncing = true,
                            processed = processed,
                            total = total,
                            phaseText = phase.toUiText()
                        )
                    )
                }

                val lastSync = repository.getLastSyncEpochMillis()
                val syncHistory = repository.getSyncHistory()
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        serverStatusText = getApplication<Application>().getString(R.string.server_active),
                        isServerActive = true,
                        phaseText = getApplication<Application>().getString(R.string.phase_sync_completed),
                        lastSyncText = lastSync?.let { formatLastSync(it) },
                        syncHistory = syncHistory,
                        lastError = when (result) {
                            SyncResult.Success -> null
                            is SyncResult.NetworkError -> result.message
                            is SyncResult.UnknownError -> result.message
                        }
                    )
                )

                if (result == SyncResult.Success) {
                    loadLibraryViews()
                }
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        phaseText = getApplication<Application>().getString(R.string.phase_sync_cancelled)
                    )
                )
            } finally {
                currentSyncJob = null
            }
        }
    }

    @Suppress("SpellCheckingInspection")
    fun cancelSync() {
        currentSyncJob?.cancel()
        val app = getApplication<Application>()
        androidx.work.WorkManager.getInstance(app)
            .cancelUniqueWork("jellyfin_manual_sync")
        _uiState.value = _uiState.value.copy(
            sync = _uiState.value.sync.copy(
                isSyncing = false,
                phaseText = getApplication<Application>().getString(R.string.phase_sync_cancelled),
                lastError = partialSyncWarning
            )
        )

        viewModelScope.launch {
            delay(6000L)
            if (_uiState.value.sync.lastError == partialSyncWarning) {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(lastError = null)
                )
            }
        }
    }

    private fun triggerStartupSync() {
        if (_uiState.value.sync.isSyncing) return
        viewModelScope.launch {
            if (!ensureServerAvailableBeforeSync()) {
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                sync = _uiState.value.sync.copy(
                    isSyncing = true,
                    processed = 0,
                    total = 0,
                    phaseText = getApplication<Application>().getString(R.string.phase_syncing_catalog),
                    lastError = null
                )
            )

            val result = repository.syncIncremental(
                scope = SyncScope.All,
                forceFullMovies = false
            ) { processed, total, phase ->
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = true,
                        processed = processed,
                        total = total,
                        phaseText = phase.toUiText()
                    )
                )
            }

            val lastSync = repository.getLastSyncEpochMillis()
            val syncHistory = repository.getSyncHistory()

            _uiState.value = _uiState.value.copy(
                sync = _uiState.value.sync.copy(
                    isSyncing = false,
                    serverStatusText = getApplication<Application>().getString(R.string.server_active),
                    isServerActive = true,
                    phaseText = getApplication<Application>().getString(R.string.phase_sync_completed),
                    lastSyncText = lastSync?.let { formatLastSync(it) },
                    syncHistory = syncHistory,
                    lastError = when (result) {
                        SyncResult.Success -> null
                        is SyncResult.NetworkError -> result.message
                        is SyncResult.UnknownError -> result.message
                    }
                )
            )

            if (result == SyncResult.Success) {
                loadLibraryViews()
            }
        }
    }

    @Suppress("unused")
    fun seriesSeasons(seriesId: String): kotlinx.coroutines.flow.Flow<List<SeasonEntity>> =
        repository.getSeasonsForSeries(seriesId)

    private fun formatLastSync(epochMillis: Long): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return formatter.format(Date(epochMillis))
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                config = _uiState.value.config.copy(
                    isValidating = true,
                    validationError = null
                )
            )

            val config = _uiState.value.config
            try {
                when (val status = withTimeoutOrNull(12_000L) {
                    repository.checkServerStatus(
                        serverAddress = config.serverAddress,
                        port = config.port
                    )
                } ?: ConnectionResult.NetworkError(getApplication<Application>().getString(R.string.timeout_validating_server))) {
                    ConnectionResult.Success -> {
                        when (val auth = withTimeoutOrNull(15_000L) {
                            repository.authenticateAndValidateConnection(
                                serverAddress = config.serverAddress,
                                port = config.port,
                                username = config.username.ifBlank { null },
                                password = config.password.ifBlank { null },
                                apiKey = config.apiKey.ifBlank { null }
                            )
                        } ?: ConnectionResult.NetworkError(getApplication<Application>().getString(R.string.timeout_validating_credentials))) {
                            ConnectionResult.Success -> {
                                val version = withTimeoutOrNull(8_000L) {
                                    repository.getServerVersion(
                                        serverAddress = config.serverAddress,
                                        port = config.port
                                    )
                                }
                                _uiState.value = _uiState.value.copy(
                                    config = _uiState.value.config.copy(
                                        connectedServerVersion = version
                                            ?: getApplication<Application>().getString(R.string.server_version_unknown)
                                    ),
                                    sync = _uiState.value.sync.copy(
                                        serverStatusText = getApplication<Application>().getString(R.string.server_active),
                                        isServerActive = true,
                                        lastError = null
                                    )
                                )
                                onResult(true, getApplication<Application>().getString(R.string.test_connection_success))
                            }

                            is ConnectionResult.AuthFailure -> {
                                _uiState.value = _uiState.value.copy(
                                    config = _uiState.value.config.copy(connectedServerVersion = null),
                                    sync = _uiState.value.sync.copy(
                                        serverStatusText = getApplication<Application>().getString(R.string.server_active_unauth),
                                        isServerActive = true,
                                        lastError = auth.message
                                    )
                                )
                                onResult(false, auth.message)
                            }

                            is ConnectionResult.NetworkError -> {
                                _uiState.value = _uiState.value.copy(
                                    config = _uiState.value.config.copy(connectedServerVersion = null),
                                    sync = _uiState.value.sync.copy(
                                        serverStatusText = getApplication<Application>().getString(R.string.server_active_unauth),
                                        isServerActive = true,
                                        lastError = auth.message
                                    )
                                )
                                onResult(false, auth.message)
                            }

                            is ConnectionResult.UnknownError -> {
                                _uiState.value = _uiState.value.copy(
                                    config = _uiState.value.config.copy(connectedServerVersion = null),
                                    sync = _uiState.value.sync.copy(
                                        serverStatusText = getApplication<Application>().getString(R.string.server_unknown_state),
                                        isServerActive = false,
                                        lastError = auth.message
                                    )
                                )
                                onResult(false, auth.message)
                            }
                        }
                    }

                    is ConnectionResult.NetworkError -> {
                        _uiState.value = _uiState.value.copy(
                            config = _uiState.value.config.copy(connectedServerVersion = null),
                            sync = _uiState.value.sync.copy(
                                serverStatusText = getApplication<Application>().getString(R.string.server_inactive),
                                isServerActive = false,
                                lastError = status.message
                            )
                        )
                        onResult(false, status.message)
                    }

                    is ConnectionResult.AuthFailure -> {
                        _uiState.value = _uiState.value.copy(
                            config = _uiState.value.config.copy(connectedServerVersion = null),
                            sync = _uiState.value.sync.copy(
                                serverStatusText = getApplication<Application>().getString(R.string.server_config_incomplete),
                                isServerActive = false,
                                lastError = status.message
                            )
                        )
                        onResult(false, status.message)
                    }

                    is ConnectionResult.UnknownError -> {
                        _uiState.value = _uiState.value.copy(
                            config = _uiState.value.config.copy(connectedServerVersion = null),
                            sync = _uiState.value.sync.copy(
                                serverStatusText = getApplication<Application>().getString(R.string.server_unknown_state),
                                isServerActive = false,
                                lastError = status.message
                            )
                        )
                        onResult(false, status.message)
                    }
                }
            } finally {
                _uiState.value = _uiState.value.copy(
                    config = _uiState.value.config.copy(isValidating = false)
                )
            }
        }
    }

    private suspend fun resolveSeriesTechnicalMatchedIds(
        filters: Map<TechnicalFilterType, Set<String>>
    ): Set<String>? {
        if (filters.isEmpty()) return null

        val perFilterMatches = mutableListOf<Set<String>>()
        filters.forEach { (type, values) ->
            values.forEach { value ->
                val matched = when (type) {
                    TechnicalFilterType.Quality -> repository.getSeriesIdsBySeasonQuality(value)
                    TechnicalFilterType.Format -> repository.getSeriesIdsBySeasonFormat(value)
                    TechnicalFilterType.Resolution -> repository.getSeriesIdsBySeasonResolution(value)
                }
                perFilterMatches += matched.toSet()
            }
        }

        if (perFilterMatches.isEmpty()) return null
        return perFilterMatches.reduce { acc, set -> acc.intersect(set) }
    }

    @SuppressLint("UsableSpace")
    private fun refreshDatabaseSize() {
        val app = getApplication<Application>()
        val dbName = "biblioteca_jelly.db"
        val dbFile = app.getDatabasePath(dbName)
        val relatedFiles = listOf(
            dbFile,
            File(dbFile.absolutePath + "-wal"),
            File(dbFile.absolutePath + "-shm"),
            File(dbFile.absolutePath + "-journal")
        )

        val totalBytes = relatedFiles
            .filter { it.exists() }
            .sumOf { it.length() }

        val freeBytes = app.filesDir.usableSpace
        val usagePercent = if (freeBytes > 0L) {
            ((totalBytes.toDouble() / freeBytes.toDouble()) * 100.0).toFloat()
        } else {
            null
        }

        val ratioText = if (usagePercent != null) {
            String.format(getApplication<Application>().getString(R.string.free_space_ratio), usagePercent.toDouble())
        } else {
            ""
        }

        val postersRoot = File(app.filesDir, "posters")
        val posterFiles = if (postersRoot.exists()) {
            postersRoot.walkTopDown().filter { it.isFile }.toList()
        } else {
            emptyList()
        }
        val postersCount = posterFiles.size
        val postersBytes = posterFiles.sumOf { it.length() }
        val postersUsagePercent = if (freeBytes > 0L) {
            ((postersBytes.toDouble() / freeBytes.toDouble()) * 100.0).toFloat()
        } else {
            null
        }

        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(
                databaseSizeText = getApplication<Application>().getString(
                    R.string.db_size_text,
                    formatBytes(totalBytes),
                    ratioText
                ),
                databaseUsagePercent = usagePercent,
                postersSizeText = getApplication<Application>().getString(
                    R.string.posters_size_text,
                    postersCount,
                    formatBytes(postersBytes)
                ),
                postersUsagePercent = postersUsagePercent
            )
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"

        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0

        return when {
            bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.getDefault(), "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private suspend fun ensureServerAvailableBeforeSync(): Boolean {
        return when (val status = repository.checkServerStatus()) {
            ConnectionResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = getApplication<Application>().getString(R.string.server_active),
                        isServerActive = true
                    )
                )
                true
            }

            is ConnectionResult.NetworkError -> {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = getApplication<Application>().getString(R.string.server_inactive),
                        isServerActive = false,
                        lastError = status.message
                    )
                )
                false
            }

            is ConnectionResult.AuthFailure -> {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = getApplication<Application>().getString(R.string.server_config_incomplete),
                        isServerActive = false,
                        lastError = status.message
                    )
                )
                false
            }

            is ConnectionResult.UnknownError -> {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = getApplication<Application>().getString(R.string.server_unknown_state),
                        isServerActive = false,
                        lastError = status.message
                    )
                )
                false
            }
        }
    }

    private suspend fun updateServerStatus() {
        when (repository.checkServerStatus()) {
            ConnectionResult.Success -> {
                val version = withTimeoutOrNull(8_000L) {
                    repository.getServerVersion()
                }
                _uiState.value = _uiState.value.copy(
                    config = _uiState.value.config.copy(
                        connectedServerVersion = version
                            ?: getApplication<Application>().getString(R.string.server_version_unknown)
                    ),
                    sync = _uiState.value.sync.copy(
                        serverStatusText = getApplication<Application>().getString(R.string.server_active),
                        isServerActive = true
                    )
                )
            }

            else -> {
                _uiState.value = _uiState.value.copy(
                    config = _uiState.value.config.copy(connectedServerVersion = null),
                    sync = _uiState.value.sync.copy(
                        serverStatusText = getApplication<Application>().getString(R.string.server_inactive),
                        isServerActive = false
                    )
                )
            }
        }
    }

    private suspend fun checkForAppUpdate() {
        val latestRelease = repository.getLatestAppRelease() ?: return
        val currentVersion = installedVersionName
        if (!isVersionNewer(currentVersion, latestRelease.versionName)) return

        val dismissedTag = repository.getDismissedReleaseTag()
        if (!dismissedTag.isNullOrBlank() && dismissedTag == latestRelease.tagName) return

        _uiState.value = _uiState.value.copy(
            update = _uiState.value.update.copy(
                showDialog = true,
                latestVersion = latestRelease.versionName,
                releaseUrl = latestRelease.releaseUrl,
                releaseTag = latestRelease.tagName
            )
        )
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        val currentParts = current.extractVersionParts()
        val latestParts = latest.extractVersionParts()
        val maxSize = maxOf(currentParts.size, latestParts.size)

        for (index in 0 until maxSize) {
            val currentValue = currentParts.getOrElse(index) { 0 }
            val latestValue = latestParts.getOrElse(index) { 0 }
            if (latestValue > currentValue) return true
            if (latestValue < currentValue) return false
        }

        return false
    }

    private fun String.extractVersionParts(): List<Int> {
        val normalized = this.trim().removePrefix("v").removePrefix("V")
        return Regex("\\d+")
            .findAll(normalized)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
            .ifEmpty { listOf(0) }
    }

    private fun SyncProgressPhase.toUiText(): String {
        return when (this) {
            SyncProgressPhase.FetchingMoviesCatalog -> getApplication<Application>().getString(R.string.phase_syncing_catalog)
            SyncProgressPhase.FetchingMoviesDetails -> getApplication<Application>().getString(R.string.phase_syncing_details_only)
            SyncProgressPhase.FetchingSeries -> getApplication<Application>().getString(R.string.phase_syncing_catalog)
            SyncProgressPhase.SeriesDetails -> getApplication<Application>().getString(R.string.phase_syncing_details_only)
        }
    }
}
