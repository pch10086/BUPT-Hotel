package com.bupt.hotel.repository;

import com.bupt.hotel.entity.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {
    List<BillingRecord> findByRoomId(String roomId);
}
