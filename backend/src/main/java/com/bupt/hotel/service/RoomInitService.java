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

    // 现有5个房间的配置
    // 对应关系：101-105依次对应原101-105
    // - 101 → 原101
    // - 102 → 原102
    // - 103 → 原103
    // - 104 → 原104
    // - 105 → 原105
    private static final Map<String, RoomConfig> EXISTING_ROOMS = new HashMap<>();
    
    static {
        EXISTING_ROOMS.put("101", new RoomConfig(32.0, 100.0)); // 原101
        EXISTING_ROOMS.put("102", new RoomConfig(28.0, 125.0)); // 原102
        EXISTING_ROOMS.put("103", new RoomConfig(30.0, 150.0)); // 原103
        EXISTING_ROOMS.put("104", new RoomConfig(29.0, 200.0)); // 原104
        EXISTING_ROOMS.put("105", new RoomConfig(35.0, 100.0)); // 原105
    }

    private static class RoomConfig {
        double initialTemp;
        double pricePerDay;

        RoomConfig(double initialTemp, double pricePerDay) {
            this.initialTemp = initialTemp;
            this.pricePerDay = pricePerDay;
        }
    }

    @PostConstruct
    @Transactional
    public void initRooms() {
        // 检查是否已经初始化过
        long count = roomRepository.count();
        if (count >= 40) {
            log.info("Rooms already initialized, count: {}", count);
            return;
        }

        log.info("Initializing 40 rooms (4 floors, 10 rooms per floor)...");

        // 生成40个房间：4层，每层10间
        for (int floor = 1; floor <= 4; floor++) {
            for (int roomNum = 1; roomNum <= 10; roomNum++) {
                String roomId = String.format("%d%02d", floor, roomNum);
                
                // 检查房间是否已存在
                if (roomRepository.findByRoomId(roomId).isPresent()) {
                    continue;
                }

                Room room = new Room();
                room.setRoomId(roomId);
                
                // 检查是否是现有房间配置
                RoomConfig config = EXISTING_ROOMS.get(roomId);
                if (config != null) {
                    // 使用现有配置
                    room.setInitialTemp(config.initialTemp);
                    room.setCurrentTemp(config.initialTemp);
                    room.setPricePerDay(config.pricePerDay);
                } else {
                    // 默认配置
                    // 每层第一个房间是大床房（200元/天），其他是标准客房（100元/天）
                    boolean isKingBed = (roomNum == 1);
                    room.setPricePerDay(isKingBed ? 200.0 : 100.0);
                    // 初始温度：大床房25℃，标准客房28℃
                    room.setInitialTemp(isKingBed ? 25.0 : 28.0);
                    room.setCurrentTemp(isKingBed ? 25.0 : 28.0);
                }
                
                room.setTargetTemp(25.0);
                room.setFanSpeed(FanSpeed.MIDDLE);
                room.setMode(Mode.COOL);
                room.setIsOn(false);
                room.setStatus(RoomStatus.SHUTDOWN);
                room.setTotalFee(0.0);

                roomRepository.save(room);
            }
        }

        log.info("Successfully initialized 40 rooms");
    }
}

