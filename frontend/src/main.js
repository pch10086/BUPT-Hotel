import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import timeService from './api/timeService'

const app = createApp(App)

app.use(router)
app.use(ElementPlus)

// 初始化时间服务
timeService.initialize().then(() => {
  app.mount('#app')
}).catch(e => {
  console.error("Failed to initialize time service", e)
  app.mount('#app')
})
