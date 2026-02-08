package com.example.demo.service;

import com.example.demo.util.EncryptionUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CryptoService {

    @Value("${app.encryption.key:}")
    private final String rawKey;

    @PostConstruct
    public void init() {
        EncryptionUtils.setKey(rawKey);
    }

    public String encrypt(String value) {
        return EncryptionUtils.encrypt(value);
    }

    public String decrypt(String value) {
        return EncryptionUtils.decrypt(value);
    }
}
