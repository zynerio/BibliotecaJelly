package com.zynerio.bibliotecajelly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zynerio.bibliotecajelly.data.AutoSyncMode
import com.zynerio.bibliotecajelly.data.ConnectionResult
import com.zynerio.bibliotecajelly.data.JellyfinRepository
import com.zynerio.bibliotecajelly.data.MovieEntity
import com.zynerio.bibliotecajelly.data.SeriesEntity
import com.zynerio.bibliotecajelly.data.SeasonEntity
import com.zynerio.bibliotecajelly.data.ServiceLocator
import com.zynerio.bibliotecajelly.data.SeriesWithSeasonsAndEpisodes
import com.zynerio.bibliotecajelly.data.MovieDetailsSyncMode
import com.zynerio.bibliotecajelly.data.ListDisplayMode
import com.zynerio.bibliotecajelly.data.LibrarySortMode
import com.zynerio.bibliotecajelly.data.ClearDataScope
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
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LibraryTab {
    Movies,
    Series
}

enum class TechnicalFilterType {
    Quality,
    Format,
    Resolution
}

data class ConfigUiState(
    val serverAddress: String = "",
    val port: String = "8096",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val isValidating: Boolean = false,
    val validationError: String? = null,
    val isConfigured: Boolean = false,
    val databaseSizeText: String = "Tamaño base de datos: calculando...",
    val postersSizeText: String = "Portadas locales: calculando...",
    val downloadPostersOffline: Boolean = false,
    val autoSyncMode: AutoSyncMode = AutoSyncMode.OnStart,
    val movieDetailsSyncMode: MovieDetailsSyncMode = MovieDetailsSyncMode.All,
    val listDisplayMode: ListDisplayMode = ListDisplayMode.Infinite
)

data class SyncUiState(
    val isSyncing: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val phaseText: String = "Sincronizando catálogo",
    val serverStatusText: String = "Servidor: sin comprobar",
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
    val selectedMovieGenres: Set<String> = emptySet(),
    val selectedSeriesGenres: Set<String> = emptySet(),
    val selectedMovieTechnicalFilters: Map<TechnicalFilterType, Set<String>> = emptyMap(),
    val selectedSeriesTechnicalFilters: Map<TechnicalFilterType, Set<String>> = emptyMap(),
    val selectedSeriesTechnicalMatchedIds: Set<String>? = null
)

