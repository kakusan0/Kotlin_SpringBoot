package com.example.demo.mapper

import com.example.demo.model.WebAuthnCredential
import org.apache.ibatis.annotations.*
import org.springframework.stereotype.Repository

@Repository
@Mapper
interface WebAuthnCredentialMapper {
    @Select(
        """
        SELECT id, username, credential_id AS credentialId, public_key_cose AS publicKeyCose,
               sign_count AS signCount, transports, attestation_type AS attestationType,
               aaguid, created_at AS createdAt, updated_at AS updatedAt
        FROM webauthn_credentials
        WHERE username = #{username}
    """
    )
    fun findByUsername(username: String): List<WebAuthnCredential>

    @Select(
        """
        SELECT id, username, credential_id AS credentialId, public_key_cose AS publicKeyCose,
               sign_count AS signCount, transports, attestation_type AS attestationType,
               aaguid, created_at AS createdAt, updated_at AS updatedAt
        FROM webauthn_credentials
        WHERE credential_id = #{credentialId}
    """
    )
    fun findByCredentialId(credentialId: ByteArray): WebAuthnCredential?

    @Insert(
        """
        INSERT INTO webauthn_credentials
        (username, credential_id, public_key_cose, sign_count, transports, attestation_type, aaguid)
        VALUES
        (#{username}, #{credentialId}, #{publicKeyCose}, #{signCount}, #{transports}, #{attestationType}, #{aaguid})
    """
    )
    @Options(useGeneratedKeys = true, keyProperty = "id")
    fun insert(credential: WebAuthnCredential): Int

    @Update(
        """
        UPDATE webauthn_credentials
        SET sign_count = #{signCount}, updated_at = NOW()
        WHERE id = #{id}
    """
    )
    fun updateSignCount(id: Long, signCount: Long): Int

    @Select(
        """
        SELECT id, username, credential_id AS credentialId, public_key_cose AS publicKeyCose,
               sign_count AS signCount, transports, attestation_type AS attestationType,
               aaguid, created_at AS createdAt, updated_at AS updatedAt
        FROM webauthn_credentials
        WHERE id = #{id}
    """
    )
    fun findById(id: Long): WebAuthnCredential?

    @Delete(
        """
        DELETE FROM webauthn_credentials
        WHERE id = #{id}
    """
    )
    fun deleteById(id: Long): Int
}

