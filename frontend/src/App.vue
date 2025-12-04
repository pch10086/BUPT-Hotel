<template>
  <div class="app-container">
    <!-- 只在非登录页面显示导航栏 -->
    <el-menu
      v-if="showNav"
      mode="horizontal"
      class="nav-menu"
    >
      <div class="logo">BUPT Hotel</div>
      <div class="nav-right">
        <div class="user-info">
          <el-icon><User /></el-icon>
          <span>{{ userInfo?.username }}</span>
          <el-tag size="small" :type="getRoleType(userInfo?.role)">
            {{ getRoleName(userInfo?.role) }}
          </el-tag>
        </div>
        <el-button
          type="danger"
          size="small"
          @click="handleLogout"
          :icon="SwitchButton"
        >
          退出登录
        </el-button>
      </div>
    </el-menu>
    <router-view />
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { User, SwitchButton } from "@element-plus/icons-vue";
import { useAuthStore } from "./stores/auth";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const showNav = computed(() => {
  return route.path !== "/login";
});

const userInfo = computed(() => {
  return authStore.getCurrentUser();
});

const getRoleName = (role) => {
  const names = {
    guest: "客户",
    clerk: "前台",
    manager: "经理",
  };
  return names[role] || "未知";
};

const getRoleType = (role) => {
  const types = {
    guest: "info",
    clerk: "warning",
    manager: "success",
  };
  return types[role] || "";
};

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm("确定要退出登录吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    authStore.logout();
    ElMessage.success("已退出登录");
    router.push("/login");
  } catch {
    // 用户取消
  }
};
</script>

<style>
body {
  margin: 0;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB",
    "Microsoft YaHei", "微软雅黑", Arial, sans-serif;
  background-color: #f5f7fa;
}
.app-container {
  min-height: 100vh;
}
.nav-menu {
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.logo {
  font-size: 20px;
  font-weight: bold;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 14px;
}
/* 覆盖 Element Plus 的一些默认样式以适应全屏 */
.el-menu--horizontal {
  border-bottom: solid 1px #e6e6e6;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}
</style>
