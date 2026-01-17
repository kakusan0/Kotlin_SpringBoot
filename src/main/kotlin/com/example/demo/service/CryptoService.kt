package com.example.demo.service

import com.example.demo.util.EncryptionUtils
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class CryptoService(
    @Value("\${app.encryption.key:}") private val rawKey: String?
) {
    @PostConstruct
    fun init() {
        EncryptionUtils.setKey(rawKey)
    }

    fun encrypt(value: String?): String? = EncryptionUtils.encrypt(value)

    fun decrypt(value: String?): String? = EncryptionUtils.decrypt(value)
}
