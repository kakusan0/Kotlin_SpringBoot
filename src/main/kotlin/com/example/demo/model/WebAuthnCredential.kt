package com.example.demo.model

import java.time.OffsetDateTime

/** WebAuthn登録済みクレデンシャル */
data class WebAuthnCredential(
    val id: Long? = null,
    val username: String,
    val credentialId: ByteArray,
    val publicKeyCose: ByteArray,
    val signCount: Long,
    val transports: String?,
    val attestationType: String?,
    val aaguid: String?,
    val createdAt: OffsetDateTime? = null,
    val updatedAt: OffsetDateTime? = null,
)

