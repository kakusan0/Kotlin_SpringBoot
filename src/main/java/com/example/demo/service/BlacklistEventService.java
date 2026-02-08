package com.example.demo.service;

import com.example.demo.mapper.BlacklistEventMapper;
import com.example.demo.model.BlacklistEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlacklistEventService {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(BlacklistEventService.class);

    private final BlacklistEventMapper blacklistEventMapper;

    @Async("taskExecutor")
    public void recordEvent(BlacklistEvent event) {
        try {
            blacklistEventMapper.insert(event);
        } catch (Exception ex) {
            log.warn(
                    "非同期ブラックリストイベント保存に失敗: ip={}, reason={}, err={}",
                    event.getIpAddress(),
                    event.getReason(),
                    ex.toString()
            );
        }
    }

    public void recordEventSync(BlacklistEvent event) {
        blacklistEventMapper.insert(event);
    }
}
