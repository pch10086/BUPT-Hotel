# 部署说明

## 单机测试（开发环境）

**默认配置就是单机模式，可以直接使用！**

### 快速开始

1. **启动后端**：

   ```bash
   cd backend
   mvn spring-boot:run
   ```

2. **启动前端**（不需要创建.env 文件）：

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

3. **访问**：`http://localhost:5173`

4. **测试方式**：
   - 打开第一个标签页：登录客户账号（guest/123456）
   - 打开第二个标签页：登录前台账号（clerk/123456）
   - 打开第三个标签页：登录经理账号（manager/admin123）
   - 三个标签页可以同时运行，互不干扰，但通过后端共享数据

### 默认配置说明

- **环境模式**：默认是 `server` 模式（可以登录所有角色）
- **API 地址**：默认使用 `http://localhost:8080/api`
- **无需配置**：不创建 `.env` 文件即可使用单机模式

---

## 双机部署配置（生产环境）

本项目支持在两台机器上运行：

- **服务器端**：运行后端服务 + 前端（前台和经理界面）
- **客户端**：运行前端（客户界面），连接到服务器端

## 一、服务器端配置

### 1. 后端配置

1. 修改 `backend/src/main/resources/application.properties`：

   ```properties
   server.port=8080
   # 如果客户端在不同网络，建议配置具体的客户端IP
   cors.allowed-origins=*
   ```

2. 启动后端服务：
   ```bash
   cd backend
   mvn spring-boot:run
   ```
   或使用 IDE 运行 `HotelApplication.java`

### 2. 前端配置（服务器端）

1. 复制环境配置文件：

   ```bash
   cd frontend
   cp .env.server .env
   ```

2. 修改 `.env` 文件（如果需要）：

   ```env
   VITE_APP_MODE=server
   VITE_API_BASE_URL=http://localhost:8080/api
   ```

3. 安装依赖并启动：

   ```bash
   npm install
   npm run dev:server
   ```

4. 访问地址：`http://localhost:5173`
   - 可以登录：前台账号（clerk/123456）和经理账号（manager/admin123）

## 二、客户端配置

### 1. 获取服务器 IP 地址

在服务器端机器上运行以下命令获取 IP 地址：

- Windows: `ipconfig`
- Linux/Mac: `ifconfig` 或 `ip addr`

假设服务器 IP 为：`192.168.1.100`

### 2. 前端配置（客户端）

1. 复制环境配置文件：

   ```bash
   cd frontend
   cp .env.client .env
   ```

2. **重要**：修改 `.env` 文件中的服务器 IP 地址：

   ```env
   VITE_APP_MODE=client
   VITE_API_BASE_URL=http://192.168.1.100:8080/api
   ```

   > 将 `192.168.1.100` 替换为实际的服务器 IP 地址

3. 安装依赖并启动：

   ```bash
   npm install
   npm run dev:client
   ```

4. 访问地址：`http://localhost:5173`
   - 只能登录：客户账号（guest/123456）

## 三、验证部署

### 测试步骤

1. **服务器端**：

   - 打开浏览器访问 `http://localhost:5173`
   - 登录前台账号（clerk/123456）
   - 办理一个房间的入住

2. **客户端**：

   - 打开浏览器访问 `http://localhost:5173`
   - 登录客户账号（guest/123456）
   - 应该能看到已入住的房间控制面板

3. **服务器端（经理界面）**：
   - 打开新标签页，访问 `http://localhost:5173`
   - 登录经理账号（manager/admin123）
   - 应该能看到所有房间状态和队列信息

### 预期结果

- 前台办理入住后，客户界面立即显示房间
- 客户开启空调后，经理界面立即看到服务队列变化
- 所有操作通过后端 API 实时同步

## 四、生产环境部署

### 构建生产版本

**服务器端**：

```bash
cd frontend
npm run build:server
# 构建产物在 dist 目录
```

**客户端**：

```bash
cd frontend
npm run build:client
# 构建产物在 dist 目录
```

### 使用 Nginx 部署前端

1. 将构建产物部署到 Nginx
2. 配置 Nginx 反向代理后端 API（如果需要）

### 防火墙配置

确保服务器端的 8080 端口对客户端开放：

- Windows 防火墙：允许 8080 端口入站
- Linux 防火墙：`sudo ufw allow 8080`

## 五、常见问题

### 1. 客户端无法连接服务器

- 检查服务器 IP 地址是否正确
- 检查服务器防火墙是否开放 8080 端口
- 检查网络是否连通：`ping 192.168.1.100`

### 2. CORS 跨域错误

- 检查后端 `application.properties` 中的 `cors.allowed-origins` 配置
- 确保客户端地址在允许列表中

### 3. 环境变量不生效

- 确保 `.env` 文件在 `frontend` 目录下
- 重启开发服务器
- 检查环境变量名称是否正确（必须以 `VITE_` 开头）

## 六、账号信息

| 角色 | 账号    | 密码     | 可访问界面 |
| ---- | ------- | -------- | ---------- |
| 客户 | guest   | 123456   | 客户界面   |
| 前台 | clerk   | 123456   | 前台界面   |
| 经理 | manager | admin123 | 经理界面   |
