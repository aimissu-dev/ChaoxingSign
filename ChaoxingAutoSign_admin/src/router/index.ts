import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/components/layout/AppLayout.vue'),
      meta: { requiresAuth: true },
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '仪表盘' },
        },
        {
          path: 'courses',
          name: 'Courses',
          component: () => import('@/views/CourseListView.vue'),
          meta: { title: '课程管理' },
        },
        {
          path: 'schedule',
          name: 'Schedule',
          component: () => import('@/views/ScheduleView.vue'),
          meta: { title: '课程表' },
        },
        {
          path: 'sign-config',
          name: 'SignConfig',
          component: () => import('@/views/SignConfigView.vue'),
          meta: { title: '签到配置' },
        },
      ],
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
