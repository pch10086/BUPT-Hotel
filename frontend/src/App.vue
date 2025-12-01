<template>
  <div class="app-container">
    <el-menu mode="horizontal" router>
      <el-menu-item index="/guest">客户面板</el-menu-item>
      <el-menu-item index="/clerk">前台面板</el-menu-item>
      <el-menu-item index="/manager">经理面板</el-menu-item>
    </el-menu>
    <router-view />
  </div>
</template>

<style>
body {
  margin: 0;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB",
    "Microsoft YaHei", "微软雅黑", Arial, sans-serif;
}
.app-container {
  max-width: 1200px;
  margin: 0 auto;
}
</style>
