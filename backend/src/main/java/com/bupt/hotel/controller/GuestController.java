package com.bupt.hotel.controller;

import com.bupt.hotel.entity.FanSpeed;
import com.bupt.hotel.entity.Mode;
import com.bupt.hotel.entity.Room;
import com.bupt.hotel.repository.RoomRepository;
import com.bupt.hotel.service.SchedulerService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

        // 验证：只有已入住的房间才能开机
        if (room.getCustomerName() == null || room.getCustomerName().trim().isEmpty()) {
            throw new RuntimeException("房间未办理入住，无法开机");
        }
        // 注意：不要事先把 room.mode 写入数据库，否则 SchedulerService.requestSupply
        // 无法感知到模式变化（oldMode != mode）的情况，导致无法把 currentTemp 重置为
        // 对应模式的初始温度。因此这里直接调用调度，调度内部会更新 room.mode/target/fan。
        // 当请求中未提供 targetTemp 时，使用模式对应的缺省目标温度：制冷=25℃，制热=23℃（与 prompt.md 规范一致）
        Double providedTarget = req.getTargetTemp();
        Double targetToUse;
        if (providedTarget == null) {
            if (req.getMode() == Mode.HEAT) {
                targetToUse = 23.0;
            } else {
                targetToUse = 25.0;
            }
        } else {
            targetToUse = providedTarget;
        }

        // 如果用户选择的模式与数据库中当前模式不同，立即把 currentTemp 设置为对应模式的初始温度，
        // 以保证用户在前端切换模式并开机时能看到预期的初始化温度（即使房间先前已开机）。
        Room before = roomRepository.findByRoomId(req.getRoomId()).orElseThrow();
        Mode oldMode = before.getMode();
        if (oldMode == null || oldMode != req.getMode()) {
            double initTemp;
            if (req.getMode() == Mode.HEAT) {
                initTemp = before.getInitialTempHeat() != null ? before.getInitialTempHeat() : before.getInitialTemp();
            } else {
                initTemp = before.getInitialTempCool() != null ? before.getInitialTempCool() : before.getInitialTemp();
            }
            before.setCurrentTemp(initTemp);
            roomRepository.save(before);
        }

        schedulerService.requestSupply(req.getRoomId(), req.getMode(), targetToUse, req.getFanSpeed());

        // 调度触发后，将房间标记为开机状态（并保存最新的房间信息）
        Room updated = roomRepository.findByRoomId(req.getRoomId()).orElseThrow();
        updated.setIsOn(true);
        roomRepository.save(updated);

        return roomRepository.findByRoomId(req.getRoomId()).orElseThrow();
    }

    @PostMapping("/powerOff")
    public Room powerOff(@RequestParam String roomId) {
        Room room = roomRepository.findByRoomId(roomId).orElseThrow();

        // 如果之前是开机状态，增加开关机次数（每次开关机算一天）
        if (room.getIsOn() != null && room.getIsOn()) {
            int count = (room.getPowerCycleCount() != null) ? room.getPowerCycleCount() : 0;
            room.setPowerCycleCount(count + 1);
        }

        room.setIsOn(false);
        roomRepository.save(room);

        schedulerService.stopSupply(roomId, true);
        return room;
    }

    @PostMapping("/changeState")
    public Room changeState(@RequestBody ControlRequest req) {
        Room room = roomRepository.findByRoomId(req.getRoomId()).orElseThrow();

        // 验证：只有已入住的房间才能调整状态
        if (room.getCustomerName() == null || room.getCustomerName().trim().isEmpty()) {
            throw new RuntimeException("房间未办理入住，无法调整状态");
        }

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
