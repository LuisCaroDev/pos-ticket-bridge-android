package com.luiscarodev.posticketbridge.bridge.https

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

object HttpsCertificates {
    private const val DAY = 86_400_000L
    private fun pair() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    fun certificate(encoded: String): X509Certificate = CertificateFactory.getInstance("X.509")
        .generateCertificate(Base64.getDecoder().decode(encoded).inputStream()) as X509Certificate
    fun privateKey(encoded: String): PrivateKey = KeyFactory.getInstance("EC")
        .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)))

    fun prepare(current: CertificateMaterial?, address: String, now: Long = System.currentTimeMillis()): CertificateMaterial {
        val caPair = if (current == null) pair() else null
        val caKey = current?.let { privateKey(it.caKey) } ?: caPair!!.private
        val ca = current?.let { certificate(it.ca) } ?: run {
            val name = X500Name("CN=POS Ticket Bridge ${UUID.randomUUID()} mobile")
            val builder = JcaX509v3CertificateBuilder(name, serial(), Date(now - 300_000), Date(now + 3650 * DAY), name, caPair!!.public)
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(0))
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
            JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(caKey)))
        }
        require(ca.basicConstraints >= 0) { "https_store_invalid" }
        ca.verify(ca.publicKey)
        require(matches(caKey, ca)) { "https_store_invalid" }
        require(now in ca.notBefore.time until ca.notAfter.time) { "https_ca_expired" }
        if (current != null) {
            val valid = runCatching {
                val cert = certificate(current.certificate)
                cert.verify(ca.publicKey)
                cert.basicConstraints == -1 && matches(privateKey(current.key), cert) &&
                    cert.subjectAlternativeNames?.any { it[0] == 7 && it[1] == address } == true &&
                    cert.extendedKeyUsage?.contains("1.3.6.1.5.5.7.3.1") == true && cert.keyUsage?.get(0) == true &&
                    now >= cert.notBefore.time && now < cert.notAfter.time &&
                    (cert.notAfter.time > now + 30 * DAY || cert.notAfter == ca.notAfter) &&
                    cert.notAfter.time <= ca.notAfter.time && cert.notAfter.time - cert.notBefore.time <= 365 * DAY
            }.getOrDefault(false)
            if (valid) return current
        }
        val leafPair = pair()
        val start = maxOf(now - 300_000, ca.notBefore.time)
        val builder = JcaX509v3CertificateBuilder(X500Name(ca.subjectX500Principal.name), serial(), Date(start),
            Date(minOf(start + 365 * DAY, ca.notAfter.time)), X500Name("CN=$address"), leafPair.public)
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        builder.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
        builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(GeneralName(GeneralName.iPAddress, address)))
        val leaf = JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(caKey)))
        return CertificateMaterial(encode(ca.encoded), encode(caKey.encoded), encode(leaf.encoded), encode(leafPair.private.encoded))
    }

    private fun serial() = BigInteger(120, SecureRandom()).add(BigInteger.ONE)
    private fun matches(key: PrivateKey, cert: X509Certificate): Boolean {
        val challenge = ByteArray(32).also(SecureRandom()::nextBytes)
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(challenge) }.sign()
        return Signature.getInstance("SHA256withECDSA").apply { initVerify(cert.publicKey); update(challenge) }.verify(signature)
    }

    fun keyStore(material: CertificateMaterial): KeyStore = KeyStore.getInstance("PKCS12").apply {
        load(null, null)
        setKeyEntry("bridge", privateKey(material.key), CharArray(0), arrayOf(certificate(material.certificate), certificate(material.ca)))
    }

    fun clientContext(ca: String): SSLContext {
        val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null); setCertificateEntry("bridge", certificate(ca)) }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
        return SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }
    }

    fun fingerprint(ca: String): String = MessageDigest.getInstance("SHA-256").digest(certificate(ca).encoded)
        .joinToString(":") { "%02X".format(it) }

    fun iosProfile(ca: String): ByteArray {
        val identity = fingerprint(ca).replace(":", "").lowercase()
        return """<?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0"><dict><key>PayloadType</key><string>Configuration</string>
            <key>PayloadVersion</key><integer>1</integer><key>PayloadIdentifier</key><string>com.pos.ticketbridge.mobile.$identity</string>
            <key>PayloadUUID</key><string>${UUID.randomUUID()}</string><key>PayloadDisplayName</key><string>POS Ticket Bridge mobile</string>
            <key>PayloadContent</key><array><dict><key>PayloadType</key><string>com.apple.security.root</string>
            <key>PayloadVersion</key><integer>1</integer><key>PayloadIdentifier</key><string>com.pos.ticketbridge.mobile.root.$identity</string>
            <key>PayloadUUID</key><string>${UUID.randomUUID()}</string><key>PayloadDisplayName</key><string>POS Ticket Bridge mobile</string>
            <key>PayloadContent</key><data>$ca</data></dict></array></dict></plist>""".trimIndent().toByteArray()
    }
}
