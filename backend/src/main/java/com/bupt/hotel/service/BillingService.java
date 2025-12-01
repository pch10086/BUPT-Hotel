package com.bupt.hotel.service;

import com.bupt.hotel.entity.*;
import com.bupt.hotel.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class BillingService {

    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private BillingRecordRepository billingRecordRepository;
    @Autowired
    private BillingDetailRepository billingDetailRepository;
    @Autowired
    private LodgingBillRepository lodgingBillRepository;

    @Transactional
    public BillingRecord generateAcBill(String roomId) {
        Room room = roomRepository.findByRoomId(roomId).orElseThrow();

        BillingRecord record = new BillingRecord();
        record.setRoomId(roomId);
        record.setCheckInTime(room.getCheckInTime());
        record.setCheckOutTime(LocalDateTime.now());
        record.setTotalAcFee(room.getTotalFee());
        record.setCreatedAt(LocalDateTime.now());

        BillingRecord saved = billingRecordRepository.save(record);

        // 关联详单 (将之前未关联的详单关联到此账单)
        List<BillingDetail> details = billingDetailRepository.findByRoomIdAndBillingRecordIdIsNull(roomId);
        for (BillingDetail d : details) {
            d.setBillingRecordId(saved.getId());
            billingDetailRepository.save(d);
        }

        return saved;
    }

    @Transactional
    public LodgingBill generateLodgingBill(String roomId) {
        Room room = roomRepository.findByRoomId(roomId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        LodgingBill bill = new LodgingBill();
        bill.setRoomId(roomId);
        bill.setCheckInTime(room.getCheckInTime());
        bill.setCheckOutTime(now);

        // 计算天数: 向上取整? 还是按自然日?
        // 假设: 只要入住就算1天，每过24h加1天
        long hours = Duration.between(room.getCheckInTime(), now).toHours();
        int days = (int) (hours / 24) + 1;

        bill.setDays(days);
        bill.setTotalLodgingFee(days * room.getPricePerDay());
        bill.setCreatedAt(now);

        return lodgingBillRepository.save(bill);
    }

    public List<BillingDetail> getDetails(String roomId) {
        return billingDetailRepository.findByRoomId(roomId);
    }
}
