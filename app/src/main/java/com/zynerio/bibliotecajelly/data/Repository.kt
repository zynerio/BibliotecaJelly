package com.zynerio.bibliotecajelly.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import com.zynerio.bibliotecajelly.data.sync.JellyfinSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class ServerConfig(
    val baseUrl: String,
    val username: String?,
    val password: String?,
    val apiKey: String?,
    val accessToken: String?,
    val userId: String?,
    val lastSyncEpochMillis: Long?
)

enum class AutoSyncMode {
    OnStart,
    OnClose,
    Manual
}

enum class MovieDetailsSyncMode {
    All,
    RecentOnly
}

enum class ListDisplayMode {
    Infinite,
    Paged50
}

enum class LibrarySortMode {
    Alphabetical,
    RecentlyAdded
}

class CredentialsStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "biblioteca_jelly_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val keyBaseUrl = "base_url"
    private val keyUsername = "username"
    private val keyPassword = "password"
    private val keyApiKey = "api_key"
    private val keyAccessToken = "access_token"
    private val keyUserId = "user_id"
    private val keyLastSyncMillis = "last_sync_millis"
    private val keyAutoSyncMode = "auto_sync_mode"
    private val keyMovieDetailsSyncMode = "movie_details_sync_mode"
    private val keyListDisplayMode = "list_display_mode"
    private val keyOfflinePostersEnabled = "offline_posters_enabled"
    private val keyMovieSortMode = "movie_sort_mode"
    private val keySeriesSortMode = "series_sort_mode"
    private val keyLibraryLastSeenMillis = "library_last_seen_millis"

    fun getServerConfig(): ServerConfig? {
        val baseUrl = prefs.getString(keyBaseUrl, null) ?: return null
        return ServerConfig(
            baseUrl = baseUrl,
            username = prefs.getString(keyUsername, null),
            password = prefs.getString(keyPassword, null),
            apiKey = prefs.getString(keyApiKey, null),
            accessToken = prefs.getString(keyAccessToken, null),
            userId = prefs.getString(keyUserId, null),
            lastSyncEpochMillis = prefs.getLong(keyLastSyncMillis, 0L).takeIf { it > 0L }
        )
    }

    fun saveServerConfig(
        baseUrl: String,
        username: String?,
        password: String?,
        apiKey: String?
    ) {
        prefs.edit {
            putString(keyBaseUrl, baseUrl)
            putString(keyUsername, username)
            putString(keyPassword, password)
            putString(keyApiKey, apiKey)
        }
    }

    fun updateAuth(accessToken: String, userId: String) {
        prefs.edit {
            putString(keyAccessToken, accessToken)
            putString(keyUserId, userId)
        }
    }

    fun updateLastSync(epochMillis: Long) {
        prefs.edit {
            putLong(keyLastSyncMillis, epochMillis)
        }
    }

    fun getAutoSyncMode(): AutoSyncMode {
        val stored = prefs.getString(keyAutoSyncMode, null)
        return stored?.let {
            try {
                AutoSyncMode.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: AutoSyncMode.OnStart
    }

    fun setAutoSyncMode(mode: AutoSyncMode) {
        prefs.edit {
            putString(keyAutoSyncMode, mode.name)
        }
    }

    fun getMovieDetailsSyncMode(): MovieDetailsSyncMode {
        val stored = prefs.getString(keyMovieDetailsSyncMode, null)
        return stored?.let {
            try {
                MovieDetailsSyncMode.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: MovieDetailsSyncMode.All
    }

    fun setMovieDetailsSyncMode(mode: MovieDetailsSyncMode) {
        prefs.edit {
            putString(keyMovieDetailsSyncMode, mode.name)
        }
    }

    fun getListDisplayMode(): ListDisplayMode {
        val stored = prefs.getString(keyListDisplayMode, null)
        return stored?.let {
            try {
                ListDisplayMode.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: ListDisplayMode.Infinite
    }

    fun setListDisplayMode(mode: ListDisplayMode) {
        prefs.edit {
            putString(keyListDisplayMode, mode.name)
        }
    }

    fun getOfflinePostersEnabled(): Boolean =
        prefs.getBoolean(keyOfflinePostersEnabled, false)

    fun setOfflinePostersEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(keyOfflinePostersEnabled, enabled)
        }
    }

    fun getMovieSortMode(): LibrarySortMode {
        val stored = prefs.getString(keyMovieSortMode, null)
        return stored?.let {
            try {
                LibrarySortMode.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: LibrarySortMode.Alphabetical
    }

    fun setMovieSortMode(mode: LibrarySortMode) {
        prefs.edit {
            putString(keyMovieSortMode, mode.name)
        }
    }

    fun getSeriesSortMode(): LibrarySortMode {
        val stored = prefs.getString(keySeriesSortMode, null)
        return stored?.let {
            try {
                LibrarySortMode.valueOf(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: LibrarySortMode.Alphabetical
    }

    fun setSeriesSortMode(mode: LibrarySortMode) {
        prefs.edit {
            putString(keySeriesSortMode, mode.name)
        }
    }

    fun getLibraryLastSeenMillis(): Long? =
        prefs.getLong(keyLibraryLastSeenMillis, 0L).takeIf { it > 0L }

    fun setLibraryLastSeenMillis(epochMillis: Long) {
        prefs.edit {
            putLong(keyLibraryLastSeenMillis, epochMillis)
        }
    }
}

sealed class ConnectionResult {
    object Success : ConnectionResult()
    data class AuthFailure(val message: String) : ConnectionResult()
    data class NetworkError(val message: String) : ConnectionResult()
    data class UnknownError(val message: String) : ConnectionResult()
}

sealed class SyncResult {
    object Success : SyncResult()
    data class NetworkError(val message: String) : SyncResult()
    data class UnknownError(val message: String) : SyncResult()
}

enum class SyncScope {
    All,
    Movies,
    Series
}

enum class ClearDataScope {
    All,
    Movies,
    Series
}

enum class SyncProgressPhase {
    FetchingMoviesCatalog,
    FetchingMoviesDetails,
    FetchingSeries,
    SeriesDetails
}

interface JellyfinRepository {
    val movies: Flow<List<MovieEntity>>
    val series: Flow<List<SeriesEntity>>

    fun searchMovies(query: String): Flow<List<MovieEntity>>
    fun searchSeries(query: String): Flow<List<SeriesEntity>>

    fun getSeriesDetails(seriesId: String): Flow<SeriesWithSeasonsAndEpisodes?>

    fun getSeasonsForSeries(seriesId: String): Flow<List<SeasonEntity>>

    suspend fun getSeriesIdsBySeasonQuality(quality: String): List<String>
    suspend fun getSeriesIdsBySeasonFormat(format: String): List<String>
    suspend fun getSeriesIdsBySeasonResolution(resolution: String): List<String>

    suspend fun saveServerConfig(
        serverAddress: String,
        port: String,
        username: String?,
        password: String?,
        apiKey: String?
    )

    suspend fun getServerConfig(): ServerConfig?

    suspend fun authenticateAndValidateConnection(): ConnectionResult
    suspend fun checkServerStatus(): ConnectionResult

    suspend fun syncIncremental(
        scope: SyncScope = SyncScope.All,
        forceFullMovies: Boolean = false,
        onProgress: (processed: Int, total: Int, phase: SyncProgressPhase) -> Unit
    ): SyncResult

    suspend fun syncFast(
        scope: SyncScope = SyncScope.All,
        onProgress: (processed: Int, total: Int, phase: SyncProgressPhase) -> Unit
    ): SyncResult

    suspend fun getLastSyncEpochMillis(): Long?

    suspend fun getAutoSyncMode(): AutoSyncMode
    suspend fun setAutoSyncMode(mode: AutoSyncMode)
    suspend fun getMovieDetailsSyncMode(): MovieDetailsSyncMode
    suspend fun setMovieDetailsSyncMode(mode: MovieDetailsSyncMode)
    suspend fun getListDisplayMode(): ListDisplayMode
    suspend fun setListDisplayMode(mode: ListDisplayMode)
    suspend fun getMovieSortMode(): LibrarySortMode
    suspend fun setMovieSortMode(mode: LibrarySortMode)
    suspend fun getSeriesSortMode(): LibrarySortMode
    suspend fun setSeriesSortMode(mode: LibrarySortMode)
    suspend fun setMovieFavorite(movieId: String, isFavorite: Boolean)
    suspend fun setSeriesFavorite(seriesId: String, isFavorite: Boolean)
    suspend fun getLibraryLastSeenMillis(): Long?
    suspend fun setLibraryLastSeenMillis(epochMillis: Long)
    suspend fun getOfflinePostersEnabled(): Boolean
    suspend fun setOfflinePostersEnabled(enabled: Boolean)

    suspend fun clearLocalData(scope: ClearDataScope = ClearDataScope.All)

    suspend fun refreshSeriesDetails(seriesId: String)
    suspend fun refreshMovieDetails(movieId: String)
}

class DefaultJellyfinRepository(
    private val db: BibliotecaDatabase,
    private val credentialsStore: CredentialsStore,
    private val appContext: Context
) : JellyfinRepository {

    private companion object {
        const val RECENT_MOVIE_DETAILS_LIMIT = 500
        const val POSTER_MOVIES_DIR = "movies"
        const val POSTER_SERIES_DIR = "series"
    }

    @Volatile
    private var cachedApi: JellyfinApi? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    private fun getApi(): JellyfinApi {
        val baseUrl = credentialsStore.getServerConfig()?.baseUrl ?: "http://127.0.0.1/"
        val existing = cachedApi
        if (existing != null && cachedBaseUrl == baseUrl) {
            return existing
        }
        synchronized(this) {
            val again = cachedApi
            if (again != null && cachedBaseUrl == baseUrl) {
                return again
            }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(AuthorizationInterceptor(credentialsStore))
                .addInterceptor(logging)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val api = retrofit.create(JellyfinApi::class.java)
            cachedApi = api
            cachedBaseUrl = baseUrl
            return api
        }
    }

    override val movies: Flow<List<MovieEntity>> = db.movieDao().getAllMovies()
    override val series: Flow<List<SeriesEntity>> = db.seriesDao().getAllSeries()

    override fun searchMovies(query: String): Flow<List<MovieEntity>> {
        return if (query.isBlank()) {
            movies
        } else {
            db.movieDao().searchMoviesByTitle(query)
        }
    }

    override fun searchSeries(query: String): Flow<List<SeriesEntity>> {
        return if (query.isBlank()) {
            series
        } else {
            db.seriesDao().searchSeriesByTitle(query)
        }
    }

    override fun getSeriesDetails(seriesId: String): Flow<SeriesWithSeasonsAndEpisodes?> {
        return db.seriesDao().getSeriesDetails(seriesId)
    }

    override fun getSeasonsForSeries(seriesId: String): Flow<List<SeasonEntity>> {
        return db.seriesDao().getSeasonsForSeries(seriesId)
    }

    override suspend fun getSeriesIdsBySeasonQuality(quality: String): List<String> {
        return db.seriesDao().getSeriesIdsBySeasonQuality(quality)
    }

    override suspend fun getSeriesIdsBySeasonFormat(format: String): List<String> {
        return db.seriesDao().getSeriesIdsBySeasonFormat(format)
    }

    override suspend fun getSeriesIdsBySeasonResolution(resolution: String): List<String> {
        return db.seriesDao().getSeriesIdsBySeasonResolution(resolution)
    }

    override suspend fun saveServerConfig(
        serverAddress: String,
        port: String,
        username: String?,
        password: String?,
        apiKey: String?
    ) {
        val baseUrl = buildNormalizedBaseUrl(serverAddress, port)
        credentialsStore.saveServerConfig(
            baseUrl = baseUrl,
            username = username?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotBlank() },
            apiKey = apiKey?.takeIf { it.isNotBlank() }
        )
    }

    private fun buildNormalizedBaseUrl(serverAddress: String, port: String): String {
        val raw = serverAddress.trim().removeSuffix("/")
        if (raw.isBlank()) return "http://127.0.0.1:8096/"

        val withScheme = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            "http://$raw"
        }

        val parsed = withScheme.toHttpUrlOrNull()
        if (parsed != null) {
            val hasExplicitPort = Regex(":\\d+($|/)").containsMatchIn(raw)
            val selectedPort = port.toIntOrNull()

            val final = if (!hasExplicitPort && selectedPort != null) {
                parsed.newBuilder().port(selectedPort).build()
            } else {
                parsed
            }

            val url = final.toString()
            return if (url.endsWith('/')) url else "$url/"
        }

        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return if (raw.endsWith('/')) raw else "$raw/"
        }

        val fallback = if (raw.contains(':')) "http://$raw/" else "http://$raw:$port/"
        return fallback
    }

    override suspend fun getServerConfig(): ServerConfig? =
        credentialsStore.getServerConfig()

    override suspend fun getAutoSyncMode(): AutoSyncMode =
        credentialsStore.getAutoSyncMode()

    override suspend fun setAutoSyncMode(mode: AutoSyncMode) {
        credentialsStore.setAutoSyncMode(mode)
    }

    override suspend fun getMovieDetailsSyncMode(): MovieDetailsSyncMode =
        credentialsStore.getMovieDetailsSyncMode()

    override suspend fun setMovieDetailsSyncMode(mode: MovieDetailsSyncMode) {
        credentialsStore.setMovieDetailsSyncMode(mode)
    }

    override suspend fun getListDisplayMode(): ListDisplayMode =
        credentialsStore.getListDisplayMode()

    override suspend fun setListDisplayMode(mode: ListDisplayMode) {
        credentialsStore.setListDisplayMode(mode)
    }

    override suspend fun getMovieSortMode(): LibrarySortMode =
        credentialsStore.getMovieSortMode()

    override suspend fun setMovieSortMode(mode: LibrarySortMode) {
        credentialsStore.setMovieSortMode(mode)
    }

    override suspend fun getSeriesSortMode(): LibrarySortMode =
        credentialsStore.getSeriesSortMode()

    override suspend fun setSeriesSortMode(mode: LibrarySortMode) {
        credentialsStore.setSeriesSortMode(mode)
    }

    override suspend fun setMovieFavorite(movieId: String, isFavorite: Boolean) {
        db.movieDao().setFavorite(movieId, isFavorite)
    }

    override suspend fun setSeriesFavorite(seriesId: String, isFavorite: Boolean) {
        db.seriesDao().setFavorite(seriesId, isFavorite)
    }

    override suspend fun getLibraryLastSeenMillis(): Long? =
        credentialsStore.getLibraryLastSeenMillis()

    override suspend fun setLibraryLastSeenMillis(epochMillis: Long) {
        credentialsStore.setLibraryLastSeenMillis(epochMillis)
    }

    override suspend fun getOfflinePostersEnabled(): Boolean =
        credentialsStore.getOfflinePostersEnabled()

    override suspend fun setOfflinePostersEnabled(enabled: Boolean) {
        credentialsStore.setOfflinePostersEnabled(enabled)
    }

    override suspend fun clearLocalData(scope: ClearDataScope) {
        withContext(Dispatchers.IO) {
            when (scope) {
                ClearDataScope.All -> {
                    db.movieDao().clearAll()
                    db.seriesDao().clearEpisodes()
                    db.seriesDao().clearSeasons()
                    db.seriesDao().clearSeries()
                    clearPosterDirectory(POSTER_MOVIES_DIR)
                    clearPosterDirectory(POSTER_SERIES_DIR)
                }

                ClearDataScope.Movies -> {
                    db.movieDao().clearAll()
                    clearPosterDirectory(POSTER_MOVIES_DIR)
                }

                ClearDataScope.Series -> {
                    db.seriesDao().clearEpisodes()
                    db.seriesDao().clearSeasons()
                    db.seriesDao().clearSeries()
                    clearPosterDirectory(POSTER_SERIES_DIR)
                }
            }
            credentialsStore.updateLastSync(0L)
        }
    }

    private fun clearPosterDirectory(type: String) {
        val dir = File(appContext.filesDir, "posters/$type")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun resolvePosterPath(itemTypeDir: String, itemId: String): File {
        val directory = File(appContext.filesDir, "posters/$itemTypeDir")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, "$itemId.jpg")
    }

    private fun downloadPosterToLocal(
        itemTypeDir: String,
        itemId: String,
        remoteUrl: String
    ): String? {
        return runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(AuthorizationInterceptor(credentialsStore))
                .build()

            val request = Request.Builder().url(remoteUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val destination = resolvePosterPath(itemTypeDir, itemId)
                destination.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }
                destination.toURI().toString()
            }
        }.getOrNull()
    }

    override suspend fun refreshSeriesDetails(seriesId: String) {
        withContext(Dispatchers.IO) {
            val config = credentialsStore.getServerConfig() ?: return@withContext

            val userId = config.userId
            if (userId.isNullOrBlank() && config.apiKey.isNullOrBlank()) {
                return@withContext
            }

            val fields = "MediaStreams,MediaSources,Width,Height"

            val api = getApi()

            val refreshedGenres = runCatching {
                api.getItemById(
                    userId = userId ?: "",
                    itemId = seriesId,
                    fields = "Genres"
                ).Genres.orEmpty()
            }.getOrNull()

            if (refreshedGenres != null) {
                val existingSeries = db.seriesDao().getSeriesById(seriesId)
                if (existingSeries != null) {
                    db.seriesDao().upsertSeries(
                        listOf(existingSeries.copy(genres = refreshedGenres))
                    )
                }
            }

            val seasonsFromShows = runCatching {
                api.getSeasonsForSeries(
                    seriesId = seriesId,
                    userId = userId ?: "",
                    fields = fields
                ).Items.orEmpty()
            }.getOrDefault(emptyList())

            val episodesFromShows = runCatching {
                api.getEpisodesForSeries(
                    seriesId = seriesId,
                    userId = userId ?: "",
                    fields = fields
                ).Items.orEmpty()
            }.getOrDefault(emptyList())

            var seasonsDtos = seasonsFromShows.filter { it.Type == "Season" }
            var episodesDtos = episodesFromShows.filter { it.Type == "Episode" }

            if (seasonsDtos.isEmpty()) {
                val seasonsFromItems = runCatching {
                    api.getItems(
                        userId = userId ?: "",
                        includeItemTypes = "Season",
                        recursive = false,
                        fields = fields,
                        parentId = seriesId
                    ).Items.orEmpty()
                }.getOrDefault(emptyList())
                seasonsDtos = seasonsFromItems.filter { it.Type == "Season" }
            }

            if (episodesDtos.isEmpty()) {
                val episodesFromItems = runCatching {
                    api.getItems(
                        userId = userId ?: "",
                        includeItemTypes = "Episode",
                        fields = fields,
                        seriesId = seriesId
                    ).Items.orEmpty()
                }.getOrDefault(emptyList())
                episodesDtos = episodesFromItems.filter { it.Type == "Episode" }

                if (episodesDtos.isEmpty()) {
                    val episodesFromItemsByParent = runCatching {
                        api.getItems(
                            userId = userId ?: "",
                            includeItemTypes = "Episode",
                            recursive = true,
                            fields = fields,
                            parentId = seriesId
                        ).Items.orEmpty()
                    }.getOrDefault(emptyList())
                    episodesDtos = episodesFromItemsByParent.filter { it.Type == "Episode" }
                }
            }

            Log.d("JellyfinDetails", "seriesId=$seriesId " +
                    "seasonsFromShows=${seasonsFromShows.size}, episodesFromShows=${episodesFromShows.size}")
            Log.d("JellyfinDetails", "after fallback: seasonsDtos=${seasonsDtos.size}, episodesDtos=${episodesDtos.size}")

            if (seasonsDtos.isEmpty() && episodesDtos.isEmpty()) return@withContext

            val seasonEntities = mutableListOf<SeasonEntity>()
            val episodeEntities = mutableListOf<EpisodeEntity>()

            if (seasonsDtos.isNotEmpty()) {
                for (seasonDto in seasonsDtos) {
                    val episodesForSeason = episodesDtos.filter { ep ->
                        ep.SeasonId == seasonDto.Id || ep.ParentId == seasonDto.Id
                    }
                    val aggregated = episodesForSeason.aggregateSeasonStats()
                    seasonEntities.add(
                        SeasonEntity(
                            id = seasonDto.Id,
                            seriesId = seriesId,
                            seasonNumber = seasonDto.IndexNumber ?: seasonDto.ParentIndexNumber ?: 0,
                            format = aggregated.format,
                            quality = aggregated.quality,
                            resolution = aggregated.resolution,
                            bitrateMbps = aggregated.bitrateMbps,
                            fps = aggregated.fps,
                            totalDurationMinutes = aggregated.totalDurationMinutes,
                            totalSizeGb = aggregated.totalSizeGb,
                            audioLanguages = aggregated.audioLanguages,
                            subtitleLanguages = aggregated.subtitleLanguages,
                            episodeCount = episodesForSeason.size
                        )
                    )
                    episodesForSeason.mapTo(episodeEntities) { ep ->
                        EpisodeEntity(
                            id = ep.Id,
                            seriesId = seriesId,
                            seasonId = seasonDto.Id,
                            seasonNumber = seasonDto.IndexNumber ?: seasonDto.ParentIndexNumber ?: 0,
                            episodeNumber = ep.IndexNumber ?: 0,
                            title = ep.Name,
                            durationMinutes = ep.estimateDurationMinutes(),
                            sizeGb = ep.estimateSizeGb()
                        )
                    }
                }
            } else {
                val episodesBySeason = episodesDtos.groupBy { it.SeasonId ?: it.ParentId ?: "" }
                for ((seasonId, eps) in episodesBySeason) {
                    if (seasonId.isBlank()) continue
                    val seasonNumber = eps.firstOrNull()?.ParentIndexNumber ?: 0
                    val aggregated = eps.aggregateSeasonStats()
                    seasonEntities.add(
                        SeasonEntity(
                            id = seasonId,
                            seriesId = seriesId,
                            seasonNumber = seasonNumber,
                            format = aggregated.format,
                            quality = aggregated.quality,
                            resolution = aggregated.resolution,
                            bitrateMbps = aggregated.bitrateMbps,
                            fps = aggregated.fps,
                            totalDurationMinutes = aggregated.totalDurationMinutes,
                            totalSizeGb = aggregated.totalSizeGb,
                            audioLanguages = aggregated.audioLanguages,
                            subtitleLanguages = aggregated.subtitleLanguages,
                            episodeCount = eps.size
                        )
                    )
                    eps.mapTo(episodeEntities) { ep ->
                        EpisodeEntity(
                            id = ep.Id,
                            seriesId = seriesId,
                            seasonId = seasonId,
                            seasonNumber = seasonNumber,
                            episodeNumber = ep.IndexNumber ?: 0,
                            title = ep.Name,
                            durationMinutes = ep.estimateDurationMinutes(),
                            sizeGb = ep.estimateSizeGb()
                        )
                    }
                }
            }

            Log.d("JellyfinDetails", "Will upsert seasons=${seasonEntities.size}, episodes=${episodeEntities.size}")

            db.seriesDao().applySeriesDetailsAtomic(
                seriesId = seriesId,
                seasons = seasonEntities,
                episodes = episodeEntities
            )
        }
    }
    override suspend fun refreshMovieDetails(movieId: String) {
        withContext(Dispatchers.IO) {
            val config = credentialsStore.getServerConfig() ?: return@withContext

            val userId = config.userId
            if (userId.isNullOrBlank() && config.apiKey.isNullOrBlank()) {
                return@withContext
            }

            val detailFields = "MediaStreams,MediaSources,Width,Height,Genres,DateCreated"
            val offlinePostersEnabled = credentialsStore.getOfflinePostersEnabled()
            val api = getApi()

            val dto = runCatching {
                api.getItemById(
                    userId = userId ?: "",
                    itemId = movieId,
                    fields = detailFields
                )
            }.getOrNull() ?: return@withContext

            val existing = db.movieDao().getMoviesByIds(listOf(movieId)).firstOrNull()
            val updated = dto.toMovieEntity(config.baseUrl, existing) ?: return@withContext
            db.movieDao().upsertAll(
                listOf(
                    updated.copy(
                        posterUrl = resolvePosterUrl(
                            itemTypeDir = POSTER_MOVIES_DIR,
                            itemId = updated.id,
                            remoteUrl = updated.posterUrl,
                            offlineEnabled = offlinePostersEnabled
                        )
                    )
                )
            )
        }
    }

    override suspend fun authenticateAndValidateConnection(): ConnectionResult {
        return withContext(Dispatchers.IO) {
            val config = credentialsStore.getServerConfig()
                ?: return@withContext ConnectionResult.AuthFailure("Configuración del servidor incompleta")

            try {
                Log.d("JellyfinAuth", "Validando conexión con ${config.baseUrl}")
                if (!config.apiKey.isNullOrBlank()) {
                    return@withContext ConnectionResult.Success
                }

                val username = config.username
                val password = config.password
                if (username.isNullOrBlank()) {
                    return@withContext ConnectionResult.AuthFailure("Usuario requerido")
                }

                val authResult = getApi().authenticateByName(
                    AuthenticationRequest(
                        Username = username,
                        Pw = password.orEmpty()
                    )
                )
                Log.d("JellyfinAuth", "Autenticación correcta para usuario $username")
                credentialsStore.updateAuth(
                    accessToken = authResult.AccessToken,
                    userId = authResult.User.Id
                )
                ConnectionResult.Success
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    ConnectionResult.AuthFailure("Auth 401: usuario o contraseña incorrectos")
                } else {
                    ConnectionResult.NetworkError(httpDiagnosticMessage(e))
                }
            } catch (e: java.net.SocketTimeoutException) {
                ConnectionResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: java.net.UnknownHostException) {
                ConnectionResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: java.net.ConnectException) {
                ConnectionResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: javax.net.ssl.SSLException) {
                ConnectionResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: Exception) {
                ConnectionResult.UnknownError(networkDiagnosticMessage(e))
            }
        }
    }

    override suspend fun checkServerStatus(): ConnectionResult {
        return withContext(Dispatchers.IO) {
            val config = credentialsStore.getServerConfig()
                ?: return@withContext ConnectionResult.AuthFailure("Configuración del servidor incompleta")

            if (config.baseUrl.isBlank()) {
                return@withContext ConnectionResult.AuthFailure("Configuración del servidor incompleta")
            }

            try {
                getApi().pingServer()
                ConnectionResult.Success
            } catch (e: java.net.SocketTimeoutException) {
                ConnectionResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: java.net.UnknownHostException) {
                ConnectionResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: java.net.ConnectException) {
                ConnectionResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: javax.net.ssl.SSLException) {
                ConnectionResult.NetworkError(networkDiagnosticMessage(e))
            } catch (_: retrofit2.HttpException) {
                ConnectionResult.Success
            } catch (e: Exception) {
                ConnectionResult.UnknownError(networkDiagnosticMessage(e))
            }
        }
    }

    override suspend fun syncFast(
        scope: SyncScope,
        onProgress: (processed: Int, total: Int, phase: SyncProgressPhase) -> Unit
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            val config = credentialsStore.getServerConfig()
                ?: return@withContext SyncResult.NetworkError("Configuración del servidor no encontrada")

            val userId = config.userId
            if (userId.isNullOrBlank() && config.apiKey.isNullOrBlank()) {
                return@withContext SyncResult.NetworkError("No hay usuario autenticado")
            }

            try {
                val syncMovies = scope == SyncScope.All || scope == SyncScope.Movies
                val syncSeries = scope == SyncScope.All || scope == SyncScope.Series
                val movieDetailsMode = credentialsStore.getMovieDetailsSyncMode()
                val offlinePostersEnabled = credentialsStore.getOfflinePostersEnabled()
                val detailFields = "MediaStreams,MediaSources,Width,Height,Genres,DateCreated"

                var processed = 0
                var total: Int

                if (syncMovies) {
                    val changedMovieIds = linkedSetOf<String>()
                    var startIndex = 0
                    val pageSize = 200
                    while (true) {
                        val response = getApi().getItems(
                            userId = userId ?: "",
                            includeItemTypes = "Movie",
                            fields = "Genres,DateCreated",
                            minDateLastSaved = null,
                            startIndex = startIndex,
                            limit = pageSize
                        )
                        val items = response.Items.orEmpty()
                        if (items.isEmpty()) break

                        val movieIds = items.asSequence()
                            .filter { it.Type == "Movie" }
                            .map { it.Id }
                            .toList()
                        val existingById = if (movieIds.isNotEmpty()) {
                            db.movieDao().getMoviesByIds(movieIds).associateBy { it.id }
                        } else {
                            emptyMap()
                        }
                        val pageEntities = items.mapNotNull { dto ->
                            dto.toMovieCatalogEntity(
                                baseUrl = config.baseUrl,
                                existing = existingById[dto.Id]
                            )
                        }.map { entity ->
                            entity.copy(
                                posterUrl = resolvePosterUrl(
                                    itemTypeDir = POSTER_MOVIES_DIR,
                                    itemId = entity.id,
                                    remoteUrl = entity.posterUrl,
                                    offlineEnabled = offlinePostersEnabled
                                )
                            )
                        }
                        if (pageEntities.isNotEmpty()) {
                            db.movieDao().upsertAll(pageEntities)
                            pageEntities.mapTo(changedMovieIds) { it.id }
                        }

                        total = processed + items.size + pageSize
                        repeat(items.size) {
                            processed++
                            onProgress(processed, total, SyncProgressPhase.FetchingMoviesCatalog)
                        }
                        if (items.size < pageSize) {
                            total = processed
                            onProgress(processed, total, SyncProgressPhase.FetchingMoviesCatalog)
                            break
                        }
                        startIndex += items.size
                    }

                    val targetMovieIds = selectMovieDetailsTargets(changedMovieIds, movieDetailsMode)
                    if (targetMovieIds.isNotEmpty()) {
                        var detailsProcessed = 0
                        val detailsTotal = targetMovieIds.size
                        onProgress(0, detailsTotal, SyncProgressPhase.FetchingMoviesDetails)

                        var detailStartIndex = 0
                        val detailPageSize = 300
                        while (true) {
                            val response = getApi().getItems(
                                userId = userId ?: "",
                                includeItemTypes = "Movie",
                                fields = detailFields,
                                minDateLastSaved = null,
                                startIndex = detailStartIndex,
                                limit = detailPageSize
                            )
                            val items = response.Items.orEmpty()
                            if (items.isEmpty()) break

                            val detailDtos = items.filter { it.Type == "Movie" && it.Id in targetMovieIds }
                            val existingById = if (detailDtos.isNotEmpty()) {
                                db.movieDao().getMoviesByIds(detailDtos.map { it.Id }).associateBy { it.id }
                            } else {
                                emptyMap()
                            }
                            val pageEntities = detailDtos.mapNotNull { dto ->
                                dto.toMovieEntity(config.baseUrl, existingById[dto.Id])
                            }.map { entity ->
                                entity.copy(
                                    posterUrl = resolvePosterUrl(
                                        itemTypeDir = POSTER_MOVIES_DIR,
                                        itemId = entity.id,
                                        remoteUrl = entity.posterUrl,
                                        offlineEnabled = offlinePostersEnabled
                                    )
                                )
                            }
                            if (pageEntities.isNotEmpty()) {
                                db.movieDao().upsertAll(pageEntities)
                            }

                            detailsProcessed += detailDtos.size
                            onProgress(
                                detailsProcessed.coerceAtMost(detailsTotal),
                                detailsTotal,
                                SyncProgressPhase.FetchingMoviesDetails
                            )

                            if (detailsProcessed >= detailsTotal || items.size < detailPageSize) {
                                break
                            }
                            detailStartIndex += items.size
                        }
                    }
                }

                if (syncSeries) {
                    val seriesIdsToRefresh = linkedSetOf<String>()
                    var startIndex = 0
                    val pageSize = 200
                    while (true) {
                        val response = getApi().getItems(
                            userId = userId ?: "",
                            includeItemTypes = "Series",
                            fields = "",
                            minDateLastSaved = null,
                            startIndex = startIndex,
                            limit = pageSize
                        )
                        val items = response.Items.orEmpty()
                        if (items.isEmpty()) break

                        val seriesEntities = items.mapNotNull { dto ->
                            if (dto.Type != "Series") {
                                null
                            } else {
                                seriesIdsToRefresh.add(dto.Id)
                                val existing = db.seriesDao().getSeriesById(dto.Id)
                                SeriesEntity(
                                    id = dto.Id,
                                    title = dto.Name,
                                    createdUtcMillis = parseDateCreatedUtcMillis(dto.DateCreated) ?: existing?.createdUtcMillis,
                                    posterUrl = resolvePosterUrl(
                                        itemTypeDir = POSTER_SERIES_DIR,
                                        itemId = dto.Id,
                                        remoteUrl = dto.buildPrimaryImageUrl(config.baseUrl),
                                        offlineEnabled = offlinePostersEnabled
                                    ),
                                    totalSeasons = existing?.totalSeasons ?: 0,
                                    totalEpisodes = existing?.totalEpisodes ?: 0,
                                    genres = dto.Genres ?: existing?.genres ?: emptyList(),
                                    isFavorite = existing?.isFavorite ?: false
                                )
                            }
                        }
                        if (seriesEntities.isNotEmpty()) {
                            db.seriesDao().upsertSeries(seriesEntities)
                        }

                        total = processed + items.size + pageSize
                        repeat(items.size) {
                            processed++
                            onProgress(processed, total, SyncProgressPhase.FetchingSeries)
                        }
                        if (items.size < pageSize) {
                            total = processed
                            onProgress(processed, total, SyncProgressPhase.FetchingSeries)
                            break
                        }
                        startIndex += items.size
                    }

                    refreshSeriesDetailsBatch(seriesIdsToRefresh, onProgress)
                }

                SyncResult.Success
            } catch (e: CancellationException) {
                throw e
            } catch (e: retrofit2.HttpException) {
                SyncResult.NetworkError(httpDiagnosticMessage(e))
            } catch (e: java.net.SocketTimeoutException) {
                SyncResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: java.net.UnknownHostException) {
                SyncResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: java.net.ConnectException) {
                SyncResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: javax.net.ssl.SSLException) {
                SyncResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: Exception) {
                SyncResult.UnknownError(networkDiagnosticMessage(e))
            }
        }
    }

    override suspend fun syncIncremental(
        scope: SyncScope,
        forceFullMovies: Boolean,
        onProgress: (processed: Int, total: Int, phase: SyncProgressPhase) -> Unit
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            val config = credentialsStore.getServerConfig()
                ?: return@withContext SyncResult.NetworkError("Configuración del servidor no encontrada")

            Log.d("JellyfinSync", "Iniciando sincronización con ${config.baseUrl}")

            val userId = config.userId
            if (userId.isNullOrBlank() && config.apiKey.isNullOrBlank()) {
                return@withContext SyncResult.NetworkError("No hay usuario autenticado")
            }

            val lastSync = config.lastSyncEpochMillis
            val lastSyncIso = lastSync?.let {
                val date = Date(it)
                val formatter = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    Locale.US
                ).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                formatter.format(date)
            }

            try {
                Log.d("JellyfinSync", "Usando sincronización incremental desde $lastSyncIso")
                val detailFields =
                    "MediaStreams,MediaSources,Width,Height,Genres,DateCreated"
                val movieDetailsMode = credentialsStore.getMovieDetailsSyncMode()
                val offlinePostersEnabled = credentialsStore.getOfflinePostersEnabled()

                val syncMovies = scope == SyncScope.All || scope == SyncScope.Movies
                val syncSeries = scope == SyncScope.All || scope == SyncScope.Series
                val movieMinDate = if (syncMovies && forceFullMovies) null else lastSyncIso

                var processed = 0
                var total: Int
                var totalMovies = 0
                var totalSeriesItems = 0

                if (syncMovies) {
                    val changedMovieIds = linkedSetOf<String>()
                    var startIndex = 0
                    val pageSize = 300
                    while (true) {
                        val response = getApi().getItems(
                            userId = userId ?: "",
                            includeItemTypes = "Movie",
                            fields = "Genres,DateCreated",
                            minDateLastSaved = movieMinDate,
                            startIndex = startIndex,
                            limit = pageSize
                        )
                        val items = response.Items.orEmpty()
                        if (items.isEmpty()) break

                        val movieIds = items.asSequence()
                            .filter { it.Type == "Movie" }
                            .map { it.Id }
                            .toList()
                        val existingById = if (movieIds.isNotEmpty()) {
                            db.movieDao().getMoviesByIds(movieIds).associateBy { it.id }
                        } else {
                            emptyMap()
                        }
                        val pageEntities = items.mapNotNull { dto ->
                            dto.toMovieCatalogEntity(
                                baseUrl = config.baseUrl,
                                existing = existingById[dto.Id]
                            )
                        }.map { entity ->
                            entity.copy(
                                posterUrl = resolvePosterUrl(
                                    itemTypeDir = POSTER_MOVIES_DIR,
                                    itemId = entity.id,
                                    remoteUrl = entity.posterUrl,
                                    offlineEnabled = offlinePostersEnabled
                                )
                            )
                        }
                        if (pageEntities.isNotEmpty()) {
                            db.movieDao().upsertAll(pageEntities)
                            totalMovies += pageEntities.size
                            pageEntities.mapTo(changedMovieIds) { it.id }
                        }

                        total = processed + items.size + pageSize
                        repeat(items.size) {
                            processed++
                            onProgress(processed, total, SyncProgressPhase.FetchingMoviesCatalog)
                        }
                        if (items.size < pageSize) {
                            total = processed
                            onProgress(processed, total, SyncProgressPhase.FetchingMoviesCatalog)
                            break
                        }
                        startIndex += items.size
                    }

                    val targetMovieIds = selectMovieDetailsTargets(changedMovieIds, movieDetailsMode)
                    if (targetMovieIds.isNotEmpty()) {
                        var detailsProcessed = 0
                        val detailsTotal = targetMovieIds.size
                        onProgress(0, detailsTotal, SyncProgressPhase.FetchingMoviesDetails)

                        var detailStartIndex = 0
                        val detailPageSize = 300
                        while (true) {
                            val response = getApi().getItems(
                                userId = userId ?: "",
                                includeItemTypes = "Movie",
                                fields = detailFields,
                                minDateLastSaved = movieMinDate,
                                startIndex = detailStartIndex,
                                limit = detailPageSize
                            )
                            val items = response.Items.orEmpty()
                            if (items.isEmpty()) break

                            val detailDtos = items.filter { it.Type == "Movie" && it.Id in targetMovieIds }
                            val existingById = if (detailDtos.isNotEmpty()) {
                                db.movieDao().getMoviesByIds(detailDtos.map { it.Id }).associateBy { it.id }
                            } else {
                                emptyMap()
                            }
                            val pageEntities = detailDtos.mapNotNull { dto ->
                                dto.toMovieEntity(config.baseUrl, existingById[dto.Id])
                            }.map { entity ->
                                entity.copy(
                                    posterUrl = resolvePosterUrl(
                                        itemTypeDir = POSTER_MOVIES_DIR,
                                        itemId = entity.id,
                                        remoteUrl = entity.posterUrl,
                                        offlineEnabled = offlinePostersEnabled
                                    )
                                )
                            }
                            if (pageEntities.isNotEmpty()) {
                                db.movieDao().upsertAll(pageEntities)
                            }

                            detailsProcessed += detailDtos.size
                            onProgress(
                                detailsProcessed.coerceAtMost(detailsTotal),
                                detailsTotal,
                                SyncProgressPhase.FetchingMoviesDetails
                            )

                            if (detailsProcessed >= detailsTotal || items.size < detailPageSize) {
                                break
                            }
                            detailStartIndex += items.size
                        }
                    }

                    Log.d("JellyfinSync", "Películas recibidas y guardadas: $totalMovies")
                }

                if (syncSeries) {
                    val seriesIdsToRefresh = linkedSetOf<String>()
                    var startIndex = 0
                    val pageSize = 200
                    while (true) {
                        val response = getApi().getItems(
                            userId = userId ?: "",
                            includeItemTypes = "Series",
                            fields = "",
                            minDateLastSaved = null,
                            startIndex = startIndex,
                            limit = pageSize
                        )
                        val items = response.Items.orEmpty()
                        if (items.isEmpty()) break

                        val seriesEntities = items.mapNotNull { dto ->
                            if (dto.Type != "Series") {
                                null
                            } else {
                                seriesIdsToRefresh.add(dto.Id)
                                val existing = db.seriesDao().getSeriesById(dto.Id)
                                SeriesEntity(
                                    id = dto.Id,
                                    title = dto.Name,
                                    createdUtcMillis = parseDateCreatedUtcMillis(dto.DateCreated) ?: existing?.createdUtcMillis,
                                    posterUrl = resolvePosterUrl(
                                        itemTypeDir = POSTER_SERIES_DIR,
                                        itemId = dto.Id,
                                        remoteUrl = dto.buildPrimaryImageUrl(config.baseUrl),
                                        offlineEnabled = offlinePostersEnabled
                                    ),
                                    totalSeasons = existing?.totalSeasons ?: 0,
                                    totalEpisodes = existing?.totalEpisodes ?: 0,
                                    genres = dto.Genres ?: existing?.genres ?: emptyList(),
                                    isFavorite = existing?.isFavorite ?: false
                                )
                            }
                        }
                        if (seriesEntities.isNotEmpty()) {
                            db.seriesDao().upsertSeries(seriesEntities)
                            totalSeriesItems += seriesEntities.size
                        }

                        total = processed + items.size + pageSize
                        repeat(items.size) {
                            processed++
                            onProgress(processed, total, SyncProgressPhase.FetchingSeries)
                        }
                        if (items.size < pageSize) {
                            total = processed
                            onProgress(processed, total, SyncProgressPhase.FetchingSeries)
                            break
                        }
                        startIndex += items.size
                    }

                    refreshSeriesDetailsBatch(seriesIdsToRefresh, onProgress)

                    Log.d("JellyfinSync", "Series recibidas y guardadas: $totalSeriesItems")
                }

                if (syncMovies) {
                    val now = System.currentTimeMillis()
                    credentialsStore.updateLastSync(now)
                }

                Log.d(
                    "JellyfinSync",
                    "Sincronización completada. Películas=$totalMovies, elementos de series=$totalSeriesItems"
                )

                SyncResult.Success
            } catch (e: CancellationException) {
                Log.d("JellyfinSync", "Sincronización cancelada", e)
                throw e
            } catch (e: retrofit2.HttpException) {
                Log.e("JellyfinSync", "Error HTTP durante sincronización", e)
                SyncResult.NetworkError(httpDiagnosticMessage(e))
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("JellyfinSync", "Timeout de red durante sincronización", e)
                SyncResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: java.net.UnknownHostException) {
                Log.e("JellyfinSync", "Servidor no disponible", e)
                SyncResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: java.net.ConnectException) {
                Log.e("JellyfinSync", "Conexión rechazada durante sincronización", e)
                SyncResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: javax.net.ssl.SSLException) {
                Log.e("JellyfinSync", "Error SSL durante sincronización", e)
                SyncResult.NetworkError(networkDiagnosticMessage(e))
            } catch (e: Exception) {
                Log.e("JellyfinSync", "Error desconocido durante sincronización", e)
                SyncResult.UnknownError(networkDiagnosticMessage(e))
            }
        }
    }

    override suspend fun getLastSyncEpochMillis(): Long? =
        credentialsStore.getServerConfig()?.lastSyncEpochMillis

    private suspend fun refreshSeriesDetailsBatch(
        seriesIds: Set<String>,
        onProgress: (processed: Int, total: Int, phase: SyncProgressPhase) -> Unit
    ) {
        if (seriesIds.isEmpty()) return

        onProgress(0, seriesIds.size, SyncProgressPhase.SeriesDetails)
        seriesIds.forEachIndexed { index, seriesId ->
            runCatching {
                refreshSeriesDetails(seriesId)
            }.onFailure { error ->
                Log.w("JellyfinSync", "No se pudo refrescar detalle de serie $seriesId", error)
            }
            onProgress(index + 1, seriesIds.size, SyncProgressPhase.SeriesDetails)
        }
    }

    private fun selectMovieDetailsTargets(
        movieIds: LinkedHashSet<String>,
        mode: MovieDetailsSyncMode
    ): Set<String> {
        return when (mode) {
            MovieDetailsSyncMode.All -> movieIds
            MovieDetailsSyncMode.RecentOnly -> movieIds.take(RECENT_MOVIE_DETAILS_LIMIT).toSet()
        }
    }

    private fun resolvePosterUrl(
        itemTypeDir: String,
        itemId: String,
        remoteUrl: String?,
        offlineEnabled: Boolean
    ): String? {
        if (remoteUrl.isNullOrBlank()) return remoteUrl

        val localFile = resolvePosterPath(itemTypeDir, itemId)
        if (localFile.exists() && localFile.length() > 0L) {
            return localFile.toURI().toString()
        }

        if (!offlineEnabled) return remoteUrl

        return downloadPosterToLocal(
            itemTypeDir = itemTypeDir,
            itemId = itemId,
            remoteUrl = remoteUrl
        ) ?: remoteUrl
    }

    private fun httpDiagnosticMessage(e: retrofit2.HttpException): String {
        val code = e.code()
        return when (code) {
            401 -> "Auth 401: token/API key inválida o usuario/contraseña incorrectos"
            403 -> "Auth 403: acceso denegado por el servidor"
            404 -> "HTTP 404: endpoint no encontrado (revisa URL/basePath)"
            502, 503, 504 -> "HTTP $code: servidor/proxy no disponible"
            else -> "HTTP $code: fallo de conexión con el servidor"
        }
    }

    private fun networkDiagnosticMessage(error: Throwable): String {
        return when (error) {
            is java.net.UnknownHostException -> "DNS: no se resuelve el dominio/IP"
            is java.net.ConnectException -> "NAT/Puerto: conexión rechazada o puerto cerrado"
            is java.net.SocketTimeoutException -> "Timeout: sin respuesta del servidor (red/NAT/firewall)"
            is javax.net.ssl.SSLHandshakeException -> "SSL: fallo de certificado/handshake TLS"
            is javax.net.ssl.SSLException -> "SSL: conexión segura no válida"
            else -> error.message ?: "Error de red desconocido"
        }
    }

    private fun BaseItemDto.toMovieEntity(
        baseUrl: String,
        existing: MovieEntity? = null
    ): MovieEntity? {
        if (Type != "Movie") return null

        val videoStream = MediaStreams?.firstOrNull { it.Type == "Video" }
        val audioStreams = MediaStreams?.filter { it.Type == "Audio" }.orEmpty()
        val subtitleStreams = MediaStreams?.filter { it.Type == "Subtitle" }.orEmpty()

        val width = videoStream?.Width
        val height = videoStream?.Height

        val resolution = if (width != null && height != null) {
            "${width}x$height"
        } else {
            null
        }

        val bitrate = videoStream?.Bitrate ?: MediaSources?.firstOrNull()?.Bitrate
        val sizeBytes = MediaSources?.firstOrNull()?.Size

        val durationMinutes = RunTimeTicks?.let { ticks ->
            val totalSeconds = ticks / 10_000_000L
            (totalSeconds / 60).toInt()
        }

        val sizeGb = sizeBytes?.let { bytes ->
            bytes.toDouble() / (1024 * 1024 * 1024)
        }

        val audioLanguages = audioStreams.mapNotNull { it.Language }.distinct()
        val subtitleLanguages = subtitleStreams.mapNotNull { it.Language }.distinct()

        val sourceFormat = MediaSources
            ?.firstOrNull { !it.Container.isNullOrBlank() }
            ?.Container
            ?.trim()
        val normalizedFormat = normalizeContainer(sourceFormat ?: Container)

        return MovieEntity(
            id = Id,
            title = Name,
            createdUtcMillis = parseDateCreatedUtcMillis(DateCreated) ?: existing?.createdUtcMillis,
            posterUrl = buildPrimaryImageUrl(baseUrl),
            format = normalizedFormat,
            quality = resolutionToQuality(resolution),
            resolution = resolution,
            bitrateMbps = bitrate?.toDouble()?.div(1_000_000.0),
            fps = null,
            durationMinutes = durationMinutes,
            sizeGb = sizeGb,
            audioLanguages = audioLanguages,
            subtitleLanguages = subtitleLanguages,
            genres = Genres.orEmpty(),
            isFavorite = existing?.isFavorite ?: false
        )
    }

    private fun BaseItemDto.toMovieCatalogEntity(
        baseUrl: String,
        existing: MovieEntity?
    ): MovieEntity? {
        if (Type != "Movie") return null

        return MovieEntity(
            id = Id,
            title = Name,
            createdUtcMillis = parseDateCreatedUtcMillis(DateCreated) ?: existing?.createdUtcMillis,
            posterUrl = buildPrimaryImageUrl(baseUrl),
            format = existing?.format,
            quality = existing?.quality,
            resolution = existing?.resolution,
            bitrateMbps = existing?.bitrateMbps,
            fps = existing?.fps,
            durationMinutes = existing?.durationMinutes,
            sizeGb = existing?.sizeGb,
            audioLanguages = existing?.audioLanguages ?: emptyList(),
            subtitleLanguages = existing?.subtitleLanguages ?: emptyList(),
            genres = Genres ?: existing?.genres ?: emptyList(),
            isFavorite = existing?.isFavorite ?: false
        )
    }

    private fun BaseItemDto.buildPrimaryImageUrl(baseUrl: String): String? {
        val tag = ImageTags?.get("Primary") ?: return null
        return "${baseUrl.trimEnd('/')}/Items/$Id/Images/Primary?tag=$tag"
    }

    private fun parseDateCreatedUtcMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun resolutionToQuality(resolution: String?): String? {
        if (resolution.isNullOrBlank()) return null

        val match = Regex("(\\d+)x(\\d+)").find(resolution) ?: return null
        val (widthStr, heightStr) = match.destructured
        val width = widthStr.toIntOrNull() ?: return null
        val height = heightStr.toIntOrNull() ?: return null

        return when {
            width >= 3840 || height >= 2160 -> "4K"
            width >= 1920 || height >= 1040 -> "1080p"
            width >= 1280 || height >= 700 -> "720p"
            width >= 854 || height >= 480 -> "480p"
            else -> null
        }
    }

    private fun normalizeContainer(rawContainer: String?): String? {
        if (rawContainer.isNullOrBlank()) return null
        val fromList = rawContainer
            .split(',')
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
        return fromList?.lowercase(Locale.ROOT)
    }

    private data class SeasonAggregates(
        val format: String?,
        val quality: String?,
        val resolution: String?,
        val bitrateMbps: Double?,
        val fps: Double?,
        val totalDurationMinutes: Int?,
        val totalSizeGb: Double?,
        val audioLanguages: List<String>,
        val subtitleLanguages: List<String>
    )

    private fun List<BaseItemDto>.aggregateSeasonStats(): SeasonAggregates {
        var totalDurationMinutes = 0
        var totalSizeGb = 0.0
        val audioLangs = mutableSetOf<String>()
        val subtitleLangs = mutableSetOf<String>()

        var resolution: String? = null
        var format: String? = null
        var bitrateMbps: Double? = null

        for (dto in this) {
            val videoStream = dto.MediaStreams?.firstOrNull { it.Type == "Video" }
            val width = videoStream?.Width
            val height = videoStream?.Height
            if (width != null && height != null && resolution == null) {
                resolution = "${width}x$height"
            }

            if (format == null && dto.Container != null) {
                format = dto.Container
            }

            val bitrate =
                videoStream?.Bitrate ?: dto.MediaSources?.firstOrNull()?.Bitrate ?: 0L
            if (bitrate > 0 && bitrateMbps == null) {
                bitrateMbps = bitrate.toDouble() / 1_000_000.0
            }

            val duration = dto.RunTimeTicks?.let { ticks ->
                val totalSeconds = ticks / 10_000_000L
                (totalSeconds / 60).toInt()
            } ?: 0
            totalDurationMinutes += duration

            val sizeBytes = dto.MediaSources?.firstOrNull()?.Size ?: 0L
            totalSizeGb += sizeBytes.toDouble() / (1024 * 1024 * 1024)

            val audioStreams = dto.MediaStreams?.filter { it.Type == "Audio" }.orEmpty()
            val subtitleStreams = dto.MediaStreams?.filter { it.Type == "Subtitle" }.orEmpty()

            audioStreams.mapNotNullTo(audioLangs) { it.Language }
            subtitleStreams.mapNotNullTo(subtitleLangs) { it.Language }
        }

        return SeasonAggregates(
            format = format,
            quality = resolutionToQuality(resolution),
            resolution = resolution,
            bitrateMbps = bitrateMbps,
            fps = null,
            totalDurationMinutes = totalDurationMinutes,
            totalSizeGb = totalSizeGb,
            audioLanguages = audioLangs.toList(),
            subtitleLanguages = subtitleLangs.toList()
        )
    }

    private fun BaseItemDto.estimateDurationMinutes(): Int? {
        return RunTimeTicks?.let { ticks ->
            val totalSeconds = ticks / 10_000_000L
            (totalSeconds / 60).toInt()
        }
    }

    private fun BaseItemDto.estimateSizeGb(): Double? {
        val sizeBytes = MediaSources?.firstOrNull()?.Size ?: return null
        return sizeBytes.toDouble() / (1024 * 1024 * 1024)
    }
}

