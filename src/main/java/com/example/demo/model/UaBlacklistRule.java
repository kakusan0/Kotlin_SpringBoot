package com.example.demo.model;

import lombok.*;

@Getter
@Setter
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
