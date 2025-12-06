<template>
  <div class="clerk-container">
    <div class="header">
      <h2>前台服务系统</h2>
      <el-tag type="info">{{ currentTime }}</el-tag>
    </div>
    
    <!-- 入住办理对话框 -->
    <el-dialog
      v-model="checkInDialogVisible"
      title="办理入住"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="checkInForm" label-width="100px">
        <el-form-item label="房间号">
          <el-input
            :value="checkInForm.roomId"
            disabled
            style="font-weight: 600; font-size: 16px;"
          />
        </el-form-item>
        <el-form-item label="房间类型">
          <el-tag :type="getRoomTypeTag(checkInForm.roomId)">
            {{ getRoomType(checkInForm.roomId) }}
          </el-tag>
        </el-form-item>
        <el-form-item label="顾客姓名" required>
          <el-input
            v-model="checkInForm.customerName"
            placeholder="请输入顾客姓名"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkInDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="handleCheckIn"
          :loading="loading"
          :disabled="!checkInForm.customerName"
        >
          确认办理
        </el-button>
      </template>
    </el-dialog>

    <!-- 退房结账对话框 -->
    <el-dialog
      v-model="checkoutDialogVisible"
      title="退房结账"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="checkoutRoomId">
        <el-descriptions title="房间信息" :column="2" border>
          <el-descriptions-item label="房间号">{{ checkoutRoomId }}</el-descriptions-item>
          <el-descriptions-item label="住客">{{ getRoomCustomerName(checkoutRoomId) }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="acBill || lodgingBill" style="margin-top: 20px">
          <el-descriptions
            title="住宿账单"
            :column="2"
            border
            v-if="lodgingBill"
            style="margin-bottom: 20px"
          >
            <el-descriptions-item label="房间号">{{ lodgingBill.roomId }}</el-descriptions-item>
            <el-descriptions-item label="入住天数">{{ lodgingBill.days }} 天</el-descriptions-item>
            <el-descriptions-item label="入住时间">{{ formatDate(lodgingBill.checkInTime) }}</el-descriptions-item>
            <el-descriptions-item label="退房时间">{{ formatDate(lodgingBill.checkOutTime) }}</el-descriptions-item>
            <el-descriptions-item label="住宿费用">
              <span class="price">¥{{ lodgingBill.totalLodgingFee?.toFixed(2) }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-descriptions
            title="空调账单"
            :column="2"
            border
            v-if="acBill"
            style="margin-bottom: 20px"
          >
            <el-descriptions-item label="空调总费用">
              <span class="price">¥{{ acBill.totalAcFee?.toFixed(2) }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-table
            :data="details"
            border
            stripe
            height="300"
            v-if="details.length"
            style="margin-bottom: 20px"
          >
            <el-table-column label="空调详单">
              <el-table-column prop="requestTime" label="请求时间" :formatter="requestTimeFormatter" width="160" />
              <el-table-column prop="startTime" label="开始时间" :formatter="timeFormatter" width="160" />
              <el-table-column prop="endTime" label="结束时间" :formatter="endTimeFormatter" width="160" />
              <el-table-column prop="duration" label="时长(s)" width="80" />
              <el-table-column prop="fanSpeed" label="风速" width="80" />
              <el-table-column prop="fee" label="费用" width="100">
                <template #default="scope">¥{{ scope.row.fee?.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="cumulativeFee" label="累积" width="100">
                <template #default="scope">¥{{ scope.row.cumulativeFee?.toFixed(2) }}</template>
              </el-table-column>
            </el-table-column>
          </el-table>
        </div>
        <el-empty v-else description="请先生成账单" />
      </div>
      <template #footer>
        <el-button @click="checkoutDialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          @click="handleCheckout"
          :loading="loading"
          :disabled="!checkoutRoomId"
        >
          生成账单
        </el-button>
        <el-button
          type="success"
          @click="handleExport"
          :loading="loading"
          :disabled="!checkoutRoomId || (!acBill && !lodgingBill)"
        >
          导出账单
        </el-button>
        <el-button
          type="danger"
          @click="handleConfirmCheckout"
          :loading="loading"
          :disabled="!checkoutRoomId"
        >
          确认退房
        </el-button>
      </template>
    </el-dialog>

    <!-- 楼层和房间展示 -->
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
              v-for="floor in floors"
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
          <el-tag type="success" size="large">
            <el-icon><User /></el-icon>
            <span>已入住 {{ getOccupiedCount(currentFloor.rooms) }} 间</span>
          </el-tag>
        </div>
      </div>
      
      <div class="rooms-container" v-if="currentFloor">
        <div class="rooms-grid">
          <div
            v-for="room in currentFloor.rooms"
            :key="room.roomId"
            class="room-card"
            :class="{
              'occupied': room.customerName,
              'original-room': isOriginalRoom(room.roomId)
            }"
            @click="handleRoomClick(room)"
          >
            <div class="room-header">
              <span class="room-id">{{ room.roomId }}</span>
              <div class="room-badges">
                <el-tag :type="getRoomTypeTag(room.roomId)" size="small" effect="plain">
                  {{ getRoomType(room.roomId) }}
                </el-tag>
              </div>
            </div>
            <div class="room-body">
              <div class="room-info-item">
                <span class="label">状态:</span>
                <el-tag :type="getStatusType(room.status)" size="small">
                  {{ formatStatus(room.status) }}
                </el-tag>
              </div>
              <div class="room-info-item" v-if="room.customerName">
                <span class="label">住客:</span>
                <span class="value">{{ room.customerName }}</span>
              </div>
              <div class="room-info-item">
                <span class="label">房价:</span>
                <span class="value price">¥{{ room.pricePerDay }}/天</span>
              </div>
            </div>
            <div class="room-footer" v-if="room.customerName">
              <el-icon><User /></el-icon>
              <span>已入住</span>
            </div>
            <div class="room-footer available" v-else>
              <el-icon><CircleCheck /></el-icon>
              <span>可入住</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from "vue";
import api from "@/api";
import { ElMessage, ElMessageBox } from "element-plus";
import { User, CircleCheck, OfficeBuilding, HomeFilled } from "@element-plus/icons-vue";

const loading = ref(false);
const currentTime = ref(new Date().toLocaleTimeString());
let clockTimer = null;
let roomsTimer = null;

// 入住相关
const checkInForm = reactive({
  roomId: "",
  customerName: "",
});
const checkInDialogVisible = ref(false);
const selectedFloor = ref(1); // 默认选择第一层
const currentFloor = ref(null);

// 退房相关
const checkoutDialogVisible = ref(false);
const checkoutRoomId = ref("");
const acBill = ref(null);
const lodgingBill = ref(null);
const details = ref([]);

// 房间数据
const allRooms = ref([]);

// 生成40个房间的楼层结构
const generateFloors = () => {
  const floors = [];
  
  for (let floor = 1; floor <= 4; floor++) {
    const upperRooms = [];
    const lowerRooms = [];
    
    // 上排5间（1-5号）
    for (let room = 1; room <= 5; room++) {
      const roomId = `${floor}${String(room).padStart(2, '0')}`;
      const isKingBed = room === 1;
      upperRooms.push({
        roomId,
        type: isKingBed ? 'king' : 'standard',
        pricePerDay: isKingBed ? 200 : 100,
      });
    }
    
    // 下排5间（6-10号）
    for (let room = 6; room <= 10; room++) {
      const roomId = `${floor}${String(room).padStart(2, '0')}`;
      lowerRooms.push({
        roomId,
        type: 'standard',
        pricePerDay: 100,
      });
    }
    
    floors.push({
      floor,
      upperRooms,
      lowerRooms,
      rooms: [...upperRooms, ...lowerRooms],
    });
  }
  return floors;
};

const floors = ref(generateFloors());

// 初始化当前楼层
const initCurrentFloor = () => {
  currentFloor.value = floors.value.find(f => f.floor === selectedFloor.value);
};

// 处理楼层切换
const handleFloorChange = () => {
  currentFloor.value = floors.value.find(f => f.floor === selectedFloor.value);
};

// 获取房间类型
const getRoomType = (roomId) => {
  const room = findRoom(roomId);
  return room?.type === 'king' ? '大床房' : '标准客房';
};

// 获取房间类型标签
const getRoomTypeTag = (roomId) => {
  const room = findRoom(roomId);
  return room?.type === 'king' ? 'warning' : 'info';
};

// 查找房间
const findRoom = (roomId) => {
  for (const floor of floors.value) {
    const room = floor.rooms.find(r => r.roomId === roomId);
    if (room) return room;
  }
  return null;
};

// 获取已入住数量
const getOccupiedCount = (rooms) => {
  return rooms.filter(r => r.customerName).length;
};

// 判断是否是原来的5个房间之一（101-105）
const isOriginalRoom = (roomId) => {
  return ['101', '102', '103', '104', '105'].includes(roomId);
};

// 获取原房间号
const getOriginalRoomNumber = (roomId) => {
  if (['101', '102', '103', '104', '105'].includes(roomId)) {
    return roomId;
  }
  return '';
};

// 获取房间住客姓名
const getRoomCustomerName = (roomId) => {
  const room = findRoom(roomId);
  return room?.customerName || '';
};

// 处理房间点击
const handleRoomClick = (room) => {
  if (!room) return;
  
  if (room.customerName) {
    // 已入住，弹出退房对话框
    checkoutRoomId.value = room.roomId;
    acBill.value = null;
    lodgingBill.value = null;
    details.value = [];
    checkoutDialogVisible.value = true;
  } else {
    // 空闲，弹出入住对话框
    checkInForm.roomId = room.roomId;
    checkInForm.customerName = "";
    checkInDialogVisible.value = true;
  }
};

// 状态格式化
const getStatusType = (status) => {
  const map = {
    SERVING: "success",
    WAITING: "warning",
    IDLE: "info",
    SHUTDOWN: "danger",
  };
  return map[status] || "";
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

// 获取所有房间数据
const fetchRooms = async () => {
  try {
    const res = await api.get("/manager/rooms");
    allRooms.value = res.data;
    
    floors.value.forEach(floor => {
      const updateRoom = (room) => {
        const roomData = allRooms.value.find(r => r.roomId === room.roomId);
        if (roomData) {
          // 从后端API获取的数据是准确的,直接使用
          Object.assign(room, roomData);
        } else {
          // 如果后端未返回该房间数据,才使用本地默认配置
          if (!room.status) room.status = 'SHUTDOWN';
          if (!room.currentTemp) room.currentTemp = room.type === 'king' ? 25 : 28;
          if (room.customerName === undefined) room.customerName = null;
          if (!room.pricePerDay) room.pricePerDay = room.type === 'king' ? 200 : 100;
        }
      };
      
      floor.upperRooms.forEach(updateRoom);
      floor.lowerRooms.forEach(updateRoom);
    });
  } catch (e) {
    console.error("获取房间数据失败", e);
  }
};

// 办理入住
const handleCheckIn = async () => {
  if (!checkInForm.roomId || !checkInForm.customerName) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  loading.value = true;
  try {
    await api.post("/clerk/checkin", {
      roomId: checkInForm.roomId,
      customerName: checkInForm.customerName,
      idCard: "",
    });
    ElMessage.success(`房间 ${checkInForm.roomId} 入住办理成功`);
    checkInForm.roomId = "";
    checkInForm.customerName = "";
    checkInDialogVisible.value = false;
    await fetchRooms();
  } catch (e) {
    ElMessage.error("办理失败: " + (e.response?.data?.message || e.message));
  } finally {
    loading.value = false;
  }
};

// 生成账单
const handleCheckout = async () => {
  if (!checkoutRoomId.value) return;
  loading.value = true;
  try {
    const resAc = await api.post("/clerk/checkout/ac", null, {
      params: { roomId: checkoutRoomId.value },
    });
    acBill.value = resAc.data;

    const resLodging = await api.post("/clerk/checkout/lodging", null, {
      params: { roomId: checkoutRoomId.value },
    });
    lodgingBill.value = resLodging.data;

    const resDetails = await api.get("/clerk/details", {
      params: { roomId: checkoutRoomId.value },
    });
    details.value = resDetails.data;

    ElMessage.success("账单生成成功");
  } catch (e) {
    ElMessage.error("结账失败");
  } finally {
    loading.value = false;
  }
};

// 导出账单
const handleExport = async () => {
  if (!checkoutRoomId.value) return;

  if (!acBill.value && !lodgingBill.value) {
    await handleCheckout();
    if (!acBill.value && !lodgingBill.value) return;
  }

  try {
    const response = await api.get("/clerk/export", {
      params: { roomId: checkoutRoomId.value },
      responseType: 'blob'
    });
    
    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `bill_${checkoutRoomId.value}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    
    ElMessage.success("导出成功");
  } catch (e) {
    console.error(e);
    ElMessage.error("导出失败");
  }
};

// 确认退房
const handleConfirmCheckout = async () => {
  if (!checkoutRoomId.value) {
    ElMessage.warning("请选择房间");
    return;
  }
  
  // 确认对话框
  try {
    await ElMessageBox.confirm(
      `确认退房房间 ${checkoutRoomId.value} 吗？`,
      '确认退房',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );
  } catch {
    // 用户取消
    return;
  }
  
  loading.value = true;
  try {
    // 先尝试生成账单（如果还没有生成）
    if (!acBill.value && !lodgingBill.value) {
      try {
        await handleCheckout();
      } catch (e) {
        // 账单生成失败不影响退房，继续执行
        console.warn("账单生成失败，继续退房流程", e);
      }
    }
    
    // 调用退房接口，清除房间的入住信息
    await api.post("/clerk/checkout/confirm", null, {
      params: { roomId: checkoutRoomId.value },
    });
    
    ElMessage.success(`房间 ${checkoutRoomId.value} 退房成功`);
    
    // 关闭对话框并重置
    checkoutDialogVisible.value = false;
    checkoutRoomId.value = "";
    acBill.value = null;
    lodgingBill.value = null;
    details.value = [];
    
    // 刷新房间数据
    await fetchRooms();
  } catch (e) {
    console.error("退房失败", e);
    ElMessage.error("退房失败: " + (e.response?.data?.message || e.message || "未知错误"));
  } finally {
    loading.value = false;
  }
};

// 日期格式化
const formatDate = (val) => {
  if (!val) return "";
  return new Date(val).toLocaleString();
};

const requestTimeFormatter = (row) => {
  return row.requestTime ? new Date(row.requestTime).toLocaleString() : "";
};

const timeFormatter = (row) => {
  return row.startTime ? new Date(row.startTime).toLocaleString() : "";
};

const endTimeFormatter = (row) => {
  return row.endTime ? new Date(row.endTime).toLocaleString() : "";
};

onMounted(() => {
  clockTimer = setInterval(() => {
    currentTime.value = new Date().toLocaleTimeString();
  }, 1000);
  
  initCurrentFloor();
  fetchRooms();
  roomsTimer = setInterval(fetchRooms, 2000);
});

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer);
  if (roomsTimer) clearInterval(roomsTimer);
});
</script>

<style scoped>
.clerk-container {
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
  margin-top: 20px;
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
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  border: 2px solid #e4e7ed;
  border-radius: 16px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  user-select: none;
  -webkit-user-select: none;
  width: 100%;
  max-width: 240px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.room-card:hover {
  transform: translateY(-6px) scale(1.02);
  box-shadow: 0 12px 32px rgba(102, 126, 234, 0.2);
  border-color: #667eea;
  background: linear-gradient(135deg, #ffffff 0%, #f0f4ff 100%);
}

.room-card.occupied {
  border-color: #f0a020;
  background: linear-gradient(135deg, #fff8e1 0%, #ffffff 100%);
  box-shadow: 0 4px 12px rgba(240, 160, 32, 0.15);
}

.room-card.occupied:hover {
  border-color: #e6a23c;
  box-shadow: 0 12px 32px rgba(230, 162, 60, 0.25);
  transform: translateY(-6px) scale(1.02);
}

.room-card.original-room {
  border: 2px solid #67c23a;
  background: linear-gradient(135deg, #f0fdf4 0%, #ffffff 100%);
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.15);
}

.room-card.original-room:hover {
  border-color: #67c23a;
  box-shadow: 0 12px 32px rgba(103, 194, 58, 0.25);
  transform: translateY(-6px) scale(1.02);
}

.room-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.room-id {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.room-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.room-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.room-info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.room-info-item .label {
  color: #909399;
  font-weight: 500;
}

.room-info-item .value {
  color: #303133;
  font-weight: 600;
}

.room-info-item .value.price {
  color: #f56c6c;
}

.room-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  margin-top: 8px;
  background: #f5f7fa;
  color: #909399;
}

.room-footer.available {
  background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
  color: #4caf50;
}

.room-card.occupied .room-footer {
  background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
  color: #ff9800;
}

.price {
  color: #f56c6c;
  font-weight: 700;
  font-size: 18px;
}
</style>
