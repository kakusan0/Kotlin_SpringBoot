package com.example.demo.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val PREFIX = "enc:"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private val secureRandom = SecureRandom()

    @Volatile
    private var keyBytes: ByteArray? = null

    fun setKey(rawKey: String?) {
        keyBytes = if (rawKey.isNullOrBlank()) {
            null
        } else {
            deriveKey(rawKey.trim())
        }
    }

    fun encrypt(value: String?): String? {
        if (value == null) {
            return null
        }
        if (value.startsWith(PREFIX)) {
            return value
        }
        val key = keyBytes ?: return value
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return PREFIX + Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(value: String?): String? {
        if (value == null) {
            return null
        }
        if (!value.startsWith(PREFIX)) {
            return value
        }
        val key = keyBytes ?: error("Encryption key is not configured")
        val decoded = Base64.getDecoder().decode(value.removePrefix(PREFIX))
        require(decoded.size > GCM_IV_LENGTH) { "Encrypted payload is too short" }
        val iv = decoded.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = decoded.copyOfRange(GCM_IV_LENGTH, decoded.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun deriveKey(rawKey: String): ByteArray {
        val decoded = try {
            Base64.getDecoder().decode(rawKey)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (decoded != null && (decoded.size == 16 || decoded.size == 24 || decoded.size == 32)) {
            return decoded
        }
        return MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(StandardCharsets.UTF_8))
    }
}
