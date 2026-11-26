import axios from 'axios'

/**
 * 统一 HTTP 客户端
 * - 自动携带 authorization 头（后端使用 UUID token，存于 localStorage）
 * - 自动拆解后端统一的 Result 包装：{ success, errorMsg, data, total }
 * - 401 统一交给外部注册的处理器（避免 http <-> router 循环依赖）
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 8000,
})

let unauthorizedHandler = null
export function onUnauthorized(fn) {
  unauthorizedHandler = fn
}

let tokenGetter = () => ''
export function setTokenGetter(fn) {
  tokenGetter = fn
}

http.interceptors.request.use((config) => {
  const token = tokenGetter()
  if (token) config.headers.authorization = token
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非 Result 结构（例如纯文本）直接返回
    if (!body || typeof body !== 'object' || !('success' in body)) {
      return body
    }
    if (!body.success) {
      return Promise.reject(new Error(body.errorMsg || '操作失败'))
    }
    // 保留 total 供分页使用，同时把 data 提到顶层方便调用方
    return { data: body.data, total: body.total }
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      unauthorizedHandler?.()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }
    if (status === 404) {
      return Promise.reject(new Error('接口不存在'))
    }
    if (status >= 500) {
      return Promise.reject(new Error('服务开小差了，请稍后重试'))
    }
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时，请检查网络'))
    }
    return Promise.reject(new Error(error.response?.data?.errorMsg || '网络异常'))
  }
)

export default http
