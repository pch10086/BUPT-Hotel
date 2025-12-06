<template>
  <div class="guest-container">
    <div class="header">
      <el-icon><House /></el-icon>
      <h2>客房空调控制系统</h2>
    </div>

    <!-- 如果没有已入住的房间，显示提示 -->
    <el-empty 
      v-if="sortedRooms.length === 0" 
      description="暂无已入住的房间，请先在前台办理入住"
      :image-size="120"
    />
    
    <div v-else class="rooms-grid">
      <el-card
        v-for="room in sortedRooms"
        :key="room.roomId"
        class="room-card"
        :class="{ 'is-on': room.isOn }"
        shadow="hover"
      >
        <template #header>
          <div class="card-header">
            <div class="room-title">
              <span>{{ room.roomId }}</span>
              <span v-if="room.customerName" class="customer-name">({{ room.customerName }})</span>
            </div>
            <el-tag
              :type="room.isOn ? 'success' : 'info'"
              effect="dark"
              size="small"
              round
            >
              {{ room.isOn ? "运行中" : "已关机" }}
            </el-tag>
          </div>
        </template>

        <!-- 关机状态：开机面板 -->
        <div v-if="!room.isOn" class="control-body off-state">
          <div class="status-placeholder">
            <el-icon :size="40" color="#909399"><SwitchButton /></el-icon>
            <p>空调已关闭</p>
          </div>
          
          <el-form size="small" label-position="top">
            <el-form-item label="模式">
              <el-radio-group 
                v-model="getControl(room.roomId).mode" 
                size="small"
                @change="(val) => handleModeChange(room.roomId, val)"
              >
                <el-radio-button label="COOL">制冷</el-radio-button>
                <el-radio-button label="HEAT">制热</el-radio-button>
              </el-radio-group>
            </el-form-item>
            
            <el-row :gutter="10">
              <el-col :span="12">
                <el-form-item label="温度">
                  <el-input-number 
                    v-model="getControl(room.roomId).targetTemp" 
                    :min="getControl(room.roomId).mode === 'COOL' ? 18 : 18" 
                    :max="getControl(room.roomId).mode === 'COOL' ? 28 : 25" 
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="风速">
                  <el-select v-model="getControl(room.roomId).fanSpeed" style="width: 100%">
                    <el-option label="高" value="HIGH" />
                    <el-option label="中" value="MIDDLE" />
                    <el-option label="低" value="LOW" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-button
              type="primary"
              class="action-btn"
              @click="powerOn(room.roomId)"
              :icon="SwitchButton"
            >
              开启空调
            </el-button>
          </el-form>
        </div>

        <!-- 开机状态：监控与控制 -->
        <div v-else class="control-body on-state">
          <div class="metrics">
            <div class="metric-item wide">
              <div class="m-label">室温</div>
              <div class="m-value temp">{{ room.currentTemp?.toFixed(1) }}℃</div>
            </div>
            <div class="metric-item">
              <div class="m-label">总费用</div>
              <div class="m-value fee">¥{{ room.totalFee?.toFixed(1) }}</div>
            </div>
            <div class="metric-item">
              <div class="m-label">本次费用</div>
              <div class="m-value fee">¥{{ room.currentSessionFee?.toFixed(1) || '0.0' }}</div>
            </div>
            <div class="metric-item">
              <div class="m-label">模式</div>
              <el-tag size="small" :type="room.mode === 'COOL' ? 'info' : 'warning'">
                {{ room.mode === 'COOL' ? '制冷' : '制热' }}
              </el-tag>
            </div>
            <div class="metric-item">
              <div class="m-label">状态</div>
              <el-tag size="small" :type="getStatusType(room.status)">
                {{ formatStatus(room.status) }}
              </el-tag>
            </div>
          </div>

          <el-divider style="margin: 12px 0" />

          <el-form size="small" label-position="top">
             <el-row :gutter="10">
              <el-col :span="12">
                <el-form-item label="目标温度">
                  <el-input-number 
                    v-model="room.targetTemp" 
                    :min="room.mode === 'COOL' ? 18 : 18" 
                    :max="room.mode === 'COOL' ? 28 : 25" 
                    controls-position="right"
                    style="width: 100%"
                    @change="(val) => updateState(room.roomId, val, room.fanSpeed)"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="风速">
                  <el-select 
                    v-model="room.fanSpeed" 
                    style="width: 100%"
                    @change="(val) => updateState(room.roomId, room.targetTemp, val)"
                  >
                    <el-option label="高" value="HIGH" />
                    <el-option label="中" value="MIDDLE" />
                    <el-option label="低" value="LOW" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            
            <el-button
              type="danger"
              plain
              class="action-btn"
              @click="powerOff(room.roomId)"
              :icon="SwitchButton"
            >
              关闭空调
            </el-button>
          </el-form>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import api from "@/api";
import { ElMessage } from "element-plus";
import { House, SwitchButton } from "@element-plus/icons-vue";

const rooms = ref([]);
const localControls = ref({}); // 存储每个房间的表单状态 { roomId: { mode, targetTemp, fanSpeed } }
let timer = null;

