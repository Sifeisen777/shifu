import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './store/auth'
import { onUnauthorized, setTokenGetter } from './api/http'
import './styles/tokens.css'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const auth = useAuthStore(pinia)

// HTTP 层通过回调取 token，避免模块间循环依赖
setTokenGetter(() => auth.token)

// 401 统一处理：清理登录态并带 redirect 回登录页
onUnauthorized(() => {
  auth.clear()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
