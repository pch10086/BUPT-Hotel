<template>
  <div class="manager-container">
    <div class="header">
      <h2>酒店空调监控系统</h2>
      <el-tag type="info">{{ currentTime }}</el-tag>
    </div>

    <!-- 房间状态卡片 - 按楼层选择显示 -->
    <div class="rooms-section">
      <div class="floor-selector">
        <div class="selector-wrapper">
          <el-icon class="selector-icon"><OfficeBuilding /></el-icon>
          <el-select
            v-model="selectedFloor"
            placeholder="选择楼层"
            class="floor-select"
            @change="handleFloorChange"
          >
            <el-option
              v-for="floor in floorRooms"
              :key="floor.floor"
              :label="`${floor.floor}层`"
              :value="floor.floor"
            />
          </el-select>
        </div>
        <div class="floor-info" v-if="currentFloor">
          <el-tag type="info" size="large">
            <el-icon><HomeFilled /></el-icon>
            <span>共 {{ currentFloor.rooms.length }} 间</span>
          </el-tag>
        </div>
      </div>
      
      <div class="rooms-container" v-if="currentFloor">
        <div class="rooms-grid">
          <el-card
            v-for="room in currentFloor.rooms"
            :key="room.roomId"
            :class="['room-card', getStatusClass(room.status)]"
            shadow="hover"
          >
            <div class="room-header">
              <span class="room-id">{{ room.roomId }}</span>
              <el-tag size="small" :type="getStatusType(room.status)">{{
                formatStatus(room.status)
              }}</el-tag>
            </div>
            <div class="room-body">
              <div class="info-row">
                <span>当前温度:</span>
                <span class="val">{{ room.currentTemp?.toFixed(2) }}℃</span>
              </div>
              <div class="info-row">
                <span>目标温度:</span>
                <span class="val">{{ room.targetTemp }}℃</span>
              </div>
              <div class="info-row">
                <span>风速:</span>
                <span class="val">{{ room.fanSpeed }}</span>
              </div>
              <div class="info-row">
                <span>总费用:</span>
                <span class="val fee">¥{{ room.totalFee?.toFixed(2) }}</span>
              </div>
              <div class="info-row" v-if="room.isOn">
                <span>当前费用:</span>
                <span class="val fee">¥{{ room.currentSessionFee?.toFixed(2) || '0.00' }}</span>
              </div>
              <div class="info-row" v-if="room.customerName">
                <span>住客:</span>
                <span class="val">{{ room.customerName }}</span>
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 队列监控 -->
    <el-row :gutter="20" style="margin-top: 30px">
      <el-col :span="12">
        <el-card class="queue-card">
          <template #header>
            <div class="card-header">
              <span>服务队列 (正在送风)</span>
              <el-tag type="success">{{
                Object.keys(serviceQueue).length
              }}</el-tag>
            </div>
          </template>
          <el-table
            :data="formatQueue(serviceQueue)"
            style="width: 100%"
            empty-text="暂无服务中房间"
          >
            <el-table-column prop="roomId" label="房间" width="80" />
            <el-table-column prop="fanSpeed" label="风速" width="80" />
            <el-table-column
              prop="startTime"
              label="开始时间"
              :formatter="timeFormatter"
            />
            <el-table-column prop="servedDurationSeconds" label="服务时长(s)" />
            <el-table-column prop="currentFee" label="本次费用">
              <template #default="scope"
                >¥{{ scope.row.currentFee?.toFixed(2) }}</template
              >
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="queue-card">
          <template #header>
            <div class="card-header">
              <span>等待队列 (等待调度)</span>
              <el-tag type="warning">{{
                Object.keys(waitingQueue).length
              }}</el-tag>
            </div>
          </template>
          <el-table
            :data="formatQueue(waitingQueue)"
            style="width: 100%"
            empty-text="暂无等待房间"
          >
            <el-table-column prop="roomId" label="房间" width="80" />
            <el-table-column prop="fanSpeed" label="风速" width="80" />
            <el-table-column prop="waitTimeRemaining" label="剩余等待(s)" />
            <el-table-column prop="totalWaitTime" label="总等待(s)" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 统计报表 -->
    <el-card class="report-card" style="margin-top: 30px">
      <template #header>
        <div class="card-header">
          <span>统计报表</span>
        </div>
      </template>

      <div class="report-controls">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss"
          style="margin-right: 15px"
        />
        <el-button
          type="primary"
          @click="generateReport"
          :loading="loadingReport"
          >生成报表</el-button
        >
      </div>

      <div v-if="reportData" class="report-content" style="margin-top: 20px">
        <!-- 摘要数据 -->
        <el-descriptions title="报表摘要" border>
          <el-descriptions-item label="总费用"
            >¥{{ reportData.totalFee }}</el-descriptions-item
          >
          <el-descriptions-item label="总服务时长"
            >{{ reportData.totalDurationSeconds }} 秒</el-descriptions-item
          >
          <el-descriptions-item label="总服务次数"
            >{{ reportData.totalServiceCount }} 次</el-descriptions-item
          >
        </el-descriptions>

        <el-row :gutter="20" style="margin-top: 20px">
          <el-col :span="12">
            <h4>各房间费用排名</h4>
            <el-table
              :data="formatRanking(reportData.roomFeeRanking)"
              border
              stripe
              height="250"
            >
              <el-table-column prop="roomId" label="房间号" />
              <el-table-column prop="fee" label="费用 (¥)" />
            </el-table>
          </el-col>
          <el-col :span="12">
            <h4>风速使用时长统计</h4>
            <el-table
              :data="formatFanStats(reportData.fanSpeedUsageDuration)"
              border
              stripe
              height="250"
            >
              <el-table-column prop="speed" label="风速" />
              <el-table-column prop="duration" label="时长 (秒)" />
            </el-table>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import api from "@/api";
