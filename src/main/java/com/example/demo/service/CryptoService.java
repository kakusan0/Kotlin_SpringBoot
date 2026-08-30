package com.example.demo.service;

import com.example.demo.util.EncryptionUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {

    private final String rawKey;

    public CryptoService(com.example.demo.config.AppProperties properties) {
        this.rawKey = properties.getEncryption().getKey();
    }

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
