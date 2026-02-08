package com.example.demo.model;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistIp {
    private Long id;
    private String ipAddress;
    private OffsetDateTime createdAt;
    private Boolean deleted;
    private Integer times;
}
