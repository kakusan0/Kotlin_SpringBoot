package com.example.demo.controller

import com.example.demo.service.WebAuthnService
import com.webauthn4j.util.Base64UrlUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webauthn")
class WebAuthnController(
    private val webAuthnService: WebAuthnService,
    private val userDetailsService: UserDetailsService,
    private val sessionAuthenticationStrategy: SessionAuthenticationStrategy
) {
    private val logger = LoggerFactory.getLogger(WebAuthnController::class.java)

    @Value("\${webauthn.rp.id:localhost}")
    private lateinit var rpId: String

    @Value("\${webauthn.rp.name:Dev RP}")
    private lateinit var rpName: String

    data class RegistrationOptionsResponse(
        val challenge: String,
        val rp: Rp,
        val user: User,
        val pubKeyCredParams: List<PubKeyParam>,
        val timeout: Long = 60000,
        val attestation: String = "none",
        val authenticatorSelection: AuthenticatorSelection = AuthenticatorSelection()
    )

    data class Rp(val id: String, val name: String)
    data class User(val id: String, val name: String, val displayName: String)
    data class PubKeyParam(val type: String, val alg: Int)
    data class AuthenticatorSelection(
        val authenticatorAttachment: String? = null,
        val residentKey: String = "required",  // Discoverable Credential必須
        val requireResidentKey: Boolean = true,
        val userVerification: String = "preferred"
    )

    data class FinishRegistrationRequest(
        val id: String,
        val rawId: String,
        val type: String,
        val response: RegistrationResponse
    )

    data class RegistrationResponse(
        val attestationObject: String,
        val clientDataJSON: String
    )

    // ユーザー名なし認証用のレスポンス（allowCredentials空）
    data class DiscoverableAuthenticationOptionsResponse(
        val challenge: String,
        val rpId: String,
        val timeout: Long = 60000,
        val userVerification: String = "preferred"
    )

    data class AuthenticationOptionsResponse(
        val challenge: String,
        val rpId: String,
        val allowCredentials: List<AllowCredential>,
        val timeout: Long = 60000,
        val userVerification: String = "preferred"
    )

    data class AllowCredential(val type: String, val id: String, val transports: List<String> = emptyList())

    data class FinishAuthenticationRequest(
        val id: String,
        val rawId: String,
        val type: String,
        val response: AuthenticationResponse
    )

    data class AuthenticationResponse(
        val authenticatorData: String,
        val clientDataJSON: String,
        val signature: String,
        val userHandle: String?
    )

    @GetMapping("/registration/options")
    fun registrationOptions(@RequestParam username: String): ResponseEntity<Any> {
        // ユーザーが存在するか確認
        try {
            userDetailsService.loadUserByUsername(username)
        } catch (e: UsernameNotFoundException) {
            logger.warn("Registration attempted for non-existent user: $username")
            return ResponseEntity.badRequest().body(mapOf("message" to "User not found"))
        }

        val challenge = webAuthnService.generateChallenge()
        webAuthnService.saveRegistrationChallenge(username, challenge)

        val rp = Rp(id = rpId, name = rpName)
        val user = User(
            id = Base64UrlUtil.encodeToString(username.toByteArray()),
            name = username,
            displayName = username
        )
        val params = listOf(
            PubKeyParam(type = "public-key", alg = -7),   // ES256
            PubKeyParam(type = "public-key", alg = -257)  // RS256
        )
        val res = RegistrationOptionsResponse(
            challenge = Base64UrlUtil.encodeToString(challenge),
            rp = rp,
            user = user,
            pubKeyCredParams = params
        )
        return ResponseEntity.ok(res)
    }

    @PostMapping("/registration/finish")
    fun finishRegistration(
        @RequestParam username: String,
        @RequestBody req: FinishRegistrationRequest
    ): ResponseEntity<Any> {
        val challenge = webAuthnService.consumeRegistrationChallenge(username)
            ?: return ResponseEntity.badRequest().body(mapOf("message" to "Challenge not found or expired"))

        return try {
            val clientDataJSON = Base64UrlUtil.decode(req.response.clientDataJSON)
            val attestationObject = Base64UrlUtil.decode(req.response.attestationObject)

            webAuthnService.registerCredential(
                username = username,
                challenge = challenge,
                clientDataJSON = clientDataJSON,
                attestationObject = attestationObject
            )
            logger.info("Passkey registered successfully for user: $username")
            ResponseEntity.ok(mapOf("message" to "registered"))
        } catch (e: Exception) {
            logger.error("Failed to register passkey for user $username: ${e.message}", e)
            ResponseEntity.badRequest().body(mapOf("message" to "Registration failed: ${e.message}"))
        }
    }

    @GetMapping("/authentication/options")
    fun authenticationOptions(@RequestParam(required = false) username: String?): ResponseEntity<Any> {
        val challenge = webAuthnService.generateChallenge()

        // ユーザー名が指定されていない場合はDiscoverable Credential認証
        if (username.isNullOrBlank()) {
            // ユーザー名なしの場合、チャレンジをセッションIDベースで保存
            val challengeId = webAuthnService.saveDiscoverableChallenge(challenge)
            val res = DiscoverableAuthenticationOptionsResponse(
                challenge = Base64UrlUtil.encodeToString(challenge),
                rpId = rpId
            )
            return ResponseEntity.ok(mapOf(
                "challenge" to res.challenge,
                "rpId" to res.rpId,
                "timeout" to res.timeout,
                "userVerification" to res.userVerification,
                "challengeId" to challengeId
            ))
        }

        // ユーザー名が指定されている場合は従来の認証
        val creds = webAuthnService.findCredentials(username)
        if (creds.isEmpty()) {
            return ResponseEntity.status(404).body(mapOf("message" to "No passkeys registered for this user"))
        }

        webAuthnService.saveAuthenticationChallenge(username, challenge)

        val allow = creds.map {
            AllowCredential(
                type = "public-key",
                id = Base64UrlUtil.encodeToString(it.credentialId),
                transports = it.transports?.split(",") ?: emptyList()
            )
        }
        val res = AuthenticationOptionsResponse(
            challenge = Base64UrlUtil.encodeToString(challenge),
            rpId = rpId,
            allowCredentials = allow
        )
        return ResponseEntity.ok(res)
    }

    /**
     * ユーザー名なしパスキー認証（Discoverable Credentials）
     */
    @PostMapping("/authentication/finish/discoverable")
    fun finishDiscoverableAuthentication(
        @RequestParam challengeId: String,
        @RequestBody req: FinishAuthenticationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        val challenge = webAuthnService.consumeDiscoverableChallenge(challengeId)
            ?: return ResponseEntity.badRequest().body(mapOf("message" to "Challenge not found or expired"))

        val credentialId = Base64UrlUtil.decode(req.rawId)
        val credential = webAuthnService.findByCredentialId(credentialId)
            ?: return ResponseEntity.status(404).body(mapOf("message" to "Credential not found"))

        // userHandleからユーザー名を取得して検証
        val userHandle = req.response.userHandle
        if (userHandle.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("message" to "userHandle is required for discoverable credentials"))
        }

        val usernameFromHandle = String(Base64UrlUtil.decode(userHandle), Charsets.UTF_8)
        if (credential.username != usernameFromHandle) {
            logger.warn("Credential ownership mismatch: credential belongs to ${credential.username}, but userHandle indicates $usernameFromHandle")
            return ResponseEntity.status(403).body(mapOf("message" to "Credential does not match userHandle"))
        }

        return try {
            val clientDataJSON = Base64UrlUtil.decode(req.response.clientDataJSON)
            val authenticatorData = Base64UrlUtil.decode(req.response.authenticatorData)
            val signature = Base64UrlUtil.decode(req.response.signature)
            val userHandleBytes = Base64UrlUtil.decode(userHandle)

            // WebAuthn署名検証
            webAuthnService.verifyAssertion(
                credential = credential,
                challenge = challenge,
                credentialId = credentialId,
                clientDataJSON = clientDataJSON,
                authenticatorData = authenticatorData,
                signature = signature,
                userHandle = userHandleBytes
            )

            // Spring Securityの認証セッションを確立
            establishSecuritySession(credential.username, request, response)

            logger.info("Discoverable passkey authentication successful for user: ${credential.username}")
            ResponseEntity.ok(mapOf("message" to "authenticated", "username" to credential.username))
        } catch (e: Exception) {
            logger.error("Discoverable passkey authentication failed: ${e::class.simpleName} - ${e.message}", e)
            val errorMessage = e.message ?: e::class.simpleName ?: "Unknown error"
            ResponseEntity.status(401).body(mapOf("message" to "Authentication failed: $errorMessage"))
        }
    }

    @PostMapping("/authentication/finish")
    fun finishAuthentication(
        @RequestParam username: String,
        @RequestBody req: FinishAuthenticationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        val challenge = webAuthnService.consumeAuthenticationChallenge(username)
            ?: return ResponseEntity.badRequest().body(mapOf("message" to "Challenge not found or expired"))

        val credentialId = Base64UrlUtil.decode(req.rawId)
        val credential = webAuthnService.findByCredentialId(credentialId)
            ?: return ResponseEntity.status(404).body(mapOf("message" to "Credential not found"))

        // クレデンシャルが指定されたユーザーに属しているか確認
        if (credential.username != username) {
            logger.warn("Credential ownership mismatch: expected $username, got ${credential.username}")
            return ResponseEntity.status(403).body(mapOf("message" to "Credential does not belong to user"))
        }

        return try {
            val clientDataJSON = Base64UrlUtil.decode(req.response.clientDataJSON)
            val authenticatorData = Base64UrlUtil.decode(req.response.authenticatorData)
            val signature = Base64UrlUtil.decode(req.response.signature)
            val userHandle = req.response.userHandle?.let { Base64UrlUtil.decode(it) }

            // WebAuthn署名検証
            webAuthnService.verifyAssertion(
                credential = credential,
                challenge = challenge,
                credentialId = credentialId,
                clientDataJSON = clientDataJSON,
                authenticatorData = authenticatorData,
                signature = signature,
                userHandle = userHandle
            )

            // Spring Securityの認証セッションを確立
            establishSecuritySession(username, request, response)

            logger.info("Passkey authentication successful for user: $username")
            ResponseEntity.ok(mapOf("message" to "authenticated"))
        } catch (e: Exception) {
            logger.error("Passkey authentication failed for user $username: ${e::class.simpleName} - ${e.message}", e)
            val errorMessage = e.message ?: e::class.simpleName ?: "Unknown error"
            ResponseEntity.status(401).body(mapOf("message" to "Authentication failed: $errorMessage"))
        }
    }

    private fun establishSecuritySession(
        username: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val userDetails = userDetailsService.loadUserByUsername(username)
        val authentication = UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.authorities
        )
        val securityContext = SecurityContextHolder.createEmptyContext()
        securityContext.authentication = authentication
        SecurityContextHolder.setContext(securityContext)

        // Spring Securityの標準セッション戦略を適用
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response)

        // セッションにセキュリティコンテキストを保存
        val session = request.getSession(true)
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            securityContext
        )
    }

    /**
     * ユーザーの登録済みパスキー一覧を取得
     */
    @GetMapping("/credentials")
    fun listCredentials(@RequestParam username: String): ResponseEntity<Any> {
        val creds = webAuthnService.findCredentials(username)
        val list = creds.map { cred ->
            mapOf(
                "id" to cred.id,
                "credentialId" to Base64UrlUtil.encodeToString(cred.credentialId),
                "createdAt" to cred.createdAt?.toString(),
                "isValid" to cred.publicKeyCose.isNotEmpty()
            )
        }
        return ResponseEntity.ok(mapOf("credentials" to list))
    }

    /**
     * 指定したパスキーを削除
     */
    @DeleteMapping("/credentials/{id}")
    fun deleteCredential(
        @PathVariable id: Long,
        @RequestParam username: String
    ): ResponseEntity<Any> {
        val credential = webAuthnService.findCredentialById(id)
            ?: return ResponseEntity.status(404).body(mapOf("message" to "Credential not found"))

        if (credential.username != username) {
            return ResponseEntity.status(403).body(mapOf("message" to "Credential does not belong to user"))
        }

        webAuthnService.deleteCredential(id)
        logger.info("Passkey deleted for user: $username, credentialId: $id")
        return ResponseEntity.ok(mapOf("message" to "Credential deleted"))
    }
}
