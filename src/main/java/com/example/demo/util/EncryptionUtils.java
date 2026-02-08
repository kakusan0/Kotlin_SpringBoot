package com.example.demo.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class EncryptionUtils {
    private static final String PREFIX = "enc:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom secureRandom = new SecureRandom();

    private static volatile byte[] keyBytes = null;

    private EncryptionUtils() {
    }

    public static void setKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            keyBytes = null;
        } else {
            keyBytes = deriveKey(rawKey.trim());
        }
    }

    public static String encrypt(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith(PREFIX)) {
            return value;
        }
        byte[] key = keyBytes;
        if (key == null) {
            return value;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt", ex);
        }
    }

    public static String decrypt(String value) {
        if (value == null) {
            return null;
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        byte[] key = keyBytes;
        if (key == null) {
            throw new IllegalStateException("Encryption key is not configured");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (decoded.length <= GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted payload is too short");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[decoded.length - GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(decoded, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt", ex);
        }
    }

    private static byte[] deriveKey(String rawKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(rawKey);
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to derive key", ex);
        }
    }
}
