package com.bupt.hotel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TimeService {

    @Value("${hotel.ac.time-scale-ms:10000}")
    private long timeScaleMs; // 真实世界多少毫秒 = 逻辑世界1分钟

    private final LocalDateTime startTime = LocalDateTime.now();
    private final long startRealTime = System.currentTimeMillis();

    public LocalDateTime getCurrentTime() {
        long elapsedRealMs = System.currentTimeMillis() - startRealTime;
        // 逻辑分钟数 = 经过的真实毫秒数 / timeScaleMs
        double logicMinutes = (double) elapsedRealMs / timeScaleMs;
        long logicSeconds = (long) (logicMinutes * 60);

        return startTime.plusSeconds(logicSeconds);
    }
}
