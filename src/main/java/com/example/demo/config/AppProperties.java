package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private boolean trustProxy;
    private Report report = new Report();
    private Encryption encryption = new Encryption();
    private Csp csp = new Csp();
    private Login login = new Login();

    @Getter
    @Setter
    public static class Login {
        private LoginRateLimit rateLimit = new LoginRateLimit();
    }

    @Getter
    @Setter
    public static class LoginRateLimit {
        private long capacity = 5;
        private long refillMinutes = 5;
    }

    @Getter
    @Setter
    public static class Report {
        private String dir = "reports";
    }

    @Getter
    @Setter
    public static class Encryption {
        private String key = "";
    }

    @Getter
    @Setter
    public static class Csp {
        private String connectSrc = "'self'";
    }
}
