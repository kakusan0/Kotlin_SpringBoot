package com.example.demo.controller;

import com.example.demo.model.WebAuthnCredential;
import com.example.demo.service.WebAuthnService;
import com.webauthn4j.util.Base64UrlUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/webauthn")
public class WebAuthnController {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WebAuthnController.class);

    private final WebAuthnService webAuthnService;
    private final UserDetailsService userDetailsService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public WebAuthnController(
            WebAuthnService webAuthnService,
            UserDetailsService userDetailsService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy
    ) {
        this.webAuthnService = webAuthnService;
        this.userDetailsService = userDetailsService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @Value("${webauthn.rp.id:localhost}")
    private String rpId;

    @Value("${webauthn.rp.name:Dev RP}")
    private String rpName;


    @GetMapping("/registration/options")
    public ResponseEntity<Object> registrationOptions(@RequestParam String username) {
        try {
            userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            logger.warn("Registration attempted for non-existent user: {}", username);
            return ResponseEntity.badRequest().body(Map.of("message", "User not found"));
        }

        byte[] challenge = webAuthnService.generateChallenge();
        webAuthnService.saveRegistrationChallenge(username, challenge);

        Rp rp = new Rp(rpId, rpName);
        User user = new User(
                Base64UrlUtil.encodeToString(username.getBytes(StandardCharsets.UTF_8)),
                username,
                username
        );
        List<PubKeyParam> params = List.of(
                new PubKeyParam("public-key", -7),
                new PubKeyParam("public-key", -257)
        );
        RegistrationOptionsResponse res = new RegistrationOptionsResponse();
        res.setChallenge(Base64UrlUtil.encodeToString(challenge));
        res.setRp(rp);
        res.setUser(user);
        res.setPubKeyCredParams(params);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/registration/finish")
    public ResponseEntity<Object> finishRegistration(
            @RequestParam String username,
            @RequestBody FinishRegistrationRequest req
    ) {
        byte[] challenge = webAuthnService.consumeRegistrationChallenge(username);
        if (challenge == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Challenge not found or expired"));
        }

        try {
            byte[] clientDataJSON = Base64UrlUtil.decode(req.getResponse().getClientDataJSON());
            byte[] attestationObject = Base64UrlUtil.decode(req.getResponse().getAttestationObject());
            webAuthnService.registerCredential(username, challenge, clientDataJSON, attestationObject);
            logger.info("Passkey registered successfully for user: {}", username);
            return ResponseEntity.ok(Map.of("message", "registered"));
        } catch (Exception e) {
            logger.error("Failed to register passkey for user {}: {}", username, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("message", "Registration failed: " + e.getMessage()));
        }
    }

    @GetMapping("/authentication/options")
    public ResponseEntity<Object> authenticationOptions(@RequestParam(required = false) String username) {
        byte[] challenge = webAuthnService.generateChallenge();
        if (username == null || username.isBlank()) {
            String challengeId = webAuthnService.saveDiscoverableChallenge(challenge);
            DiscoverableAuthenticationOptionsResponse res = new DiscoverableAuthenticationOptionsResponse();
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

        List<AllowCredential> allow = new ArrayList<>();
        for (WebAuthnCredential cred : creds) {
            List<String> transports = new ArrayList<>();
            if (cred.getTransports() != null) {
                Collections.addAll(transports, cred.getTransports().split(","));
            }
            allow.add(new AllowCredential("public-key", Base64UrlUtil.encodeToString(cred.getCredentialId()), transports));
        }
        AuthenticationOptionsResponse res = new AuthenticationOptionsResponse();
        res.setChallenge(Base64UrlUtil.encodeToString(challenge));
        res.setRpId(rpId);
        res.setAllowCredentials(allow);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/authentication/finish/discoverable")
    public ResponseEntity<Object> finishDiscoverableAuthentication(
            @RequestParam String challengeId,
            @RequestBody FinishAuthenticationRequest req,
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
            logger.warn("Credential ownership mismatch: credential belongs to {}, but userHandle indicates {}",
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

            logger.info("Discoverable passkey authentication successful for user: {}", credential.getUsername());
            return ResponseEntity.ok(Map.of("message", "authenticated", "username", credential.getUsername()));
        } catch (Exception e) {
            logger.error("Discoverable passkey authentication failed: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.status(401).body(Map.of("message", "Authentication failed: " + errorMessage));
        }
    }

    @PostMapping("/authentication/finish")
    public ResponseEntity<Object> finishAuthentication(
            @RequestParam String username,
            @RequestBody FinishAuthenticationRequest req,
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
            logger.warn("Credential ownership mismatch: expected {}, got {}", username, credential.getUsername());
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

            logger.info("Passkey authentication successful for user: {}", username);
            return ResponseEntity.ok(Map.of("message", "authenticated"));
        } catch (Exception e) {
            logger.error("Passkey authentication failed for user {}: {} - {}", username, e.getClass().getSimpleName(), e.getMessage(), e);
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
    public ResponseEntity<Object> listCredentials(@RequestParam String username) {
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
    public ResponseEntity<Object> deleteCredential(@PathVariable Long id, @RequestParam String username) {
        WebAuthnCredential credential = webAuthnService.findCredentialById(id);
        if (credential == null) {
            return ResponseEntity.status(404).body(Map.of("message", "Credential not found"));
        }

        if (!credential.getUsername().equals(username)) {
            return ResponseEntity.status(403).body(Map.of("message", "Credential does not belong to user"));
        }

        webAuthnService.deleteCredential(id);
        logger.info("Passkey deleted for user: {}, credentialId: {}", username, id);
        return ResponseEntity.ok(Map.of("message", "Credential deleted"));
    }

    public static class RegistrationOptionsResponse {
        private final long timeout = 60000;
        private final String attestation = "none";
        private final AuthenticatorSelection authenticatorSelection = new AuthenticatorSelection();
        private String challenge;
        private Rp rp;
        private User user;
        private List<PubKeyParam> pubKeyCredParams;

        public RegistrationOptionsResponse() {
        }

        public String getChallenge() {
            return challenge;
        }

        public void setChallenge(String challenge) {
            this.challenge = challenge;
        }

        public Rp getRp() {
            return rp;
        }

        public void setRp(Rp rp) {
            this.rp = rp;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public List<PubKeyParam> getPubKeyCredParams() {
            return pubKeyCredParams;
        }

        public void setPubKeyCredParams(List<PubKeyParam> pubKeyCredParams) {
            this.pubKeyCredParams = pubKeyCredParams;
        }

        public long getTimeout() {
            return timeout;
        }

        public String getAttestation() {
            return attestation;
        }

        public AuthenticatorSelection getAuthenticatorSelection() {
            return authenticatorSelection;
        }
    }

    public static class Rp {
        private String id;
        private String name;

        public Rp() {
        }

        public Rp(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class User {
        private String id;
        private String name;
        private String displayName;

        public User() {
        }

        public User(String id, String name, String displayName) {
            this.id = id;
            this.name = name;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class PubKeyParam {
        private String type;
        private int alg;

        public PubKeyParam() {
        }

        public PubKeyParam(String type, int alg) {
            this.type = type;
            this.alg = alg;
        }

        public String getType() {
            return type;
        }

        public int getAlg() {
            return alg;
        }
    }

    public static class AuthenticatorSelection {
        private final String residentKey = "required";
        private final boolean requireResidentKey = true;
        private final String userVerification = "preferred";
        private String authenticatorAttachment;

        public AuthenticatorSelection() {
        }

        public String getAuthenticatorAttachment() {
            return authenticatorAttachment;
        }

        public String getResidentKey() {
            return residentKey;
        }

        public boolean isRequireResidentKey() {
            return requireResidentKey;
        }

        public String getUserVerification() {
            return userVerification;
        }
    }

    public static class FinishRegistrationRequest {
        private String id;
        private String rawId;
        private String type;
        private RegistrationResponse response;

        public FinishRegistrationRequest() {
        }

        public String getId() {
            return id;
        }

        public String getRawId() {
            return rawId;
        }

        public String getType() {
            return type;
        }

        public RegistrationResponse getResponse() {
            return response;
        }
    }

    public static class RegistrationResponse {
        private String attestationObject;
        private String clientDataJSON;

        public RegistrationResponse() {
        }

        public String getAttestationObject() {
            return attestationObject;
        }

        public String getClientDataJSON() {
            return clientDataJSON;
        }
    }

    public static class DiscoverableAuthenticationOptionsResponse {
        private final long timeout = 60000;
        private final String userVerification = "preferred";
        private String challenge;
        private String rpId;

        public DiscoverableAuthenticationOptionsResponse() {
        }

        public String getChallenge() {
            return challenge;
        }

        public void setChallenge(String challenge) {
            this.challenge = challenge;
        }

        public String getRpId() {
            return rpId;
        }

        public void setRpId(String rpId) {
            this.rpId = rpId;
        }

        public long getTimeout() {
            return timeout;
        }

        public String getUserVerification() {
            return userVerification;
        }
    }

    public static class AuthenticationOptionsResponse {
        private final long timeout = 60000;
        private final String userVerification = "preferred";
        private String challenge;
        private String rpId;
        private List<AllowCredential> allowCredentials;

        public AuthenticationOptionsResponse() {
        }

        public String getChallenge() {
            return challenge;
        }

        public void setChallenge(String challenge) {
            this.challenge = challenge;
        }

        public String getRpId() {
            return rpId;
        }

        public void setRpId(String rpId) {
            this.rpId = rpId;
        }

        public List<AllowCredential> getAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(List<AllowCredential> allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getTimeout() {
            return timeout;
        }

        public String getUserVerification() {
            return userVerification;
        }
    }

    public static class AllowCredential {
        private String type;
        private String id;
        private List<String> transports = new ArrayList<>();

        public AllowCredential() {
        }

        public AllowCredential(String type, String id, List<String> transports) {
            this.type = type;
            this.id = id;
            this.transports = transports != null ? transports : new ArrayList<>();
        }

        public String getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public List<String> getTransports() {
            return transports;
        }
    }

    public static class FinishAuthenticationRequest {
        private String id;
        private String rawId;
        private String type;
        private AuthenticationResponse response;

        public FinishAuthenticationRequest() {
        }

        public String getId() {
            return id;
        }

        public String getRawId() {
            return rawId;
        }

        public String getType() {
            return type;
        }

        public AuthenticationResponse getResponse() {
            return response;
        }
    }

    public static class AuthenticationResponse {
        private String authenticatorData;
        private String clientDataJSON;
        private String signature;
        private String userHandle;

        public AuthenticationResponse() {
        }

        public String getAuthenticatorData() {
            return authenticatorData;
        }

        public String getClientDataJSON() {
            return clientDataJSON;
        }

        public String getSignature() {
            return signature;
        }

        public String getUserHandle() {
            return userHandle;
        }
    }
}
