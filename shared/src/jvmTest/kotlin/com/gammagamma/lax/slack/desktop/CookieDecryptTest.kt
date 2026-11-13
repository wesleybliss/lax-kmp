package com.gammagamma.lax.slack.desktop

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class CookieDecryptTest {
    @Test
    fun roundTripWithHostKeyHashPrefix() {
        val password = "hunter2"
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val key = factory.generateSecret(
            PBEKeySpec(password.toCharArray(), "saltysalt".toByteArray(), 1, 128),
        ).encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(".slack.com".toByteArray())
        val plain = digest + "xoxd-test".toByteArray() + ByteArray(7) { 7 }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(ByteArray(16) { ' '.code.toByte() }),
        )
        val ct = cipher.doFinal(plain)
        val out = CookieDecrypt.decryptCbc(ct, ".slack.com", password, 1)
        assertEquals("xoxd-test", out)
    }
}
