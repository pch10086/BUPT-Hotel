// 环境配置
// 通过环境变量 VITE_APP_MODE 来区分服务器端和客户端
// server: 服务器端模式（可以登录前台和经理）
// client: 客户端模式（只能登录客户）

const APP_MODE = import.meta.env.VITE_APP_MODE || 'server'; // 默认为服务器端模式

export const isServerMode = () => APP_MODE === 'server';
export const isClientMode = () => APP_MODE === 'client';

// 获取允许的角色列表
export const getAllowedRoles = () => {
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

