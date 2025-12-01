<template>
  <div class="guest-container">
    <el-card class="control-panel" shadow="hover">
      <template #header>
        <div class="panel-header">
          <div class="room-info">
            <el-icon :size="24"><House /></el-icon>
            <span class="room-number">Room {{ roomId }}</span>
          </div>
          <el-tag :type="room.isOn ? 'success' : 'info'" effect="dark" round>
            {{ room.isOn ? "运行中" : "已关机" }}
          </el-tag>
        </div>
      </template>

      <!-- 关机状态 -->
      <div v-if="!room.isOn" class="off-state">
        <el-empty description="空调已关闭">
          <el-form label-width="80px" class="init-form">
            <el-form-item label="房间号">
              <el-select
                v-model="roomId"
                placeholder="选择房间"
                @change="fetchStatus"
              >
                <el-option label="101" value="101" />
                <el-option label="102" value="102" />
                <el-option label="103" value="103" />
                <el-option label="104" value="104" />
                <el-option label="105" value="105" />
              </el-select>
            </el-form-item>
            <el-form-item label="模式">
              <el-radio-group v-model="mode">
                <el-radio-button label="COOL">制冷</el-radio-button>
                <el-radio-button label="HEAT">制热</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="目标温度">
              <el-input-number v-model="targetTemp" :min="18" :max="28" />
            </el-form-item>
            <el-form-item label="风速">
              <el-radio-group v-model="fanSpeed">
                <el-radio-button label="HIGH">高</el-radio-button>
                <el-radio-button label="MIDDLE">中</el-radio-button>
                <el-radio-button label="LOW">低</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-button
              type="primary"
              size="large"
              class="power-btn"
              @click="powerOn"
              :icon="SwitchButton"
            >
              开启空调
            </el-button>
          </el-form>
        </el-empty>
      </div>

      <!-- 开机状态 -->
      <div v-else class="on-state">
        <div class="dashboard">
          <div class="metric-card">
            <div class="label">当前温度</div>
            <div class="value temp">
              {{ room.currentTemp?.toFixed(2) }}<small>℃</small>
            </div>
          </div>
          <div class="metric-card">
            <div class="label">当前费用</div>
            <div class="value fee">¥{{ room.totalFee?.toFixed(2) }}</div>
          </div>
          <div class="metric-card">
            <div class="label">运行状态</div>
            <div class="value status">
              <el-tag :type="getStatusType(room.status)">{{
                formatStatus(room.status)
              }}</el-tag>
            </div>
          </div>
        </div>

        <el-divider content-position="center">控制面板</el-divider>

        <el-form label-position="top">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="调节温度">
                <el-input-number
                  v-model="room.targetTemp"
                  :min="18"
                  :max="28"
                  @change="updateState"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="调节风速">
                <el-radio-group
                  v-model="room.fanSpeed"
                  @change="updateState"
                  size="small"
                >
                  <el-radio-button label="HIGH">高</el-radio-button>
                  <el-radio-button label="MIDDLE">中</el-radio-button>
                  <el-radio-button label="LOW">低</el-radio-button>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>
          <el-button
            type="danger"
            class="power-btn"
            @click="powerOff"
            :icon="SwitchButton"
            plain
          >
            关闭空调
          </el-button>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import api from "@/api";
import { ElMessage } from "element-plus";
import { House, SwitchButton } from "@element-plus/icons-vue";

const roomId = ref("101");
const mode = ref("COOL");
const targetTemp = ref(25);
const fanSpeed = ref("MIDDLE");
const room = ref({ isOn: false });
let timer = null;

// 获取最新状态
const fetchStatus = async () => {
  try {
    const res = await api.get("/guest/status", {
      params: { roomId: roomId.value },
    });
    const data = res.data;
    room.value = data;

    // 如果房间是开机状态，同步本地控制参数
    if (data.isOn) {
      mode.value = data.mode;
      targetTemp.value = data.targetTemp;
      fanSpeed.value = data.fanSpeed;
    }
  } catch (e) {
    console.error(e);
  }
};

const powerOn = async () => {
  try {
    const res = await api.post("/guest/powerOn", {
      roomId: roomId.value,
      mode: mode.value,
      targetTemp: targetTemp.value,
      fanSpeed: fanSpeed.value,
    });
    room.value = res.data;
    ElMessage.success("空调已开启");
  } catch (e) {
    ElMessage.error("开机失败");
  }
};

const powerOff = async () => {
  try {
    await api.post("/guest/powerOff", null, {
      params: { roomId: roomId.value },
    });
    room.value.isOn = false;
    ElMessage.success("空调已关闭");
  } catch (e) {
    ElMessage.error("关机失败");
  }
};

const updateState = async () => {
  try {
    await api.post("/guest/changeState", {
      roomId: roomId.value,
      targetTemp: room.value.targetTemp,
      fanSpeed: room.value.fanSpeed,
    });
    ElMessage.success("设置已更新");
  } catch (e) {
    ElMessage.error("更新失败");
  }
};

const getStatusType = (status) => {
  switch (status) {
    case "SERVING":
      return "success";
    case "WAITING":
      return "warning";
    case "IDLE":
      return "info";
    default:
      return "";
  }
};

const formatStatus = (status) => {
  const map = {
    SERVING: "送风中",
    WAITING: "等待中",
    IDLE: "待机(回温)",
    SHUTDOWN: "关机",
  };
  return map[status] || status;
};

onMounted(() => {
  fetchStatus(); // 初始化时立即获取一次状态
  timer = setInterval(fetchStatus, 1000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.guest-container {
  display: flex;
  justify-content: center;
  padding: 40px 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.control-panel {
  width: 100%;
  max-width: 480px;
  border-radius: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.room-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.dashboard {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
  margin-bottom: 25px;
}

.metric-card {
  text-align: center;
  background: #f0f9eb;
  padding: 15px 5px;
  border-radius: 8px;
}

.metric-card:nth-child(2) {
  background: #ecf5ff;
}

.metric-card:nth-child(3) {
  background: #fdf6ec;
}

.label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.value {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.value.temp {
  color: #67c23a;
}
.value.fee {
  color: #409eff;
}

.power-btn {
  width: 100%;
  margin-top: 20px;
}

.init-form {
  margin-top: 20px;
}
</style>
