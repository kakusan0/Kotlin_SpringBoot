package com.example.demo.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IpLatestPath {
    private String ipAddress;
    private String path;
    private String userAgent;
}
