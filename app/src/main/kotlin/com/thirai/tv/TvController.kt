package com.thirai.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import remote.RemoteKeyCode
import kotlin.coroutines.resume

/**
 * The app's single entry point for talking to the TV over the Android TV Remote
 * protocol. Replaces the old ADB transport: no developer mode, no port 5555 —
 * just a one-time PIN pairing, then a trusted TLS control channel on 6466.
 *
 * Host resolution mirrors the old behaviour: a manually-set IP wins, else the
 * last-known IP, else mDNS discovery of the Remote service — with the discovered
 * address cached for next time.
 */
class TvController(private val context: Context) {

    private val identity by lazy { TvIdentity(context) }
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val manualHost: String? get() = prefs.getString(PREF_MANUAL_IP, null)?.ifBlank { null }
    val isPaired: Boolean get() = prefs.getBoolean(PREF_PAIRED, false)

    /** The friendly name of the TV we're set up with, or null. */
    val tvName: String? get() = prefs.getString(PREF_TV_NAME, null)?.ifBlank { null }

    /** Remember the TV picked from discovery (its name + address). */
    fun rememberTv(name: String, host: String) {
        prefs.edit().putString(PREF_MANUAL_IP, host).putString(PREF_TV_NAME, name).apply()
    }

    // ---- Pairing ----

    /** Begin pairing with [host]; when this returns the TV is showing its PIN. */
    fun newPairing(host: String) = TvPairing(identity, host)

    fun markPaired() {
        prefs.edit().putBoolean(PREF_PAIRED, true).apply()
    }

    // ---- Connectivity ----

    /** True if we can open a trusted control session with the TV right now. */
    suspend fun testConnection(): Boolean {
        val host = resolveHost() ?: return false
        return runRemote(host) { /* reaching ready is the whole test */ }
            .also { if (it) markPaired() }
    }

    // ---- Playback ----

    /**
     * Launch [deepLink] on the TV and start it playing.
     *
     * Rather than a blind fixed delay, this waits for the streaming app to
     * actually come to the foreground (the TV reports it), which removes the
     * biggest source of mis-timed presses — the app still launching. It then
     * sends two DPAD-centre presses followed by a play-only MEDIA_PLAY:
     *
     *  - Cold start (from the TV home, with a "Who's watching?" profile screen):
     *    press 1 picks the profile, press 2 clicks Watch, MEDIA_PLAY is a no-op.
     *  - Cold start with no profile prompt: press 1 clicks Watch, press 2 would
     *    pause, and MEDIA_PLAY resumes it.
     *  - Warm start (already in the app): press 1 clicks Watch, press 2 pauses,
     *    MEDIA_PLAY resumes.
     *
     * Because the final key is play-only (never a toggle), every path ends
     * playing. The one case this can't cover blindly is a slow-loading profile
     * screen — disabling it on the TV (single profile) makes playback fully
     * deterministic.
     *
     * @param appPackage the streaming app the deep link opens (from the show
     *   config). When set, playback waits for it to reach the foreground instead
     *   of guessing a delay. Blank skips the wait — nothing platform-specific is
     *   baked into the app.
     */
    suspend fun play(deepLink: String, appPackage: String? = null): Boolean {
        val host = resolveHost() ?: return false
        return runRemote(host) { remote ->
            remote.launchAppLink(deepLink)
            val foreground = if (!appPackage.isNullOrBlank()) {
                remote.awaitForeground(appPackage, FOREGROUND_TIMEOUT_MS)
            } else {
                false
            }
            // Let the details screen render, then drive it into playback.
            delay(if (foreground) SETTLE_AFTER_FOREGROUND_MS else BLIND_SETTLE_MS)
            remote.sendKey(RemoteKeyCode.KEYCODE_DPAD_CENTER)
            delay(PRESS_GAP_MS)
            remote.sendKey(RemoteKeyCode.KEYCODE_DPAD_CENTER)
            delay(PRESS_GAP_MS)
            remote.sendKey(RemoteKeyCode.KEYCODE_MEDIA_PLAY)
        }
    }

