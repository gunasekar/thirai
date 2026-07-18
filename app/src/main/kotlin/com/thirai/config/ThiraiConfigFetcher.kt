package com.thirai.config

import android.content.Context
import android.util.Log
import com.thirai.BuildConfig
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
    private const val PREF_SOURCE_URL = "config_source_url"

    // The default show-source, injected from .env at build time (BuildConfig).
    // Only a seed — the source is a user setting (see [sourceUrl]/[setSourceUrl]),
    // so the app isn't bound to any one host and a config can be shared by QR.
    val DEFAULT_SOURCE_URL: String = BuildConfig.SHOWS_URL

    /** The URL the show list is fetched from — a user setting, seeded with [DEFAULT_SOURCE_URL]. */
    fun sourceUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_SOURCE_URL, DEFAULT_SOURCE_URL)
            ?.ifBlank { DEFAULT_SOURCE_URL }
            ?: DEFAULT_SOURCE_URL

    /** Point the app at a new show-source URL (e.g. from a scanned QR). */
    fun setSourceUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SOURCE_URL, url.trim())
            .apply()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchShows(context: Context): List<Show> = withContext(Dispatchers.IO) {
        // Live source first; fall back to the last successfully fetched copy so a
        // brief network blip doesn't blank the widget. A first launch with no
        // network yet shows the empty state that guides setting the source.
        val config = fetchFromNetwork(sourceUrl(context))?.also { cacheConfig(context, it) }
            ?: loadCachedConfig(context)?.also { Log.w(TAG, "Using cached config") }
            ?: run {
                Log.w(TAG, "No config available (network or cache)")
                return@withContext emptyList()
            }
        // Each show is self-describing (its own app_package + home_link), so the
        // list is used as-is — no app-level defaults to apply.
        config.shows
    }

    private fun fetchFromNetwork(url: String): ShowConfig? {
        if (url.isEmpty()) {
            Log.w(TAG, "No show-source URL configured")
            return null
        }

        return try {
            val request = Request.Builder()
                .url(url)
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