private class AuthorizationInterceptor(
    private val credentialsStore: CredentialsStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val config = credentialsStore.getServerConfig()

        val token = config?.accessToken
        val apiKey = config?.apiKey

        val builder = original.newBuilder()

        val appName = "BibliotecaJelly"
        val deviceName = Build.MODEL ?: "AndroidDevice"
        val version = "1.0.0"
        val deviceId = deviceName

        val embyAuth =
            """MediaBrowser Client="$appName", Device="$deviceName", DeviceId="$deviceId", Version="$version""""
        builder.addHeader("X-Emby-Authorization", embyAuth)

        val effectiveToken = when {
            !apiKey.isNullOrBlank() -> apiKey
            !token.isNullOrBlank() -> token
            else -> null
        }

        if (!effectiveToken.isNullOrBlank()) {
            builder.addHeader("X-Emby-Token", effectiveToken)
        }

        return chain.proceed(builder.build())
    }
}

object ServiceLocator {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE movies ADD COLUMN genres TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE series ADD COLUMN genres TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE movies ADD COLUMN created_utc_millis INTEGER")
            database.execSQL("ALTER TABLE series ADD COLUMN created_utc_millis INTEGER")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE movies ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE series ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Volatile
    private var dbInstance: BibliotecaDatabase? = null

    @Volatile
    private var credentialsStoreInstance: CredentialsStore? = null

    @Volatile
    private var repositoryInstance: JellyfinRepository? = null

    fun provideDatabase(context: Context): BibliotecaDatabase {
        return dbInstance ?: synchronized(this) {
            dbInstance ?: androidx.room.Room.databaseBuilder(
                context.applicationContext,
                BibliotecaDatabase::class.java,
                "biblioteca_jelly.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { dbInstance = it }
        }
    }

    fun provideCredentialsStore(context: Context): CredentialsStore {
        return credentialsStoreInstance ?: synchronized(this) {
            credentialsStoreInstance ?: CredentialsStore(context.applicationContext)
                .also { credentialsStoreInstance = it }
        }
    }

    fun provideRepository(context: Context): JellyfinRepository {
        return repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: DefaultJellyfinRepository(
                db = provideDatabase(context),
                credentialsStore = provideCredentialsStore(context),
                appContext = context.applicationContext
            ).also { repositoryInstance = it }
        }
    }
}
