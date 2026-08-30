package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "webauthn.rp")
public class WebAuthnProperties {
    private String id = "localhost";
    private String name = "Dev RP";
    private String origin = "http://localhost:8080";
    private String allowedOrigins = "";
}
