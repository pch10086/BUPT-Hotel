package com.bupt.hotel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class TimeService {

    @Value("${hotel.ac.time-scale-ms:10000}")
    private long timeScaleMs; // 真实世界多少毫秒 = 逻辑世界1分钟

    private final LocalDateTime startTime = LocalDateTime.now();
    private final LocalDateTime startRealDateTime = LocalDateTime.now();
    private final long startRealTime = System.currentTimeMillis();

    /**
     * 获取当前逻辑时间（受缩放影响）。
     */
    public LocalDateTime getCurrentTime() {
        long elapsedRealMs = System.currentTimeMillis() - startRealTime;
        // 逻辑分钟数 = 经过的真实毫秒数 / timeScaleMs
        double logicMinutes = (double) elapsedRealMs / timeScaleMs;
        long logicSeconds = (long) (logicMinutes * 60);

        return startTime.plusSeconds(logicSeconds);
    }

    /**
     * 当前真实时间（墙钟时间）。
     */
    public LocalDateTime getCurrentRealTime() {
        return LocalDateTime.now();
    }

    /**
     * 将逻辑时间转换为真实时间，用于界面显示。
     */
    public LocalDateTime toRealTime(LocalDateTime logicTime) {
        if (logicTime == null) {
            return null;
        }
        long logicSeconds = Duration.between(startTime, logicTime).getSeconds();
        double realSeconds = logicSeconds * (timeScaleMs / 1000.0) / 60.0;
        long realNanos = (long) (realSeconds * 1_000_000_000L);
        return startRealDateTime.plusNanos(realNanos);
    }

    /**
     * 将真实时间转换为逻辑时间，用于查询逻辑时间存储的数据。
     */
    public LocalDateTime toLogicTime(LocalDateTime realTime) {
        if (realTime == null) {
            return null;
        }
        long realSeconds = Duration.between(startRealDateTime, realTime).getSeconds();
        double logicSeconds = realSeconds * 60.0 / (timeScaleMs / 1000.0);
        return startTime.plusSeconds((long) logicSeconds);
    }

    /**
     * 逻辑秒转换为真实秒（四舍五入）。
     */
    public long logicSecondsToRealSeconds(long logicSeconds) {
        double realSeconds = logicSeconds * (timeScaleMs / 1000.0) / 60.0;
        return Math.round(realSeconds);
    }

    /**
     * 真实秒转换为逻辑秒（四舍五入）。
     */
    public long realSecondsToLogicSeconds(long realSeconds) {
        double logicSeconds = realSeconds * 60.0 / (timeScaleMs / 1000.0);
        return Math.round(logicSeconds);
    }
}
