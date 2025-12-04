import { ref } from 'vue'

// 简单的认证状态管理
// 使用 sessionStorage 确保每个浏览器标签页/窗口有独立的登录状态
// 这样可以在不同标签页同时登录不同角色进行测试
const isAuthenticated = ref(false)
const currentUser = ref(null)

export function useAuthStore() {
  const login = (userInfo) => {
    isAuthenticated.value = true
    currentUser.value = userInfo
    // 保存到 sessionStorage（每个标签页独立）
    sessionStorage.setItem('auth', JSON.stringify({
      isAuthenticated: true,
      user: userInfo
    }))
  }

  const logout = () => {
    isAuthenticated.value = false
    currentUser.value = null
    sessionStorage.removeItem('auth')
  }

  const checkAuth = () => {
    // 从 sessionStorage 恢复认证状态（每个标签页独立）
    const authData = sessionStorage.getItem('auth')
    if (authData) {
      try {
        const data = JSON.parse(authData)
        isAuthenticated.value = data.isAuthenticated
        currentUser.value = data.user
      } catch (e) {
        console.error('Failed to parse auth data', e)
      }
    }
    return isAuthenticated.value
  }

  const getCurrentUser = () => {
    return currentUser.value
  }

  const isAuth = () => {
    return isAuthenticated.value
  }

  return {
    login,
    logout,
    checkAuth,
    getCurrentUser,
    isAuth
  }
}

