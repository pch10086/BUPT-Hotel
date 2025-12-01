package com.bupt.hotel.controller;

import com.bupt.hotel.entity.BillingDetail;
import com.bupt.hotel.entity.BillingRecord;
import com.bupt.hotel.entity.LodgingBill;
import com.bupt.hotel.entity.Room;
import com.bupt.hotel.repository.RoomRepository;
import com.bupt.hotel.service.BillingService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clerk")
public class ClerkController {

    @Autowired
    private BillingService billingService;

    @Autowired
    private RoomRepository roomRepository;

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
        room.setIdCard(req.getIdCard());
        room.setCheckInTime(java.time.LocalDateTime.now());
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
}
