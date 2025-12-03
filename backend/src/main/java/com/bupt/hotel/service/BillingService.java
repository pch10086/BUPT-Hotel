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
        // 添加 BOM 以支持 Excel 打开 UTF-8 CSV
        sb.append('\uFEFF');

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss");

        // 1. 获取数据
        List<BillingRecord> records = billingRecordRepository.findByRoomId(roomId);
        // 取最新的账单
        BillingRecord acBill = records.isEmpty() ? null : records.get(records.size() - 1);

        List<LodgingBill> lodgingBills = lodgingBillRepository.findByRoomId(roomId);
        // 过滤出该房间最新的住宿账单
        LodgingBill lodgingBill = lodgingBills.isEmpty() ? null : lodgingBills.get(lodgingBills.size() - 1);

        List<BillingDetail> details = billingDetailRepository.findByRoomId(roomId);

        // 2. 拼接 CSV 格式
        sb.append("=== 酒店账单 ===\n");
        sb.append("房间号,入住时间,退房时间,入住天数,住宿费用,空调总费用,总计费用\n");

        double lodgingFee = lodgingBill != null ? lodgingBill.getTotalLodgingFee() : 0.0;
        double acFee = acBill != null ? acBill.getTotalAcFee() : 0.0;
        double totalFee = lodgingFee + acFee;

        String checkIn = "";
        String checkOut = "";
        String days = "0";

        if (lodgingBill != null) {
            checkIn = lodgingBill.getCheckInTime().format(formatter);
            checkOut = lodgingBill.getCheckOutTime().format(formatter);
            days = String.valueOf(lodgingBill.getDays());
        } else if (acBill != null) {
            checkIn = acBill.getCheckInTime().format(formatter);
            checkOut = acBill.getCheckOutTime().format(formatter);
        }

        sb.append(roomId).append(",")
                .append("=\"").append(checkIn).append("\",")
                .append("=\"").append(checkOut).append("\",")
                .append(days).append(",")
                .append(String.format("%.2f", lodgingFee)).append(",")
                .append(String.format("%.2f", acFee)).append(",")
                .append(String.format("%.2f", totalFee)).append("\n");
        sb.append("\n");

        sb.append("=== 空调详单 ===\n");
        sb.append("房间号,请求时间,服务开始时间,服务结束时间,服务时长(s),风速,当前费用,累积费用\n");
        for (BillingDetail d : details) {
            String reqTime = d.getRequestTime() != null ? d.getRequestTime().format(formatter) : "";
            String startTime = d.getStartTime() != null ? d.getStartTime().format(formatter) : "";
            String endTime = d.getEndTime() != null ? d.getEndTime().format(formatter) : "";

            sb.append(d.getRoomId()).append(",")
                    .append("=\"").append(reqTime).append("\",")
                    .append("=\"").append(startTime).append("\",")
                    .append("=\"").append(endTime).append("\",")
                    .append(d.getDuration()).append(",")
                    .append(d.getFanSpeed()).append(",")
                    .append(String.format("%.2f", d.getFee())).append(",")
                    .append(String.format("%.2f", d.getCumulativeFee())).append("\n");
        }

        return sb.toString();
    }
}
