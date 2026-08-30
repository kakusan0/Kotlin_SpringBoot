package com.example.demo.util;

import java.time.LocalTime;

public final class TextUtils {

    private TextUtils() {
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static LocalTime parseLocalTimeOrNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value).withSecond(0).withNano(0);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