import { ElMessage } from "element-plus";

const rooms = ref([]);
const serviceQueue = ref({});
const waitingQueue = ref({});
const currentTime = ref(new Date().toLocaleTimeString());
const selectedFloor = ref(1); // 默认选择第一层
const currentFloor = ref(null);
let timer = null;
let clockTimer = null;

// 报表相关
const dateRange = ref([]);
const loadingReport = ref(false);
const reportData = ref(null);

// 按楼层组织房间
const floorRooms = ref([]);

const fetchData = async () => {
  try {
    const resRooms = await api.get("/manager/rooms");
    rooms.value = resRooms.data;
    
    // 按楼层组织房间
    organizeRoomsByFloor();

    const resService = await api.get("/manager/queue/service");
    serviceQueue.value = resService.data;

    const resWaiting = await api.get("/manager/queue/waiting");
    waitingQueue.value = resWaiting.data;
  } catch (e) {
    console.error(e);
  }
};

// 按楼层组织房间
const organizeRoomsByFloor = () => {
  const floors = {};
  rooms.value.forEach(room => {
    const floor = parseInt(room.roomId.charAt(0));
    if (!floors[floor]) {
      floors[floor] = [];
    }
    floors[floor].push(room);
  });
  
  // 转换为数组并按楼层排序，同时分离上下排
  floorRooms.value = Object.keys(floors)
    .map(floor => {
      const floorNum = parseInt(floor);
      const allRooms = floors[floor].sort((a, b) => a.roomId.localeCompare(b.roomId));
      // 上排：101-105, 201-205, ...
      const upperRooms = allRooms.filter(r => {
        const roomNum = parseInt(r.roomId.substring(1));
        return roomNum >= 1 && roomNum <= 5;
      });
      // 下排：106-110, 206-210, ...
      const lowerRooms = allRooms.filter(r => {
        const roomNum = parseInt(r.roomId.substring(1));
        return roomNum >= 6 && roomNum <= 10;
      });
      return {
        floor: floorNum,
        rooms: allRooms,
        upperRooms,
        lowerRooms
      };
    })
    .sort((a, b) => a.floor - b.floor);
  
  // 更新当前楼层
  updateCurrentFloor();
};

// 更新当前楼层
const updateCurrentFloor = () => {
  currentFloor.value = floorRooms.value.find(f => f.floor === selectedFloor.value);
};

// 处理楼层切换
const handleFloorChange = () => {
  updateCurrentFloor();
};

const formatQueue = (queueObj) => {
  return Object.values(queueObj);
};

const getStatusType = (status) => {
  switch (status) {
    case "SERVING":
      return "success";
    case "WAITING":
      return "warning";
    case "IDLE":
      return "info";
    case "SHUTDOWN":
      return "danger";
    default:
      return "";
  }
};

const getStatusClass = (status) => {
  return `status-${status.toLowerCase()}`;
};

