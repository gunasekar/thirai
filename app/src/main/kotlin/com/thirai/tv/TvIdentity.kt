package com.thirai.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * The phone's stable client identity for the Android TV Remote protocol.
 *
 * The protocol authenticates the phone by a self-signed client certificate: the
 * TV records it as trusted once, during PIN pairing, and thereafter accepts any
 * TLS connection presenting the same certificate — no developer mode, no ADB.
 *
 * The keypair and its self-signed certificate are generated and held in the
 * **Android Keystore**: the private key is hardware-backed and never leaves it
 * (signing for TLS happens inside the keystore), and no third-party crypto
 * library is needed. The same identity is reused for every TV and survives the
 * TV's IP changing (trust is tied to the cert, not the address).
 */
class TvIdentity(context: Context) {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    val certificate: X509Certificate
    private val privateKey: PrivateKey

    init {
        // One-time cleanup of the pre-keystore identity file, if present.
        File(context.filesDir, "tv_identity.p12").delete()
        if (!keyStore.containsAlias(ALIAS)) generate()
        certificate = keyStore.getCertificate(ALIAS) as X509Certificate
        privateKey = keyStore.getKey(ALIAS, null) as PrivateKey
    }

    private fun generate() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            // SIGN/VERIFY alone is not enough: Conscrypt computes the TLS client
            // CertificateVerify signature as a *raw RSA operation through Cipher*
            // (a private-key encrypt/decrypt of the pre-padded digest), not via
            // the Signature API. Without DECRYPT/ENCRYPT purpose the handshake
            // dies with KeyStoreException: INCOMPATIBLE_PADDING_MODE.
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(2048)
            // Allow the digests and paddings a TLS handshake might pick for the
            // client CertificateVerify — PKCS#1 (TLS 1.2) and PSS (TLS 1.3), plus
            // DIGEST_NONE for Conscrypt's raw pre-hashed signing path.
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512,
                KeyProperties.DIGEST_SHA1,
            )
            .setSignaturePaddings(
                KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                KeyProperties.SIGNATURE_PADDING_RSA_PSS,
            )
            // The raw-RSA Cipher path uses NoPadding (Conscrypt pads the digest
            // itself); PKCS#1 is allowed too. NONE padding is deterministic, so
            // randomized encryption must be disabled or the builder rejects it.
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE,
                KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
            )
            .setRandomizedEncryptionRequired(false)
            .setCertificateSubject(X500Principal("CN=atvremote/Thirai"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - 24L * 60 * 60 * 1000))
            .setCertificateNotAfter(Date(now + 10L * 365 * 24 * 60 * 60 * 1000))
            .build()
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        kpg.initialize(spec)
        kpg.generateKeyPair()
        Log.i(TAG, "Generated Android Keystore TV client identity")
    }

    /**
     * A TLS context that presents this client certificate and trusts any server
     * certificate. Trusting all servers is correct here: the TV's certificate is
     * self-signed and unknown ahead of time, and the protocol's own SHA-256
     * pairing secret — computed over both certificates and the on-screen PIN — is
     * what actually authenticates the TV.
     */
    fun sslContext(): SSLContext {
        val keyManagers = arrayOf<KeyManager>(ForcingKeyManager())
        val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers, trustAll, SecureRandom())
        }
    }

    /**
     * Always offer our single client certificate, regardless of the CA names the
     * TV advertises in its CertificateRequest. The default key manager filters by
     * issuer and would decline to send a self-signed cert; the TV needs to see it.
     */
    private inner class ForcingKeyManager : X509ExtendedKeyManager() {
        private val chain = arrayOf(certificate)
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?) = chain
        override fun getPrivateKey(alias: String?) = privateKey
    }

    companion object {
        private const val TAG = "ThiraiTvId"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "thirai_tv_identity"

        /** RSA modulus and public exponent of a certificate, for the pairing secret. */
        fun modulusExponent(cert: X509Certificate): Pair<BigInteger, BigInteger> {
            val pub = cert.publicKey as RSAPublicKey
            return pub.modulus to pub.publicExponent
        }
    }
}

/** The TV's certificate presented on this TLS session (for the pairing secret). */
fun SSLSocket.peerCertificate(): X509Certificate =
    session.peerCertificates[0] as X509Certificate
