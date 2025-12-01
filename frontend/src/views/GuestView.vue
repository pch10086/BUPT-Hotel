<template>
  <div class="guest-panel">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>房间控制面板 (Room: {{ roomId }})</span>
          <el-tag :type="room.isOn ? 'success' : 'info'">{{
            room.isOn ? "开机" : "关机"
          }}</el-tag>
        </div>
      </template>

      <div v-if="!room.isOn">
        <el-form>
          <el-form-item label="房间号">
            <el-select v-model="roomId" placeholder="选择房间">
              <el-option label="101" value="101" />
              <el-option label="102" value="102" />
              <el-option label="103" value="103" />
              <el-option label="104" value="104" />
              <el-option label="105" value="105" />
            </el-select>
          </el-form-item>
          <el-form-item label="模式">
            <el-radio-group v-model="mode">
              <el-radio label="COOL">制冷</el-radio>
              <el-radio label="HEAT">制热</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="目标温度">
            <el-input-number v-model="targetTemp" :min="18" :max="28" />
          </el-form-item>
          <el-form-item label="风速">
            <el-radio-group v-model="fanSpeed">
              <el-radio label="HIGH">高</el-radio>
              <el-radio label="MIDDLE">中</el-radio>
              <el-radio label="LOW">低</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-button type="primary" @click="powerOn">开机</el-button>
        </el-form>
      </div>

      <div v-else>
        <div class="status-display">
          <p>当前温度: {{ room.currentTemp?.toFixed(2) }} ℃</p>
          <p>目标温度: {{ room.targetTemp }} ℃</p>
          <p>当前费用: {{ room.totalFee?.toFixed(2) }} 元</p>
          <p>状态: {{ room.status }}</p>
        </div>

        <el-divider />

        <el-form>
          <el-form-item label="调节温度">
            <el-input-number
              v-model="room.targetTemp"
              :min="18"
              :max="28"
              @change="updateState"
            />
          </el-form-item>
          <el-form-item label="调节风速">
            <el-radio-group v-model="room.fanSpeed" @change="updateState">
              <el-radio label="HIGH">高</el-radio>
              <el-radio label="MIDDLE">中</el-radio>
              <el-radio label="LOW">低</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-button type="danger" @click="powerOff">关机</el-button>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from "vue";
import api from "@/api";
import { ElMessage } from "element-plus";

const roomId = ref("101");
const mode = ref("COOL");
const targetTemp = ref(25);
const fanSpeed = ref("MIDDLE");
const room = ref({ isOn: false });
let timer = null;

const refreshStatus = async () => {
  if (!room.value.isOn) return;
  try {
    const res = await api.get("/guest/status", {
      params: { roomId: roomId.value },
    });
    room.value = res.data;
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
    startTimer();
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
    stopTimer();
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

const startTimer = () => {
  if (timer) clearInterval(timer);
  timer = setInterval(refreshStatus, 1000);
};

const stopTimer = () => {
  if (timer) clearInterval(timer);
};

onUnmounted(() => {
  stopTimer();
});
</script>

<style scoped>
.guest-panel {
  max-width: 500px;
  margin: 20px auto;
}
.status-display p {
  font-size: 16px;
  margin: 10px 0;
}
</style>
