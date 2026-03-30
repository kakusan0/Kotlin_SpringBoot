package com.example.demo.controller;

import com.example.demo.dto.WebAuthnAllowCredential;
import com.example.demo.dto.WebAuthnAuthenticationOptionsResponse;
import com.example.demo.dto.WebAuthnDiscoverableAuthenticationOptionsResponse;
import com.example.demo.dto.WebAuthnFinishAuthenticationRequest;
import com.example.demo.dto.WebAuthnFinishRegistrationRequest;
import com.example.demo.dto.WebAuthnPubKeyParam;
import com.example.demo.dto.WebAuthnRegistrationOptionsResponse;
import com.example.demo.dto.WebAuthnRp;
import com.example.demo.dto.WebAuthnUser;
import com.example.demo.model.WebAuthnCredential;
import com.example.demo.service.WebAuthnService;
import com.webauthn4j.util.Base64UrlUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/webauthn")
@Validated
@RequiredArgsConstructor
public class WebAuthnController {

    private final WebAuthnService webAuthnService;
    private final UserDetailsService userDetailsService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;


    @Value("${webauthn.rp.id:localhost}")
    private String rpId;

    @Value("${webauthn.rp.name:Dev RP}")
    private String rpName;


    @GetMapping("/registration/options")
    public ResponseEntity<Object> registrationOptions(@RequestParam @NotBlank String username) {
        try {
            userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            log.warn("Registration attempted for non-existent user: {}", username);
            return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
        }

        byte[] challenge = webAuthnService.generateChallenge();
        webAuthnService.saveRegistrationChallenge(username, challenge);

        WebAuthnRp rp = new WebAuthnRp(rpId, rpName);
        WebAuthnUser user = new WebAuthnUser(
                Base64UrlUtil.encodeToString(username.getBytes(StandardCharsets.UTF_8)),
                username,
                username
        );
        List<WebAuthnPubKeyParam> params = List.of(
                new WebAuthnPubKeyParam("public-key", -7),
                new WebAuthnPubKeyParam("public-key", -257)
        );
        WebAuthnRegistrationOptionsResponse res = new WebAuthnRegistrationOptionsResponse();
        res.setChallenge(Base64UrlUtil.encodeToString(challenge));
        res.setRp(rp);
        res.setUser(user);
        res.setPubKeyCredParams(params);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/registration/finish")
    public ResponseEntity<Object> finishRegistration(
            @RequestParam @NotBlank String username,
            @Valid @RequestBody WebAuthnFinishRegistrationRequest req
    ) {
        byte[] challenge = webAuthnService.consumeRegistrationChallenge(username);
        if (challenge == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Challenge not found or expired"));
        }

        try {
            byte[] clientDataJSON = Base64UrlUtil.decode(req.getResponse().getClientDataJSON());
            byte[] attestationObject = Base64UrlUtil.decode(req.getResponse().getAttestationObject());
            webAuthnService.registerCredential(username, challenge, clientDataJSON, attestationObject);
            log.info("Passkey registered successfully for user: {}", username);
            return ResponseEntity.ok(Map.of("message", "registered"));
        } catch (Exception e) {
            log.error("Failed to register passkey for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("message", "Registration failed: " + e.getMessage()));
        }
    }

    @GetMapping("/authentication/options")
    public ResponseEntity<Object> authenticationOptions(@RequestParam(required = false) String username) {
        byte[] challenge = webAuthnService.generateChallenge();
        if (username == null || username.isBlank()) {
            String challengeId = webAuthnService.saveDiscoverableChallenge(challenge);
            WebAuthnDiscoverableAuthenticationOptionsResponse res = new WebAuthnDiscoverableAuthenticationOptionsResponse();
            res.setChallenge(Base64UrlUtil.encodeToString(challenge));
            res.setRpId(rpId);
            Map<String, Object> body = new HashMap<>();
            body.put("challenge", res.getChallenge());
            body.put("rpId", res.getRpId());
            body.put("timeout", res.getTimeout());
            body.put("userVerification", res.getUserVerification());
            body.put("challengeId", challengeId);
            return ResponseEntity.ok(body);
        }

        List<WebAuthnCredential> creds = webAuthnService.findCredentials(username);
        if (creds.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "No passkeys registered for this user"));
        }

        webAuthnService.saveAuthenticationChallenge(username, challenge);

        List<WebAuthnAllowCredential> allow = new ArrayList<>();
        for (WebAuthnCredential cred : creds) {
            List<String> transports = new ArrayList<>();
            if (cred.getTransports() != null) {
                Collections.addAll(transports, cred.getTransports().split(","));
            }
            allow.add(new WebAuthnAllowCredential("public-key", Base64UrlUtil.encodeToString(cred.getCredentialId()), transports));
        }
        WebAuthnAuthenticationOptionsResponse res = new WebAuthnAuthenticationOptionsResponse();
        res.setChallenge(Base64UrlUtil.encodeToString(challenge));
        res.setRpId(rpId);
        res.setAllowCredentials(allow);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/authentication/finish/discoverable")
    public ResponseEntity<Object> finishDiscoverableAuthentication(
            @RequestParam @NotBlank String challengeId,
            @Valid @RequestBody WebAuthnFinishAuthenticationRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        byte[] challenge = webAuthnService.consumeDiscoverableChallenge(challengeId);
        if (challenge == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Challenge not found or expired"));
        }

        byte[] credentialId = Base64UrlUtil.decode(req.getRawId());
        WebAuthnCredential credential = webAuthnService.findByCredentialId(credentialId);
        if (credential == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Credential not found"));
        }

        String userHandle = req.getResponse().getUserHandle();
        if (userHandle == null || userHandle.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "userHandle is required for discoverable credentials"));
        }

