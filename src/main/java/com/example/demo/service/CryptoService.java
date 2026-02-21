package com.example.demo.service;

import com.example.demo.util.EncryptionUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CryptoService {

    private final String rawKey;

    public CryptoService(@Value("${app.encryption.key:}") String rawKey) {
        this.rawKey = rawKey;
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
