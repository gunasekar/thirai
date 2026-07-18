package com.thirai

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import android.util.Log
import com.tananaev.adblib.AdbBase64
import com.tananaev.adblib.AdbConnection
import com.tananaev.adblib.AdbCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Socket
import kotlin.coroutines.resume

class AdbController(private val context: Context) {

    companion object {
        private const val TAG = "ThiraiADB"
        private const val TV_PORT = 5555
        private const val SERVICE_TYPE = "_adb._tcp."
        private const val PREFS_NAME = "thirai_prefs"
        private const val PREF_LAST_IP = "last_tv_ip"
    }

    private val adbBase64 = object : AdbBase64 {
        override fun encodeToString(data: ByteArray): String {
            return Base64.encodeToString(data, Base64.NO_WRAP)
        }
    }

    suspend fun triggerTvPlayback(url: String) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var tvIp = prefs.getString(PREF_LAST_IP, null)

            // Step 1: Try the cached IP first (The "Hybrid" approach for instant execution)
            if (tvIp != null) {
                Log.i(TAG, "Trying cached IP: $tvIp")
                val success = attemptExecution(tvIp, url)
                if (success) return@withContext
                Log.w(TAG, "Cached IP failed. TV might have changed IP. Falling back to Auto-Discovery...")
            } else {
                Log.i(TAG, "No cached IP found. Starting Auto-Discovery...")
            }

            // Step 2: Auto Discovery Fallback
            tvIp = discoverTvIp()
            
            if (tvIp != null) {
                Log.i(TAG, "Auto-Discovery found TV at: $tvIp. Saving to cache.")
                prefs.edit().putString(PREF_LAST_IP, tvIp).apply()
                
                // Execute on the newly discovered IP
                attemptExecution(tvIp, url)
            } else {
                Log.e(TAG, "Auto-Discovery failed to find the TV on the network.")
            }
        }
    }

    private fun attemptExecution(ip: String, url: String): Boolean {
        var socket: Socket? = null
        var connection: AdbConnection? = null
        
        return try {
            val crypto = AdbCrypto.generateAdbKeyPair(adbBase64)

            socket = Socket(ip, TV_PORT)
            socket.soTimeout = 3000 // Short 3-second timeout so it fails fast if IP is wrong

            connection = AdbConnection.create(socket, crypto)
            connection.connect()

            val command = "shell:am start -a android.intent.action.VIEW -d \"$url\" com.hotstar.tv"
            val stream = connection.open(command)
            Thread.sleep(500)
            
            Log.i(TAG, "Command executed successfully on $ip!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Execution failed on $ip: ${e.message}")
            false
        } finally {
            try {
                connection?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing connection", e)
            }
        }
    }

    private suspend fun discoverTvIp(): String? = withContext(Dispatchers.Main) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        
        // We use a 10-second timeout for the discovery process
        withTimeoutOrNull(10000) {
            suspendCancellableCoroutine<String> { continuation ->
                var discoveryListener: NsdManager.DiscoveryListener? = null

                discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
                        if (continuation.isActive) continuation.resume(null.toString())
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
                    }

                    override fun onDiscoveryStarted(serviceType: String) {
                        Log.d(TAG, "Scanning network for TV...")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        Log.d(TAG, "Discovery stopped")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Found service: ${serviceInfo.serviceName}")
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val ip = serviceInfo.host.hostAddress
                                Log.i(TAG, "Resolved TV IP: $ip")
                                discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }

                                if (continuation.isActive) {
                                    continuation.resume(ip)
                                }
                            }
                        })
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                }

                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

                continuation.invokeOnCancellation {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                }
            }
        }?.takeIf { it != "null" }
    }
}
