package com.example.demo.service;

import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AipoSessionStore {

    private final Map<String, WebDriver> sessions = new ConcurrentHashMap<>();

    public WebDriver get(String username) {
        return sessions.get(username);
    }

    public void replace(String username, WebDriver driver) {
        close(username);
        sessions.put(username, driver);
    }

    public WebDriver remove(String username) {
        return sessions.remove(username);
    }

    public boolean contains(String username) {
        return sessions.containsKey(username);
    }

    public void removeIfBroken(String username) {
        sessions.remove(username);
    }

    public void closeAll() {
        sessions.forEach((username, driver) -> quitQuietly(driver));
        sessions.clear();
    }

    private void close(String username) {
        WebDriver driver = sessions.remove(username);
        if (driver != null) {
            quitQuietly(driver);
        }
    }

    private void quitQuietly(WebDriver driver) {
        try {
            driver.quit();
        } catch (RuntimeException ignored) {
            // A failed browser shutdown must not block other sessions.
        }
    }
}
