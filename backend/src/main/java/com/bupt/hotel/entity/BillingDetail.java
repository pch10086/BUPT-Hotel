package com.bupt.hotel.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "billing_detail")
public class BillingDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id")
    private String roomId;

    @Column(name = "request_time")
    private LocalDateTime requestTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration")
    private Long duration; // Seconds

    @Enumerated(EnumType.STRING)
    @Column(name = "fan_speed")
    private FanSpeed fanSpeed;

    @Column(name = "fee")
    private Double fee;

    @Column(name = "cumulative_fee")
    private Double cumulativeFee;

    @Column(name = "billing_record_id")
    private Long billingRecordId;
}
