package com.bupt.hotel.entity;

public enum RoomStatus {
    SHUTDOWN, // 关机
    IDLE, // 开机但未送风 (如达到目标温度回温中)
    WAITING, // 等待队列中
    SERVING // 服务队列中 (正在送风)
}
