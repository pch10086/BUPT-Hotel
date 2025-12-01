package com.bupt.hotel.repository;

import com.bupt.hotel.entity.LodgingBill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LodgingBillRepository extends JpaRepository<LodgingBill, Long> {
    List<LodgingBill> findByRoomId(String roomId);
}
