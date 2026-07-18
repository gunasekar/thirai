package com.thirai.tv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import remote.RemoteAppLinkLaunchRequest
import remote.RemoteConfigure
import remote.RemoteDeviceInfo
import remote.RemoteDirection
import remote.RemoteKeyCode
import remote.RemoteKeyInject
import remote.RemoteMessage
import remote.RemotePingResponse
import remote.RemoteSetActive
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket

/**
 * A control session with an Android TV over the Remote protocol (port 6466).
 *
 * Requires the phone's certificate to already be trusted (see [TvPairing]); an
 * untrusted certificate is refused at the TLS layer. A background reader thread
 * completes the configure/set-active/start handshake and — crucially — answers
 * the TV's ping every ~5s, so the connection stays alive through the multi-second
 * "nudge into playback" sequence. Without answered pings the TV drops us after
 * three misses.
 */
class TvRemote(
    private val identity: TvIdentity,
    private val host: String,
) {
    private var socket: SSLSocket? = null
    private var output: OutputStream? = null
    private val writeLock = Any()
    private val readyLatch = CountDownLatch(1)

    @Volatile
    private var readerError: Exception? = null
    private var reader: Thread? = null

    /** The TV's current foreground app package, as reported by the TV, or null. */
    @Volatile
    var currentApp: String? = null
        private set

    /**
     * Connect and block until the TV reports it is ready ([RemoteMessage.remote_start]).
     * @throws Exception if the TLS/handshake fails (e.g. not paired) or times out.
     */
    suspend fun connect() = withContext(Dispatchers.IO) {
        val s = identity.sslContext().socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS)
        s.soTimeout = 0 // reader blocks indefinitely; server pings keep it live
        s.startHandshake()
        socket = s
        output = s.outputStream

        val input = s.inputStream
        reader = Thread({ readLoop(input) }, "thirai-tv-reader").apply {
            isDaemon = true
            start()
        }

        if (!readyLatch.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            val err = readerError
            close()
            throw err ?: java.io.IOException("TV did not become ready in time")
        }
        readerError?.let { close(); throw it }
    }

    fun launchAppLink(url: String) {
        send(RemoteMessage(remote_app_link_launch_request = RemoteAppLinkLaunchRequest(app_link = url)))
    }

    fun sendKey(keyCode: RemoteKeyCode) {
        send(
            RemoteMessage(
                remote_key_inject = RemoteKeyInject(key_code = keyCode, direction = RemoteDirection.SHORT),
            ),
        )
    }

    /**
     * Suspend until [pkg] becomes the TV's foreground app, or [timeoutMs] passes.
     * Returns true if it was seen. Depends on the TV emitting foreground-app
     * updates (the IME feature); if it never does, this simply times out and the
     * caller falls back to a fixed wait.
     */
    suspend fun awaitForeground(pkg: String, timeoutMs: Long): Boolean {
        var waited = 0L
        val step = 200L
        while (waited < timeoutMs) {
            if (currentApp == pkg) return true
            delay(step)
            waited += step
        }
        return currentApp == pkg
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private fun readLoop(input: InputStream) {
        try {
            while (true) {
                val bytes = AtvFraming.readMessage(input) ?: break
                handle(RemoteMessage.ADAPTER.decode(bytes))
            }
        } catch (e: Exception) {
            if (socket != null) { // ignore errors caused by our own close()
                readerError = e
                Log.w(TAG, "Reader stopped: ${e.message}")
            }
            readyLatch.countDown()
        }
    }

    private fun handle(msg: RemoteMessage) {
        when {
            msg.remote_configure != null -> send(
                RemoteMessage(
                    remote_configure = RemoteConfigure(
                        code1 = ACTIVE_FEATURES,
                        device_info = RemoteDeviceInfo(
                            model = "Thirai",
                            vendor = "Thirai",
                            unknown1 = 1,
                            unknown2 = "1",
                            package_name = "atvremote",
                            app_version = "1.0.0",
                        ),
                    ),
                ),
            )
            msg.remote_set_active != null -> send(
                RemoteMessage(remote_set_active = RemoteSetActive(active = ACTIVE_FEATURES)),
            )
            msg.remote_start != null -> readyLatch.countDown()
            msg.remote_ping_request != null -> send(
                RemoteMessage(remote_ping_response = RemotePingResponse(val1 = msg.remote_ping_request!!.val1)),
            )
            // The TV reports the foreground app via IME focus messages; track it
            // so playback can wait for the target app instead of guessing a delay.
            msg.remote_ime_key_inject != null ->
                msg.remote_ime_key_inject!!.app_info?.app_package
                    ?.takeIf { it.isNotBlank() }
                    ?.let { currentApp = it }
        }
    }

    private fun send(msg: RemoteMessage) {
        val out = output ?: return
        synchronized(writeLock) {
            AtvFraming.writeMessage(out, RemoteMessage.ADAPTER.encode(msg))
        }
    }

    companion object {
        private const val TAG = "ThiraiTvRemote"
        const val PORT = 6466
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READY_TIMEOUT_MS = 12000L

        // Features we advertise as active: PING(1) | KEY(2) | IME(4) | POWER(32)
        // | VOLUME(64) | APP_LINK(512) = 615. IME is what makes the TV report its
        // foreground app, which playback uses to time its key presses.
        private const val ACTIVE_FEATURES = 615
    }
}
