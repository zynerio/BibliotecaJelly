package com.zynerio.bibliotecajelly.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "movies",
    indices = [Index(value = ["title"])]
)
data class MovieEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "created_utc_millis")
    val createdUtcMillis: Long?,
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,
    val format: String?,
    val quality: String?,
    val resolution: String?,
    @ColumnInfo(name = "bitrate_mbps")
    val bitrateMbps: Double?,
    val fps: Double?,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int?,
    @ColumnInfo(name = "size_gb")
    val sizeGb: Double?,
    @ColumnInfo(name = "audio_languages")
    val audioLanguages: List<String>,
    @ColumnInfo(name = "subtitle_languages")
    val subtitleLanguages: List<String>,
    val genres: List<String>,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "series",
    indices = [Index(value = ["title"])]
)
data class SeriesEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "created_utc_millis")
    val createdUtcMillis: Long?,
    @ColumnInfo(name = "poster_url")
    val posterUrl: String?,
    @ColumnInfo(name = "total_seasons")
    val totalSeasons: Int,
    @ColumnInfo(name = "total_episodes")
    val totalEpisodes: Int,
    val genres: List<String>,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["series_id"])]
)
data class SeasonEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,
    val format: String?,
    val quality: String?,
    val resolution: String?,
    @ColumnInfo(name = "bitrate_mbps")
    val bitrateMbps: Double?,
    val fps: Double?,
    @ColumnInfo(name = "total_duration_minutes")
    val totalDurationMinutes: Int?,
    @ColumnInfo(name = "total_size_gb")
    val totalSizeGb: Double?,
    @ColumnInfo(name = "audio_languages")
    val audioLanguages: List<String>,
    @ColumnInfo(name = "subtitle_languages")
    val subtitleLanguages: List<String>,
    @ColumnInfo(name = "episode_count")
    val episodeCount: Int
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SeasonEntity::class,
            parentColumns = ["id"],
            childColumns = ["season_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["series_id"]),
        Index(value = ["season_id"])
    ]
)
data class EpisodeEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "series_id")
    val seriesId: String,
    @ColumnInfo(name = "season_id")
    val seasonId: String,
    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,
    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int,
    val title: String,
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int?,
    @ColumnInfo(name = "size_gb")
    val sizeGb: Double?
)

data class SeasonWithEpisodes(
    @Embedded
    val season: SeasonEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "season_id"
    )
    val episodes: List<EpisodeEntity>
)

data class SeriesWithSeasonsAndEpisodes(
    @Embedded
    val series: SeriesEntity,
    @Relation(
        entity = SeasonEntity::class,
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val seasons: List<SeasonWithEpisodes>
)

class RoomConverters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? =
        value?.joinToString(separator = "|")

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        value?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
}

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY title")
    fun getAllMovies(): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE title LIKE '%' || :query || '%' ORDER BY title")
    fun searchMoviesByTitle(query: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id IN (:ids)")
    suspend fun getMoviesByIds(ids: List<String>): List<MovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(movies: List<MovieEntity>)

    @Query("UPDATE movies SET is_favorite = :isFavorite WHERE id = :movieId")
    suspend fun setFavorite(movieId: String, isFavorite: Boolean)

    @Query("DELETE FROM movies")
    suspend fun clearAll()
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM series ORDER BY title")
    fun getAllSeries(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE title LIKE '%' || :query || '%' ORDER BY title")
    fun searchSeriesByTitle(query: String): Flow<List<SeriesEntity>>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :seriesId")
    fun getSeriesDetails(seriesId: String): Flow<SeriesWithSeasonsAndEpisodes?>

    @Query("SELECT * FROM series WHERE id = :seriesId LIMIT 1")
    suspend fun getSeriesById(seriesId: String): SeriesEntity?

    @Upsert
    suspend fun upsertSeries(series: List<SeriesEntity>)

    @Query("UPDATE series SET is_favorite = :isFavorite WHERE id = :seriesId")
    suspend fun setFavorite(seriesId: String, isFavorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasons(seasons: List<SeasonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM seasons WHERE series_id = :seriesId ORDER BY season_number")
    fun getSeasonsForSeries(seriesId: String): Flow<List<SeasonEntity>>

    @Query("SELECT DISTINCT series_id FROM seasons WHERE quality = :quality")
    suspend fun getSeriesIdsBySeasonQuality(quality: String): List<String>

    @Query("SELECT DISTINCT series_id FROM seasons WHERE format = :format")
    suspend fun getSeriesIdsBySeasonFormat(format: String): List<String>

    @Query("SELECT DISTINCT series_id FROM seasons WHERE resolution = :resolution")
    suspend fun getSeriesIdsBySeasonResolution(resolution: String): List<String>

    @Transaction
    suspend fun applySeriesDetailsAtomic(
        seriesId: String,
        seasons: List<SeasonEntity>,
        episodes: List<EpisodeEntity>
    ) {
        if (seasons.isNotEmpty()) {
            upsertSeasons(seasons)
        }
        if (episodes.isNotEmpty()) {
            upsertEpisodes(episodes)
        }
        val existing = getSeriesById(seriesId)
        if (existing != null) {
            val updated = existing.copy(
                totalSeasons = seasons.size,
                totalEpisodes = episodes.size
            )
            upsertSeries(listOf(updated))
        }
    }

    @Query("DELETE FROM series")
    suspend fun clearSeries()

    @Query("DELETE FROM seasons")
    suspend fun clearSeasons()

    @Query("DELETE FROM episodes")
    suspend fun clearEpisodes()
}

@Database(
    entities = [
        MovieEntity::class,
        SeriesEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class BibliotecaDatabase : RoomDatabase() {
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
}
