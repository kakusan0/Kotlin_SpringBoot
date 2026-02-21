package com.example.demo.controller;

import com.example.demo.service.AipoLoginResult;
import com.example.demo.service.AipoLoginService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/aipo")
public class AipoLoginController {

    private final AipoLoginService aipoLoginService;

    public AipoLoginController(AipoLoginService aipoLoginService) {
        this.aipoLoginService = aipoLoginService;
    }

    @PostMapping("/login")
    public ResponseEntity<AipoLoginResult> login(Authentication auth, @RequestBody AipoLoginRequest request) {
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
    public ResponseEntity<Map<String, Object>> submit(Authentication auth, @RequestBody AipoSubmitRequest request) {
        Map.Entry<Boolean, String> result = aipoLoginService.submitRequest(auth.getName(), request.getSubmitButtonId());
        Map<String, Object> body = new HashMap<>();
        body.put("success", result.getKey());
        body.put("message", result.getValue());
        if (result.getKey()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.badRequest().body(body);
    }

    public static class AipoLoginRequest {
        private String username;
        private String password;
        private String yearMonth;
        private boolean autoSubmit;

        public AipoLoginRequest() {
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getYearMonth() {
            return yearMonth;
        }

        public void setYearMonth(String yearMonth) {
            this.yearMonth = yearMonth;
        }

        public boolean isAutoSubmit() {
            return autoSubmit;
        }

        public void setAutoSubmit(boolean autoSubmit) {
            this.autoSubmit = autoSubmit;
        }
    }

    public static class AipoSubmitRequest {
        private String submitButtonId;

        public AipoSubmitRequest() {
        }

        public String getSubmitButtonId() {
            return submitButtonId;
        }

        public void setSubmitButtonId(String submitButtonId) {
            this.submitButtonId = submitButtonId;
        }
    }
}
