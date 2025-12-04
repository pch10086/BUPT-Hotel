package com.bupt.hotel.service;

import com.bupt.hotel.entity.BillingDetail;
import com.bupt.hotel.entity.FanSpeed;
import com.bupt.hotel.repository.BillingDetailRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

@Service
public class ReportService {

    @Autowired
    private BillingDetailRepository billingDetailRepository;

    @Data
    public static class GlobalReport {
        private double totalFee;
        private long totalDurationSeconds;
        private Map<String, Double> roomFeeRanking;
        private Map<FanSpeed, Long> fanSpeedUsageDuration;
        private int totalServiceCount;
    }

    public GlobalReport generateGlobalReport(LocalDateTime start, LocalDateTime end) {
        List<BillingDetail> details = billingDetailRepository.findByStartTimeBetween(start, end);

        GlobalReport report = new GlobalReport();

        // 1. 总费用 (基于详单累加更精确，或者基于账单)
        // 这里使用详单累加，因为账单可能跨越时间段
        double totalFee = details.stream().mapToDouble(BillingDetail::getFee).sum();
        report.setTotalFee(Math.round(totalFee * 100.0) / 100.0);

        // 2. 总服务时长
        long totalDuration = details.stream().mapToLong(BillingDetail::getDuration).sum();
        report.setTotalDurationSeconds(totalDuration);

        // 3. 各房间费用排名
        Map<String, Double> ranking = details.stream()
                .collect(Collectors.groupingBy(
                        BillingDetail::getRoomId,
                        Collectors.summingDouble(BillingDetail::getFee)));
        // 排序
        Map<String, Double> sortedRanking = ranking.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Math.round(e.getValue() * 100.0) / 100.0,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new));
        report.setRoomFeeRanking(sortedRanking);

        // 4. 各风速使用时长占比
        Map<FanSpeed, Long> fanStats = details.stream()
                .collect(Collectors.groupingBy(
                        BillingDetail::getFanSpeed,
                        Collectors.summingLong(BillingDetail::getDuration)));
        report.setFanSpeedUsageDuration(fanStats);

        // 5. 总服务次数
        report.setTotalServiceCount(details.size());

        return report;
    }
}
