# 环境配置说明

## 单机测试（默认，无需配置）

**不需要创建 `.env` 文件！** 直接运行即可：

```bash
npm run dev
```

默认配置：

- 环境模式：`server`（可以登录所有角色）
- API 地址：`http://localhost:8080/api`

可以在同一台电脑上打开多个标签页，登录不同角色进行测试。

---

## 双机部署配置

### 服务器端配置

在 `frontend` 目录下创建 `.env` 文件，内容如下：

```env
VITE_APP_MODE=server
VITE_API_BASE_URL=http://localhost:8080/api
```

然后运行：

```bash
npm run dev:server
```

### 客户端配置

在 `frontend` 目录下创建 `.env` 文件，内容如下：

```env
VITE_APP_MODE=client
VITE_API_BASE_URL=http://192.168.1.100:8080/api
```

**重要**：将 `192.168.1.100` 替换为实际的服务器 IP 地址。

然后运行：

```bash
npm run dev:client
```
