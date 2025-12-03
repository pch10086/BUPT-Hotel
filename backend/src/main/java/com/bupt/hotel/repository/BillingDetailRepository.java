package com.bupt.hotel.repository;

import com.bupt.hotel.entity.BillingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface BillingDetailRepository extends JpaRepository<BillingDetail, Long> {
    List<BillingDetail> findByRoomId(String roomId);

    List<BillingDetail> findByRoomIdAndBillingRecordIdIsNull(String roomId);

    List<BillingDetail> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
}
