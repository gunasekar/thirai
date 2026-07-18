package com.thirai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ThiraiConfigFetcher {

    private const val TAG = "ThiraiConfig"
    private const val PREFS_NAME = "thirai_prefs"
    private const val PREF_CONFIG_JSON = "shows_config_json"

    // TODO: Replace with your actual GitHub Gist raw URL
    // Create a Gist at https://gist.github.com with a file named shows.json
    // Then click "Raw" to get the raw URL
    private const val GIST_RAW_URL = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchShows(context: Context): List<Show> = withContext(Dispatchers.IO) {
        val networkConfig = fetchFromNetwork()
        if (networkConfig != null) {
            cacheConfig(context, networkConfig)
            return@withContext networkConfig.shows
        }

        val cachedConfig = loadCachedConfig(context)
        if (cachedConfig != null) {
            Log.w(TAG, "Network fetch failed, using cached config")
            return@withContext cachedConfig.shows
        }

        Log.e(TAG, "No config available (network or cache)")
        emptyList()
    }

    private fun fetchFromNetwork(): ShowConfig? {
        if (GIST_RAW_URL.isEmpty()) {
            Log.w(TAG, "GIST_RAW_URL not configured")
            return null
        }

        return try {
            val request = Request.Builder()
                .url(GIST_RAW_URL)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                json.decodeFromString<ShowConfig>(body)
            } else {
                Log.e(TAG, "HTTP ${response.code}: ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network fetch failed: ${e.message}")
            null
        }
    }

    private fun cacheConfig(context: Context, config: ShowConfig) {
        val jsonString = json.encodeToString(ShowConfig.serializer(), config)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CONFIG_JSON, jsonString)
            .apply()
    }

    private fun loadCachedConfig(context: Context): ShowConfig? {
        val jsonString = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CONFIG_JSON, null) ?: return null

        return try {
            json.decodeFromString<ShowConfig>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached config: ${e.message}")
            null
        }
    }
}
