// 环境配置
// 通过环境变量 VITE_APP_MODE 来区分服务器端和客户端
// server: 服务器端模式（可以登录前台和经理）
// client: 客户端模式（只能登录客户）

const APP_MODE = import.meta.env.VITE_APP_MODE || 'server'; // 默认为服务器端模式

// 判断是否通过 localhost 或 127.0.0.1 访问
const isLocalAccess = () => {
  const hostname = window.location.hostname;
  return hostname === 'localhost' || hostname === '127.0.0.1';
};

export const isServerMode = () => APP_MODE === 'server' && isLocalAccess();
export const isClientMode = () => APP_MODE === 'client' || !isLocalAccess();

// 获取允许的角色列表
export const getAllowedRoles = () => {
  // 如果是显式的客户端模式，或者是非本地访问（即其他机器通过IP访问），则只允许客户登录
  if (isClientMode()) {
    return ['guest']; // 客户端只能登录客户
  }
  return ['guest', 'clerk', 'manager']; // 服务器端可以登录所有角色
};

// 获取允许访问的路由
export const getAllowedRoutes = () => {
  if (isClientMode()) {
    return ['/guest']; // 客户端只能访问客户界面
  }
  return ['/guest', '/clerk', '/manager']; // 服务器端可以访问所有界面
};

