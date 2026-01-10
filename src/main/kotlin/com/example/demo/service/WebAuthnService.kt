package com.example.demo.service

import com.example.demo.mapper.WebAuthnCredentialMapper
import com.example.demo.model.WebAuthnCredential
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.*
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.util.Base64UrlUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

@Service
class WebAuthnService(
    private val credentialMapper: WebAuthnCredentialMapper
) {
    private val logger = LoggerFactory.getLogger(WebAuthnService::class.java)
    private val random = SecureRandom()
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()
    private val objectConverter = ObjectConverter()
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

    @Value("\${webauthn.rp.id:localhost}")
    private lateinit var rpId: String

    @Value("\${webauthn.rp.origin:http://localhost:8080}")
    private lateinit var rpOrigin: String

    // チャレンジは有効期限付きで保存すべきだが、簡易実装としてConcurrentHashMapを使用
    private val registrationChallenges = ConcurrentHashMap<String, ChallengeData>()
    private val authenticationChallenges = ConcurrentHashMap<String, ChallengeData>()

    data class ChallengeData(
        val challenge: ByteArray,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        // チャレンジの有効期限（5分）
        fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > 5 * 60 * 1000
    }

    fun generateChallenge(): ByteArray {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes
    }

    fun saveRegistrationChallenge(username: String, challenge: ByteArray) {
        registrationChallenges[username] = ChallengeData(challenge)
    }

    fun consumeRegistrationChallenge(username: String): ByteArray? {
        val data = registrationChallenges.remove(username) ?: return null
        return if (data.isExpired()) null else data.challenge
    }

    fun saveAuthenticationChallenge(username: String, challenge: ByteArray) {
        authenticationChallenges[username] = ChallengeData(challenge)
    }

    fun consumeAuthenticationChallenge(username: String): ByteArray? {
        val data = authenticationChallenges.remove(username) ?: return null
        return if (data.isExpired()) null else data.challenge
    }

    fun findCredentials(username: String): List<WebAuthnCredential> = credentialMapper.findByUsername(username)

    fun findByCredentialId(credentialId: ByteArray): WebAuthnCredential? =
        credentialMapper.findByCredentialId(credentialId)

    fun findCredentialById(id: Long): WebAuthnCredential? = credentialMapper.findById(id)

    fun deleteCredential(id: Long) {
        credentialMapper.deleteById(id)
        logger.info("Deleted WebAuthn credential with id: $id")
    }

    /**
     * パスキー登録の検証と保存
     */
    fun registerCredential(
        username: String,
        challenge: ByteArray,
        clientDataJSON: ByteArray,
        attestationObject: ByteArray
    ): WebAuthnCredential {
        val origin = Origin.create(rpOrigin)

        @Suppress("DEPRECATION")
        val serverProperty = ServerProperty(
            origin,
            rpId,
            DefaultChallenge(challenge),
            null // tokenBinding
        )

        val registrationRequest = RegistrationRequest(
            attestationObject,
            clientDataJSON
        )

        val registrationParameters = RegistrationParameters(
            serverProperty,
            null, // pubKeyCredParams - nullで全て許可
            false, // userVerificationRequired
            false  // userPresenceRequired
        )

        // WebAuthn4jで検証
        val registrationData = webAuthnManager.parse(registrationRequest)
        @Suppress("DEPRECATION")
        webAuthnManager.validate(registrationData, registrationParameters)

        val attestedCredentialData = registrationData.attestationObject!!.authenticatorData.attestedCredentialData
            ?: throw IllegalStateException("Attested credential data is null")

        val credentialId = attestedCredentialData.credentialId
        val publicKeyCose = attestedCredentialDataConverter.convert(attestedCredentialData)
        val aaguid = attestedCredentialData.aaguid.toString()
        val signCount = registrationData.attestationObject!!.authenticatorData.signCount

        val credential = WebAuthnCredential(
            username = username,
            credentialId = credentialId,
            publicKeyCose = publicKeyCose,
            signCount = signCount,
            transports = null,
            attestationType = registrationData.attestationObject!!.attestationStatement.format,
            aaguid = aaguid
        )
        credentialMapper.insert(credential)
        logger.info(
            "WebAuthn credential registered for user: $username, credentialId: ${
                Base64UrlUtil.encodeToString(
                    credentialId
                )
            }"
        )
        return credential
    }

    /**
     * パスキー認証の検証
     */
    fun verifyAssertion(
        credential: WebAuthnCredential,
        challenge: ByteArray,
        credentialId: ByteArray,
        clientDataJSON: ByteArray,
        authenticatorData: ByteArray,
        signature: ByteArray,
        userHandle: ByteArray?
    ): Boolean {
        val origin = Origin.create(rpOrigin)

        @Suppress("DEPRECATION")
        val serverProperty = ServerProperty(
            origin,
            rpId,
            DefaultChallenge(challenge),
            null // tokenBinding
        )

        val authenticationRequest = AuthenticationRequest(
            credentialId,
            userHandle,
            authenticatorData,
            clientDataJSON,
            null, // clientExtensionsJSON
            signature
        )

        val credentialRecord = buildCredentialRecord(credential)

        @Suppress("DEPRECATION")
        val authenticationParameters = AuthenticationParameters(
            serverProperty,
            credentialRecord,
            null, // allowCredentials
            false, // userVerificationRequired
            false  // userPresenceRequired
        )

        // WebAuthn4jで検証
        val authenticationData = webAuthnManager.parse(authenticationRequest)
        @Suppress("DEPRECATION")
        webAuthnManager.validate(authenticationData, authenticationParameters)

        // signCountを更新
        val newSignCount = authenticationData.authenticatorData!!.signCount
        if (newSignCount > credential.signCount) {
            credentialMapper.updateSignCount(credential.id!!, newSignCount)
            logger.debug("Updated signCount for credential ${credential.id}: ${credential.signCount} -> $newSignCount")
        } else if (newSignCount > 0 && newSignCount <= credential.signCount) {
            // signCountが減少または同じ場合はクローン検出の可能性（警告ログ）
            logger.warn(
                "Possible cloned authenticator detected for user ${credential.username}. " +
                        "Expected signCount > ${credential.signCount}, got $newSignCount"
            )
        }

        logger.info("WebAuthn authentication successful for user: ${credential.username}")
        return true
    }

    private fun buildCredentialRecord(credential: WebAuthnCredential): CredentialRecord {
        // 古い形式（空のByteArray）で保存されたクレデンシャルのチェック
        if (credential.publicKeyCose.isEmpty()) {
            throw IllegalStateException(
                "Invalid credential: public key is empty. " +
                        "This credential was registered with an old implementation. " +
                        "Please re-register the passkey."
            )
        }

        val attestedCredentialData = try {
            attestedCredentialDataConverter.convert(credential.publicKeyCose)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to parse credential public key: ${e.message}. " +
                        "Please re-register the passkey.", e
            )
        }
        return CredentialRecordImpl(
            null, // attestationStatement
            null, // uvInitialized
            null, // backupEligible
            null, // backupState
            credential.signCount,
            attestedCredentialData,
            null, // authenticatorExtensions
            null, // collectedClientData
            null, // clientExtensions
            null  // transports
        )
    }
}

