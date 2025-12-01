package com.bupt.hotel.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "room")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", unique = true, nullable = false)
    private String roomId;

    @Column(name = "current_temp")
    private Double currentTemp;

    @Column(name = "target_temp")
    private Double targetTemp;

    @Enumerated(EnumType.STRING)
    @Column(name = "fan_speed")
    private FanSpeed fanSpeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode")
    private Mode mode;

    @Column(name = "is_on")
    private Boolean isOn;

    @Column(name = "initial_temp")
    private Double initialTemp;

    @Column(name = "price_per_day")
    private Double pricePerDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private RoomStatus status;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "total_fee")
    private Double totalFee;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "id_card")
    private String idCard;
}
