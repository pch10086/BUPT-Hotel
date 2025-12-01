package com.bupt.hotel.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "lodging_bill")
public class LodgingBill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id")
    private String roomId;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "days")
    private Integer days;

    @Column(name = "total_lodging_fee")
    private Double totalLodgingFee;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
