package com.gammagamma.lax.slack.desktop

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chromium cookie decryption (Linux AES-128-CBC).
 * Key = PBKDF2-HMAC-SHA1(password, "saltysalt", rounds, 16);
 * IV = 16 spaces; plaintext may start with SHA-256(host_key).
 */
internal object CookieDecrypt {
    fun decrypt(encrypted: ByteArray, hostKey: String, password: String, rounds: Int = 1): String {
        require(encrypted.size >= 3 + 16) { "bad format" }
        val version = encrypted.copyOfRange(0, 3).decodeToString()
        require(version == "v10" || version == "v11") { "bad format" }
        return decryptCbc(encrypted.copyOfRange(3, encrypted.size), hostKey, password, rounds)
    }

    /** Try [password] as UTF-8 text and as raw Latin-1 bytes (portal secrets may be binary). */
    fun decryptWithPasswordBytes(
        encrypted: ByteArray,
        hostKey: String,
        password: ByteArray,
        rounds: Int = 1,
    ): String? {
        val asText = password.toString(Charsets.UTF_8)
        val candidates = linkedSetOf(asText, password.toString(Charsets.ISO_8859_1))
        for (pw in candidates) {
            val plain = runCatching { decrypt(encrypted, hostKey, pw, rounds) }.getOrNull()
            if (plain != null && plain.startsWith("xoxd-")) return plain
        }
        return null
    }

    fun decryptCbc(ct: ByteArray, hostKey: String, password: String, rounds: Int): String {
        require(ct.isNotEmpty() && ct.size % 16 == 0) { "bad format" }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val keyBytes = factory.generateSecret(
            PBEKeySpec(password.toCharArray(), "saltysalt".toByteArray(), rounds, 128),
        ).encoded
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            IvParameterSpec(ByteArray(16) { ' '.code.toByte() }),
        )
        val out = cipher.doFinal(ct)
        val pad = out.last().toInt() and 0xff
        require(pad in 1..16 && pad <= out.size) { "decrypt failed" }
        var plain = out.copyOf(out.size - pad)
        val digest = MessageDigest.getInstance("SHA-256").digest(hostKey.toByteArray())
        if (plain.size >= 32 && plain.copyOfRange(0, 32).contentEquals(digest)) {
            plain = plain.copyOfRange(32, plain.size)
        }
        return plain.decodeToString()
    }
}
