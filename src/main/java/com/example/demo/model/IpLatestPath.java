package com.example.demo.model;

public class IpLatestPath {
    private String ipAddress;
    private String path;
    private String userAgent;

    public IpLatestPath() {
    }

    public IpLatestPath(String ipAddress, String path, String userAgent) {
        this.ipAddress = ipAddress;
        this.path = path;
        this.userAgent = userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
