<script setup>
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseIcon from '@/components/BaseIcon.vue'
import { login, sendCode } from '@/api'
import { useAuthStore } from '@/store/auth'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const mode = ref('code')
const phone = ref('')
const code = ref('')
const password = ref('')
const agreed = ref(true)
const countdown = ref(0)
const sending = ref(false)
const submitting = ref(false)
let timer = null

function validPhone(value) {
  return /^1[3-9]\d{9}$/.test(value)
}

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function onSendCode() {
  if (!validPhone(phone.value)) return toast.error('请输入正确的手机号')
  if (countdown.value > 0 || sending.value) return
  sending.value = true
  try {
    await sendCode(phone.value)
    toast.success('验证码已发送')
    startCountdown()
  } catch (e) {
    toast.error(e.message)
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  if (submitting.value) return
  if (!validPhone(phone.value)) return toast.error('请输入正确的手机号')
  if (!agreed.value) return toast.error('请先阅读并同意用户协议')

  const payload = { phone: phone.value }
  if (mode.value === 'code') {
    if (!code.value.trim()) return toast.error('请输入验证码')
    payload.code = code.value.trim()
  } else {
    if (!password.value) return toast.error('请输入密码')
    payload.password = password.value
  }

  submitting.value = true
  try {
    const { data: token } = await login(payload)
    auth.setToken(token)
    await auth.refreshUser()
    toast.success('登录成功')
    const redirect = route.query.redirect
    setTimeout(() => router.replace(typeof redirect === 'string' ? redirect : '/'), 260)
  } catch (e) {
    toast.error(e.message)
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="login">
    <header class="login__hero">
      <button class="back" aria-label="返回" @click="router.replace('/')">
        <BaseIcon name="back" :size="20" />
      </button>
      <p class="hero__mark">食赴</p>
      <p class="hero__slogan">循味而去，赴一场好食</p>
    </header>

    <section class="card">
      <h1 class="card__title">{{ mode === 'code' ? '手机号登录' : '密码登录' }}</h1>
      <p class="card__sub">
        {{ mode === 'code' ? '未注册的手机号验证后将自动创建账号' : '使用已设置的账号密码登录' }}
      </p>

      <div class="switch">
        <button
          class="switch__item"
          :class="{ 'switch__item--on': mode === 'code' }"
          @click="mode = 'code'"
        >
          验证码登录
        </button>
        <button
          class="switch__item"
          :class="{ 'switch__item--on': mode === 'password' }"
          @click="mode = 'password'"
        >
          密码登录
        </button>
      </div>

      <label class="field">
        <span class="field__prefix">+86</span>
        <input
          v-model="phone"
          type="tel"
          inputmode="numeric"
          maxlength="11"
          placeholder="请输入手机号"
        />
      </label>

      <label v-if="mode === 'code'" class="field">
        <input
          v-model="code"
          type="text"
          inputmode="numeric"
          maxlength="6"
          placeholder="请输入 6 位验证码"
        />
        <button
          class="field__code"
          :disabled="countdown > 0 || sending"
          @click="onSendCode"
        >
          {{ countdown > 0 ? `${countdown}s 后重发` : sending ? '发送中' : '获取验证码' }}
        </button>
      </label>

      <label v-else class="field">
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          maxlength="32"
          placeholder="请输入密码"
        />
      </label>

      <button class="btn btn--primary btn--block submit" :disabled="submitting" @click="onSubmit">
        {{ submitting ? '登录中…' : '登录' }}
      </button>

      <label class="agree">
        <input v-model="agreed" type="checkbox" class="agree__box" />
        <span class="agree__text">
          我已阅读并同意 <a href="javascript:void(0)">《用户服务协议》</a> 与
          <a href="javascript:void(0)">《隐私政策》</a>
        </span>
      </label>
    </section>

    <p class="dev-hint">
      {{
        mode === 'code'
          ? '开发环境未接短信网关，验证码会打印在后端控制台日志中'
          : '还没有密码？先用验证码登录，再到「我的」页面设置'
      }}
    </p>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  background: var(--surface);
}

.login__hero {
  position: relative;
  padding: 40px 24px 56px;
  background: linear-gradient(158deg, #241b15 0%, #3a2a1e 62%, #4a3324 100%);
  border-radius: 0 0 var(--r-xl) var(--r-xl);
  color: #f7efe4;
}
.back {
  position: absolute;
  top: 14px;
  left: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: rgba(247, 239, 228, 0.82);
}
.hero__mark {
  margin-top: 34px;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: 0.16em;
}
.hero__slogan {
  margin-top: 10px;
  font-size: 12.5px;
  letter-spacing: 0.3em;
  color: rgba(247, 239, 228, 0.55);
}

.card {
  position: relative;
  margin: -32px 18px 0;
  padding: 24px 20px 20px;
  background: var(--surface);
  border-radius: var(--r-lg);
  box-shadow: var(--shadow-2);
}
.card__title {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.01em;
}
.card__sub {
  margin-top: 5px;
  margin-bottom: 18px;
  font-size: 12px;
  color: var(--ink-3);
}

.switch {
  display: flex;
  gap: 4px;
  margin-bottom: 18px;
  padding: 4px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
}
.switch__item {
  flex: 1;
  height: 32px;
  border-radius: calc(var(--r-sm) - 2px);
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-3);
  transition: all var(--dur) var(--ease);
}
.switch__item--on {
  background: var(--surface);
  color: var(--ink-1);
  box-shadow: var(--shadow-1);
}

.field {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 50px;
  margin-bottom: 12px;
  padding: 0 16px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
  transition: box-shadow var(--dur) var(--ease);
}
.field:focus-within {
  box-shadow: inset 0 0 0 1.5px var(--brand-line);
}
.field input {
  flex: 1;
  min-width: 0;
  font-size: 15px;
}
.field__prefix {
  flex: none;
  padding-right: 10px;
  border-right: 1px solid var(--line-strong);
  font-family: var(--font-num);
  font-size: 13.5px;
  color: var(--ink-2);
}
.field__code {
  flex: none;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--brand);
  white-space: nowrap;
}
.field__code:disabled {
  color: var(--ink-3);
}

.submit {
  margin-top: 8px;
}

.agree {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 16px;
}
.agree__box {
  position: relative;
  flex: none;
  width: 15px;
  height: 15px;
  margin-top: 3px;
  border: 1.2px solid var(--line-strong);
  border-radius: 4px;
  background: #fff;
  -webkit-appearance: none;
  appearance: none;
  transition: all var(--dur) var(--ease);
}
.agree__box:checked {
  border-color: var(--brand);
  background: var(--brand);
}
.agree__box:checked::after {
  content: '';
  position: absolute;
  top: 1px;
  left: 4px;
  width: 4px;
  height: 8px;
  border: solid #fff;
  border-width: 0 1.6px 1.6px 0;
  transform: rotate(45deg);
}
.agree__text {
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--ink-3);
  text-align: left;
}
.agree__text a {
  color: var(--brand);
}

.dev-hint {
  padding: 22px 32px 40px;
  font-size: 11px;
  line-height: 1.7;
  color: var(--ink-3);
  text-align: center;
}
</style>
