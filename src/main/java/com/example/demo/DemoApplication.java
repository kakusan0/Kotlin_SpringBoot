package com.example.demo;

import org.jspecify.annotations.NonNull;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
@ConfigurationPropertiesScan
@MapperScan("com.example.demo.mapper")
public class DemoApplication extends SpringBootServletInitializer {

    static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Override
    @NonNull
    protected SpringApplicationBuilder configure(@NonNull SpringApplicationBuilder builder) {
        return builder.sources(DemoApplication.class);
    }
}
