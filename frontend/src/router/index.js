import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '食赴' },
  },
  {
    path: '/merchants',
    name: 'merchants',
    component: () => import('@/views/MerchantListView.vue'),
    meta: { title: '发现商户' },
  },
  {
    path: '/merchant/:id',
    name: 'merchant',
    component: () => import('@/views/MerchantDetailView.vue'),
    meta: { title: '商户详情' },
  },
  {
    path: '/note/new',
    name: 'note-new',
    component: () => import('@/views/NoteNewView.vue'),
    meta: { title: '发布笔记', requiresAuth: true },
  },
  {
    path: '/note/:id',
    name: 'note',
    component: () => import('@/views/NoteDetailView.vue'),
    meta: { title: '笔记详情' },
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/me',
    name: 'me',
    component: () => import('@/views/ProfileView.vue'),
    meta: { title: '我的', requiresAuth: true },
  },
  {
    path: '/me/edit',
    name: 'me-edit',
    component: () => import('@/views/ProfileEditView.vue'),
    meta: { title: '编辑资料', requiresAuth: true },
  },
  {
    path: '/user/:id',
    name: 'user',
    component: () => import('@/views/UserProfileView.vue'),
    meta: { title: '个人主页' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLogin) {
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} · 食赴` : '食赴 · 本地生活社区'
})

export default router
