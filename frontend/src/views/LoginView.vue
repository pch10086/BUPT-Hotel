<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>BUPT Hotel</h1>
        <p class="subtitle">酒店中央空调管理系统</p>
      </div>
      
      <el-form
        :model="loginForm"
        :rules="rules"
        ref="loginFormRef"
        class="login-form"
        label-position="top"
      >
        <el-form-item label="选择角色" prop="role">
          <el-select
            v-model="loginForm.role"
            placeholder="请选择角色"
            style="width: 100%"
            size="large"
            @change="handleRoleChange"
          >
            <el-option 
              v-if="allowedRoles.includes('guest')"
              label="客户" 
              value="guest"
            >
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon><User /></el-icon>
                <span>客户</span>
              </div>
            </el-option>
            <el-option 
              v-if="allowedRoles.includes('clerk')"
              label="前台" 
              value="clerk"
            >
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon><Service /></el-icon>
                <span>前台</span>
              </div>
            </el-option>
            <el-option 
              v-if="allowedRoles.includes('manager')"
              label="经理" 
              value="manager"
            >
              <div style="display: flex; align-items: center; gap: 8px">
                <el-icon><Monitor /></el-icon>
                <span>经理</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="账号" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入账号"
            size="large"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-hint">
        <el-alert
          :title="hintTitle"
          :description="hintDescription"
          type="info"
          :closable="false"
          show-icon
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { User, Service, Monitor, Lock } from "@element-plus/icons-vue";
import { useAuthStore } from "@/stores/auth";
import { getAllowedRoles } from "@/config/env";

const router = useRouter();
const loginFormRef = ref(null);
const loading = ref(false);
const allowedRoles = ref(getAllowedRoles());

const loginForm = reactive({
  role: "",
  username: "",
  password: "",
});

// 预设账号密码
const accounts = {
  guest: {
    username: "guest",
    password: "123456"
  },
  clerk: {
    username: "clerk",
    password: "123456"
  },
  manager: {
    username: "manager",
    password: "admin123"
  },
};

const rules = {
  role: [{ required: true, message: "请选择角色", trigger: "change" }],
  username: [{ required: true, message: "请输入账号", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};

const hintTitle = computed(() => {
  if (!loginForm.role) return "请选择角色";
  const roleNames = {
    guest: "客户",
    clerk: "前台",
    manager: "经理",
  };
  return `${roleNames[loginForm.role]}角色提示`;
});

const hintDescription = computed(() => {
  if (!loginForm.role) return "";
  const hints = {
    guest: "客户账号：guest，密码：123456",
    clerk: "前台账号：clerk，密码：123456",
    manager: "经理账号：manager，密码：admin123",
  };
  return hints[loginForm.role];
});

const handleRoleChange = () => {
  // 切换角色时清空账号密码
  loginForm.username = "";
  loginForm.password = "";
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  await loginFormRef.value.validate((valid) => {
    if (!valid) return;

    loading.value = true;

    // 模拟登录验证
    setTimeout(() => {
      const account = accounts[loginForm.role];

      if (
        account &&
        account.username === loginForm.username &&
        account.password === loginForm.password
      ) {
        // 登录成功，保存认证信息
        const authStore = useAuthStore();
        authStore.login({
          role: loginForm.role,
          username: loginForm.username,
        });

        ElMessage.success("登录成功");

        // 根据角色跳转到对应页面
        const routes = {
          guest: "/guest",
          clerk: "/clerk",
          manager: "/manager",
        };

        router.push(routes[loginForm.role]);
      } else {
        ElMessage.error("账号或密码错误");
        loading.value = false;
      }
    }, 500);
  });
};
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-box {
  width: 100%;
  max-width: 450px;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.login-header h1 {
  margin: 0 0 10px 0;
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.login-form {
  margin-bottom: 20px;
}

.login-hint {
  margin-top: 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #606266;
  font-size: 14px;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
}
</style>

