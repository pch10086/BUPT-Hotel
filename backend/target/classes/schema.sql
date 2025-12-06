-- 房间表
CREATE TABLE IF NOT EXISTS `room` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `room_id` VARCHAR(20) NOT NULL UNIQUE COMMENT '房间号',
    `current_temp` DOUBLE NOT NULL COMMENT '当前温度',
    `target_temp` DOUBLE NOT NULL COMMENT '目标温度',
    `fan_speed` VARCHAR(10) NOT NULL COMMENT '风速: HIGH, MIDDLE, LOW',
    `mode` VARCHAR(10) NOT NULL COMMENT '模式: COOL, HEAT',
    `is_on` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否开机',
    `initial_temp` DOUBLE NOT NULL COMMENT '初始温度（兼容字段）',
    `initial_temp_cool` DOUBLE NOT NULL COMMENT '制冷模式初始温度',
    `initial_temp_heat` DOUBLE NOT NULL COMMENT '制热模式初始温度',
    `price_per_day` DOUBLE NOT NULL COMMENT '房价/每天',
    `status` VARCHAR(20) NOT NULL DEFAULT 'IDLE' COMMENT '状态: IDLE(空闲), SERVING(服务中), WAITING(等待中), SHUTDOWN(关机)',
    `check_in_time` DATETIME COMMENT '当前入住时间',
    `total_fee` DOUBLE DEFAULT 0.0 COMMENT '当前入住累计空调费用',
    `customer_name` VARCHAR(50) COMMENT '入住人姓名',
    `id_card` VARCHAR(20) COMMENT '身份证号',
    `power_cycle_count` INT DEFAULT 0 COMMENT '开关机次数（每次开关机算一天）'
);
-- 空调账单表 (总费用)
CREATE TABLE IF NOT EXISTS `billing_record` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `room_id` VARCHAR(20) NOT NULL,
    `check_in_time` DATETIME NOT NULL,
    `check_out_time` DATETIME NOT NULL,
    `total_ac_fee` DOUBLE NOT NULL COMMENT '空调总费用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 空调详单表 (详细记录)
CREATE TABLE IF NOT EXISTS `billing_detail` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `room_id` VARCHAR(20) NOT NULL,
    `request_time` DATETIME NOT NULL COMMENT '请求时间',
    `start_time` DATETIME NOT NULL COMMENT '服务开始时间',
    `end_time` DATETIME NOT NULL COMMENT '服务结束时间',
    `duration` BIGINT NOT NULL COMMENT '服务时长(秒)',
    `fan_speed` VARCHAR(10) NOT NULL COMMENT '风速',
    `fee` DOUBLE NOT NULL COMMENT '本次会话费用',
    `cumulative_fee` DOUBLE NOT NULL COMMENT '截至本次的累积费用',
    `billing_record_id` BIGINT COMMENT '关联的账单ID'
);
-- 住宿费账单表
CREATE TABLE IF NOT EXISTS `lodging_bill` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `room_id` VARCHAR(20) NOT NULL,
    `check_in_time` DATETIME NOT NULL,
    `check_out_time` DATETIME NOT NULL,
    `days` INT NOT NULL COMMENT '入住天数',
    `total_lodging_fee` DOUBLE NOT NULL COMMENT '住宿总费用',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 数据库迁移：为现有表添加新字段（如果不存在）
-- 注意：H2 数据库不支持 IF NOT EXISTS 在 ALTER TABLE 中，所以使用异常处理或先检查
-- 由于使用 H2 内存数据库，每次重启都会重建表，所以这个迁移主要用于兼容性
-- 如果使用 MySQL，可以取消注释以下语句：
-- ALTER TABLE `room` ADD COLUMN IF NOT EXISTS `initial_temp_cool` DOUBLE;
-- ALTER TABLE `room` ADD COLUMN IF NOT EXISTS `initial_temp_heat` DOUBLE;
-- UPDATE `room` SET `initial_temp_cool` = `initial_temp`, `initial_temp_heat` = `initial_temp` WHERE `initial_temp_cool` IS NULL;
-- 初始化房间数据 (根据测试用例)
-- 注意：由于 RoomInitService 会自动初始化40个房间，这里的 INSERT 语句主要用于测试
-- 如果数据库已存在旧数据，建议先删除或使用 RoomInitService 进行初始化
INSERT INTO `room` (
        `room_id`,
        `current_temp`,
        `target_temp`,
        `fan_speed`,
        `mode`,
        `is_on`,
        `initial_temp`,
        `initial_temp_cool`,
        `initial_temp_heat`,
        `price_per_day`,
        `status`
    )
VALUES (
        '101',
        32.0,
        25.0,
        'MIDDLE',
        'COOL',
        FALSE,
        32.0,
        32.0,
        10.0,
        100.0,
        'SHUTDOWN'
    ),
    (
        '102',
        28.0,
        25.0,
        'MIDDLE',
        'COOL',
        FALSE,
        28.0,
        28.0,
        15.0,
        125.0,
        'SHUTDOWN'
    ),
    (
        '103',
        30.0,
        25.0,
        'MIDDLE',
        'COOL',
        FALSE,
        30.0,
        30.0,
        18.0,
        150.0,
        'SHUTDOWN'
    ),
    (
        '104',
        29.0,
        25.0,
        'MIDDLE',
        'COOL',
        FALSE,
        29.0,
        29.0,
        12.0,
        200.0,
        'SHUTDOWN'
    ),
    (
        '105',
        35.0,
        25.0,
        'MIDDLE',
        'COOL',
        FALSE,
        35.0,
        35.0,
        14.0,
        100.0,
        'SHUTDOWN'
    )
ON DUPLICATE KEY UPDATE
    `initial_temp_cool` = VALUES(`initial_temp_cool`),
    `initial_temp_heat` = VALUES(`initial_temp_heat`),
    `price_per_day` = VALUES(`price_per_day`);