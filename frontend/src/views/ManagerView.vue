<template>
  <div class="manager-panel">
    <h2>经理监控面板</h2>

    <el-row :gutter="20">
      <el-col :span="24">
        <el-card>
          <template #header>房间状态概览</template>
          <el-table :data="rooms" style="width: 100%">
            <el-table-column prop="roomId" label="房间号" />
            <el-table-column prop="status" label="状态">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)">{{
                  scope.row.status
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="currentTemp" label="当前温度" />
            <el-table-column prop="targetTemp" label="目标温度" />
            <el-table-column prop="fanSpeed" label="风速" />
            <el-table-column prop="totalFee" label="当前费用" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card header="服务队列 (正在送风)">
          <div
            v-for="(unit, rid) in serviceQueue"
            :key="rid"
            class="queue-item"
          >
            Room {{ rid }} - {{ unit.fanSpeed }} -
            {{ unit.servedDurationSeconds }}s
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="等待队列">
          <div
            v-for="(info, rid) in waitingQueue"
            :key="rid"
            class="queue-item"
          >
            Room {{ rid }} - {{ info.fanSpeed }} - Wait:
            {{ info.waitTimeRemaining }}s
          </div>
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
let timer = null;

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

const getStatusType = (status) => {
  switch (status) {
    case "SERVING":
      return "success";
    case "WAITING":
      return "warning";
    case "SHUTDOWN":
      return "info";
    default:
      return "";
  }
};

onMounted(() => {
  fetchData();
  timer = setInterval(fetchData, 2000);
});

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.manager-panel {
  padding: 20px;
}
.queue-item {
  padding: 5px;
  border-bottom: 1px solid #eee;
}
</style>
