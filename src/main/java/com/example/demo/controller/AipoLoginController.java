package com.example.demo.controller;

import com.example.demo.dto.AipoLoginRequest;
import com.example.demo.dto.AipoSubmitRequest;
import com.example.demo.service.AipoLoginResult;
import com.example.demo.service.AipoLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/aipo")
@Validated
@RequiredArgsConstructor
public class AipoLoginController {

    private final AipoLoginService aipoLoginService;


    @PostMapping("/login")
    public ResponseEntity<AipoLoginResult> login(Authentication auth, @Valid @RequestBody AipoLoginRequest request) {
        AipoLoginResult result = aipoLoginService.login(
                auth.getName(),
                request.getUsername(),
                request.getPassword(),
                request.getYearMonth(),
                null,
                request.isAutoSubmit()
        );
        if (result.success()) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(Authentication auth) {
        boolean success = aipoLoginService.logout(auth.getName());
        Map<String, Object> body = new HashMap<>();
        body.put("success", success);
        body.put("message", success ? "ログアウトしました" : "セッションが見つかりません");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication auth) {
        boolean loggedIn = aipoLoginService.isLoggedIn(auth.getName());
        Map<String, Object> body = new HashMap<>();
        body.put("loggedIn", loggedIn);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(Authentication auth, @Valid @RequestBody AipoSubmitRequest request) {
        Map.Entry<Boolean, String> result = aipoLoginService.submitRequest(auth.getName(), request.getSubmitButtonId());
        Map<String, Object> body = new HashMap<>();
        body.put("success", result.getKey());
        body.put("message", result.getValue());
        if (result.getKey()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.badRequest().body(body);
    }

}
