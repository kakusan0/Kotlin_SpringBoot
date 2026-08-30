package com.example.demo.service;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

@Component
public class AipoDriverFactory {

    public WebDriver create() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                "--disable-gpu", "--window-size=1920,1080",
                "--remote-allow-origins=*", "--lang=ja");
        return new ChromeDriver(options);
    }
}
