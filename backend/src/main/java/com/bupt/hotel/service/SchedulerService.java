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

    // 在 tick 内是否延迟执行等待队列分配（防止 mid-tick stop 导致不一致分配）
    private volatile boolean deferAllocations = false;

    // 防止在执行强制抢占检查时递归触发多次检查的守护标志
    private volatile boolean enforcingPreemptions = false;

    // 内存中维护的队列
    // 服务队列: RoomId -> ServiceUnit
    private final Map<String, ServiceUnit> serviceQueue = new ConcurrentHashMap<>();

    // 内存缓存：存储每个房间的实时总费用（用于快速读取，避免数据库延迟）
    private final Map<String, Double> totalFeeCache = new ConcurrentHashMap<>();
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
        private long totalWaitTime; // 分配的等待时间（每个时间片 = 120秒）
        private long totalWaitedTime; // 累计已等待时间(逻辑秒)，用于比较优先级
        private boolean priorityBoosted; // 是否因为等待超过时间片而被提升优先级（用于 UI 展示）
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
                // 风速改变：直接在服务队列中更新，并结算之前的费用
                log.info("Fan speed changed for room {} in service queue, updating in place", roomId);

                // 1. 结算旧风速段的详单
                createBillingDetail(unit);

                // 2. 更新服务单元信息（重置开始时间和费用，保留在服务队列）
                unit.setFanSpeed(fanSpeed);
                unit.setStartTime(timeService.getCurrentTime());
                unit.setServedDurationSeconds(0);
                unit.setCurrentFee(0.0);

                // 3. 触发抢占检查：新风速可能导致优先级变化，需要检查是否应被等待队列抢占
                enforceNoMissedPreemptions();
            } else {
                // 风速没变，仅更新目标温度等，不影响调度
                roomRepository.save(room);
            }
        } else if (waitingQueue.containsKey(roomId)) {
            // 如果在等待队列，且风速改变 -> 视为新请求，重新调度
            WaitingInfo info = waitingQueue.get(roomId);
            if (info.getFanSpeed() != fanSpeed) {
                // 风速改变：从等待队列移除，重新请求（重置等待时间）
                log.info("Fan speed changed for room {} in waiting queue, re-dispatching", roomId);
                waitingQueue.remove(roomId);
                dispatch(roomId, fanSpeed);
            } else {
                // 风速没变，仅更新目标温度等，不影响调度
                roomRepository.save(room);
            }
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
                    int cmp = Long.compare(u2.getServedDurationSeconds(), u1.getServedDurationSeconds());
                    if (cmp != 0)
                        return cmp;
                    return compareRoomIdAsc(u1.getRoomId(), u2.getRoomId());
                })
                .collect(Collectors.toList());

        ServiceUnit candidateToPreempt = sortedServiceUnits.get(0); // 优先级最低且服务时间最长的

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
        // 容量保护：如果已经满，则把请求放回等待队列（以防并发导致超出容量）
        if (serviceQueue.size() >= maxServiceUnits) {
            addToWaitingQueue(roomId, fanSpeed, TIME_SLICE_LOGIC_SECONDS);
            log.warn("StartService rejected (capacity full). Room {} moved to waiting", roomId);
            return;
        }

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

        // 服务对象释放后，从等待队列中选择下一个合适的房间
        // 如果当前 tick 正在处理服务队列，则延迟分配，待 tick 完成后统一分配
        if (!deferAllocations) {
            tryAllocateFromWaitingQueue();
        }
    }

    /**
     * 停止服务但不触发等待队列分配（用于风速改变重新调度的场景）
     */
    private void stopServiceWithoutAllocation(String roomId) {
        ServiceUnit unit = serviceQueue.remove(roomId);
        if (unit == null)
            return;

        // 生成详单
        createBillingDetail(unit);

        RoomStatus nextStatus = RoomStatus.IDLE;
        updateRoomStatus(roomId, nextStatus);
        // 注意：不调用 tryAllocateFromWaitingQueue()
    }

    /**
     * 尝试从等待队列中分配下一个房间到服务队列
     * 规则：优先级高的优先，同优先级选择累计等待时间最长的
     */
    private void tryAllocateFromWaitingQueue() {
        if (waitingQueue.isEmpty() || serviceQueue.size() >= maxServiceUnits) {
            return;
        }

        // 按基础风速优先（不可被等待提升跨越）；在相同风速下，
        // 如果累计等待时间超过时间片则在同风速内提升优先级。最终以累计等待时间和 roomId 做稳定排序。
        List<WaitingInfo> sortedWaiting = waitingQueue.values().stream()
                .sorted((w1, w2) -> {
                    // 先比较基础风速优先级（HIGH > MIDDLE > LOW）——这是绝对性的
                    int base1 = getPriority(w1.getFanSpeed());
                    int base2 = getPriority(w2.getFanSpeed());
                    if (base1 != base2) {
                        return base2 - base1; // 降序：风速高的在前
                    }

                    // 到此说明风速相同：在相同风速内，等待超过时间片的会获得提升
                    boolean boosted1 = w1.getTotalWaitedTime() >= TIME_SLICE_LOGIC_SECONDS;
                    boolean boosted2 = w2.getTotalWaitedTime() >= TIME_SLICE_LOGIC_SECONDS;
                    if (boosted1 != boosted2) {
                        return boosted2 ? -1 : 1; // boosted 的排在前面
                    }

                    // 同风速、同提升状态：按累计等待时间降序
                    int waitedCmp = Long.compare(w2.getTotalWaitedTime(), w1.getTotalWaitedTime());
                    if (waitedCmp != 0)
                        return waitedCmp;

                    // 最后以房间号升序作为稳定的 tie-break
                    return compareRoomIdAsc(w1.getRoomId(), w2.getRoomId());
                })
                .collect(Collectors.toList());

        if (!sortedWaiting.isEmpty()) {
            WaitingInfo next = sortedWaiting.get(0);
            log.info("Allocating from waiting queue: Room {} (priority {}, totalWaited={}s)",
                    next.getRoomId(), getPriority(next.getFanSpeed()), next.getTotalWaitedTime());
            startService(next.getRoomId(), next.getFanSpeed());
        }
    }

    private void preempt(String kickedRoomId, String newRoomId, FanSpeed newFanSpeed) {
        // 关键修复：先从等待队列移除新房间，防止后续 addToWaitingQueue 触发的 enforceNoMissedPreemptions
        // 再次扫描到该房间从而导致二次抢占（Double Preemption）
        waitingQueue.remove(newRoomId);

        // 踢出旧房间
        // 使用不触发分配的停止方法，避免 stopService 在释放槽位时触发 tryAllocateFromWaitingQueue
        // 从而导致新房间无法立刻占位的竞态
        stopServiceWithoutAllocation(kickedRoomId);
        // 加入等待队列，分配等待时间（120秒逻辑时间）
        addToWaitingQueue(kickedRoomId, roomRequests.get(kickedRoomId).getFanSpeed(), TIME_SLICE_LOGIC_SECONDS);

        // 立即启动新房间
        startService(newRoomId, newFanSpeed);
    }

    private void addToWaitingQueue(String roomId, FanSpeed fanSpeed, long waitSeconds) {
        WaitingInfo info = new WaitingInfo();
        info.setRoomId(roomId);
        info.setFanSpeed(fanSpeed);
        info.setTotalWaitTime(waitSeconds);
        info.setWaitTimeRemaining(waitSeconds);
        info.setTotalWaitedTime(0); // 初始化为0，表示刚进入等待队列
        info.setPriorityBoosted(false);
        waitingQueue.put(roomId, info);

        updateRoomStatus(roomId, RoomStatus.WAITING);
        log.info("Added to Waiting Queue: Room={}, WaitTime={}", roomId, waitSeconds);
        // 在每次有新的等待者加入时，立即检查是否存在可以发生的抢占，
        // 防止出现"等待队列可以抢占服务队列但未抢占"的不一致情况。
        enforceNoMissedPreemptions();
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
        // 使用逻辑秒记录本次会话时长，避免与真实时间刻度产生偏差
        long duration = unit.getServedDurationSeconds();
        detail.setDuration(duration);
        // 将结束时间设置为开始时间加上逻辑秒数，保持展示一致性
        detail.setEndTime(unit.getStartTime().plusSeconds(duration));
        detail.setFanSpeed(unit.getFanSpeed());
        // 将本次会话费用及累计费用四舍五入到两位后写入详单
        double roundedSessionFee = Math.round(unit.getCurrentFee() * 100.0) / 100.0;
        detail.setFee(roundedSessionFee);

        // 获取当前总费用（已经在服务过程中实时更新了）
        Room room = roomRepository.findByRoomId(unit.getRoomId()).orElseThrow();
        double currentTotal = (room.getTotalFee() == null ? 0 : room.getTotalFee());
        // 将累计费用四舍五入用于账单记录与缓存展示
        double roundedTotal = Math.round(currentTotal * 100.0) / 100.0;
        roomRepository.save(room);

        // 更新缓存（服务结束时，缓存以两位数显示）
        totalFeeCache.put(unit.getRoomId(), roundedTotal);

        detail.setCumulativeFee(roundedTotal);
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

    // 辅助: 返回指定风速每分钟的温度变化速率（度/分钟）
    private double getRatePerMin(FanSpeed speed) {
        switch (speed) {
            case HIGH:
                return 1.0;
            case MIDDLE:
                return 0.5;
            case LOW:
                return 1.0 / 3.0;
            default:
                return 0.0;
        }
    }

    private Room findRoom(String roomId) {
        return roomRepository.findByRoomId(roomId).orElse(null);
    }

    private int compareRoomIdAsc(String r1, String r2) {
        if (r1 == null && r2 == null)
            return 0;
        if (r1 == null)
            return -1;
        if (r2 == null)
            return 1;
        return r1.compareTo(r2);
    }

    // --- 定时任务: 模拟时间流逝、温度变化、计费、时间片检查 ---

    // 每 1 秒执行一次 (模拟逻辑时间推进)
    @Scheduled(fixedRate = 1000)
    @Transactional
    public synchronized void tick() {
        // 计算逻辑时间流逝
        // timeScaleMs: 多少毫秒真实时间 = 1分钟逻辑时间 (默认10000ms = 10s)
        // 1s real = (60 / (timeScaleMs / 1000.0)) logic seconds
        double scaleFactor = 60.0 / (timeScaleMs / 1000.0);
        long logicSecondsPassed = (long) scaleFactor;
        double logicMinutesPassed = logicSecondsPassed / 60.0;

        // 1. 更新服务队列中的房间 (温度、费用)
        // 采用事件模拟：在一个 tick 的逻辑秒范围内，按事件（某些服务达到目标温度而结束）划分区间，
        // 对每个区间逐步更新正在服务的房间；当某个房间在区间内结束时，立即释放服务位并允许等待队列竞争剩余时间。
        // 这样可以处理像“在半个逻辑秒内达到目标并将余下时间分配给其它等待者”这样的场景。
        // 为支持中途分配，允许 stopService 在区间中触发 tryAllocateFromWaitingQueue()
        deferAllocations = false;

        long remaining = logicSecondsPassed;
        // 如果没有正在服务的房间，直接跳过模拟
        while (remaining > 0 && !serviceQueue.isEmpty()) {
            // 快照当前服务单元（注意：startService/stopService 可能在循环中修改 serviceQueue）
            List<ServiceUnit> snapshot = serviceQueue.values().stream().collect(Collectors.toList());

            if (snapshot.isEmpty()) {
                break;
            }

            // 安全检查：移除未入住或不可服务的单元（立即产生详单）
            boolean removedAny = false;
            for (ServiceUnit u : snapshot) {
                String rid = u.getRoomId();
                Room r = roomRepository.findByRoomId(rid).orElse(null);
                if (r != null && (r.getCustomerName() == null || r.getCustomerName().trim().isEmpty())) {
                    log.warn("Room {} is not checked in, stopping service", rid);
                    serviceQueue.remove(rid);
                    createBillingDetail(u);
                    r.setStatus(RoomStatus.SHUTDOWN);
                    r.setIsOn(false);
                    roomRepository.save(r);
                    mqttService.publishStatus(rid, r);
                    removedAny = true;
                }
            }
            if (removedAny) {
                // 若有被移除的，重新进入下一轮以更新 snapshot
                continue;
            }

            // 计算下一个事件时间（若某些服务将在 remaining 秒内达到目标）
            long nextEvent = Long.MAX_VALUE;
            for (ServiceUnit u : snapshot) {
                Room r = roomRepository.findByRoomId(u.getRoomId()).orElse(null);
                if (r == null)
                    continue;
                double ratePerSec = getRatePerMin(u.getFanSpeed()) / 60.0;
                double beforeTemp = r.getCurrentTemp();
                double targetTemp = r.getTargetTemp();
                double neededChange = 0.0;
                if (r.getMode() == Mode.COOL) {
                    neededChange = Math.max(0.0, beforeTemp - targetTemp);
                } else {
                    neededChange = Math.max(0.0, targetTemp - beforeTemp);
                }
                if (neededChange <= 0.0) {
                    // 已在目标温度或超额，立即作为事件处理（时间0）
                    nextEvent = Math.min(nextEvent, 0L);
                } else if (ratePerSec > 0) {
                    long timeToTarget = (long) Math.ceil(neededChange / ratePerSec);
                    if (timeToTarget <= remaining) {
                        nextEvent = Math.min(nextEvent, timeToTarget);
                    }
                }
            }

            long delta = (nextEvent == Long.MAX_VALUE) ? remaining : Math.max(1, nextEvent);
            if (delta > remaining)
                delta = remaining;
            double deltaMinutes = delta / 60.0;

            // 对当前快照中的每个服务单元应用 delta 时间段
            for (ServiceUnit u : snapshot) {
                // 可能在上一步中被 stop/removed，因此以当前 serviceQueue 为准
                if (!serviceQueue.containsKey(u.getRoomId()))
                    continue;
                // 调用已有的更新逻辑以处理温度/费用/可能的停止
                updateRoomState(u.getRoomId(), u, delta, deltaMinutes);
            }

            remaining -= delta;
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

            long prevRem = info.getWaitTimeRemaining();
            long dec = Math.min(prevRem, logicSecondsPassed);
            info.setWaitTimeRemaining(prevRem - dec);

            // 更新累计等待时间（包括已经超时后的继续累计），用于优先级调整
            long newTotalWaited = info.getTotalWaitedTime() + logicSecondsPassed;
            info.setTotalWaitedTime(newTotalWaited);
            // 更新 priorityBoosted 标志以便 UI 显示
            info.setPriorityBoosted(newTotalWaited >= TIME_SLICE_LOGIC_SECONDS);

            // 仅当刚好从>0 到 0 时触发一次时间片耗尽检查，避免重复调用导致竞态
            if (prevRem > 0 && info.getWaitTimeRemaining() == 0) {
                log.info("Time slice expired for Room {} (totalWaited={}s)", info.getRoomId(),
                        info.getTotalWaitedTime());
                checkTimeSliceAllocation(info);
            }
        }

        // 等待队列倒计时与时间片检查完成后，允许进行分配（一次性分配多个空位）
        deferAllocations = false;
        // 如果有空位，尝试批量分配直到服务队列满或等待队列空
        while (serviceQueue.size() < maxServiceUnits && !waitingQueue.isEmpty()) {
            tryAllocateFromWaitingQueue();
        }

        // 分配完毕后，再次强制检查是否存在等待者可以抢占当前服务队列中的低优先级对象，
        // 这个检查保证在真实时间（每秒）上不会出现可抢占却未抢占的情况。
        enforceNoMissedPreemptions();

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

        // 计算本次实际服务消耗的逻辑秒数（在到达目标温度时需要按比例计算）
        long actualServedSeconds = 0L;

        // 温度变化
        double ratePerMin = getRatePerMin(unit.getFanSpeed());
        double tempChange = ratePerMin * logicMinutesPassed;
        double beforeTemp = room.getCurrentTemp();
        double targetTemp = room.getTargetTemp();

        // 以实际（clamped）温差作为费用增量，避免靠近目标时多计费
        double actualChange = 0.0;
        if (room.getMode() == Mode.COOL) {
            if (beforeTemp - tempChange <= targetTemp) {
                // 本次 tick 会到达或超过目标，只计到达目标的实际温差
                actualChange = Math.max(0.0, beforeTemp - targetTemp);
                room.setCurrentTemp(targetTemp);
                // 计算本次实际消耗的逻辑秒数的比例
                if (tempChange > 0) {
                    double ratio = actualChange / tempChange;
                    actualServedSeconds = Math.max(0L, Math.round(ratio * logicSecondsPassed));
                } else {
                    actualServedSeconds = 0L;
                }
                // 先记录费用
                unit.setCurrentFee(unit.getCurrentFee() + actualChange);
                double currentTotal = (room.getTotalFee() == null ? 0 : room.getTotalFee());
                double newTotal = currentTotal + actualChange;
                room.setTotalFee(newTotal);
                // 增加仅实际服务的秒数
                unit.setServedDurationSeconds(unit.getServedDurationSeconds() + actualServedSeconds);
                // 达到目标温度，停止送风
                stopSupply(roomId, false);
            } else {
                actualChange = tempChange;
                room.setCurrentTemp(beforeTemp - actualChange);
                unit.setCurrentFee(unit.getCurrentFee() + actualChange);
                double currentTotal = (room.getTotalFee() == null ? 0 : room.getTotalFee());
                room.setTotalFee(currentTotal + actualChange);
                // 未达到目标，整个逻辑秒都算入服务时长
                unit.setServedDurationSeconds(unit.getServedDurationSeconds() + logicSecondsPassed);
            }
        } else {
            if (beforeTemp + tempChange >= targetTemp) {
                actualChange = Math.max(0.0, targetTemp - beforeTemp);
                room.setCurrentTemp(targetTemp);
                if (tempChange > 0) {
                    double ratio = actualChange / tempChange;
                    actualServedSeconds = Math.max(0L, Math.round(ratio * logicSecondsPassed));
                } else {
                    actualServedSeconds = 0L;
                }
                unit.setCurrentFee(unit.getCurrentFee() + actualChange);
                double currentTotal = (room.getTotalFee() == null ? 0 : room.getTotalFee());
                room.setTotalFee(currentTotal + actualChange);
                unit.setServedDurationSeconds(unit.getServedDurationSeconds() + actualServedSeconds);
                // 达到目标温度，停止送风
                stopSupply(roomId, false);
            } else {
                actualChange = tempChange;
                room.setCurrentTemp(beforeTemp + actualChange);
                unit.setCurrentFee(unit.getCurrentFee() + actualChange);
                double currentTotal = (room.getTotalFee() == null ? 0 : room.getTotalFee());
                room.setTotalFee(currentTotal + actualChange);
                unit.setServedDurationSeconds(unit.getServedDurationSeconds() + logicSecondsPassed);
            }
        }

        // 保留温度两位小数以便展示，但不在此处对总费用进行截断或四舍五入
        room.setCurrentTemp(Math.round(room.getCurrentTemp() * 100.0) / 100.0);

        // 同时更新内存缓存，使用高精度值（不提前舍入）
        double currentTotalForCache = (room.getTotalFee() == null ? 0 : room.getTotalFee());
        totalFeeCache.put(roomId, currentTotalForCache);

        // 使用 saveAndFlush 确保立即刷新到数据库，避免读取延迟
        roomRepository.saveAndFlush(room);
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
        // 时间片轮转策略（严格遵循风速优先级）:
        // 1. 首先检查是否有低于等待房间风速的服务对象
        // 2. 如果有，按优先级调度规则抢占（选择风速最低且服务时间最长的）
        // 3. 如果没有（全是高优先级或同优先级），检查是否有同风速的
        // 4. 如果有同风速的，替换服务时间最长的
        // 5. 如果全是高优先级的，继续等待（风速优先级的绝对性）

        int waiterPriority = getPriority(waiter.getFanSpeed());
        // 如果等待者等待已经超过时间片，则视为提升一级优先
        int effectivePriority = waiterPriority + (waiter.getTotalWaitedTime() >= TIME_SLICE_LOGIC_SECONDS ? 1 : 0);
        // 查找所有低于等待房间风速的服务对象
        List<ServiceUnit> lowerPriorityUnits = serviceQueue.values().stream()
                .filter(u -> getPriority(u.getFanSpeed()) < effectivePriority)
                .sorted((u1, u2) -> {
                    int p1 = getPriority(u1.getFanSpeed());
                    int p2 = getPriority(u2.getFanSpeed());
                    if (p1 != p2)
                        return p1 - p2; // 优先选择风速最低的
                    int cmp = Long.compare(u2.getServedDurationSeconds(), u1.getServedDurationSeconds()); // 再选服务时间最长的
                    if (cmp != 0)
                        return cmp;
                    return compareRoomIdAsc(u1.getRoomId(), u2.getRoomId());
                })
                .collect(Collectors.toList());

        if (!lowerPriorityUnits.isEmpty()) {
            // 有低优先级的，抢占
            ServiceUnit toPreempt = lowerPriorityUnits.get(0);
            log.info("Time slice: {} (priority {}) preempts {} (priority {})",
                    waiter.getRoomId(), waiterPriority, toPreempt.getRoomId(), getPriority(toPreempt.getFanSpeed()));
            preempt(toPreempt.getRoomId(), waiter.getRoomId(), waiter.getFanSpeed());
            return;
        }

        // 查找同风速的服务对象
        List<ServiceUnit> sameSpeedUnits = serviceQueue.values().stream()
                .filter(u -> u.getFanSpeed() == waiter.getFanSpeed())
                .sorted((u1, u2) -> {
                    int cmp = Long.compare(u2.getServedDurationSeconds(), u1.getServedDurationSeconds());
                    if (cmp != 0)
                        return cmp;
                    return compareRoomIdAsc(u1.getRoomId(), u2.getRoomId());
                })
                .collect(Collectors.toList());

        if (!sameSpeedUnits.isEmpty()) {
            // 有同风速的，时间片轮转
            ServiceUnit toReplace = sameSpeedUnits.get(0);
            log.info("Time slice swap: {} replaces {} (same priority {})",
                    waiter.getRoomId(), toReplace.getRoomId(), waiterPriority);
            preempt(toReplace.getRoomId(), waiter.getRoomId(), waiter.getFanSpeed());
        } else {
            // 全是高优先级的，风速优先级绝对性：继续等待
            // 不重置等待时间，继续累计等待时间，等待将来的服务机会
            log.info("Room {} cannot preempt (all higher priority in service), continue waiting (totalWaited={}s)",
                    waiter.getRoomId(), waiter.getTotalWaitedTime());
            // 注意：不调用 setWaitTimeRemaining()，保持当前状态
            // totalWaitedTime 会在 tick() 中继续累计
        }
    }

    public Map<String, ServiceUnit> getServiceQueue() {
        return serviceQueue;
    }

    public Map<String, WaitingInfo> getWaitingQueue() {
        return waitingQueue;
    }

    /**
     * 强制检查并执行等待队列中能够对服务队列发起的抢占。此方法保证：在任意时刻（按真实时间刻度），
     * 不会存在一个等待者可以抢占服务队列但没有被抢占的情况。
     *
     * 实现细节：
     * - 按等待者的“有效优先级”降序选择候选（基础风速优先级 + 超过时间片的提升），
     * - 按服务队列选择优先级最低且服务时间最长的候选进行比较与抢占，
     * - 重复直到不存在可执行的抢占。
     */
    private synchronized void enforceNoMissedPreemptions() {
        if (enforcingPreemptions) {
            return;
        }
        enforcingPreemptions = true;
        try {
            if (waitingQueue.isEmpty() || serviceQueue.isEmpty()) {
                return;
            }

            boolean changed = true;
            while (changed) {
                changed = false;

                // 选出等待队列中最有资格抢占的等待者（有效优先级最高，然后累计等待时间）
                WaitingInfo bestWaiting = waitingQueue.values().stream()
                        .sorted((w1, w2) -> {
                            int p1 = getPriority(w1.getFanSpeed())
                                    + (w1.getTotalWaitedTime() >= TIME_SLICE_LOGIC_SECONDS ? 1 : 0);
                            int p2 = getPriority(w2.getFanSpeed())
                                    + (w2.getTotalWaitedTime() >= TIME_SLICE_LOGIC_SECONDS ? 1 : 0);
                            if (p1 != p2)
                                return p2 - p1; // 降序
                            int waitedCmp = Long.compare(w2.getTotalWaitedTime(), w1.getTotalWaitedTime());
                            if (waitedCmp != 0)
                                return waitedCmp;
                            return compareRoomIdAsc(w1.getRoomId(), w2.getRoomId());
                        })
                        .findFirst().orElse(null);

                if (bestWaiting == null)
                    break;

                // 选出服务队列中最容易被抢占的服务对象（最低优先级，若相同则服务时间最长）
                List<ServiceUnit> sortedServiceUnits = serviceQueue.values().stream()
                        .sorted((u1, u2) -> {
                            int p1 = getPriority(u1.getFanSpeed());
                            int p2 = getPriority(u2.getFanSpeed());
                            if (p1 != p2)
                                return p1 - p2; // 升序：优先选择最低优先级
                            int cmp = Long.compare(u2.getServedDurationSeconds(), u1.getServedDurationSeconds());
                            if (cmp != 0)
                                return cmp;
                            return compareRoomIdAsc(u1.getRoomId(), u2.getRoomId());
                        })
                        .collect(Collectors.toList());

                if (sortedServiceUnits.isEmpty())
                    break;

                ServiceUnit candidate = sortedServiceUnits.get(0);
                int candidatePriority = getPriority(candidate.getFanSpeed());
                int bestEffective = getPriority(bestWaiting.getFanSpeed())
                        + (bestWaiting.getTotalWaitedTime() >= TIME_SLICE_LOGIC_SECONDS ? 1 : 0);

                if (bestEffective > candidatePriority) {
                    log.info("Enforce preempt: waiting {} (effPr={}) preempts {} (pr={})",
                            bestWaiting.getRoomId(), bestEffective, candidate.getRoomId(), candidatePriority);
                    preempt(candidate.getRoomId(), bestWaiting.getRoomId(), bestWaiting.getFanSpeed());
                    changed = true;
                } else if (bestEffective == candidatePriority) {
                    // 当有效优先级相等时，仅在同风速且等待者已达到时间片时进行替换（轮转）
                    if (bestWaiting.getFanSpeed() == candidate.getFanSpeed()
                            && bestWaiting.getTotalWaitedTime() >= TIME_SLICE_LOGIC_SECONDS) {
                        log.info("Enforce same-speed swap: waiting {} replaces {} (priority={})",
                                bestWaiting.getRoomId(), candidate.getRoomId(), candidatePriority);
                        preempt(candidate.getRoomId(), bestWaiting.getRoomId(), bestWaiting.getFanSpeed());
                        changed = true;
                    } else {
                        // 否则不抢占，继续寻找下一个等待者
                        // 将该等待者视为不可抢占，临时从考虑集中排除（通过在后续迭代重新评估）
                        // 直接跳出本次循环以避免无限循环
                        break;
                    }
                }
            }
        } finally {
            enforcingPreemptions = false;
        }
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

    /**
     * 获取缓存中的总费用（实时更新）
     */
    public Double getCachedTotalFee(String roomId) {
        return totalFeeCache.get(roomId);
    }

    /**
     * 更新总费用缓存
     */
    public void updateTotalFeeCache(String roomId, double totalFee) {
        totalFeeCache.put(roomId, totalFee);
    }

    /**
     * 清除总费用缓存（用于入住/退房时）
     */
    public void clearTotalFeeCache(String roomId) {
        totalFeeCache.remove(roomId);
    }
}
