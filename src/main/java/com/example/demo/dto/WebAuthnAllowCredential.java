package com.example.demo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class WebAuthnAllowCredential {
    private String type;
    private String id;
    private List<String> transports = new ArrayList<>();

    public WebAuthnAllowCredential(String type, String id, List<String> transports) {
        this.type = type;
        this.id = id;
        this.transports = transports != null ? transports : new ArrayList<>();
    }
}

