package com.bupt.hotel.service;

import com.bupt.hotel.entity.BillingDetail;
import com.bupt.hotel.entity.FanSpeed;
import com.bupt.hotel.entity.Mode;
import com.bupt.hotel.entity.Room;
import com.bupt.hotel.entity.RoomStatus;
import com.bupt.hotel.repository.BillingDetailRepository;
import com.bupt.hotel.repository.RoomRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SchedulerService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BillingDetailRepository billingDetailRepository;

    @Autowired
    private MqttService mqttService;

    @Autowired
    private TimeService timeService;

    @Value("${hotel.ac.max-service-units}")
    private int maxServiceUnits;

    @Value("${hotel.ac.time-slice-seconds}")
    private int timeSliceSeconds;

    // 逻辑时间刻度 (ms)
    @Value("${hotel.ac.time-scale-ms}")
    private long timeScaleMs;

    // 时间片长度（逻辑秒）：2分钟 = 120秒
    private static final long TIME_SLICE_LOGIC_SECONDS = 120L;

    // 内存中维护的队列
    // 服务队列: RoomId -> ServiceUnit
    private final Map<String, ServiceUnit> serviceQueue = new ConcurrentHashMap<>();
    // 等待队列: RoomId -> WaitingInfo
    private final Map<String, WaitingInfo> waitingQueue = new ConcurrentHashMap<>();

    // 辅助结构: 记录每个房间当前的请求信息 (用于恢复)
    private final Map<String, RequestInfo> roomRequests = new ConcurrentHashMap<>();

    @Data
    public static class ServiceUnit {
        private String roomId;
        private LocalDateTime startTime;
        private FanSpeed fanSpeed;
        private long servedDurationSeconds; // 累计服务时长(逻辑秒)
        private double currentFee; // 本次会话费用
    }

    @Data
    public static class WaitingInfo {
        private String roomId;
        private FanSpeed fanSpeed;
        private long waitTimeRemaining; // 剩余等待时间(逻辑秒)
        private long totalWaitTime; // 分配的等待时间
    }

    @Data
    public static class RequestInfo {
        private String roomId;
        private Mode mode;
        private Double targetTemp;
        private FanSpeed fanSpeed;
    }

    /**
     * 接收送风请求 (开机、调风、回温触发)
     */
    @Transactional
    public synchronized void requestSupply(String roomId, Mode mode, Double targetTemp, FanSpeed fanSpeed) {
        log.info("Request Supply: Room={}, Mode={}, Target={}, Fan={}", roomId, mode, targetTemp, fanSpeed);

        // 参数校验
        if (mode == Mode.COOL) {
            if (targetTemp < 18 || targetTemp > 28) {
                throw new IllegalArgumentException("Cooling target temp must be between 18 and 28");
            }
        } else if (mode == Mode.HEAT) {
            if (targetTemp < 18 || targetTemp > 25) {
                throw new IllegalArgumentException("Heating target temp must be between 18 and 25");
            }
        }

        // 更新或保存请求信息
        RequestInfo req = new RequestInfo();
        req.setRoomId(roomId);
        req.setMode(mode);
        req.setTargetTemp(targetTemp);
        req.setFanSpeed(fanSpeed);
        roomRequests.put(roomId, req);

        Room room = roomRepository.findByRoomId(roomId).orElseThrow();

        // 如果模式改变且房间处于关机状态，重置当前温度为对应模式的初始温度
        Mode oldMode = room.getMode();
        if (oldMode != mode && (room.getIsOn() == null || !room.getIsOn())) {
            double newInitialTemp;
            if (mode == Mode.COOL) {
                newInitialTemp = room.getInitialTempCool() != null ? room.getInitialTempCool() : room.getInitialTemp();
            } else {
                newInitialTemp = room.getInitialTempHeat() != null ? room.getInitialTempHeat() : room.getInitialTemp();
            }
            room.setCurrentTemp(newInitialTemp);
            log.info("Mode changed from {} to {} for room {}, resetting current temp to {}", oldMode, mode, roomId,
                    newInitialTemp);
        }

        room.setMode(mode);
        room.setTargetTemp(targetTemp);
        room.setFanSpeed(fanSpeed);

        // 如果已经在服务队列，且风速改变 -> 视为新请求，重新调度
        if (serviceQueue.containsKey(roomId)) {
            ServiceUnit unit = serviceQueue.get(roomId);
            if (unit.getFanSpeed() != fanSpeed) {
                // 结束当前服务，重新请求
                stopService(roomId, false); // false表示不是关机，是重新调度
                dispatch(roomId, fanSpeed);
            } else {
                // 风速没变，仅更新目标温度等，不影响调度
                roomRepository.save(room);
            }
        } else if (waitingQueue.containsKey(roomId)) {
            // 如果在等待队列，更新参数
            WaitingInfo info = waitingQueue.get(roomId);
            info.setFanSpeed(fanSpeed);
            // 风速改变可能影响优先级，重新尝试调度?
            // 简化策略: 保持在等待队列，但在下一次调度检查时会使用新风速
            roomRepository.save(room);
        } else {
            // 新请求
            room.setStatus(RoomStatus.WAITING); // 暂时设为WAITING，由dispatch决定
            roomRepository.save(room);
            dispatch(roomId, fanSpeed);
        }
    }

    /**
     * 停止送风 (关机、达到目标温度)
     * 
     * @param isPowerOff true=关机(清空请求), false=暂停(保留请求用于回温)
     */
    @Transactional
    public synchronized void stopSupply(String roomId, boolean isPowerOff) {
        log.info("Stop Supply: Room={}, PowerOff={}", roomId, isPowerOff);

        // 如果在服务中，结束服务并生成详单
        if (serviceQueue.containsKey(roomId)) {
            stopService(roomId, isPowerOff);
        }

        // 如果在等待队列中，移出等待队列
        if (waitingQueue.containsKey(roomId)) {
            waitingQueue.remove(roomId);
        }

        if (isPowerOff) {
            // 关机：清空请求，更新状态为 SHUTDOWN
            roomRequests.remove(roomId);
            Room room = roomRepository.findByRoomId(roomId).orElse(null);
            if (room != null) {
                room.setStatus(RoomStatus.SHUTDOWN);
                room.setIsOn(false);
                roomRepository.save(room);
                mqttService.publishStatus(roomId, room);
            }
        } else {
            // 仅仅是达到温度暂停，状态改为 IDLE
            Room room = roomRepository.findByRoomId(roomId).orElse(null);
            if (room != null) {
                room.setStatus(RoomStatus.IDLE);
                roomRepository.save(room);
                mqttService.publishStatus(roomId, room);
            }
        }
    }

    /**
     * 核心调度逻辑
     */
    private void dispatch(String newRoomId, FanSpeed newFanSpeed) {
        // 情形 1: 服务队列未满
        if (serviceQueue.size() < maxServiceUnits) {
            startService(newRoomId, newFanSpeed);
            return;
        }

        // 情形 2: 服务队列已满，需比较优先级
        // 优先级: HIGH > MIDDLE > LOW
        // 比较 newFanSpeed 与 serviceQueue 中的最低优先级

        List<ServiceUnit> sortedServiceUnits = serviceQueue.values().stream()
                .sorted((u1, u2) -> {
                    int p1 = getPriority(u1.getFanSpeed());
                    int p2 = getPriority(u2.getFanSpeed());
                    if (p1 != p2)
                        return p1 - p2; // 升序: LOW < MIDDLE < HIGH
                    // 同风速，服务时间长的排前面 (容易被踢)
                    return Long.compare(u2.getServedDurationSeconds(), u1.getServedDurationSeconds());
                })
                .collect(Collectors.toList());

        ServiceUnit candidateToPreempt = sortedServiceUnits.getFirst(); // 优先级最低且服务时间最长的

        int newPriority = getPriority(newFanSpeed);
        int candidatePriority = getPriority(candidateToPreempt.getFanSpeed());

        if (candidatePriority < newPriority) {
            // 抢占
            log.info("Preempt: Room {} (Speed {}) preempts Room {} (Speed {})",
                    newRoomId, newFanSpeed, candidateToPreempt.getRoomId(), candidateToPreempt.getFanSpeed());

            preempt(candidateToPreempt.getRoomId(), newRoomId, newFanSpeed);
        } else if (candidatePriority == newPriority) {
            // 同级，时间片轮转逻辑
            // 新请求进入等待队列，分配时间片（120秒逻辑时间）
            addToWaitingQueue(newRoomId, newFanSpeed, TIME_SLICE_LOGIC_SECONDS);
        } else {
            // 优先级更低，进入等待队列（120秒逻辑时间）
            addToWaitingQueue(newRoomId, newFanSpeed, TIME_SLICE_LOGIC_SECONDS);
        }
    }

    private void startService(String roomId, FanSpeed fanSpeed) {
        // 从等待队列移除
        waitingQueue.remove(roomId);

        ServiceUnit unit = new ServiceUnit();
        unit.setRoomId(roomId);
        unit.setStartTime(timeService.getCurrentTime());
        unit.setFanSpeed(fanSpeed);
        unit.setServedDurationSeconds(0);
        unit.setCurrentFee(0.0);

        serviceQueue.put(roomId, unit);

        updateRoomStatus(roomId, RoomStatus.SERVING);
        log.info("Start Service: Room={}", roomId);
    }

    private void stopService(String roomId, boolean isPowerOff) {
        ServiceUnit unit = serviceQueue.remove(roomId);
        if (unit == null)
            return;

        // 生成详单
        createBillingDetail(unit);

        RoomStatus nextStatus = isPowerOff ? RoomStatus.SHUTDOWN : RoomStatus.IDLE;
        updateRoomStatus(roomId, nextStatus);
    }

    private void preempt(String kickedRoomId, String newRoomId, FanSpeed newFanSpeed) {
        // 踢出旧房间
        stopService(kickedRoomId, false); // 这里的 false 表示不是关机，而是被挂起
        // 加入等待队列，分配等待时间（120秒逻辑时间）
        addToWaitingQueue(kickedRoomId, roomRequests.get(kickedRoomId).getFanSpeed(), TIME_SLICE_LOGIC_SECONDS);

        // 启动新房间
        startService(newRoomId, newFanSpeed);
    }

    private void addToWaitingQueue(String roomId, FanSpeed fanSpeed, long waitSeconds) {
        WaitingInfo info = new WaitingInfo();
        info.setRoomId(roomId);
        info.setFanSpeed(fanSpeed);
        info.setTotalWaitTime(waitSeconds);
        info.setWaitTimeRemaining(waitSeconds);
        waitingQueue.put(roomId, info);

        updateRoomStatus(roomId, RoomStatus.WAITING);
        log.info("Added to Waiting Queue: Room={}, WaitTime={}", roomId, waitSeconds);
    }

    private void updateRoomStatus(String roomId, RoomStatus status) {
        Room room = roomRepository.findByRoomId(roomId).orElse(null);
        if (room != null) {
            room.setStatus(status);
            roomRepository.save(room);
            // 推送 MQTT 状态
            mqttService.publishStatus(roomId, room);
        }
    }

    private void createBillingDetail(ServiceUnit unit) {
        BillingDetail detail = new BillingDetail();
        detail.setRoomId(unit.getRoomId());
        detail.setRequestTime(unit.getStartTime()); // 简化: 请求时间约等于开始时间
        detail.setStartTime(unit.getStartTime());
        detail.setEndTime(timeService.getCurrentTime());

        long duration = Duration.between(detail.getStartTime(), detail.getEndTime()).getSeconds();
        detail.setDuration(duration);
        detail.setFanSpeed(unit.getFanSpeed());
        detail.setFee(unit.getCurrentFee());

        // 计算累计费用
        Room room = roomRepository.findByRoomId(unit.getRoomId()).orElseThrow();
        double newTotal = (room.getTotalFee() == null ? 0 : room.getTotalFee()) + unit.getCurrentFee();
        room.setTotalFee(newTotal);
        roomRepository.save(room);

        detail.setCumulativeFee(newTotal);
        billingDetailRepository.save(detail);
    }

    private int getPriority(FanSpeed speed) {
        switch (speed) {
            case HIGH:
                return 3;
            case MIDDLE:
                return 2;
            case LOW:
                return 1;
            default:
                return 0;
        }
    }

    // --- 定时任务: 模拟时间流逝、温度变化、计费、时间片检查 ---

    // 每 1 秒执行一次 (模拟逻辑时间推进)
    @Scheduled(fixedRate = 1000)
    @Transactional
    public void tick() {
        // 计算逻辑时间流逝
        // timeScaleMs: 多少毫秒真实时间 = 1分钟逻辑时间 (默认10000ms = 10s)
        // 1s real = (60 / (timeScaleMs / 1000.0)) logic seconds
        double scaleFactor = 60.0 / (timeScaleMs / 1000.0);
        long logicSecondsPassed = (long) scaleFactor;
        double logicMinutesPassed = logicSecondsPassed / 60.0;

        // 1. 更新服务队列中的房间 (温度、费用)
        // 使用迭代器以安全删除
        Iterator<Map.Entry<String, ServiceUnit>> serviceIt = serviceQueue.entrySet().iterator();
        while (serviceIt.hasNext()) {
            Map.Entry<String, ServiceUnit> entry = serviceIt.next();
            String roomId = entry.getKey();
            ServiceUnit unit = entry.getValue();

            // 安全检查：如果房间未入住，自动停止服务
            Room room = roomRepository.findByRoomId(roomId).orElse(null);
            if (room != null && (room.getCustomerName() == null || room.getCustomerName().trim().isEmpty())) {
                log.warn("Room {} is not checked in, stopping service", roomId);
                serviceIt.remove();
                // 生成详单并更新房间状态
                createBillingDetail(unit);
                room.setStatus(RoomStatus.SHUTDOWN);
                room.setIsOn(false);
                roomRepository.save(room);
                mqttService.publishStatus(roomId, room);
                continue;
            }

            updateRoomState(roomId, unit, logicSecondsPassed, logicMinutesPassed);
        }

        // 2. 更新等待队列 (倒计时)
        // 使用迭代器以安全删除
        Iterator<Map.Entry<String, WaitingInfo>> waitIt = waitingQueue.entrySet().iterator();
        while (waitIt.hasNext()) {
            Map.Entry<String, WaitingInfo> entry = waitIt.next();
            WaitingInfo info = entry.getValue();

            // 安全检查：如果房间未入住或已关机，从等待队列移除
            Room room = roomRepository.findByRoomId(info.getRoomId()).orElse(null);
            if (room != null) {
                // 检查是否未入住
                if (room.getCustomerName() == null || room.getCustomerName().trim().isEmpty()) {
                    log.warn("Room {} is not checked in, removing from waiting queue", info.getRoomId());
                    waitIt.remove();
                    updateRoomStatus(info.getRoomId(), RoomStatus.SHUTDOWN);
                    continue;
                }
                // 检查是否已关机
                if (room.getIsOn() == null || !room.getIsOn()) {
                    log.warn("Room {} is powered off, removing from waiting queue", info.getRoomId());
                    waitIt.remove();
                    updateRoomStatus(info.getRoomId(), RoomStatus.SHUTDOWN);
                    continue;
                }
            }

            info.setWaitTimeRemaining(info.getWaitTimeRemaining() - logicSecondsPassed);

            if (info.getWaitTimeRemaining() <= 0) {
                // 时间片耗尽，触发调度检查
                log.info("Time slice expired for Room {}", info.getRoomId());
                // 尝试获取服务
                checkTimeSliceAllocation(info);
            }
        }

        // 3. 关机/空闲房间的回温逻辑
        List<Room> allRooms = roomRepository.findAll();
        for (Room room : allRooms) {
            if (room.getStatus() == RoomStatus.SHUTDOWN || room.getStatus() == RoomStatus.IDLE) {
                handleTemperatureRecovery(room, logicMinutesPassed);
            }
        }
    }

    private void updateRoomState(String roomId, ServiceUnit unit, long logicSecondsPassed, double logicMinutesPassed) {
        Room room = roomRepository.findByRoomId(roomId).orElse(null);
        if (room == null)
            return;

        // 更新服务时长
        unit.setServedDurationSeconds(unit.getServedDurationSeconds() + logicSecondsPassed);

        // 温度变化
        // 高: 1度/min, 中: 0.5度/min (2min 1度), 低: 0.33度/min (3min 1度)
        double ratePerMin = 0;
        switch (unit.getFanSpeed()) {
            case HIGH:
                ratePerMin = 1.0;
                break;
            case MIDDLE:
                ratePerMin = 0.5;
                break;
            case LOW:
                ratePerMin = 1.0 / 3.0;
                break;
        }

        double tempChange = ratePerMin * logicMinutesPassed;
        double currentTemp = room.getCurrentTemp();
        double targetTemp = room.getTargetTemp();

        if (room.getMode() == Mode.COOL) {
            currentTemp -= tempChange;
            if (currentTemp <= targetTemp) {
                currentTemp = targetTemp;
                // 达到目标温度，停止送风
                stopSupply(roomId, false);
            }
        } else {
            currentTemp += tempChange;
            if (currentTemp >= targetTemp) {
                currentTemp = targetTemp;
                // 达到目标温度，停止送风
                stopSupply(roomId, false);
            }
        }
        room.setCurrentTemp(currentTemp);

        // 计费: 1元/1度变化 (等效)
        // 费用 = 温度变化量 * 1
        double feeIncrement = tempChange * 1.0;
        unit.setCurrentFee(unit.getCurrentFee() + feeIncrement);

        // 格式化保留2位小数
        room.setCurrentTemp(Math.round(room.getCurrentTemp() * 100.0) / 100.0);
        room.setTotalFee(Math.round((room.getTotalFee() == null ? 0 : room.getTotalFee()) * 100.0) / 100.0);

        roomRepository.save(room);
        // MQTT 推送实时状态
        mqttService.publishStatus(roomId, room);
    }

    private void handleTemperatureRecovery(Room room, double logicMinutesPassed) {
        // 回温: 0.5度/min
        double recoveryRate = 0.5 * logicMinutesPassed;

        double current = room.getCurrentTemp();
        // 根据当前模式选择对应的初始温度
        double initial;
        if (room.getMode() == Mode.COOL) {
            initial = room.getInitialTempCool() != null ? room.getInitialTempCool() : room.getInitialTemp();
        } else {
            initial = room.getInitialTempHeat() != null ? room.getInitialTempHeat() : room.getInitialTemp();
        }

        if (room.getStatus() == RoomStatus.SHUTDOWN) {
            // 关机状态：趋向对应模式下的初始温度
            if (Math.abs(current - initial) < recoveryRate) {
                current = initial;
            } else if (current > initial) {
                current -= recoveryRate;
            } else {
                current += recoveryRate;
            }
            room.setCurrentTemp(current);
            roomRepository.save(room);
        } else if (room.getStatus() == RoomStatus.IDLE) {
            // 达到目标温度后的回温
            // 只有开机状态才进行回温并重新启动
            if (room.getIsOn() != null && room.getIsOn()) {
                // 制冷模式: 温度回升; 制热模式: 温度下降
                // 阈值: 回温 1 度后重新启动
                double target = room.getTargetTemp();

                if (room.getMode() == Mode.COOL) {
                    // 制冷模式：温度回升
                    current += recoveryRate;
                    // 当温度回升到目标温度+1度时，重新启动
                    if (current >= target + 1.0) {
                        // 重新请求送风
                        RequestInfo req = roomRequests.get(room.getRoomId());
                        if (req != null) {
                            requestSupply(req.getRoomId(), req.getMode(), req.getTargetTemp(), req.getFanSpeed());
                        }
                    }
                } else if (room.getMode() == Mode.HEAT) {
                    // 制热模式：温度下降
                    current -= recoveryRate;
                    // 当温度下降到目标温度-1度时，重新启动
                    if (current <= target - 1.0) {
                        // 重新请求送风
                        RequestInfo req = roomRequests.get(room.getRoomId());
                        if (req != null) {
                            requestSupply(req.getRoomId(), req.getMode(), req.getTargetTemp(), req.getFanSpeed());
                        }
                    }
                }
                room.setCurrentTemp(current);
                roomRepository.save(room);
            }
        }
        // 即使是关机或IDLE，也推送状态更新温度
        mqttService.publishStatus(room.getRoomId(), room);
    }

    private void checkTimeSliceAllocation(WaitingInfo waiter) {
        // 简单的时间片轮转策略:
        // 找到服务队列中同风速且服务时间最长的，如果它的服务时间 > 某个阈值 (比如也服务了一个时间片)，则替换
        // 这里简化: 只要有同风速的，就替换服务时间最长的那个

        List<ServiceUnit> sameSpeedUnits = serviceQueue.values().stream()
                .filter(u -> u.getFanSpeed() == waiter.getFanSpeed())
                .sorted((u1, u2) -> Long.compare(u2.getServedDurationSeconds(), u1.getServedDurationSeconds()))
                .collect(Collectors.toList());

        if (!sameSpeedUnits.isEmpty()) {
            ServiceUnit toReplace = sameSpeedUnits.getFirst();
            // 替换
            log.info("Time slice swap: {} replaces {}", waiter.getRoomId(), toReplace.getRoomId());
            preempt(toReplace.getRoomId(), waiter.getRoomId(), waiter.getFanSpeed());
        } else {
            // 没有同风速的 (说明全是高优先级的?)
            // 重置等待时间，继续等
            waiter.setWaitTimeRemaining(waiter.getTotalWaitTime());
        }
    }

    public Map<String, ServiceUnit> getServiceQueue() {
        return serviceQueue;
    }

    public Map<String, WaitingInfo> getWaitingQueue() {
        return waitingQueue;
    }

    /**
     * 获取当前送风会话的费用，若不在服务队列则返回0
     */
    public double getCurrentSessionFee(String roomId) {
        ServiceUnit unit = serviceQueue.get(roomId);
        if (unit == null) {
            return 0.0;
        }
        return unit.getCurrentFee();
    }
}
