package com.example.demo.service;

import com.example.demo.mapper.AccessLogMapper;
import com.example.demo.model.AccessLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogWriteService {

    private final AccessLogMapper accessLogMapper;

    @Async("auditLogExecutor")
    public void write(AccessLog accessLog) {
        try {
            accessLogMapper.insert(accessLog);
        } catch (Exception ex) {
            log.warn(
                    "非同期アクセスログ保存に失敗: method={}, path={}, status={}, err={}",
                    accessLog.getMethod(),
                    accessLog.getPath(),
                    accessLog.getStatus(),
                    ex.toString()
            );
        }
    }
}

