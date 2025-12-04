<template>
  <div class="clerk-container">
    <el-tabs type="border-card" class="main-tabs">
      <!-- 入住办理 -->
      <el-tab-pane label="入住办理">
        <div class="tab-content">
          <el-form
            :model="checkInForm"
            label-width="100px"
            class="checkin-form"
          >
            <el-form-item label="房间号">
              <el-select v-model="checkInForm.roomId" placeholder="请选择房间">
                <el-option label="101" value="101" />
                <el-option label="102" value="102" />
                <el-option label="103" value="103" />
                <el-option label="104" value="104" />
                <el-option label="105" value="105" />
              </el-select>
            </el-form-item>
            <el-form-item label="顾客姓名">
              <el-input
                v-model="checkInForm.customerName"
                placeholder="请输入姓名"
              />
            </el-form-item>
            <el-form-item label="身份证号">
              <el-input
                v-model="checkInForm.idCard"
                placeholder="请输入身份证号"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                @click="handleCheckIn"
                :loading="loading"
                >办理入住</el-button
              >
            </el-form-item>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- 退房结账 -->
      <el-tab-pane label="退房结账">
        <div class="tab-content">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-card shadow="never">
                <template #header>选择房间</template>
                <el-form>
                  <el-form-item>
                    <el-select
                      v-model="checkoutRoomId"
                      placeholder="选择房间"
                      style="width: 100%"
                    >
                      <el-option label="101" value="101" />
                      <el-option label="102" value="102" />
                      <el-option label="103" value="103" />
                      <el-option label="104" value="104" />
                      <el-option label="105" value="105" />
                    </el-select>
                  </el-form-item>
                  <el-button
                    type="danger"
                    @click="handleCheckout"
                    style="width: 100%"
                    >生成账单</el-button
                  >
                  <el-button
                    type="primary"
                    @click="handleExport"
                    style="width: 100%; margin-top: 10px; margin-left: 0;"
                    :disabled="!checkoutRoomId"
                    >导出账单文件</el-button
                  >
                </el-form>
              </el-card>
            </el-col>

            <el-col :span="16">
              <div v-if="acBill || lodgingBill" class="bill-preview">
                <el-divider content-position="left">账单明细</el-divider>

                <el-descriptions
                  title="住宿账单"
                  :column="2"
                  border
                  v-if="lodgingBill"
                >
                  <el-descriptions-item label="房间号">{{
                    lodgingBill.roomId
                  }}</el-descriptions-item>
                  <el-descriptions-item label="入住天数"
                    >{{ lodgingBill.days }} 天</el-descriptions-item
                  >
                  <el-descriptions-item label="入住时间">{{
                    formatDate(lodgingBill.checkInTime)
                  }}</el-descriptions-item>
                  <el-descriptions-item label="退房时间">{{
                    formatDate(lodgingBill.checkOutTime)
                  }}</el-descriptions-item>
                  <el-descriptions-item label="住宿费用">
                    <span class="price"
                      >¥{{ lodgingBill.totalLodgingFee?.toFixed(2) }}</span
                    >
                  </el-descriptions-item>
                </el-descriptions>

                <br />

                <el-descriptions
                  title="空调账单"
                  :column="2"
                  border
                  v-if="acBill"
                >
                  <el-descriptions-item label="空调总费用">
                    <span class="price"
                      >¥{{ acBill.totalAcFee?.toFixed(2) }}</span
                    >
                  </el-descriptions-item>
                </el-descriptions>

                <br />

                <el-table
                  :data="details"
                  border
                  stripe
                  height="300"
                  v-if="details.length"
                >
                  <el-table-column label="空调详单">
                    <el-table-column
                      prop="requestTime"
                      label="请求时间"
                      :formatter="requestTimeFormatter"
                      width="160"
                    />
                    <el-table-column
                      prop="startTime"
                      label="开始时间"
                      :formatter="timeFormatter"
                      width="160"
                    />
                    <el-table-column
                      prop="endTime"
                      label="结束时间"
                      :formatter="endTimeFormatter"
                      width="160"
                    />
                    <el-table-column
                      prop="duration"
                      label="时长(s)"
                      width="80"
                    />
                    <el-table-column prop="fanSpeed" label="风速" width="80" />
                    <el-table-column prop="fee" label="费用" width="100">
                      <template #default="scope"
                        >¥{{ scope.row.fee?.toFixed(2) }}</template
                      >
                    </el-table-column>
                    <el-table-column
                      prop="cumulativeFee"
                      label="累积"
                      width="100"
                    >
                      <template #default="scope"
                        >¥{{ scope.row.cumulativeFee?.toFixed(2) }}</template
                      >
                    </el-table-column>
                  </el-table-column>
                </el-table>
              </div>
              <el-empty v-else description="请选择房间并生成账单" />
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from "vue";
import api from "@/api";
import { ElMessage } from "element-plus";

const loading = ref(false);

// 入住相关
const checkInForm = reactive({
  roomId: "",
  customerName: "",
  idCard: "",
});

// 结账相关
const checkoutRoomId = ref("");
const acBill = ref(null);
const lodgingBill = ref(null);
const details = ref([]);

watch(checkoutRoomId, () => {
  acBill.value = null;
  lodgingBill.value = null;
  details.value = [];
});

const handleCheckIn = async () => {
  if (!checkInForm.roomId || !checkInForm.customerName) {
    ElMessage.warning("请填写完整信息");
    return;
  }
  loading.value = true;
  try {
    await api.post("/clerk/checkin", checkInForm);
    ElMessage.success(`房间 ${checkInForm.roomId} 入住办理成功`);
    // 重置表单
    checkInForm.roomId = "";
    checkInForm.customerName = "";
    checkInForm.idCard = "";
  } catch (e) {
    ElMessage.error("办理失败");
  } finally {
    loading.value = false;
  }
};

const handleCheckout = async () => {
  if (!checkoutRoomId.value) return;
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
  }
};

const handleExport = async () => {
  if (!checkoutRoomId.value) return;

  // 如果尚未生成账单，先尝试生成
  if (!acBill.value && !lodgingBill.value) {
    await handleCheckout();
    // 如果生成后仍然没有数据，说明失败或无账单，终止导出
    if (!acBill.value && !lodgingBill.value) return;
  }

  try {
    // 直接下载文件
    const response = await api.get("/clerk/export", {
      params: { roomId: checkoutRoomId.value },
      responseType: 'blob' // 重要：指定响应类型为 blob
    });
    
    // 创建下载链接
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
</script>

<style scoped>
.clerk-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}
.main-tabs {
  min-height: 500px;
}
.tab-content {
  padding: 20px;
}
.checkin-form {
  max-width: 500px;
  margin: 0 auto;
}
.price {
  color: #f56c6c;
  font-weight: bold;
  font-size: 16px;
}
</style>
