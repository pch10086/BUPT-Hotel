<template>
  <div class="manager-container">
    <div class="header">
      <h2>酒店中央空调监控系统</h2>
      <el-tag type="info">{{ currentTime }}</el-tag>
    </div>

    <!-- 房间状态卡片 -->
    <el-row :gutter="20" class="room-grid">
      <el-col :span="4" v-for="room in rooms" :key="room.roomId">
        <el-card
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
              <span>费用:</span>
              <span class="val fee">¥{{ room.totalFee?.toFixed(2) }}</span>
            </div>
            <div class="info-row" v-if="room.customerName">
              <span>住客:</span>
              <span class="val">{{ room.customerName }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

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
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import api from "@/api";

const rooms = ref([]);
const serviceQueue = ref({});
const waitingQueue = ref({});
const currentTime = ref(new Date().toLocaleTimeString());
let timer = null;
let clockTimer = null;

const fetchData = async () => {
  try {
    const resRooms = await api.get("/manager/rooms");
    rooms.value = resRooms.data;

    const resService = await api.get("/manager/queue/service");
    serviceQueue.value = resService.data;

    const resWaiting = await api.get("/manager/queue/waiting");
    waitingQueue.value = resWaiting.data;
  } catch (e) {
    console.error(e);
  }
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

onMounted(() => {
  fetchData();
  timer = setInterval(fetchData, 1000);
  clockTimer = setInterval(() => {
    currentTime.value = new Date().toLocaleTimeString();
  }, 1000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
  if (clockTimer) clearInterval(clockTimer);
});
</script>

<style scoped>
.manager-container {
  padding: 20px;
  background-color: #f0f2f5;
  min-height: calc(100vh - 60px);
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.room-card {
  margin-bottom: 20px;
  transition: all 0.3s;
}

.room-card:hover {
  transform: translateY(-5px);
}

.room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-weight: bold;
  font-size: 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 5px;
  color: #606266;
}

.val {
  font-weight: 500;
  color: #303133;
}

.val.fee {
  color: #f56c6c;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 状态边框颜色 */
.status-serving {
  border-top: 3px solid #67c23a;
}
.status-waiting {
  border-top: 3px solid #e6a23c;
}
.status-idle {
  border-top: 3px solid #909399;
}
.status-shutdown {
  border-top: 3px solid #f56c6c;
}
</style>
