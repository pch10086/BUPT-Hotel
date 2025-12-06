package com.bupt.hotel.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import jakarta.persistence.Transient;

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
    private Double initialTemp; // 保留用于兼容，实际使用initialTempCool和initialTempHeat

    @Column(name = "initial_temp_cool", nullable = false)
    private Double initialTempCool; // 制冷模式下的初始温度

    @Column(name = "initial_temp_heat", nullable = false)
    private Double initialTempHeat; // 制热模式下的初始温度

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

    @Transient
    private Double currentSessionFee; // 当前送风会话费用（非持久化）
}
