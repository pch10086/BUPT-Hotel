package com.bupt.hotel.controller;

import com.bupt.hotel.entity.FanSpeed;
import com.bupt.hotel.entity.Mode;
import com.bupt.hotel.entity.Room;
import com.bupt.hotel.repository.RoomRepository;
import com.bupt.hotel.service.SchedulerService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(GuestController.class);

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
    public java.util.Map<String, Object> powerOn(@RequestBody PowerOnRequest req) {
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
        boolean tempInvalid = false;
        String warning = null;
        if (providedTarget == null) {
            if (req.getMode() == Mode.HEAT) {
                targetToUse = 23.0;
            } else {
                targetToUse = 25.0;
            }
        } else {
            targetToUse = providedTarget;
        }

        // 验证目标温度是否在允许范围内（若有提供或使用缺省值）
        try {
            validateTargetTemp(req.getMode(), targetToUse);
        } catch (IllegalArgumentException ex) {
            // 忽略非法目标温度，但仍按用户选择的风速开机
            tempInvalid = true;
            warning = ex.getMessage();
            logger.warn("Ignored invalid target temp during powerOn for room {}: {}", req.getRoomId(), ex.getMessage());
            // 选择一个安全的 targetToUse：优先使用房间已有目标，否则使用模式默认
            Room existing = roomRepository.findByRoomId(req.getRoomId()).orElse(null);
            if (existing != null && existing.getTargetTemp() != null) {
                targetToUse = existing.getTargetTemp();
            } else {
                targetToUse = (req.getMode() == Mode.HEAT) ? 23.0 : 25.0;
            }
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

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("room", roomRepository.findByRoomId(req.getRoomId()).orElseThrow());
        if (tempInvalid) {
            resp.put("warning", warning);
        }
        return resp;
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
    public java.util.Map<String, Object> changeState(@RequestBody ControlRequest req) {
        Room room = roomRepository.findByRoomId(req.getRoomId()).orElseThrow();

        // 验证：只有已入住的房间才能调整状态
        if (room.getCustomerName() == null || room.getCustomerName().trim().isEmpty()) {
            throw new RuntimeException("房间未办理入住，无法调整状态");
        }

        // changeState 需要房间已设置模式（mode），否则无法确定校验范围
        if (room.getMode() == null) {
            throw new RuntimeException("房间当前未设置模式，无法调整状态");
        }

        Double providedTarget = req.getTargetTemp();
        Double targetToUse = null;
        boolean tempInvalid = false;
        String warning = null;

        // If provided target is invalid, we should IGNORE the target change but still
        // process fanSpeed.
        if (providedTarget != null) {
            try {
                // validate against the room's current mode
                validateTargetTemp(room.getMode(), providedTarget);
                targetToUse = providedTarget;
            } catch (IllegalArgumentException ex) {
                tempInvalid = true;
                warning = ex.getMessage();
                logger.warn("Ignored invalid target temp for room {}: {}", req.getRoomId(), ex.getMessage());
            }
        }

        // Determine a final target to pass to scheduler: prefer provided valid target,
        // then room.targetTemp, then defaults
        if (targetToUse == null) {
            targetToUse = room.getTargetTemp();
            if (targetToUse == null) {
                if (room.getMode() == Mode.HEAT) {
                    targetToUse = 23.0;
                } else {
                    targetToUse = 25.0;
                }
            }
        }

        // Call scheduler with the room's current mode (changeState shouldn't change
        // mode)
        schedulerService.requestSupply(req.getRoomId(), room.getMode(), targetToUse, req.getFanSpeed());

        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("room", roomRepository.findByRoomId(req.getRoomId()).orElseThrow());
        if (tempInvalid) {
            resp.put("warning", warning);
        }
        return resp;
    }

    @GetMapping("/status")
    public Room getStatus(@RequestParam String roomId) {
        return roomRepository.findByRoomId(roomId).orElseThrow();
    }

    /**
     * Validate that target temperature falls within allowed ranges for the given
     * mode.
     * If target is null, validation is skipped (caller may supply a default later).
     */
    private void validateTargetTemp(Mode mode, Double target) {
        if (target == null)
            return;
        if (mode == Mode.COOL) {
            if (target < 18.0 || target > 28.0) {
                throw new IllegalArgumentException("Cooling target temp must be between 18 and 28");
            }
        } else if (mode == Mode.HEAT) {
            if (target < 18.0 || target > 25.0) {
                throw new IllegalArgumentException("Heating target temp must be between 18 and 25");
            }
        }
    }
}
