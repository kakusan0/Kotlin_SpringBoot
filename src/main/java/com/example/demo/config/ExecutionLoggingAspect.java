package com.example.demo.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Aspect
@Component
public class ExecutionLoggingAspect {

    private static final Logger log = LogManager.getLogger(ExecutionLoggingAspect.class);

    @Pointcut("execution(public * com.example.demo.controller..*(..)) || execution(public * com.example.demo.service..*(..))")
    public void applicationPublicMethods() {
    }

    @Around("applicationPublicMethods()")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        String args = formatArgs(joinPoint.getArgs());

        if (log.isDebugEnabled()) {
            log.debug("-> {}.{}({})", className, methodName, args);
        }

        try {
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

            if (log.isDebugEnabled()) {
                log.debug("<- {}.{} [{} ms] => {}", className, methodName, elapsedMs, summarize(result));
            }

            return result;
        } catch (Throwable ex) {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.error("!! {}.{} [{} ms] failed: {}", className, methodName, elapsedMs, ex.toString(), ex);
            throw ex;
        }
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        return Arrays.stream(args)
                .map(this::summarize)
                .collect(Collectors.joining(", "));
    }

    private String summarize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence s) {
            return "\"" + trim(s.toString()) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean || value.getClass().isEnum()) {
            return value.toString();
        }
        if (value instanceof Collection<?> collection) {
            return value.getClass().getSimpleName() + "(size=" + collection.size() + ")";
        }
        if (value instanceof Map<?, ?> map) {
            return value.getClass().getSimpleName() + "(size=" + map.size() + ")";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[]";
        }
        return value.getClass().getSimpleName();
    }

    private String trim(String value) {
        if (value.length() <= 80) {
            return value;
        }
        return value.substring(0, 77) + "...";
    }
}
