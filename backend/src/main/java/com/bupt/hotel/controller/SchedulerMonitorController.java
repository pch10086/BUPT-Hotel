package com.bupt.hotel.controller;

import com.bupt.hotel.service.SchedulerService;
import com.bupt.hotel.service.SchedulerService.ServiceUnit;
import com.bupt.hotel.service.SchedulerService.WaitingInfo;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitor")
public class SchedulerMonitorController {

    @Autowired
    private SchedulerService schedulerService;

    @Data
    public static class ServiceSnapshot {
        private String roomId;
        private String fanSpeed;
        private long servedDurationSeconds;
        private double currentFee;
    }

    @Data
    public static class WaitingSnapshot {
        private String roomId;
        private String fanSpeed;
        private long waitTimeRemaining;
        private long totalWaitedTime;
        private boolean priorityBoosted;
    }

    @Data
    public static class SnapshotResponse {
        private LocalDateTime timestamp;
        private List<ServiceSnapshot> serviceQueue;
        private List<WaitingSnapshot> waitingQueue;
    }

    @GetMapping("/snapshot")
    public SnapshotResponse snapshot() {
        SnapshotResponse resp = new SnapshotResponse();
        resp.setTimestamp(LocalDateTime.now());

        Map<String, ServiceUnit> services = schedulerService.getServiceQueue();
        resp.setServiceQueue(services.values().stream().map(u -> {
            ServiceSnapshot s = new ServiceSnapshot();
            s.setRoomId(u.getRoomId());
            s.setFanSpeed(u.getFanSpeed() == null ? null : u.getFanSpeed().name());
            s.setServedDurationSeconds(u.getServedDurationSeconds());
            s.setCurrentFee(u.getCurrentFee());
            return s;
        }).collect(Collectors.toList()));

        Map<String, WaitingInfo> waits = schedulerService.getWaitingQueue();
        resp.setWaitingQueue(waits.values().stream().map(w -> {
            WaitingSnapshot ws = new WaitingSnapshot();
            ws.setRoomId(w.getRoomId());
            ws.setFanSpeed(w.getFanSpeed() == null ? null : w.getFanSpeed().name());
            ws.setWaitTimeRemaining(w.getWaitTimeRemaining());
            ws.setTotalWaitedTime(w.getTotalWaitedTime());
            ws.setPriorityBoosted(w.isPriorityBoosted());
            return ws;
        }).collect(Collectors.toList()));

        return resp;
    }
}
