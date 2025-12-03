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

    public String exportBillAndDetail(String roomId) {
        StringBuilder sb = new StringBuilder();

        // 1. 获取数据
        List<BillingRecord> records = billingRecordRepository.findByRoomId(roomId);
        // 取最新的账单
        BillingRecord acBill = records.isEmpty() ? null : records.get(records.size() - 1);

        List<LodgingBill> lodgingBills = lodgingBillRepository.findByRoomId(roomId);
        // 过滤出该房间最新的住宿账单
        LodgingBill lodgingBill = lodgingBills.isEmpty() ? null : lodgingBills.get(lodgingBills.size() - 1);

        List<BillingDetail> details = billingDetailRepository.findByRoomId(roomId);

        // 2. 拼接文本 (TXT格式)
        sb.append("========== 酒店账单 ==========\n");
        sb.append("房间号: ").append(roomId).append("\n");
        if (lodgingBill != null) {
            sb.append("入住时间: ").append(lodgingBill.getCheckInTime()).append("\n");
            sb.append("退房时间: ").append(lodgingBill.getCheckOutTime()).append("\n");
            sb.append("入住天数: ").append(lodgingBill.getDays()).append("\n");
            sb.append("住宿费用: ").append(lodgingBill.getTotalLodgingFee()).append("\n");
        }
        if (acBill != null) {
            sb.append("空调总费用: ").append(acBill.getTotalAcFee()).append("\n");
        }
        sb.append("------------------------------\n");
        sb.append("总计费用: ").append(
                (lodgingBill != null ? lodgingBill.getTotalLodgingFee() : 0) +
                        (acBill != null ? acBill.getTotalAcFee() : 0))
                .append("\n");
        sb.append("\n");

        sb.append("========== 空调详单 ==========\n");
        sb.append(String.format("%-20s %-10s %-10s %-10s %-10s\n", "开始时间", "时长(s)", "风速", "费用", "累积"));
        for (BillingDetail d : details) {
            sb.append(String.format("%-20s %-10d %-10s %-10.2f %-10.2f\n",
                    d.getStartTime().toString().replace("T", " "),
                    d.getDuration(),
                    d.getFanSpeed(),
                    d.getFee(),
                    d.getCumulativeFee()));
        }

        return sb.toString();
    }
}
