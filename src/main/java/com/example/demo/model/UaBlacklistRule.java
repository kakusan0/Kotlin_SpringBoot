package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UaBlacklistRule {
    private Long id;
    private String pattern;
    @Builder.Default
    private String matchType = "EXACT";
    @Builder.Default
    private Boolean deleted = false;
}
