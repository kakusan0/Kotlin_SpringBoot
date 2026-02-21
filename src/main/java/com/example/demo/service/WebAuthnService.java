package com.example.demo.service;

import com.example.demo.mapper.WebAuthnCredentialMapper;
import com.example.demo.model.WebAuthnCredential;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.Base64UrlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebAuthnService {

    private final WebAuthnCredentialMapper credentialMapper;
    private final SecureRandom random = new SecureRandom();
    private final WebAuthnManager webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    private final ObjectConverter objectConverter = new ObjectConverter();
    private final AttestedCredentialDataConverter attestedCredentialDataConverter = new AttestedCredentialDataConverter(objectConverter);
    private final Map<String, ChallengeData> registrationChallenges = new ConcurrentHashMap<>();
    private final Map<String, ChallengeData> authenticationChallenges = new ConcurrentHashMap<>();
    private final Map<String, ChallengeData> discoverableChallenges = new ConcurrentHashMap<>();
    @Value("${webauthn.rp.id:localhost}")
    private String rpId;
    @Value("${webauthn.rp.origin:http://localhost:8080}")
    private String rpOrigin;


    public byte[] generateChallenge() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return bytes;
    }

    public void saveRegistrationChallenge(String username, byte[] challenge) {
        registrationChallenges.put(username, new ChallengeData(challenge));
    }

    public byte[] consumeRegistrationChallenge(String username) {
        ChallengeData data = registrationChallenges.remove(username);
        if (data == null || data.isExpired()) {
            return null;
        }
        return data.getChallenge();
    }

    public void saveAuthenticationChallenge(String username, byte[] challenge) {
        authenticationChallenges.put(username, new ChallengeData(challenge));
    }

    public byte[] consumeAuthenticationChallenge(String username) {
        ChallengeData data = authenticationChallenges.remove(username);
        if (data == null || data.isExpired()) {
            return null;
        }
        return data.getChallenge();
    }

    public String saveDiscoverableChallenge(byte[] challenge) {
        String challengeId = UUID.randomUUID().toString();
        discoverableChallenges.put(challengeId, new ChallengeData(challenge));
        return challengeId;
    }

    public byte[] consumeDiscoverableChallenge(String challengeId) {
        ChallengeData data = discoverableChallenges.remove(challengeId);
        if (data == null || data.isExpired()) {
            return null;
        }
        return data.getChallenge();
    }

    public List<WebAuthnCredential> findCredentials(String username) {
        return credentialMapper.findByUsername(username);
    }

    public WebAuthnCredential findByCredentialId(byte[] credentialId) {
        return credentialMapper.findByCredentialId(credentialId);
    }

    public WebAuthnCredential findCredentialById(Long id) {
        return credentialMapper.findById(id);
    }

    public void deleteCredential(Long id) {
        credentialMapper.deleteById(id);
        log.info("Deleted WebAuthn credential with id: {}", id);
    }

    public WebAuthnCredential registerCredential(
            String username,
            byte[] challenge,
            byte[] clientDataJSON,
            byte[] attestationObject
    ) {
        Origin origin = Origin.create(rpOrigin);

        @SuppressWarnings("deprecation")
        ServerProperty serverProperty = new ServerProperty(
                origin,
                rpId,
                new DefaultChallenge(challenge),
                null
        );

        RegistrationRequest registrationRequest = new RegistrationRequest(attestationObject, clientDataJSON);
        RegistrationParameters registrationParameters = new RegistrationParameters(
                serverProperty,
                null,
                false,
                false
        );

        var registrationData = webAuthnManager.parse(registrationRequest);
        @SuppressWarnings("deprecation")
        var ignored = webAuthnManager.validate(registrationData, registrationParameters);

        var attestedCredentialData = registrationData.getAttestationObject().getAuthenticatorData().getAttestedCredentialData();
        if (attestedCredentialData == null) {
            throw new IllegalStateException("Attested credential data is null");
        }

        byte[] credentialId = attestedCredentialData.getCredentialId();
        byte[] publicKeyCose = attestedCredentialDataConverter.convert(attestedCredentialData);
        String aaguid = attestedCredentialData.getAaguid().toString();
        long signCount = registrationData.getAttestationObject().getAuthenticatorData().getSignCount();

        WebAuthnCredential credential = new WebAuthnCredential();
        credential.setUsername(username);
        credential.setCredentialId(credentialId);
        credential.setPublicKeyCose(publicKeyCose);
        credential.setSignCount(signCount);
        credential.setTransports(null);
        credential.setAttestationType(registrationData.getAttestationObject().getAttestationStatement().getFormat());
        credential.setAaguid(aaguid);

        credentialMapper.insert(credential);
        log.info(
                "WebAuthn credential registered for user: {}, credentialId: {}",
                username,
                Base64UrlUtil.encodeToString(credentialId)
        );
        return credential;
    }

    public boolean verifyAssertion(
            WebAuthnCredential credential,
            byte[] challenge,
            byte[] credentialId,
            byte[] clientDataJSON,
            byte[] authenticatorData,
            byte[] signature,
            byte[] userHandle
    ) {
        Origin origin = Origin.create(rpOrigin);

        @SuppressWarnings("deprecation")
        ServerProperty serverProperty = new ServerProperty(
                origin,
                rpId,
                new DefaultChallenge(challenge),
                null
        );

        AuthenticationRequest authenticationRequest = new AuthenticationRequest(
                credentialId,
                userHandle,
                authenticatorData,
                clientDataJSON,
                null,
                signature
        );

        CredentialRecord credentialRecord = buildCredentialRecord(credential);

        @SuppressWarnings("deprecation")
        AuthenticationParameters authenticationParameters = new AuthenticationParameters(
                serverProperty,
                credentialRecord,
                null,
                false,
                false
        );

        var authenticationData = webAuthnManager.parse(authenticationRequest);
        @SuppressWarnings("deprecation")
        var ignored = webAuthnManager.validate(authenticationData, authenticationParameters);

        long newSignCount = authenticationData.getAuthenticatorData().getSignCount();
        if (newSignCount > credential.getSignCount()) {
            credentialMapper.updateSignCount(credential.getId(), newSignCount);
            log.debug("Updated signCount for credential {}: {} -> {}",
                    credential.getId(), credential.getSignCount(), newSignCount);
        } else if (newSignCount > 0 && newSignCount <= credential.getSignCount()) {
            log.warn(
                    "Possible cloned authenticator detected for user {}. Expected signCount > {}, got {}",
                    credential.getUsername(),
                    credential.getSignCount(),
                    newSignCount
            );
        }

        log.info("WebAuthn authentication successful for user: {}", credential.getUsername());
        return true;
    }

    private CredentialRecord buildCredentialRecord(WebAuthnCredential credential) {
        byte[] publicKeyCose = credential.getPublicKeyCose();
        if (publicKeyCose == null || publicKeyCose.length == 0) {
            throw new IllegalStateException(
                    "Invalid credential: public key is empty. " +
                            "This credential was registered with an old implementation. " +
                            "Please re-register the passkey."
            );
        }

        var attestedCredentialData = tryConvert(publicKeyCose);
        return new CredentialRecordImpl(
                null,
                null,
                null,
                null,
                credential.getSignCount(),
                attestedCredentialData,
                null,
                null,
                null,
                null
        );
    }

    private com.webauthn4j.data.attestation.authenticator.AttestedCredentialData tryConvert(byte[] publicKeyCose) {
        try {
            return attestedCredentialDataConverter.convert(publicKeyCose);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse credential public key: " + e.getMessage() + ". " +
                            "Please re-register the passkey.",
                    e
            );
        }
    }

    @lombok.Getter
    public static class ChallengeData {
        private final byte[] challenge;
        private final long createdAt;

        public ChallengeData(byte[] challenge) {
            this.challenge = challenge;
            this.createdAt = System.currentTimeMillis();
        }


        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 5 * 60 * 1000;
        }
    }
}
