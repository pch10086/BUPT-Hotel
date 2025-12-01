import { createRouter, createWebHistory } from 'vue-router'
import GuestView from '../views/GuestView.vue'
import ClerkView from '../views/ClerkView.vue'
import ManagerView from '../views/ManagerView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/guest'
    },
    {
      path: '/guest',
      name: 'guest',
      component: GuestView
    },
    {
      path: '/clerk',
      name: 'clerk',
      component: ClerkView
    },
    {
      path: '/manager',
      name: 'manager',
      component: ManagerView
    }
  ]
})

export default router
