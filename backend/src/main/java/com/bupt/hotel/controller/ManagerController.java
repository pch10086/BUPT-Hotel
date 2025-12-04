package com.bupt.hotel.controller;

import com.bupt.hotel.entity.Room;
import com.bupt.hotel.repository.RoomRepository;
import com.bupt.hotel.service.ReportService;
import com.bupt.hotel.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private ReportService reportService;

    @GetMapping("/rooms")
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @GetMapping("/queue/service")
    public Map<String, SchedulerService.ServiceUnit> getServiceQueue() {
        return schedulerService.getServiceQueue();
    }

    @GetMapping("/queue/waiting")
    public Map<String, SchedulerService.WaitingInfo> getWaitingQueue() {
        return schedulerService.getWaitingQueue();
    }

    @GetMapping("/report")
    public ReportService.GlobalReport getReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return reportService.generateGlobalReport(start, end);
    }
}
