# BUPT Hotel - 快捷廉价酒店中央空调自助计费系统

这是一个基于 **Spring Boot + Vue.js + MySQL + MQTT** 的酒店中央空调自助计费系统原型，用于软件工程课程作业的“软件原型实现”环节。

## 📚 项目简介

该系统模拟了一个拥有中央空调系统的酒店环境。系统包含三个主要角色视图：

1.  **客户 (Guest)**: 在房间内通过控制面板（Web 模拟）控制空调（开关、模式、温度、风速）。
2.  **前台 (Clerk)**: 处理退房，生成空调账单和住宿账单，查看详单。
3.  **经理 (Manager)**: 实时监控各房间空调状态、服务队列与等待队列，查看统计信息。

系统核心实现了**多线程并发调度**（优先级 + 时间片轮转）和**时间加速模拟**，以满足课程测试用例的需求。

## 🛠 技术栈

- **后端**: Java 21, Spring Boot 3.2.0, Spring Data JPA, Spring Integration MQTT
- **前端**: Vue.js 3, Vite, Element Plus, Axios
- **数据库**: MySQL 8.0+
- **消息中间件**: MQTT (默认使用公共 Broker `broker.emqx.io`，可配置本地)

## 📂 目录结构

```
BUPT-Hotel/
├── backend/            # Spring Boot 后端项目
│   ├── src/main/java/com/bupt/hotel/
│   │   ├── config/     # 配置类
│   │   ├── controller/ # REST API 控制器
│   │   ├── entity/     # 数据库实体与枚举
│   │   ├── repository/ # JPA 数据访问层
│   │   └── service/    # 核心业务逻辑 (调度、计费、MQTT)
│   └── src/main/resources/
│       └── application.properties # 配置文件
├── frontend/           # Vue.js 前端项目
│   ├── src/
│   │   ├── api/        # Axios 封装
│   │   ├── components/ # 公共组件
│   │   └── views/      # 页面视图 (Guest, Clerk, Manager)
└── database/           # 数据库脚本
    └── schema.sql      # 初始化 SQL
```

## 🚀 快速开始

### 1. 环境准备

- JDK 17 或更高版本
- Node.js 16.0 或更高版本
- MySQL 8.0 数据库
- Maven 3.6+

### 2. 数据库设置

1.  登录 MySQL 数据库。
2.  执行 `database/schema.sql` 脚本。
    - 该脚本会创建 `bupt_hotel` 数据库。
    - 创建 `room`, `billing_record`, `billing_detail`, `lodging_bill` 表。
    - 插入 5 个测试房间的初始数据（101-105 号房）。

### 3. 后端启动

1.  进入 `backend` 目录。
2.  打开 `src/main/resources/application.properties`，修改数据库连接信息：
    ```properties
    spring.datasource.username=root
    spring.datasource.password=你的密码
    ```
3.  使用 Maven 运行项目：
    ```bash
    mvn spring-boot:run
    ```
    或者在 IDE (IntelliJ IDEA / Eclipse) 中运行 `HotelApplication.java`。
4.  后端默认运行在 `http://localhost:8080`。

### 4. 前端启动

1.  进入 `frontend` 目录。
2.  安装依赖：
    ```bash
    npm install
    ```
3.  启动开发服务器：
    ```bash
    npm run dev
    ```
4.  浏览器访问控制台输出的地址（通常是 `http://localhost:5173`）。

## 📖 使用说明

### 角色入口

启动前端后，页面顶部导航栏可切换不同角色视图：

1.  **客户面板 (/guest)**

    - 选择房间号（101-105）。
    - 点击“开机”请求送风。
    - 调节模式（制冷/制热）、目标温度、风速。
    - 观察实时温度变化和费用更新。
    - **注意**: 频繁点击会有防抖处理；调节风速会触发重新调度。

2.  **前台面板 (/clerk)**

    - 选择房间号。
    - 点击“生成账单”查看空调账单、住宿账单和详细的空调使用详单。

3.  **经理面板 (/manager)**
    - 查看所有房间的实时状态列表（温度、费用、状态）。
    - 查看当前的**服务队列**（正在送风的房间）和**等待队列**（等待调度的房间）。
    - 观察调度算法（优先级抢占或时间片轮转）的效果。

## ⚙️ 关键配置 (application.properties)

- **调度参数**:
  - `hotel.ac.max-service-units=3`: 最多同时服务 3 个房间。
  - `hotel.ac.time-slice-seconds=20`: 时间片长度（真实时间秒数）。
- **时间模拟**:
  - `hotel.ac.time-scale-ms=10000`: 逻辑时间刻度。
  - 默认配置下：**10 秒真实时间 = 1 分钟逻辑时间**。
  - 后端 `SchedulerService` 中的 `@Scheduled(fixedRate = 1000)` 任务每秒执行一次，每次推进 0.1 分钟逻辑时间（即 6 秒逻辑时间），以保证平滑过渡。

## 📝 注意事项

1.  **MQTT**: 项目默认连接公共 MQTT Broker (`broker.emqx.io`) 用于演示。如果网络不稳定，建议在本地部署 EMQX 或 Mosquitto，并修改 `application.properties` 中的 `mqtt.broker.url`。
2.  **并发调度**: 当开启房间数 > 3 时，会触发调度逻辑。高风速优先级最高；同风速下，服务时间最长的会被抢占（时间片轮转）。
3.  **回温模拟**: 房间达到目标温度后会自动“挂起”（IDLE 状态）并开始回温，回温超过 1℃ 后会自动重新请求送风。