        String usernameFromHandle = new String(Base64UrlUtil.decode(userHandle), StandardCharsets.UTF_8);
        if (!credential.getUsername().equals(usernameFromHandle)) {
            log.warn("Credential ownership mismatch: credential belongs to {}, but userHandle indicates {}",
                    credential.getUsername(), usernameFromHandle);
            return ResponseEntity.status(403).body(Map.of("message", "Credential does not match userHandle"));
        }

        try {
            byte[] clientDataJSON = Base64UrlUtil.decode(req.getResponse().getClientDataJSON());
            byte[] authenticatorData = Base64UrlUtil.decode(req.getResponse().getAuthenticatorData());
            byte[] signature = Base64UrlUtil.decode(req.getResponse().getSignature());
            byte[] userHandleBytes = Base64UrlUtil.decode(userHandle);

            webAuthnService.verifyAssertion(
                    credential,
                    challenge,
                    credentialId,
                    clientDataJSON,
                    authenticatorData,
                    signature,
                    userHandleBytes
            );

            establishSecuritySession(credential.getUsername(), request, response);

            log.info("Discoverable passkey authentication successful for user: {}", credential.getUsername());
            return ResponseEntity.ok(Map.of("message", "authenticated", "username", credential.getUsername()));
        } catch (Exception e) {
            log.error("Discoverable passkey authentication failed: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(401).body(Map.of("message", "Authentication failed: " + errorMessage));
        }
    }

    @PostMapping("/authentication/finish")
    public ResponseEntity<Object> finishAuthentication(
            @RequestParam @NotBlank String username,
            @Valid @RequestBody WebAuthnFinishAuthenticationRequest req,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        byte[] challenge = webAuthnService.consumeAuthenticationChallenge(username);
        if (challenge == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Challenge not found or expired"));
        }

        byte[] credentialId = Base64UrlUtil.decode(req.getRawId());
        WebAuthnCredential credential = webAuthnService.findByCredentialId(credentialId);
        if (credential == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Credential not found"));
        }

        if (!credential.getUsername().equals(username)) {
            log.warn("Credential ownership mismatch: expected {}, got {}", username, credential.getUsername());
            return ResponseEntity.status(403).body(Map.of("message", "Credential does not belong to user"));
        }

        try {
            byte[] clientDataJSON = Base64UrlUtil.decode(req.getResponse().getClientDataJSON());
            byte[] authenticatorData = Base64UrlUtil.decode(req.getResponse().getAuthenticatorData());
            byte[] signature = Base64UrlUtil.decode(req.getResponse().getSignature());
            byte[] userHandle = req.getResponse().getUserHandle() != null
                    ? Base64UrlUtil.decode(req.getResponse().getUserHandle())
                    : null;

            webAuthnService.verifyAssertion(
                    credential,
                    challenge,
                    credentialId,
                    clientDataJSON,
                    authenticatorData,
                    signature,
                    userHandle
            );

            establishSecuritySession(username, request, response);

            log.info("Passkey authentication successful for user: {}", username);
            return ResponseEntity.ok(Map.of("message", "authenticated"));
        } catch (Exception e) {
            log.error("Passkey authentication failed for user {}: {} - {}", username, e.getClass().getSimpleName(), e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(401).body(Map.of("message", "Authentication failed: " + errorMessage));
        }
    }

    private void establishSecuritySession(String username, HttpServletRequest request, HttpServletResponse response) {
        var userDetails = userDetailsService.loadUserByUsername(username);
        var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        var session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    }

    @GetMapping("/credentials")
    public ResponseEntity<Object> listCredentials(@RequestParam @NotBlank String username) {
        List<WebAuthnCredential> creds = webAuthnService.findCredentials(username);
        List<Map<String, Object>> list = new ArrayList<>();
        for (WebAuthnCredential cred : creds) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", cred.getId());
            item.put("credentialId", Base64UrlUtil.encodeToString(cred.getCredentialId()));
            item.put("createdAt", cred.getCreatedAt() != null ? cred.getCreatedAt().toString() : null);
            item.put("isValid", cred.getPublicKeyCose() != null && cred.getPublicKeyCose().length > 0);
            list.add(item);
        }
        return ResponseEntity.ok(Map.of("credentials", list));
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<Object> deleteCredential(@PathVariable Long id, @RequestParam @NotBlank String username) {
        WebAuthnCredential credential = webAuthnService.findCredentialById(id);
        if (credential == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Credential not found"));
        }

        if (!credential.getUsername().equals(username)) {
            return ResponseEntity.status(403).body(Map.of("message", "Credential does not belong to user"));
        }

        webAuthnService.deleteCredential(id);
        log.info("Passkey deleted for user: {}, credentialId: {}", username, id);
        return ResponseEntity.ok(Map.of("message", "Credential deleted"));
    }

}
