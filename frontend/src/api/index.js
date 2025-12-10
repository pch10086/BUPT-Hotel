import axios from 'axios';

// 从环境变量获取后端API地址，如果没有则使用默认值
// 开发环境：使用 localhost
// 生产环境：使用环境变量 VITE_API_BASE_URL
// 客户端模式：需要配置服务器的IP地址
const getBaseURL = () => {
    // 优先使用环境变量
    if (import.meta.env.VITE_API_BASE_URL) {
        return import.meta.env.VITE_API_BASE_URL;
    }
    
    // 智能判断：如果当前是通过IP访问的（非localhost），则假设后端也在同一个IP上
    // 这样在局域网测试时，客户端浏览器会自动连接到服务器IP的8080端口
    const hostname = window.location.hostname;
    if (hostname !== 'localhost' && hostname !== '127.0.0.1') {
        return `http://${hostname}:8080/api`;
    }

    // 默认使用 localhost（本机开发）
    return 'http://localhost:8080/api';
};

const api = axios.create({
    baseURL: getBaseURL(),
    timeout: 10000, // 增加超时时间，适应网络延迟
    headers: {
        'Content-Type': 'application/json'
    }
});

// 请求拦截器
api.interceptors.request.use(
    config => {
        return config;
    },
    error => {
        return Promise.reject(error);
    }
);

// 响应拦截器
api.interceptors.response.use(
    response => {
        return response;
    },
    error => {
        if (error.response) {
            // 服务器返回了错误状态码
            console.error('API Error:', error.response.status, error.response.data);
        } else if (error.request) {
            // 请求已发出但没有收到响应
            console.error('Network Error:', error.message);
        }
        return Promise.reject(error);
    }
);

export default api;