    /**
     * Restart whatever is currently playing on the TV from the beginning. Sends
     * MEDIA_PREVIOUS, which an ExoPlayer-based app (like Hotstar) treats as "jump
     * to the start of the current item" when already a few seconds in. Triggered
     * on demand from the widget's "Play from start" button.
     */
    suspend fun restartCurrent(): Boolean {
        val host = resolveHost() ?: return false
        return runRemote(host) { remote ->
            remote.sendKey(RemoteKeyCode.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    // All socket work runs on IO regardless of the caller's dispatcher — the
    // in-app path calls this from the main thread, and a blocking write there
    // would throw NetworkOnMainThreadException.
    private suspend fun runRemote(host: String, block: suspend (TvRemote) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val remote = TvRemote(identity, host)
            try {
                remote.connect()
                block(remote)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Remote session on $host failed: ${e.message}")
                false
            } finally {
                remote.close()
            }
        }

    // ---- Discovery ----

    /** An Android TV found on the local network via the Remote service. */
    data class DiscoveredTv(val name: String, val host: String)

    /**
     * Scan the local network for Android TVs advertising the Remote service and
     * return each with its friendly name and address. Runs a fixed discovery
     * window, then resolves the found services one at a time (NsdManager dislikes
     * concurrent resolves).
     */
    suspend fun discoverTvs(windowMs: Long = 5000): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = java.util.concurrent.ConcurrentHashMap<String, NsdServiceInfo>()
        var listener: NsdManager.DiscoveryListener? = null
        listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceFound(info: NsdServiceInfo) { found[info.serviceName] = info }
            override fun onServiceLost(info: NsdServiceInfo) { found.remove(info.serviceName) }
        }
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Discovery start failed: ${e.message}")
            return@withContext emptyList()
        }
        delay(windowMs)
        runCatching { nsd.stopServiceDiscovery(listener) }

        val tvs = mutableListOf<DiscoveredTv>()
        for (info in found.values) {
            resolveService(nsd, info)?.let { tvs.add(it) }
        }
        // Prefer a readable name; de-dupe by address.
        tvs.distinctBy { it.host }.sortedBy { it.name }
    }

    private suspend fun resolveService(nsd: NsdManager, info: NsdServiceInfo): DiscoveredTv? =
        withTimeoutOrNull(3000) {
            suspendCancellableCoroutine { cont ->
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, e: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    @Suppress("DEPRECATION")
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        val host = i.host?.hostAddress
                        val name = i.serviceName?.takeIf { it.isNotBlank() } ?: "Android TV"
                        if (cont.isActive) cont.resume(host?.let { DiscoveredTv(name, it) })
                    }
                })
            }
        }

    // ---- Host resolution ----

    suspend fun resolveHost(): String? {
        manualHost?.let { return it }
        prefs.getString(PREF_LAST_IP, null)?.ifBlank { null }?.let { return it }
        val discovered = discover()
        if (discovered != null) prefs.edit().putString(PREF_LAST_IP, discovered).apply()
        return discovered
    }

    private suspend fun discover(): String? = withContext(Dispatchers.Main) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        withTimeoutOrNull(8000) {
            suspendCancellableCoroutine { cont ->
                var listener: NsdManager.DiscoveryListener? = null
                listener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(t: String, e: Int) {
                        runCatching { listener?.let { nsd.stopServiceDiscovery(it) } }
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onStopDiscoveryFailed(t: String, e: Int) {}
                    override fun onDiscoveryStarted(t: String) {}
                    override fun onDiscoveryStopped(t: String) {}
                    override fun onServiceFound(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                            @Suppress("DEPRECATION")
                            override fun onServiceResolved(i: NsdServiceInfo) {
                                val ip = i.host?.hostAddress
                                runCatching { listener?.let { nsd.stopServiceDiscovery(it) } }
                                if (ip != null && cont.isActive) cont.resume(ip)
                            }
                        })
                    }
                    override fun onServiceLost(info: NsdServiceInfo) {}
                }
                nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                cont.invokeOnCancellation {
                    runCatching { nsd.stopServiceDiscovery(listener) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ThiraiTv"
        private const val PREFS = "thirai_prefs"
        private const val PREF_MANUAL_IP = "manual_tv_ip"
        private const val PREF_LAST_IP = "last_tv_ip"
        private const val PREF_PAIRED = "tv_paired"
        private const val PREF_TV_NAME = "tv_name"
        private const val SERVICE_TYPE = "_androidtvremote2._tcp."

        private const val FOREGROUND_TIMEOUT_MS = 12000L
        private const val SETTLE_AFTER_FOREGROUND_MS = 4500L
        private const val BLIND_SETTLE_MS = 8000L
        private const val PRESS_GAP_MS = 2500L
    }
}
