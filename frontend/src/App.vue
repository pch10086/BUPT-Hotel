<template>
  <div class="app-container">
    <el-menu
      mode="horizontal"
      router
      :default-active="$route.path"
      class="nav-menu"
    >
      <div class="logo">BUPT Hotel</div>
      <el-menu-item index="/guest">
        <el-icon><User /></el-icon>客户面板
      </el-menu-item>
      <el-menu-item index="/clerk">
        <el-icon><Service /></el-icon>前台面板
      </el-menu-item>
      <el-menu-item index="/manager">
        <el-icon><Monitor /></el-icon>经理面板
      </el-menu-item>
    </el-menu>
    <router-view />
  </div>
</template>

<script setup>
import { User, Service, Monitor } from "@element-plus/icons-vue";
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
}
.logo {
  font-size: 20px;
  font-weight: bold;
  color: #409eff;
  margin-right: 40px;
}
/* 覆盖 Element Plus 的一些默认样式以适应全屏 */
.el-menu--horizontal {
  border-bottom: solid 1px #e6e6e6;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}
</style>
