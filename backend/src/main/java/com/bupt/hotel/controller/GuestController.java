package com.bupt.hotel.controller;

import com.bupt.hotel.entity.*;
import com.bupt.hotel.repository.RoomRepository;
import com.bupt.hotel.service.SchedulerService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/guest")
public class GuestController {

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private RoomRepository roomRepository;

    @Data
    public static class PowerOnRequest {
        private String roomId;
        private Mode mode;
        private Double targetTemp;
        private FanSpeed fanSpeed;
    }

    @Data
    public static class ControlRequest {
        private String roomId;
        private Double targetTemp;
        private FanSpeed fanSpeed;
    }

    @PostMapping("/powerOn")
    public Room powerOn(@RequestBody PowerOnRequest req) {
        Room room = roomRepository.findByRoomId(req.getRoomId()).orElseThrow();
        room.setIsOn(true);
        room.setCheckInTime(LocalDateTime.now());
        room.setTotalFee(0.0);
        roomRepository.save(room);

        schedulerService.requestSupply(req.getRoomId(), req.getMode(), req.getTargetTemp(), req.getFanSpeed());
        return room;
    }

    @PostMapping("/powerOff")
    public Room powerOff(@RequestParam String roomId) {
        Room room = roomRepository.findByRoomId(roomId).orElseThrow();
        room.setIsOn(false);
        roomRepository.save(room);

        schedulerService.stopSupply(roomId, true);
        return room;
    }

    @PostMapping("/changeState")
    public Room changeState(@RequestBody ControlRequest req) {
        Room room = roomRepository.findByRoomId(req.getRoomId()).orElseThrow();
        if (!room.getIsOn()) {
            throw new RuntimeException("Room is OFF");
        }
        // 只有风速改变才触发重新调度，温度改变只更新参数
        schedulerService.requestSupply(req.getRoomId(), room.getMode(), req.getTargetTemp(), req.getFanSpeed());
        return roomRepository.findByRoomId(req.getRoomId()).orElseThrow();
    }

    @GetMapping("/status")
    public Room getStatus(@RequestParam String roomId) {
        return roomRepository.findByRoomId(roomId).orElseThrow();
    }
}
