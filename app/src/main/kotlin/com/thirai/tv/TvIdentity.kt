package com.thirai.tv

import android.content.Context
import android.util.Log
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.asn1.x500.X500Name
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
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * The phone's stable client identity for the Android TV Remote protocol.
 *
 * The protocol authenticates the phone by a self-signed client certificate: the
 * TV records it as trusted once, during PIN pairing, and thereafter accepts any
 * TLS connection presenting the same certificate — no developer mode, no ADB.
 * So this certificate IS the pairing credential. It is generated once and
 * persisted in a PKCS12 keystore in the app's private storage; the same identity
 * is reused for every TV, and it survives the TV's IP changing (trust is tied to
 * the cert, not the address).
 */
class TvIdentity(context: Context) {

    private val keystoreFile = File(context.filesDir, "tv_identity.p12")
    private val password = "thirai".toCharArray()
    private val alias = "thirai"

    private val keyStore: KeyStore = loadOrCreate()

    val certificate: X509Certificate =
        keyStore.getCertificate(alias) as X509Certificate

    private val privateKey: PrivateKey =
        keyStore.getKey(alias, password) as PrivateKey

    private fun loadOrCreate(): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        if (keystoreFile.exists()) {
            try {
                keystoreFile.inputStream().use { ks.load(it, password) }
                if (ks.containsAlias(alias)) return ks
            } catch (e: Exception) {
                Log.w(TAG, "Could not load TV identity, regenerating: ${e.message}")
            }
        }
        ks.load(null, null)
        val (cert, key) = generateSelfSigned()
        ks.setKeyEntry(alias, key, password, arrayOf(cert))
        keystoreFile.outputStream().use { ks.store(it, password) }
        Log.i(TAG, "Generated new TV client identity")
        return ks
    }

    private fun generateSelfSigned(): Pair<X509Certificate, PrivateKey> {
        val bc = BouncyCastleProvider()
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val subject = X500Name("CN=atvremote/Thirai")
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60 * 60 * 1000)
        val notAfter = Date(now + 10L * 365 * 24 * 60 * 60 * 1000)
        val serial = BigInteger.valueOf(now)

        val builder = JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, kp.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(bc).build(kp.private)
        val cert = JcaX509CertificateConverter().setProvider(bc).getCertificate(builder.build(signer))
        return cert to kp.private
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
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(alias)
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getCertificateChain(alias: String?) = chain
        override fun getPrivateKey(alias: String?) = privateKey
    }

    companion object {
        private const val TAG = "ThiraiTvId"

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
