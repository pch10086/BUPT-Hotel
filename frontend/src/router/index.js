import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import LoginView from '../views/LoginView.vue'
import GuestView from '../views/GuestView.vue'
import ClerkView from '../views/ClerkView.vue'
import ManagerView from '../views/ManagerView.vue'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { requiresAuth: false }
    },
    {
      path: '/guest',
      name: 'guest',
      component: GuestView,
      meta: { requiresAuth: true, role: 'guest' }
    },
    {
      path: '/clerk',
      name: 'clerk',
      component: ClerkView,
      meta: { requiresAuth: true, role: 'clerk' }
    },
    {
      path: '/manager',
      name: 'manager',
      component: ManagerView,
      meta: { requiresAuth: true, role: 'manager' }
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  authStore.checkAuth()

  // 如果访问登录页且已登录，重定向到对应角色页面
  if (to.path === '/login' && authStore.isAuth()) {
    const user = authStore.getCurrentUser()
    const routes = {
      guest: '/guest',
      clerk: '/clerk',
      manager: '/manager'
    }
    next(routes[user?.role] || '/login')
    return
  }

  // 如果路由需要认证
  if (to.meta.requiresAuth) {
    if (!authStore.isAuth()) {
      // 未登录，跳转到登录页
      next('/login')
      return
    }

    // 检查角色权限
    const user = authStore.getCurrentUser()
    if (to.meta.role && user?.role !== to.meta.role) {
      // 角色不匹配，跳转到对应角色页面
      const routes = {
        guest: '/guest',
        clerk: '/clerk',
        manager: '/manager'
      }
      ElMessage.warning('您没有权限访问该页面')
      next(routes[user?.role] || '/login')
      return
    }
  }

  next()
})

export default router
