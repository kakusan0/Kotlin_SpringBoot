package com.example.demo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * DB call wrapper that logs failures and rethrows.
 */
public final class DbUtils {
    private static final Logger logger = LoggerFactory.getLogger("com.example.demo.util.DbUtils");

    private DbUtils() {
    }

    public static <T> T dbCall(String action, Supplier<T> block, Object... context) {
        try {
            return block.get();
        } catch (Exception ex) {
            String ctx = Arrays.stream(context).map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
            logger.error("DB {} failed; context={}", action, ctx, ex);
            throw ex;
        }
    }
}
