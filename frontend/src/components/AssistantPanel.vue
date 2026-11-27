<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchAssistantConfig, streamAssistantChat } from '@/api/assistant'
import { useAuthStore } from '@/store/auth'
import BaseIcon from '@/components/BaseIcon.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const SID_KEY = 'sf_assistant_sid'
const QUICK_QUESTIONS = ['附近有什么好吃的', '这家店有什么优惠券', '我的券还能用吗']

const open = ref(false)
const checked = ref(false)
const enabled = ref(true)
const modelReady = ref(true)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scroller = ref(null)
let controller = null

const hidden = computed(() => route.name === 'login' || !enabled.value)
const showEmpty = computed(() => messages.value.length === 0)

function sessionId() {
  let sid = sessionStorage.getItem(SID_KEY)
  if (sid) return sid
  sid = 'w' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36)
  sessionStorage.setItem(SID_KEY, sid)
  return sid
}

async function ensureConfig() {
  if (checked.value) return
  checked.value = true
  try {
    const { data } = await fetchAssistantConfig()
    enabled.value = Boolean(data?.enabled)
    modelReady.value = Boolean(data?.modelReady)
  } catch {
    // 配置读取失败时保留入口，交给问答接口兜底提示
  }
}

function toggle() {
  if (open.value) {
    close()
    return
  }
  ensureConfig()
  open.value = true
  scrollToBottom()
}

function close() {
  open.value = false
}

function scrollToBottom() {
  nextTick(() => {
    const el = scroller.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function send(text) {
  const question = (text ?? draft.value).trim()
  if (!question || sending.value) return
  draft.value = ''
  sending.value = true
  messages.value.push({ role: 'user', text: question })
  const reply = { role: 'assistant', text: '', streaming: true }
  messages.value.push(reply)
  scrollToBottom()

  controller = new AbortController()
  try {
    await streamAssistantChat({
      message: question,
      sessionId: sessionId(),
      token: auth.token,
      signal: controller.signal,
      onChunk: (chunk) => {
        reply.text += chunk
        scrollToBottom()
      },
    })
    if (!reply.text) reply.text = '暂时没有生成回答，请换个问法试试。'
  } catch (error) {
    if (error.name === 'AbortError') {
      reply.text = reply.text || '已取消本次提问。'
    } else {
      reply.text = reply.text || error.message || '客服暂时不可用，请稍后重试。'
    }
  } finally {
    reply.streaming = false
    sending.value = false
    controller = null
    scrollToBottom()
  }
}

function restart() {
  controller?.abort()
  sessionStorage.removeItem(SID_KEY)
  messages.value = []
  draft.value = ''
}

function onEnter(event) {
  if (event.shiftKey) return
  event.preventDefault()
  send()
}

function openMerchant(id) {
  close()
  router.push(`/merchant/${id}`)
}

/** 把 **加粗** 标记拆成片段，避免把星号直接显示出来 */
function segments(text) {
  return String(text)
    .split(/\*\*(.+?)\*\*/g)
    .map((piece, index) => ({ text: piece, bold: index % 2 === 1 }))
    .filter((item) => item.text !== '')
}

/** 从回答里识别「商户名（id: 12）」形式的引用，渲染为可跳转卡片 */
function references(text) {
  const result = []
  const pattern = /[（(]\s*id\s*[:：]\s*(\d+)\s*[）)]/i
  for (const line of String(text).split('\n')) {
    const match = pattern.exec(line)
    if (!match) continue
    // id 标记之前整段作为名称，去掉列表符号，避免把分店括号截断
    let name = line.slice(0, match.index).replace(/^[\s\-*•·]+/, '').trim()
    if (!name) continue
    if (name.length > 26) name = `${name.slice(0, 26)}…`
    if (!result.some((item) => item.id === match[1])) {
      result.push({ id: match[1], name })
    }
  }
  return result
}

watch(open, (value) => {
  if (value) scrollToBottom()
})

onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div v-if="!hidden" class="assistant">
    <transition name="sheet">
      <div v-if="open" class="mask" @click.self="close">
        <section class="panel" role="dialog" aria-label="到店助手">
          <header class="panel__head">
            <div class="panel__title">
              <span class="panel__dot" />
              <div>
                <h2>到店助手</h2>
                <p>{{ modelReady ? '基于商户与权益数据作答' : '简化模式 · 常见问题应答' }}</p>
              </div>
            </div>
            <div class="panel__actions">
              <button class="ghost" type="button" @click="restart">新对话</button>
              <button class="icon-btn" type="button" aria-label="关闭" @click="close">
                <BaseIcon name="close" :size="18" />
              </button>
            </div>
          </header>

          <div ref="scroller" class="panel__body">
            <div v-if="showEmpty" class="intro">
              <p class="intro__lead">找店、看优惠、查已购券，可以直接问我。</p>
              <div class="chips">
                <button
                  v-for="q in QUICK_QUESTIONS"
                  :key="q"
                  class="chip"
                  type="button"
                  @click="send(q)"
                >
                  {{ q }}
                </button>
              </div>
            </div>

            <div
              v-for="(msg, index) in messages"
              :key="index"
              class="msg"
              :class="`msg--${msg.role}`"
            >
              <div class="bubble">
                <template v-for="(seg, i) in segments(msg.text)" :key="i">
                  <strong v-if="seg.bold">{{ seg.text }}</strong>
                  <span v-else>{{ seg.text }}</span>
                </template>
                <span v-if="msg.streaming" class="caret" />
              </div>

              <div v-if="msg.role === 'assistant' && !msg.streaming && references(msg.text).length" class="refs">
                <button
                  v-for="ref in references(msg.text).slice(0, 6)"
                  :key="ref.id"
                  class="ref"
                  type="button"
                  @click="openMerchant(ref.id)"
                >
                  <BaseIcon name="location" :size="13" />
                  {{ ref.name }}
                </button>
              </div>
            </div>
          </div>

          <footer class="panel__foot">
            <div class="composer">
              <textarea
                v-model="draft"
                rows="1"
                maxlength="500"
                placeholder="问问附近的店、优惠或订单"
                @keydown.enter="onEnter"
              />
              <button
                class="send"
                type="button"
                :disabled="sending || !draft.trim()"
                aria-label="发送"
                @click="send()"
              >
                <BaseIcon name="send" :size="18" />
              </button>
            </div>
            <p class="tip">回答由 AI 生成，仅供参考；实际以门店页面信息为准。</p>
          </footer>
        </section>
      </div>
    </transition>

    <button v-if="!open" class="fab" type="button" aria-label="打开到店助手" @click="toggle">
      <BaseIcon name="chat" :size="22" />
      <span>助手</span>
    </button>
  </div>
</template>

<style scoped>
.assistant {
  position: relative;
  z-index: 900;
}
.fab {
  position: fixed;
  right: 16px;
  bottom: calc(var(--tabbar-h, 56px) + 42px);
  z-index: 900;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(150deg, var(--brand) 0%, var(--brand-strong) 100%);
  color: var(--ink-inverse);
  box-shadow: 0 10px 24px rgba(171, 56, 26, 0.28);
  cursor: pointer;
  transition: transform 0.18s var(--ease);
}
.fab span {
  font-size: 9.5px;
  letter-spacing: 0.06em;
  opacity: 0.92;
}
.fab:active {
  transform: scale(0.94);
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 950;
  display: flex;
  justify-content: flex-end;
  background: rgba(23, 19, 16, 0.34);
  backdrop-filter: blur(2px);
}
.panel {
  display: flex;
  flex-direction: column;
  width: min(100vw, 420px);
  height: 100%;
  background: var(--surface-2);
  box-shadow: -12px 0 32px rgba(23, 19, 16, 0.16);
}
.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 14px 14px 18px;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
}
.panel__title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.panel__dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 4px rgba(47, 125, 79, 0.12);
}
.panel__title h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--ink);
}
.panel__title p {
  margin: 2px 0 0;
  font-size: 11px;
  color: var(--ink-3);
}
.panel__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ghost {
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--ink-2);
  font-size: 11.5px;
  padding: 5px 10px;
  border-radius: var(--r-pill);
  cursor: pointer;
}
.icon-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
}
.icon-btn:active,
.ghost:active {
  background: var(--surface-2);
}

.panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 14px 8px;
  -webkit-overflow-scrolling: touch;
}
.intro {
  padding: 6px 4px 0;
}
.intro__lead {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-2);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  padding: 8px 12px;
  border: 1px solid var(--brand-line);
  border-radius: var(--r-pill);
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-size: 12.5px;
  cursor: pointer;
}
.chip:active {
  background: var(--brand-line);
}

.msg {
  display: flex;
  flex-direction: column;
  margin-bottom: 14px;
}
.msg--user {
  align-items: flex-end;
}
.msg--assistant {
  align-items: flex-start;
}
.bubble {
  max-width: 84%;
  padding: 10px 13px;
  border-radius: var(--r-md);
  font-size: 13.5px;
  line-height: 1.72;
  word-break: break-word;
  white-space: pre-wrap;
}
.msg--user .bubble {
  background: var(--brand);
  color: var(--ink-inverse);
  border-bottom-right-radius: 6px;
}
.msg--assistant .bubble {
  background: var(--surface);
  color: var(--ink);
  border: 1px solid var(--line);
  border-bottom-left-radius: 6px;
}
.bubble strong {
  font-weight: 700;
}
.caret {
  display: inline-block;
  width: 6px;
  height: 13px;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--brand);
  border-radius: 1px;
  animation: blink 1s steps(2, start) infinite;
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}

.refs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}
.ref {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--surface);
  color: var(--ink-2);
  font-size: 12px;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ref:active {
  background: var(--brand-soft);
  border-color: var(--brand-line);
  color: var(--brand-strong);
}

.panel__foot {
  padding: 10px 14px calc(12px + var(--safe-b));
  background: var(--surface);
  border-top: 1px solid var(--line);
}
.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 8px 8px 14px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-lg);
  background: var(--surface-2);
}
.composer textarea {
  flex: 1;
  min-height: 22px;
  max-height: 96px;
  border: none;
  background: transparent;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--ink);
  resize: none;
  outline: none;
}
.send {
  display: grid;
  place-items: center;
  flex: none;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: var(--brand);
  color: var(--ink-inverse);
  cursor: pointer;
  transition: opacity 0.16s var(--ease);
}
.send:disabled {
  opacity: 0.35;
  cursor: default;
}
.tip {
  margin: 8px 2px 0;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--ink-3);
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.2s var(--ease);
}
.sheet-enter-active .panel,
.sheet-leave-active .panel {
  transition: transform 0.26s var(--ease);
}
.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}
.sheet-enter-from .panel,
.sheet-leave-to .panel {
  transform: translateX(100%);
}
</style>
