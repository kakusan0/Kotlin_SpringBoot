package com.example.demo.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * DB call wrapper that logs failures and rethrows.
 */
@Slf4j
@UtilityClass
public class DbUtils {

    public <T> T dbCall(String action, Supplier<T> block, Object... context) {
        try {
            return block.get();
        } catch (Exception ex) {
            String ctx = Arrays.stream(context).map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
            log.error("DB {} failed; context={}", action, ctx, ex);
            throw ex;
        }
    }
}
