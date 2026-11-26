import { defineStore } from 'pinia'
import { fetchMe } from '@/api'

const TOKEN_KEY = 'sf_token'
const USER_KEY = 'sf_user'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: readUser(),
  }),
  getters: {
    isLogin: (state) => Boolean(state.token),
    userId: (state) => state.user?.id ?? null,
  },
  actions: {
    setToken(token) {
      this.token = token || ''
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    },
    setUser(user) {
      this.user = user || null
      if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
      else localStorage.removeItem(USER_KEY)
    },
    clear() {
      this.setToken('')
      this.setUser(null)
    },
    /** 拉取当前登录用户，失败时清空登录态 */
    async refreshUser() {
      if (!this.token) return null
      try {
        const { data } = await fetchMe()
        this.setUser(data)
        return data
      } catch {
        this.clear()
        return null
      }
    },
  },
})
