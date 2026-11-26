import { reactive } from 'vue'

/**
 * 轻量 Toast：避免为了一个提示引入整个组件库
 */
export const toasts = reactive([])
let seq = 0

export function toast(message, type = 'info', duration = 2200) {
  if (!message) return
  const id = ++seq
  toasts.push({ id, message: String(message), type })
  setTimeout(() => {
    const index = toasts.findIndex((t) => t.id === id)
    if (index > -1) toasts.splice(index, 1)
  }, duration)
}

toast.success = (msg, d) => toast(msg, 'success', d)
toast.error = (msg, d) => toast(msg, 'error', d)

export default toast
