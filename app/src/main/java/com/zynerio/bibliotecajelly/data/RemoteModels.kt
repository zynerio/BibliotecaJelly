package com.zynerio.bibliotecajelly.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AuthenticationRequest(
    val Username: String,
    val Pw: String
)

data class AuthUser(
    val Id: String,
    val Name: String
)

data class AuthenticationResult(
    val AccessToken: String,
    val User: AuthUser
)

data class ItemsResponse(
    val Items: List<BaseItemDto>?
)

data class BaseItemDto(
    val Id: String,
    val Name: String,
    val Type: String,
    val DateCreated: String?,
    val Container: String?,
    val RunTimeTicks: Long?,
    val ImageTags: Map<String, String>?,
    val ParentId: String?,
    val SeriesId: String?,
    val SeasonId: String?,
    val Genres: List<String>?,
    val IndexNumber: Int?,
    val ParentIndexNumber: Int?,
    val MediaStreams: List<MediaStream>?,
    val MediaSources: List<MediaSource>?
)

data class MediaStream(
    val Type: String?,
    val Language: String?,
    val Codec: String?,
    val Width: Int?,
    val Height: Int?,
    val AverageBitrate: Long?,
    val Bitrate: Long?
)

data class MediaSource(
    val Container: String?,
    val RunTimeTicks: Long?,
    val Size: Long?,
    val Bitrate: Long?
)

interface JellyfinApi {
    @GET("System/Ping")
    suspend fun pingServer(): String

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body body: AuthenticationRequest
    ): AuthenticationResult

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("IncludeItemTypes") includeItemTypes: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String? = null,
        @Query("MinDateLastSaved") minDateLastSaved: String? = null,
        @Query("StartIndex") startIndex: Int? = null,
        @Query("Limit") limit: Int? = null,
        @Query("SeriesId") seriesId: String? = null,
        @Query("ParentId") parentId: String? = null
    ): ItemsResponse

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItemById(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Query("Fields") fields: String? = null
    ): BaseItemDto

    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasonsForSeries(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("Fields") fields: String? = null
    ): ItemsResponse

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodesForSeries(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("Fields") fields: String? = null
    ): ItemsResponse
}