// 只显示已办理入住的房间（有customerName的房间），并按ID排序
const sortedRooms = computed(() => {
  return [...rooms.value]
    .filter(room => room.customerName) // 只显示已入住的房间
    .sort((a, b) => a.roomId.localeCompare(b.roomId));
});

// 获取或初始化本地控制状态
const getControl = (roomId) => {
  if (!localControls.value[roomId]) {
    localControls.value[roomId] = {
      mode: 'COOL',
      targetTemp: 25,
      fanSpeed: 'MIDDLE'
    };
  }
  return localControls.value[roomId];
};

// 处理模式切换，自动设置缺省值
const handleModeChange = (roomId, mode) => {
  const control = getControl(roomId);
  if (mode === 'COOL') {
    control.targetTemp = 25;
    control.fanSpeed = 'MIDDLE';
  } else if (mode === 'HEAT') {
    control.targetTemp = 23;
    control.fanSpeed = 'MIDDLE';
  }
};

const fetchStatus = async () => {
  try {
    // 使用管理员接口获取所有房间状态，效率更高
    const res = await api.get("/manager/rooms");
    rooms.value = res.data;
  } catch (e) {
    console.error("Fetch status failed", e);
  }
};

const powerOn = async (roomId) => {
  const control = getControl(roomId);
  try {
    await api.post("/guest/powerOn", {
      roomId: roomId,
      mode: control.mode,
      targetTemp: control.targetTemp,
      fanSpeed: control.fanSpeed,
    });
    ElMessage.success(`${roomId} 开机成功`);
    fetchStatus(); // 立即刷新
  } catch (e) {
    ElMessage.error("开机失败: " + (e.response?.data?.message || e.message));
  }
};

const powerOff = async (roomId) => {
  try {
    await api.post("/guest/powerOff", null, {
      params: { roomId: roomId },
    });
    ElMessage.success(`${roomId} 关机成功`);
    fetchStatus();
  } catch (e) {
    ElMessage.error("关机失败");
  }
};

const timeoutMap = {};

// 针对每个房间的防抖更新
const updateState = (roomId, targetTemp, fanSpeed) => {
  if (timeoutMap[roomId]) {
    clearTimeout(timeoutMap[roomId]);
  }
  
  timeoutMap[roomId] = setTimeout(async () => {
    try {
      await api.post("/guest/changeState", {
        roomId: roomId,
        targetTemp: targetTemp,
        fanSpeed: fanSpeed,
      });
      ElMessage.success(`房间 ${roomId} 设置已更新`);
    } catch (e) {
      ElMessage.error(`房间 ${roomId} 更新失败: ` + (e.response?.data?.message || e.message));
    }
    delete timeoutMap[roomId];
  }, 1000);
};

const getStatusType = (status) => {
  const map = {
    SERVING: "success",
    WAITING: "warning",
    IDLE: "info",
    SHUTDOWN: "danger"
  };
  return map[status] || "";
};

const formatStatus = (status) => {
  const map = {
    SERVING: "送风",
    WAITING: "等待",
    IDLE: "待机",
    SHUTDOWN: "关机",
  };
  return map[status] || status;
};

onMounted(() => {
  fetchStatus();
  timer = setInterval(fetchStatus, 1000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.guest-container {
  padding: 30px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  min-height: calc(100vh - 60px);
}

.header {
  margin-bottom: 30px;
  display: flex;
  align-items: center;
  gap: 15px;
  background: rgba(255, 255, 255, 0.95);
  padding: 20px 30px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.header h2 {
  margin: 0;
  color: #303133;
  font-size: 24px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.rooms-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.room-card {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border-radius: 12px;
  overflow: hidden;
  background: #ffffff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  min-height: 420px;
  display: flex;
  flex-direction: column;
}

.room-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.room-card.is-on {
  border-top: 4px solid #67c23a;
  box-shadow: 0 4px 16px rgba(103, 194, 58, 0.2);
}

:deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.room-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 18px;
  color: #303133;
}

.room-title .el-icon {
  color: #667eea;
  font-size: 20px;
}

.customer-name {
  font-size: 13px;
  font-weight: normal;
  color: #909399;
  margin-left: 6px;
}

.control-body {
  padding: 16px 0;
  min-height: 320px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.status-placeholder {
  text-align: center;
  color: #909399;
  margin-bottom: 24px;
  padding: 20px;
  min-height: 100px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.status-placeholder .el-icon {
  margin-bottom: 12px;
}

.status-placeholder p {
  margin: 0;
  font-size: 14px;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  align-items: stretch;
}

.metric-item {
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px 14px;
  box-shadow: 0 6px 16px rgba(0,0,0,0.06);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.metric-item.wide {
  grid-column: span 2;
}

.m-label {
  color: #909399;
  font-size: 12px;
  letter-spacing: 0.2px;
}

.m-value {
  font-weight: 700;
  font-size: 20px;
  color: #303133;
}

.m-value.temp { 
  color: #67c23a;
}

.m-value.fee { 
  color: #409eff;
}

.action-btn {
  width: 100%;
  margin-top: 16px;
  height: 42px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 8px;
  transition: all 0.3s;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
}

:deep(.el-card__header) {
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
  border-bottom: 1px solid #ebeef5;
  padding: 16px 20px;
}
</style>
