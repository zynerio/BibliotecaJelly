package com.zynerio.bibliotecajelly

import com.google.gson.Gson
import com.zynerio.bibliotecajelly.data.AuthenticationResult
import com.zynerio.bibliotecajelly.data.ItemsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class JellyfinParsingTest {

    private val gson = Gson()

    @Test
    fun parseAuthenticationResult() {
        val json = """
            {
              "AccessToken": "token-123",
              "User": {
                "Id": "user-1",
                "Name": "demo"
              }
            }
        """.trimIndent()

        val result = gson.fromJson(json, AuthenticationResult::class.java)

        assertEquals("token-123", result.AccessToken)
        assertEquals("user-1", result.User.Id)
        assertEquals("demo", result.User.Name)
    }

    @Test
    fun parseItemsResponse() {
        val json = """
            {
              "Items": [
                {
                  "Id": "movie-1",
                  "Name": "Película de prueba",
                  "Type": "Movie",
                  "Container": "mkv",
                  "RunTimeTicks": 6000000000,
                  "ImageTags": { "Primary": "tag123" },
                  "MediaStreams": [
                    {
                      "Type": "Video",
                      "Language": "es",
                      "Width": 1920,
                      "Height": 1080,
                      "Bitrate": 8000000
                    }
                  ],
                  "MediaSources": [
                    {
                      "Container": "mkv",
                      "RunTimeTicks": 6000000000,
                      "Size": 2147483648,
                      "Bitrate": 8000000
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = gson.fromJson(json, ItemsResponse::class.java)

        assertNotNull(result.Items)
        val first = result.Items!!.first()
        assertEquals("movie-1", first.Id)
        assertEquals("Movie", first.Type)
        assertEquals("mkv", first.Container)
        assertEquals(6000000000L, first.RunTimeTicks)
        assertEquals("tag123", first.ImageTags?.get("Primary"))
    }
}

