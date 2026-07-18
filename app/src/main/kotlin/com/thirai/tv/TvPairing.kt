package com.thirai.tv

import android.util.Log
import com.google.polo.wire.protobuf.Configuration
import com.google.polo.wire.protobuf.Options
import com.google.polo.wire.protobuf.OuterMessage
import com.google.polo.wire.protobuf.PairingRequest
import com.google.polo.wire.protobuf.Secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

class PairingException(message: String) : IOException(message)

/**
 * One-time pairing with an Android TV over the Polo protocol (port 6467).
 *
 * Two steps, with the user in the middle:
 *  1. [start] performs the TLS handshake and the option/configuration exchange.
 *     When it returns, the TV is displaying a 6-character hex PIN.
 *  2. [finish] takes that PIN, derives the shared secret from both certificates
 *     and the code, and sends it. On success the TV permanently trusts this
 *     phone's certificate.
 *
 * The socket is held open between the two steps, so keep the instance alive
 * across the PIN prompt and call [close] when done.
 */
class TvPairing(
    private val identity: TvIdentity,
    private val host: String,
    private val clientName: String = "Thirai",
) {
    private var socket: SSLSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    suspend fun start() = withContext(Dispatchers.IO) {
        val s = identity.sslContext().socketFactory.createSocket() as SSLSocket
        s.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT_MS)
        s.soTimeout = READ_TIMEOUT_MS
        s.startHandshake()
        socket = s
        input = s.inputStream
        output = s.outputStream

        send(
            outer(
                pairing_request = PairingRequest(service_name = "atvremote", client_name = clientName),
            ),
        )

        // Drive the exchange until the TV acknowledges our configuration, at
        // which point it shows the PIN and we hand back to the UI.
        while (true) {
            val msg = recv() ?: throw PairingException("TV closed the pairing connection")
            if (msg.status != OuterMessage.Status.STATUS_OK) {
                throw PairingException("Pairing failed with status ${msg.status}")
            }
            when {
                msg.pairing_request_ack != null -> send(
                    outer(
                        options = Options(
                            input_encodings = listOf(hexEncoding()),
                            preferred_role = Options.RoleType.ROLE_TYPE_INPUT,
                        ),
                    ),
                )
                msg.options != null -> send(
                    outer(
                        configuration = Configuration(
                            encoding = hexEncoding(),
                            client_role = Options.RoleType.ROLE_TYPE_INPUT,
                        ),
                    ),
                )
                msg.configuration_ack != null -> return@withContext // PIN is now on screen
                else -> throw PairingException("Unexpected pairing message from TV")
            }
        }
    }

    suspend fun finish(pin: String): Unit = withContext(Dispatchers.IO) {
        val s = socket ?: throw PairingException("Pairing not started")
        val code = pin.trim().lowercase()
        if (code.length != 6 || code.any { it !in "0123456789abcdef" }) {
            throw PairingException("The code should be the 6 characters shown on the TV")
        }
        val secret = computeSecret(identity.certificate, s.peerCertificate(), code)
        send(outer(secret = Secret(secret = secret.toByteString())))
        val ack = recv() ?: throw PairingException("TV closed the connection before confirming")
        if (ack.status != OuterMessage.Status.STATUS_OK || ack.secret_ack == null) {
            throw PairingException("Wrong code — check the PIN on the TV and try again")
        }
        Log.i(TAG, "Paired with TV at $host")
    }

    fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private fun send(msg: OuterMessage) {
        AtvFraming.writeMessage(output!!, OuterMessage.ADAPTER.encode(msg))
    }

    private fun recv(): OuterMessage? {
        val bytes = AtvFraming.readMessage(input!!) ?: return null
        return OuterMessage.ADAPTER.decode(bytes)
    }

    companion object {
        private const val TAG = "ThiraiTvPair"
        const val PORT = 6467
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 15000

        private fun outer(
            pairing_request: PairingRequest? = null,
            options: Options? = null,
            configuration: Configuration? = null,
            secret: Secret? = null,
        ) = OuterMessage(
            protocol_version = 2,
            status = OuterMessage.Status.STATUS_OK,
            pairing_request = pairing_request,
            options = options,
            configuration = configuration,
            secret = secret,
        )

        private fun hexEncoding() = Options.Encoding(
            type = Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL,
            symbol_length = 6,
        )

        /**
         * The Polo pairing secret: SHA-256 over the client and server RSA public
         * key (modulus then exponent, each as raw bytes) followed by the PIN's
         * payload nibbles. The PIN's first byte is a checksum of the digest, which
         * we verify so a mistyped code fails locally instead of on the TV.
         */
        private fun computeSecret(
            clientCert: X509Certificate,
            serverCert: X509Certificate,
            code: String,
        ): ByteArray {
            val (cn, ce) = TvIdentity.modulusExponent(clientCert)
            val (sn, se) = TvIdentity.modulusExponent(serverCert)
            val md = MessageDigest.getInstance("SHA-256")
            md.update(hexToBytes(cn.toString(16)))
            md.update(hexToBytes("0" + ce.toString(16)))
            md.update(hexToBytes(sn.toString(16)))
            md.update(hexToBytes("0" + se.toString(16)))
            md.update(hexToBytes(code.substring(2)))
            val digest = md.digest()
            val expected = code.substring(0, 2).toInt(16)
            if ((digest[0].toInt() and 0xFF) != expected) {
                throw PairingException("Wrong code — check the PIN on the TV and try again")
            }
            return digest
        }

        private fun hexToBytes(hexIn: String): ByteArray {
            val hex = if (hexIn.length % 2 != 0) "0$hexIn" else hexIn
            val out = ByteArray(hex.length / 2)
            for (i in out.indices) {
                out[i] = ((hex[i * 2].digitToInt(16) shl 4) or hex[i * 2 + 1].digitToInt(16)).toByte()
            }
            return out
        }
    }
}