data class MainUiState(
    val config: ConfigUiState = ConfigUiState(),
    val sync: SyncUiState = SyncUiState(),
    val library: LibraryUiState = LibraryUiState()
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: JellyfinRepository =
        ServiceLocator.provideRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val searchQueryMovies = MutableStateFlow("")
    private val searchQuerySeries = MutableStateFlow("")

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

    val isConfigured: StateFlow<Boolean> =
        uiState.map { it.config.isConfigured }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private var currentSyncJob: Job? = null
    private val partialSyncWarning =
        "Sincronización cancelada: solo tendrás una sincronización parcial."

    init {
        viewModelScope.launch {
            val config = repository.getServerConfig()
            val lastSync = repository.getLastSyncEpochMillis()
            val autoSyncMode = repository.getAutoSyncMode()
            val movieDetailsSyncMode = repository.getMovieDetailsSyncMode()
            val listDisplayMode = repository.getListDisplayMode()
            val offlinePostersEnabled = repository.getOfflinePostersEnabled()
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
                        isConfigured = true,
                        downloadPostersOffline = offlinePostersEnabled,
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

                updateServerStatus()

                if (autoSyncMode == AutoSyncMode.OnStart) {
                    triggerStartupSync()
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    config = _uiState.value.config.copy(
                        downloadPostersOffline = offlinePostersEnabled,
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
            }

            refreshDatabaseSize()
        }
    }

    fun onServerAddressChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(serverAddress = value)
        )
    }

    fun onPortChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(port = value)
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

    fun onSearchQueryChanged(value: String) {
        val currentTab = _uiState.value.library.selectedTab
        when (currentTab) {
            LibraryTab.Movies -> searchQueryMovies.value = value
            LibraryTab.Series -> searchQuerySeries.value = value
        }

        _uiState.value = _uiState.value.copy(
            library = _uiState.value.library.copy(searchQuery = value)
        )
    }

    fun onTabSelected(tab: LibraryTab) {
        val newQuery = when (tab) {
            LibraryTab.Movies -> searchQueryMovies.value
            LibraryTab.Series -> searchQuerySeries.value
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
            }
        )
    }

    fun clearGenreFilter() {
        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = when (library.selectedTab) {
                LibraryTab.Movies -> library.copy(selectedMovieGenres = emptySet())
                LibraryTab.Series -> library.copy(selectedSeriesGenres = emptySet())
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

    fun markNovedadesAsSeen() {
        val now = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            library = _uiState.value.library.copy(lastSeenEpochMillis = now)
        )
        viewModelScope.launch {
            repository.setLibraryLastSeenMillis(now)
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
                        selectedSeriesGenres = emptySet(),
                        selectedSeriesTechnicalFilters = emptyMap(),
                        selectedSeriesTechnicalMatchedIds = null
                    )
                )
            }
        }
    }

    fun clearAllFiltersAcrossTabs() {
        searchQueryMovies.value = ""
        searchQuerySeries.value = ""

        val library = _uiState.value.library
        _uiState.value = _uiState.value.copy(
            library = library.copy(
                searchQuery = "",
                showOnlyMovieFavorites = false,
                showOnlySeriesFavorites = false,
                selectedMovieGenres = emptySet(),
                selectedSeriesGenres = emptySet(),
                selectedMovieTechnicalFilters = emptyMap(),
                selectedSeriesTechnicalFilters = emptyMap(),
                selectedSeriesTechnicalMatchedIds = null
            )
        )
    }

    fun openConfigScreen() {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(isConfigured = false)
        )
        viewModelScope.launch {
            refreshDatabaseSize()
        }
    }

    fun cancelConfigChanges() {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(
                isValidating = false,
                validationError = null,
                isConfigured = true
            )
        )
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

    fun saveAndValidateConfig() {
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

            when (val result = repository.authenticateAndValidateConnection()) {
                ConnectionResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        config = _uiState.value.config.copy(
                            isValidating = false,
                            validationError = null,
                            isConfigured = true
                        )
                    )
                }

                is ConnectionResult.AuthFailure -> {
                    _uiState.value = _uiState.value.copy(
                        config = _uiState.value.config.copy(
                            isValidating = false,
                            validationError = result.message
                        )
                    )
                }

                is ConnectionResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(
                        config = _uiState.value.config.copy(
                            isValidating = false,
                            validationError = result.message
                        )
                    )
                }

                is ConnectionResult.UnknownError -> {
                    _uiState.value = _uiState.value.copy(
                        config = _uiState.value.config.copy(
                            isValidating = false,
                            validationError = result.message
                        )
                    )
                }
            }
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

    fun triggerFastSync() {
        triggerFastSyncWithScope(SyncScope.All)
    }

    fun triggerFastMoviesSync() {
        triggerFastSyncWithScope(SyncScope.Movies)
    }

    fun triggerFastSeriesSync() {
        triggerFastSyncWithScope(SyncScope.Series)
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

    private fun triggerSyncWithScope(scope: SyncScope) {
        currentSyncJob?.cancel()
        currentSyncJob = viewModelScope.launch {
            try {
                if (!ensureServerAvailableBeforeSync()) {
                    currentSyncJob = null
                    return@launch
                }

                val forceFullMovies = scope == SyncScope.All || scope == SyncScope.Movies

                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = true,
                        processed = 0,
                        total = 0,
                        phaseText = if (forceFullMovies) {
                            "Refrescando catálogo de películas (completo)"
                        } else {
                            "Sincronizando catálogo"
                        },
                        lastError = null
                    )
                )

                val result = repository.syncIncremental(
                    scope = scope,
                    forceFullMovies = forceFullMovies
                ) { processed, total, phase ->
                    val phaseText = if (forceFullMovies && phase == SyncProgressPhase.FetchingMoviesCatalog) {
                        "Refrescando catálogo de películas (completo)"
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
                        serverStatusText = "Servidor: activo",
                        isServerActive = true,
                        phaseText = "Sincronización completada",
                        lastSyncText = lastSync?.let { formatLastSync(it) },
                        syncHistory = syncHistory,
                        lastError = when (result) {
                            SyncResult.Success -> null
                            is SyncResult.NetworkError -> result.message
                            is SyncResult.UnknownError -> result.message
                        }
                    )
                )
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        phaseText = "Sincronización cancelada"
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
                        phaseText = "Sincronizando catálogo",
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
                        serverStatusText = "Servidor: activo",
                        isServerActive = true,
                        phaseText = "Sincronización completada",
                        lastSyncText = lastSync?.let { formatLastSync(it) },
                        syncHistory = syncHistory,
                        lastError = when (result) {
                            SyncResult.Success -> null
                            is SyncResult.NetworkError -> result.message
                            is SyncResult.UnknownError -> result.message
                        }
                    )
                )
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        phaseText = "Sincronización cancelada"
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
                        phaseText = "Sincronizando solo detalles",
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
                        serverStatusText = "Servidor: activo",
                        isServerActive = true,
                        phaseText = "Sincronización completada",
                        lastSyncText = lastSync?.let { formatLastSync(it) },
                        syncHistory = syncHistory,
                        lastError = when (result) {
                            SyncResult.Success -> null
                            is SyncResult.NetworkError -> result.message
                            is SyncResult.UnknownError -> result.message
                        }
                    )
                )
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        isSyncing = false,
                        phaseText = "Sincronización cancelada"
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
                phaseText = "Sincronización cancelada",
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
                    phaseText = "Sincronizando catálogo",
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
                    serverStatusText = "Servidor: activo",
                    isServerActive = true,
                    phaseText = "Sincronización completada",
                    lastSyncText = lastSync?.let { formatLastSync(it) },
                    syncHistory = syncHistory,
                    lastError = when (result) {
                        SyncResult.Success -> null
                        is SyncResult.NetworkError -> result.message
                        is SyncResult.UnknownError -> result.message
                    }
                )
            )
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
            when (val status = repository.checkServerStatus()) {
                ConnectionResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        sync = _uiState.value.sync.copy(
                            serverStatusText = "Servidor: activo",
                            isServerActive = true,
                            lastError = null
                        )
                    )
                    onResult(true, "Conexión correcta con el servidor")
                }

                is ConnectionResult.NetworkError -> {
                    _uiState.value = _uiState.value.copy(
                        sync = _uiState.value.sync.copy(
                            serverStatusText = "Servidor: inactivo",
                            isServerActive = false,
                            lastError = status.message
                        )
                    )
                    onResult(false, status.message)
                }

                is ConnectionResult.AuthFailure -> {
                    _uiState.value = _uiState.value.copy(
                        sync = _uiState.value.sync.copy(
                            serverStatusText = "Servidor: configuración incompleta",
                            isServerActive = false,
                            lastError = status.message
                        )
                    )
                    onResult(false, status.message)
                }

                is ConnectionResult.UnknownError -> {
                    _uiState.value = _uiState.value.copy(
                        sync = _uiState.value.sync.copy(
                            serverStatusText = "Servidor: estado desconocido",
                            isServerActive = false,
                            lastError = status.message
                        )
                    )
                    onResult(false, status.message)
                }
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
        val ratioText = if (freeBytes > 0L) {
            val percent = (totalBytes.toDouble() / freeBytes.toDouble()) * 100.0
            String.format(Locale.getDefault(), " (%.2f%% del espacio libre)", percent)
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

        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(
                databaseSizeText = "Tamaño base de datos: ${formatBytes(totalBytes)}$ratioText",
                postersSizeText = "Portadas locales: $postersCount archivos / ${formatBytes(postersBytes)}"
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
                        serverStatusText = "Servidor: activo",
                        isServerActive = true
                    )
                )
                true
            }

            is ConnectionResult.NetworkError -> {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = "Servidor: inactivo",
                        isServerActive = false,
                        lastError = status.message
                    )
                )
                false
            }

            is ConnectionResult.AuthFailure -> {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = "Servidor: configuración incompleta",
                        isServerActive = false,
                        lastError = status.message
                    )
                )
                false
            }

            is ConnectionResult.UnknownError -> {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = "Servidor: estado desconocido",
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
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = "Servidor: activo",
                        isServerActive = true
                    )
                )
            }

            else -> {
                _uiState.value = _uiState.value.copy(
                    sync = _uiState.value.sync.copy(
                        serverStatusText = "Servidor: inactivo",
                        isServerActive = false
                    )
                )
            }
        }
    }

    private fun SyncProgressPhase.toUiText(): String {
        return when (this) {
            SyncProgressPhase.FetchingMoviesCatalog -> "Sincronizando películas (catálogo)"
            SyncProgressPhase.FetchingMoviesDetails -> "Sincronizando películas (detalles)"
            SyncProgressPhase.FetchingSeries -> "Sincronizando series"
            SyncProgressPhase.SeriesDetails -> "Refrescando detalles de series"
        }
    }
}