const formatStatus = (status) => {
  const map = {
    SERVING: "服务中",
    WAITING: "等待中",
    IDLE: "待机",
    SHUTDOWN: "关机",
  };
  return map[status] || status;
};

const timeFormatter = (row) => {
  if (!row.startTime) return "-";
  return new Date(row.startTime).toLocaleTimeString();
};

// 报表相关方法
const generateReport = async () => {
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning("请选择时间范围");
    return;
  }

  loadingReport.value = true;
  try {
    const res = await api.get("/manager/report", {
      params: {
        start: dateRange.value[0],
        end: dateRange.value[1],
      },
    });
    reportData.value = res.data;
    ElMessage.success("报表生成成功");
  } catch (e) {
    console.error(e);
    ElMessage.error("报表生成失败");
  } finally {
    loadingReport.value = false;
  }
};

const formatRanking = (rankingMap) => {
  if (!rankingMap) return [];
  return Object.entries(rankingMap).map(([roomId, fee]) => ({ roomId, fee }));
};

const formatFanStats = (statsMap) => {
  if (!statsMap) return [];
  const map = { HIGH: "高风", MIDDLE: "中风", LOW: "低风" };
  return Object.entries(statsMap).map(([speed, duration]) => ({
    speed: map[speed] || speed,
    duration,
  }));
};

onMounted(() => {
  fetchData();
  timer = setInterval(fetchData, 500);
  clockTimer = setInterval(() => {
    currentTime.value = new Date().toLocaleTimeString();
  }, 1000);
  // 初始化当前楼层
  updateCurrentFloor();
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
  if (clockTimer) clearInterval(clockTimer);
});
</script>

<style scoped>
.manager-container {
  padding: 30px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: calc(100vh - 60px);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  background: rgba(255, 255, 255, 0.95);
  padding: 20px 30px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.header h2 {
  margin: 0;
  color: #303133;
  font-size: 26px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.rooms-section {
  margin-bottom: 30px;
  background: rgba(255, 255, 255, 0.95);
  padding: 24px;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.floor-selector {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  border-radius: 12px;
  border: 1px solid #e4e7ed;
}

.selector-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selector-icon {
  font-size: 24px;
  color: #667eea;
}

.floor-select {
  width: 180px;
}

:deep(.floor-select .el-input__wrapper) {
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  padding: 8px 12px;
}

:deep(.floor-select .el-input__inner) {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}

.floor-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.floor-info .el-tag {
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.rooms-container {
  width: 100%;
}

.rooms-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 20px;
  justify-items: center;
}

.room-card {
  width: 100%;
  max-width: 240px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  border: 1px solid #e4e7ed;
}

.room-card:hover {
  transform: translateY(-6px) scale(1.02);
  box-shadow: 0 12px 32px rgba(102, 126, 234, 0.2);
  border-color: #667eea;
  background: linear-gradient(135deg, #ffffff 0%, #f0f4ff 100%);
}

.room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #f0f0f0;
}

.room-id {
  font-weight: 700;
  font-size: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 1px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  margin-bottom: 8px;
  color: #606266;
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.val {
  font-weight: 600;
  color: #303133;
  font-size: 15px;
}

.val.fee {
  color: #f56c6c;
  font-size: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
}

.queue-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
}

.queue-card:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
}

.report-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.report-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 状态边框颜色 */
.status-serving {
  border-left: 4px solid #67c23a;
  background: linear-gradient(135deg, #f0f9ff 0%, #ffffff 100%);
  box-shadow: 0 4px 16px rgba(103, 194, 58, 0.15);
}

.status-waiting {
  border-left: 4px solid #e6a23c;
  background: linear-gradient(135deg, #fff8e1 0%, #ffffff 100%);
  box-shadow: 0 4px 16px rgba(230, 162, 60, 0.15);
}

.status-idle {
  border-left: 4px solid #909399;
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  box-shadow: 0 4px 16px rgba(144, 147, 153, 0.15);
}

.status-shutdown {
  border-left: 4px solid #f56c6c;
  background: linear-gradient(135deg, #fef0f0 0%, #ffffff 100%);
  box-shadow: 0 4px 16px rgba(245, 108, 108, 0.15);
}

:deep(.el-card__header) {
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: linear-gradient(135deg, #f5f7fa 0%, #e9ecef 100%);
  font-weight: 600;
  color: #303133;
}
</style>
