package com.bupt.hotel.controller;

import com.bupt.hotel.service.TimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private TimeService timeService;

    @GetMapping("/time")
    public long getTime() {
        // 返回逻辑时间的时间戳 (毫秒)
        return timeService.getCurrentTime().toInstant(ZoneOffset.of("+8")).toEpochMilli();
    }
}
