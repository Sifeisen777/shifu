import http from './http'

/** 客服入口配置：是否开放、模型是否就绪 */
export const fetchAssistantConfig = () => http.get('/assistant/config')

/**
 * 流式提问。
 *
 * 走 fetch + ReadableStream 而非 axios：axios 在浏览器端拿不到增量响应，
 * 只能等整段返回，失去逐字展示的效果。
 *
 * @param {object} options
 * @param {string} options.message   问题
 * @param {string} options.sessionId 会话标识
 * @param {string} [options.token]   登录令牌，游客为空
 * @param {(text: string) => void} options.onChunk 增量回调
 * @param {AbortSignal} [options.signal] 中断信号
 */
export async function streamAssistantChat({ message, sessionId, token, onChunk, signal }) {
  const response = await fetch('/api/assistant/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { authorization: token } : {}),
    },
    body: JSON.stringify({ message, sessionId }),
    signal,
  })
  if (!response.ok) {
    throw new Error(response.status === 429 ? '提问过于频繁，请稍后再试' : '客服暂时不可用，请稍后重试')
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // SSE 以空行分隔事件；保留最后一段不完整的帧
    const frames = buffer.split('\n\n')
    buffer = frames.pop() ?? ''
    for (const frame of frames) {
      const text = frame
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        // 同一条消息若含换行，会被拆成多行 data，按 SSE 规范用 \n 还原
        .map((line) => line.slice(5).replace(/^ /, ''))
        .join('\n')
      if (text) onChunk(text)
    }
  }
}
