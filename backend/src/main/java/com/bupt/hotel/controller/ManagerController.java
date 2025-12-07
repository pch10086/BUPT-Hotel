package com.bupt.hotel.controller;

import com.bupt.hotel.entity.Room;
import com.bupt.hotel.repository.RoomRepository;
import com.bupt.hotel.service.ReportService;
import com.bupt.hotel.service.SchedulerService;
import com.bupt.hotel.service.TimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private TimeService timeService;

    @lombok.Data
    public static class ServiceUnitView {
        private String roomId;
        private LocalDateTime startTime;
        private com.bupt.hotel.entity.FanSpeed fanSpeed;
        private long servedDurationSeconds;
        private double currentFee;
    }

    @lombok.Data
    public static class WaitingInfoView {
        private String roomId;
        private com.bupt.hotel.entity.FanSpeed fanSpeed;
        private long waitTimeRemaining;
        private long totalWaitTime;
    }

    @GetMapping("/rooms")
    public List<Room> getAllRooms() {
        List<Room> rooms = roomRepository.findAll();
        // 填充当前会话费用（非持久化字段）
        // 使用内存缓存确保 totalFee 和 currentSessionFee 实时同步
        for (Room room : rooms) {
            double currentSessionFee = schedulerService.getCurrentSessionFee(room.getRoomId());
            room.setCurrentSessionFee(currentSessionFee);
            
            // 优先使用内存缓存中的总费用（实时更新），如果没有则使用数据库中的值
            Double cachedTotalFee = schedulerService.getCachedTotalFee(room.getRoomId());
            if (cachedTotalFee != null) {
                room.setTotalFee(cachedTotalFee);
            }
            // 如果缓存中没有，使用数据库中的值（并更新缓存）
            else if (room.getTotalFee() != null) {
                schedulerService.updateTotalFeeCache(room.getRoomId(), room.getTotalFee());
            }
        }
        return rooms;
    }

    @GetMapping("/queue/service")
    public Map<String, ServiceUnitView> getServiceQueue() {
        return schedulerService.getServiceQueue()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toRealServiceUnit(e.getValue())));
    }

    @GetMapping("/queue/waiting")
    public Map<String, WaitingInfoView> getWaitingQueue() {
        return schedulerService.getWaitingQueue()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toRealWaitingInfo(e.getValue())));
    }

    @GetMapping("/report")
    public ReportService.GlobalReport getReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        LocalDateTime logicStart = timeService.toLogicTime(start);
        LocalDateTime logicEnd = timeService.toLogicTime(end);
        return reportService.generateGlobalReport(logicStart, logicEnd);
    }

    private ServiceUnitView toRealServiceUnit(SchedulerService.ServiceUnit unit) {
        ServiceUnitView view = new ServiceUnitView();
        view.setRoomId(unit.getRoomId());
        view.setFanSpeed(unit.getFanSpeed());
        view.setCurrentFee(unit.getCurrentFee());
        view.setStartTime(timeService.toRealTime(unit.getStartTime()));
        view.setServedDurationSeconds(timeService.logicSecondsToRealSeconds(unit.getServedDurationSeconds()));
        return view;
    }

    private WaitingInfoView toRealWaitingInfo(SchedulerService.WaitingInfo info) {
        WaitingInfoView view = new WaitingInfoView();
        view.setRoomId(info.getRoomId());
        view.setFanSpeed(info.getFanSpeed());
        view.setWaitTimeRemaining(timeService.logicSecondsToRealSeconds(info.getWaitTimeRemaining()));
        view.setTotalWaitTime(timeService.logicSecondsToRealSeconds(info.getTotalWaitTime()));
        return view;
    }
}
