package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhitelistIp {
    private Long id;
    private String ipAddress;
    private OffsetDateTime createdAt;
    private Boolean blacklisted;
    private Integer blacklistedCount;
}
