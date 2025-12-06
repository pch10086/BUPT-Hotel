package com.bupt.hotel.controller;

import com.bupt.hotel.entity.BillingDetail;
import com.bupt.hotel.entity.BillingRecord;
import com.bupt.hotel.entity.LodgingBill;
import com.bupt.hotel.entity.Room;
import com.bupt.hotel.repository.RoomRepository;
import com.bupt.hotel.service.BillingService;
import com.bupt.hotel.service.TimeService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clerk")
public class ClerkController {

    @Autowired
    private BillingService billingService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private TimeService timeService;

    @Autowired
    private com.bupt.hotel.service.SchedulerService schedulerService;

    @Data
    public static class CheckInRequest {
        private String roomId;
        private String customerName;
        private String idCard;
    }

    @Data
    public static class BillingRecordView {
        private Long id;
        private String roomId;
        private LocalDateTime checkInTime;
        private LocalDateTime checkOutTime;
        private Double totalAcFee;
        private LocalDateTime createdAt;
    }

    @Data
    public static class LodgingBillView {
        private Long id;
        private String roomId;
        private LocalDateTime checkInTime;
        private LocalDateTime checkOutTime;
        private Integer days;
        private Double totalLodgingFee;
        private LocalDateTime createdAt;
    }

    @Data
    public static class BillingDetailView {
        private Long id;
        private String roomId;
        private LocalDateTime requestTime;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long duration;
        private com.bupt.hotel.entity.FanSpeed fanSpeed;
        private Double fee;
        private Double cumulativeFee;
        private Long billingRecordId;
    }

    @PostMapping("/checkin")
    public Room checkIn(@RequestBody CheckInRequest req) {
        Room room = roomRepository.findByRoomId(req.getRoomId()).orElseThrow();
        room.setCustomerName(req.getCustomerName());
        // 身份证号可以为空，空字符串转换为null
        room.setIdCard(req.getIdCard() != null && !req.getIdCard().trim().isEmpty()
                ? req.getIdCard()
                : null);
        room.setCheckInTime(timeService.getCurrentTime());
        room.setTotalFee(0.0);
        // 重置空调状态
        room.setIsOn(false);
        room.setStatus(com.bupt.hotel.entity.RoomStatus.SHUTDOWN);
        return roomRepository.save(room);
    }

    @PostMapping("/checkout/ac")
    public BillingRecordView checkoutAc(@RequestParam String roomId) {
        return toBillingRecordView(billingService.generateAcBill(roomId));
    }

    @PostMapping("/checkout/lodging")
    public LodgingBillView checkoutLodging(@RequestParam String roomId) {
        return toLodgingBillView(billingService.generateLodgingBill(roomId));
    }

    @GetMapping("/details")
    public List<BillingDetailView> getDetails(@RequestParam String roomId) {
        return billingService.getDetails(roomId)
                .stream()
                .map(this::toBillingDetailView)
                .collect(Collectors.toList());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportBill(@RequestParam String roomId) {
        String content = billingService.exportBillAndDetail(roomId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bill_" + roomId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @PostMapping("/checkout/confirm")
    public Room confirmCheckout(@RequestParam String roomId) {
        Room room = roomRepository.findByRoomId(roomId).orElseThrow();

        // 先停止空调服务（如果正在运行），从服务队列和等待队列中移除
        schedulerService.stopSupply(roomId, true);

        // 清除入住信息
        room.setCustomerName(null);
        room.setIdCard(null);
        room.setCheckInTime(null);
        room.setTotalFee(0.0);
        // 重置空调状态
        room.setIsOn(false);
        room.setStatus(com.bupt.hotel.entity.RoomStatus.SHUTDOWN);

        return roomRepository.save(room);
    }

    private BillingRecordView toBillingRecordView(BillingRecord record) {
        BillingRecordView view = new BillingRecordView();
        view.setId(record.getId());
        view.setRoomId(record.getRoomId());
        view.setTotalAcFee(record.getTotalAcFee());
        view.setCheckInTime(timeService.toRealTime(record.getCheckInTime()));
        view.setCheckOutTime(timeService.toRealTime(record.getCheckOutTime()));
        view.setCreatedAt(timeService.toRealTime(record.getCreatedAt()));
        return view;
    }

    private LodgingBillView toLodgingBillView(LodgingBill bill) {
        LodgingBillView view = new LodgingBillView();
        view.setId(bill.getId());
        view.setRoomId(bill.getRoomId());
        view.setDays(bill.getDays());
        view.setTotalLodgingFee(bill.getTotalLodgingFee());
        view.setCheckInTime(timeService.toRealTime(bill.getCheckInTime()));
        view.setCheckOutTime(timeService.toRealTime(bill.getCheckOutTime()));
        view.setCreatedAt(timeService.toRealTime(bill.getCreatedAt()));
        return view;
    }

    private BillingDetailView toBillingDetailView(BillingDetail detail) {
        BillingDetailView view = new BillingDetailView();
        view.setId(detail.getId());
        view.setRoomId(detail.getRoomId());
        view.setRequestTime(timeService.toRealTime(detail.getRequestTime()));
        view.setStartTime(timeService.toRealTime(detail.getStartTime()));
        view.setEndTime(timeService.toRealTime(detail.getEndTime()));
        view.setDuration(timeService.logicSecondsToRealSeconds(detail.getDuration()));
        view.setFanSpeed(detail.getFanSpeed());
        view.setFee(detail.getFee());
        view.setCumulativeFee(detail.getCumulativeFee());
        view.setBillingRecordId(detail.getBillingRecordId());
        return view;
    }
}
