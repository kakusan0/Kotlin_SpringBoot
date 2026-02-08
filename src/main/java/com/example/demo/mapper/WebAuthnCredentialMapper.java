package com.example.demo.mapper;

import com.example.demo.model.WebAuthnCredential;
import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Mapper
public interface WebAuthnCredentialMapper {

    @Select("""
                SELECT id, username, credential_id AS credentialId, public_key_cose AS publicKeyCose,
                       sign_count AS signCount, transports, attestation_type AS attestationType,
                       aaguid, created_at AS createdAt, updated_at AS updatedAt
                FROM webauthn_credentials
                WHERE username = #{username}
            """)
    List<WebAuthnCredential> findByUsername(String username);

    @Select("""
                SELECT id, username, credential_id AS credentialId, public_key_cose AS publicKeyCose,
                       sign_count AS signCount, transports, attestation_type AS attestationType,
                       aaguid, created_at AS createdAt, updated_at AS updatedAt
                FROM webauthn_credentials
                WHERE credential_id = #{credentialId}
            """)
    WebAuthnCredential findByCredentialId(byte[] credentialId);

    @Insert("""
                INSERT INTO webauthn_credentials
                (username, credential_id, public_key_cose, sign_count, transports, attestation_type, aaguid)
                VALUES
                (#{username}, #{credentialId}, #{publicKeyCose}, #{signCount}, #{transports}, #{attestationType}, #{aaguid})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WebAuthnCredential credential);

    @Update("""
                UPDATE webauthn_credentials
                SET sign_count = #{signCount}, updated_at = NOW()
                WHERE id = #{id}
            """)
    int updateSignCount(Long id, Long signCount);

    @Select("""
                SELECT id, username, credential_id AS credentialId, public_key_cose AS publicKeyCose,
                       sign_count AS signCount, transports, attestation_type AS attestationType,
                       aaguid, created_at AS createdAt, updated_at AS updatedAt
                FROM webauthn_credentials
                WHERE id = #{id}
            """)
    WebAuthnCredential findById(Long id);

    @Delete("""
                DELETE FROM webauthn_credentials
                WHERE id = #{id}
            """)
    int deleteById(Long id);
}
