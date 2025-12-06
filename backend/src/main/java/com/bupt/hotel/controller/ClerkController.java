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

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/clerk")
public class ClerkController {

    @Autowired
    private BillingService billingService;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private TimeService timeService;

    @Data
    public static class CheckInRequest {
        private String roomId;
        private String customerName;
        private String idCard;
    }

    @PostMapping("/checkin")
    public Room checkIn(@RequestBody CheckInRequest req) {
        Room room = roomRepository.findByRoomId(req.getRoomId()).orElseThrow();
        room.setCustomerName(req.getCustomerName());
        // 身份证号可以为空，空字符串转换为null
        room.setIdCard(req.getIdCard() != null && !req.getIdCard().trim().isEmpty() 
            ? req.getIdCard() : null);
        room.setCheckInTime(timeService.getCurrentTime());
        room.setTotalFee(0.0);
        // 重置空调状态
        room.setIsOn(false);
        room.setStatus(com.bupt.hotel.entity.RoomStatus.SHUTDOWN);
        return roomRepository.save(room);
    }

    @PostMapping("/checkout/ac")
    public BillingRecord checkoutAc(@RequestParam String roomId) {
        return billingService.generateAcBill(roomId);
    }

    @PostMapping("/checkout/lodging")
    public LodgingBill checkoutLodging(@RequestParam String roomId) {
        return billingService.generateLodgingBill(roomId);
    }

    @GetMapping("/details")
    public List<BillingDetail> getDetails(@RequestParam String roomId) {
        return billingService.getDetails(roomId);
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
}
