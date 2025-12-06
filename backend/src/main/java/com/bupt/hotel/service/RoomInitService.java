package com.bupt.hotel.service;

import com.bupt.hotel.entity.FanSpeed;
import com.bupt.hotel.entity.Mode;
import com.bupt.hotel.entity.Room;
import com.bupt.hotel.entity.RoomStatus;
import com.bupt.hotel.repository.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class RoomInitService {

    @Autowired
    private RoomRepository roomRepository;

    // 现有5个房间的配置（根据prompt.md）
    // 第一层的前5个房间（101-105）对应原设置的5个房间
    // 制冷模式初始温度：
    // - 101 → 32.0℃, 100.0元/天
    // - 102 → 28.0℃, 125.0元/天
    // - 103 → 30.0℃, 150.0元/天
    // - 104 → 29.0℃, 200.0元/天
    // - 105 → 35.0℃, 100.0元/天
    // 制热模式初始温度：
    // - 101 → 10.0℃, 100.0元/天
    // - 102 → 15.0℃, 125.0元/天
    // - 103 → 18.0℃, 150.0元/天
    // - 104 → 12.0℃, 200.0元/天
    // - 105 → 14.0℃, 100.0元/天
    private static final Map<String, RoomConfig> EXISTING_ROOMS = new HashMap<>();

    static {
        // 第一层前5个房间使用原配置（制冷和制热初始温度不同）
        EXISTING_ROOMS.put("101", new RoomConfig(32.0, 10.0, 100.0)); // 原101
        EXISTING_ROOMS.put("102", new RoomConfig(28.0, 15.0, 125.0)); // 原102
        EXISTING_ROOMS.put("103", new RoomConfig(30.0, 18.0, 150.0)); // 原103
        EXISTING_ROOMS.put("104", new RoomConfig(29.0, 12.0, 200.0)); // 原104
        EXISTING_ROOMS.put("105", new RoomConfig(35.0, 14.0, 100.0)); // 原105
    }

    private static class RoomConfig {
        double initialTempCool; // 制冷模式初始温度
        double initialTempHeat; // 制热模式初始温度
        double pricePerDay;

        RoomConfig(double initialTempCool, double initialTempHeat, double pricePerDay) {
            this.initialTempCool = initialTempCool;
            this.initialTempHeat = initialTempHeat;
            this.pricePerDay = pricePerDay;
        }
    }

    @PostConstruct
    @Transactional
    public void initRooms() {
        log.info("Initializing/Updating 40 rooms (4 floors, 10 rooms per floor)...");

        // 生成40个房间：4层，每层10间
        for (int floor = 1; floor <= 4; floor++) {
            for (int roomNum = 1; roomNum <= 10; roomNum++) {
                String roomId = String.format("%d%02d", floor, roomNum);

                // 检查房间是否已存在，不存在则创建
                Room room = roomRepository.findByRoomId(roomId).orElseGet(() -> {
                    Room newRoom = new Room();
                    newRoom.setRoomId(roomId);
                    return newRoom;
                });

                boolean isNewRoom = room.getId() == null; // 判断是否是新创建的房间

                // 检查是否是101-105房间（优先级最高,强制更新）
                RoomConfig config = EXISTING_ROOMS.get(roomId);
                if (config != null) {
                    // 强制更新101-105房间配置
                    room.setInitialTempCool(config.initialTempCool);
                    room.setInitialTempHeat(config.initialTempHeat);
                    room.setInitialTemp(config.initialTempCool);
                    room.setPricePerDay(config.pricePerDay);

                    if (isNewRoom) {
                        room.setCurrentTemp(config.initialTempCool);
                        room.setTargetTemp(25.0);
                        room.setFanSpeed(FanSpeed.MIDDLE);
                        room.setMode(Mode.COOL);
                        room.setIsOn(false);
                        room.setStatus(RoomStatus.SHUTDOWN);
                        room.setTotalFee(0.0);
                    }

                    log.info("✓ {} room {}: price={}元/天, coolTemp={}℃, heatTemp={}℃",
                            (isNewRoom ? "Created" : "Updated"),
                            roomId, config.pricePerDay, config.initialTempCool, config.initialTempHeat);
                } else if (isNewRoom) {
                    // 其他新房间使用默认配置
                    boolean isKingBed = (roomNum == 1); // 每层第一个房间是大床房
                    room.setPricePerDay(isKingBed ? 200.0 : 100.0);
                    double coolTemp = isKingBed ? 25.0 : 28.0;
                    double heatTemp = isKingBed ? 20.0 : 15.0;
                    room.setInitialTempCool(coolTemp);
                    room.setInitialTempHeat(heatTemp);
                    room.setInitialTemp(coolTemp);
                    room.setCurrentTemp(coolTemp);
                    room.setTargetTemp(25.0);
                    room.setFanSpeed(FanSpeed.MIDDLE);
                    room.setMode(Mode.COOL);
                    room.setIsOn(false);
                    room.setStatus(RoomStatus.SHUTDOWN);
                    room.setTotalFee(0.0);
                }

                roomRepository.save(room);
                roomRepository.flush();
            }
        }

        // 再次验证101-105的费用是否正确
        log.info("Final verification of room prices for 101-105:");
        for (String roomId : EXISTING_ROOMS.keySet()) {
            Room room = roomRepository.findByRoomId(roomId).orElse(null);
            if (room != null) {
                RoomConfig expected = EXISTING_ROOMS.get(roomId);
                Double actualPrice = room.getPricePerDay();
                boolean match = (actualPrice != null && Math.abs(expected.pricePerDay - actualPrice) < 0.01);
                log.info("  Room {}: expected={}元/天, actual={}元/天 {}",
                        roomId, expected.pricePerDay, actualPrice,
                        (match ? "✓" : "✗ MISMATCH!"));
            }
        }

        log.info("Successfully initialized/updated all 40 rooms");
    }
}
