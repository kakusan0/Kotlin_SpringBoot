package com.example.demo.service;

import com.example.demo.mapper.BlacklistEventMapper;
import com.example.demo.model.BlacklistEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlacklistEventService {

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
                    ex.toString());
        }
    }

    public void recordEventSync(BlacklistEvent event) {
        blacklistEventMapper.insert(event);
    }

    public void recordEventsSync(List<BlacklistEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        blacklistEventMapper.insertBulk(events);
    }

    @Async("taskExecutor")
    public void recordEvents(List<BlacklistEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        try {
            blacklistEventMapper.insertBulk(events);
        } catch (Exception ex) {
            log.warn(
                    "非同期ブラックリストイベント一括保存に失敗: count={}, err={}",
                    events.size(),
                    ex.toString());
        }
    }
}
