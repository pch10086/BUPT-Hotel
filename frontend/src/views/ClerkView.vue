<template>
  <div class="clerk-panel">
    <h2>前台结账系统</h2>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>结账操作</template>
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
            <el-button type="primary" @click="checkout">生成账单</el-button>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <div v-if="acBill" class="bill-section">
      <h3>空调账单</h3>
      <el-descriptions border>
        <el-descriptions-item label="房间号">{{
          acBill.roomId
        }}</el-descriptions-item>
        <el-descriptions-item label="入住时间">{{
          acBill.checkInTime
        }}</el-descriptions-item>
        <el-descriptions-item label="退房时间">{{
          acBill.checkOutTime
        }}</el-descriptions-item>
        <el-descriptions-item label="空调总费用"
          >{{ acBill.totalAcFee }} 元</el-descriptions-item
        >
      </el-descriptions>
    </div>

    <div v-if="lodgingBill" class="bill-section">
      <h3>住宿账单</h3>
      <el-descriptions border>
        <el-descriptions-item label="房间号">{{
          lodgingBill.roomId
        }}</el-descriptions-item>
        <el-descriptions-item label="入住天数">{{
          lodgingBill.days
        }}</el-descriptions-item>
        <el-descriptions-item label="住宿总费用"
          >{{ lodgingBill.totalLodgingFee }} 元</el-descriptions-item
        >
      </el-descriptions>
    </div>

    <div v-if="details.length > 0" class="bill-section">
      <h3>空调详单</h3>
      <el-table :data="details" style="width: 100%">
        <el-table-column prop="startTime" label="开始时间" />
        <el-table-column prop="endTime" label="结束时间" />
        <el-table-column prop="duration" label="时长(秒)" />
        <el-table-column prop="fanSpeed" label="风速" />
        <el-table-column prop="fee" label="费用" />
        <el-table-column prop="cumulativeFee" label="累积费用" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref } from "vue";
import api from "@/api";
import { ElMessage } from "element-plus";

const roomId = ref("101");
const acBill = ref(null);
const lodgingBill = ref(null);
const details = ref([]);

const checkout = async () => {
  try {
    const resAc = await api.post("/clerk/checkout/ac", null, {
      params: { roomId: roomId.value },
    });
    acBill.value = resAc.data;

    const resLodging = await api.post("/clerk/checkout/lodging", null, {
      params: { roomId: roomId.value },
    });
    lodgingBill.value = resLodging.data;

    const resDetails = await api.get("/clerk/details", {
      params: { roomId: roomId.value },
    });
    details.value = resDetails.data;

    ElMessage.success("账单生成成功");
  } catch (e) {
    ElMessage.error("结账失败");
  }
};
</script>

<style scoped>
.clerk-panel {
  padding: 20px;
}
.bill-section {
  margin-top: 20px;
}
</style>
