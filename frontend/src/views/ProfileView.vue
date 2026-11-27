<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '@/components/AppTabBar.vue'
import AppAvatar from '@/components/AppAvatar.vue'
import BaseIcon from '@/components/BaseIcon.vue'
import NoteCard from '@/components/NoteCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { fetchFollowFeed, fetchMyNotes, fetchSignCount, fetchUserProfile, logout, setPassword, signIn } from '@/api'
import { useAuthStore } from '@/store/auth'
import { relativeTime } from '@/utils/format'
import toast from '@/utils/toast'

const router = useRouter()
const auth = useAuthStore()

const profile = ref(null)
const notes = ref([])
const notesLoading = ref(true)

const tab = ref('notes')
const feed = ref([])
const feedCursor = ref({ lastId: Date.now() + 1, offset: 0 })
const feedLoading = ref(false)
const feedFinished = ref(false)

const signDays = ref(0)
const signing = ref(false)

async function loadProfile() {
  const user = await auth.refreshUser()
  if (!user) {
    router.replace({ name: 'login', query: { redirect: '/me' } })
    return
  }
  try {
    const { data } = await fetchUserProfile(user.id)
    profile.value = data
  } catch {
    profile.value = null
  }
  loadSignCount()
}

async function loadNotes() {
  notesLoading.value = true
  try {
    const { data } = await fetchMyNotes(1)
    notes.value = data || []
  } catch (e) {
    toast.error(e.message)
  } finally {
    notesLoading.value = false
  }
}

async function loadFeed(reset = false) {
  if (feedLoading.value || (feedFinished.value && !reset)) return
  feedLoading.value = true
  try {
    if (reset) {
      feed.value = []
      feedFinished.value = false
      feedCursor.value = { lastId: Date.now() + 1, offset: 0 }
    }
    const { data } = await fetchFollowFeed(
      feedCursor.value.lastId,
      feedCursor.value.offset
    )
    const list = data?.list || []
    feed.value = feed.value.concat(list)
    if (list.length) {
      feedCursor.value = { lastId: data.minTime, offset: data.offset }
    }
    if (list.length < 2) feedFinished.value = true
  } catch (e) {
    feedFinished.value = true
    toast.error(e.message)
  } finally {
    feedLoading.value = false
  }
}

async function loadSignCount() {
  try {
    const { data } = await fetchSignCount()
    signDays.value = Number(data) || 0
  } catch {
    signDays.value = 0
  }
}

async function onSign() {
  if (signing.value) return
  signing.value = true
  try {
    await signIn()
    await loadSignCount()
    toast.success('签到成功')
  } catch (e) {
    toast.error(e.message)
  } finally {
    signing.value = false
  }
}

async function onLogout() {
  try {
    await logout()
  } catch {
    /* 服务端失败也允许本地退出 */
  }
  auth.clear()
  toast.success('已退出登录')
  setTimeout(() => router.replace('/'), 240)
}

const passwordPanel = ref(false)
const pwdForm = ref({ oldPassword: '', password: '', confirm: '' })
const savingPassword = ref(false)

async function onSavePassword() {
  if (savingPassword.value) return
  const { oldPassword, password, confirm } = pwdForm.value
  if (!password || password.length < 6 || password.length > 32) {
    return toast.error('密码长度需为 6~32 位')
  }
  if (password !== confirm) return toast.error('两次输入的密码不一致')

  savingPassword.value = true
  try {
    await setPassword({ password, oldPassword: oldPassword || undefined })
    toast.success('密码设置成功')
    passwordPanel.value = false
    pwdForm.value = { oldPassword: '', password: '', confirm: '' }
  } catch (e) {
    toast.error(e.message)
  } finally {
    savingPassword.value = false
  }
}

watch(tab, (value) => {
  if (value === 'feed' && !feed.value.length) loadFeed(true)
  if (value === 'sign') loadSignCount()
})

onMounted(() => {
  loadProfile()
  loadNotes()
})
</script>

<template>
  <div class="page me">
    <header class="hero">
      <div class="hero__bar">
        <span class="hero__label">我的</span>
        <div class="hero__actions">
          <button class="ghost-btn" @click="router.push('/me/edit')">
            <BaseIcon name="edit" :size="15" />
            编辑
          </button>
          <button class="ghost-btn" @click="onLogout">
            <BaseIcon name="logout" :size="15" />
            退出
          </button>
        </div>
      </div>

      <div class="hero__user">
        <AppAvatar :src="auth.user?.icon" :name="auth.user?.nickName" :size="60" />
        <div class="hero__info">
          <h1 class="hero__name">{{ auth.user?.nickName || '食客' }}</h1>
          <p class="hero__city">
            <BaseIcon name="location" :size="12" />
            {{ profile?.city || '杭州' }}
          </p>
        </div>
      </div>

      <p class="hero__intro clamp-2">
        {{ profile?.introduce || '还没有个人简介，说说你的口味偏好' }}
      </p>

      <div class="stats">
        <div class="stat">
          <span class="stat__num">{{ profile?.followee ?? 0 }}</span>
          <span class="stat__label">关注</span>
        </div>
        <div class="stat">
          <span class="stat__num">{{ profile?.fans ?? 0 }}</span>
          <span class="stat__label">粉丝</span>
        </div>
        <div class="stat">
          <span class="stat__num">{{ profile?.credits ?? 0 }}</span>
          <span class="stat__label">积分</span>
        </div>
      </div>
    </header>

    <!-- 打卡 -->
    <div class="checkin card">
      <div class="checkin__left">
        <p class="checkin__title">每日打卡</p>
        <p class="checkin__sub">
          本月已连续签到 <b>{{ signDays }}</b> 天
        </p>
      </div>
      <button class="btn btn--soft checkin__btn" :disabled="signing" @click="onSign">
        {{ signing ? '处理中' : '今日签到' }}
      </button>
    </div>

    <!-- 账号安全 -->
    <div class="security card">
      <div class="security__left">
        <p class="security__title">登录密码</p>
        <p class="security__sub">设置后可用手机号 + 密码登录</p>
      </div>
      <button class="btn btn--soft security__btn" @click="passwordPanel = !passwordPanel">
        {{ passwordPanel ? '收起' : '设置' }}
      </button>
    </div>

    <div v-if="passwordPanel" class="pwd card">
      <label class="pwd__field">
        <span>原密码</span>
        <input
          v-model="pwdForm.oldPassword"
          type="password"
          maxlength="32"
          placeholder="首次设置请留空"
        />
      </label>
      <label class="pwd__field">
        <span>新密码</span>
        <input
          v-model="pwdForm.password"
          type="password"
          maxlength="32"
          placeholder="6~32 位"
        />
      </label>
      <label class="pwd__field">
        <span>确认</span>
        <input
          v-model="pwdForm.confirm"
          type="password"
          maxlength="32"
          placeholder="再输入一次"
        />
      </label>
      <button class="btn btn--primary btn--block" :disabled="savingPassword" @click="onSavePassword">
        {{ savingPassword ? '保存中…' : '保存密码' }}
      </button>
    </div>

    <!-- 标签页 -->
    <div class="tabs">
      <button class="tab" :class="{ 'tab--on': tab === 'notes' }" @click="tab = 'notes'">
        笔记
      </button>
      <button class="tab" :class="{ 'tab--on': tab === 'feed' }" @click="tab = 'feed'">
        关注动态
      </button>
    </div>

    <!-- 我的笔记 -->
    <section v-if="tab === 'notes'" class="pane">
      <div v-if="notesLoading" class="grid">
        <div v-for="i in 4" :key="i" class="skeleton grid__ph" />
      </div>
      <div v-else-if="notes.length" class="grid">
        <NoteCard v-for="n in notes" :key="n.id" :note="n" :show-author="false" />
      </div>
      <EmptyState
        v-else
        icon="edit"
        title="还没有发布过笔记"
        desc="记录一次到店体验，让别人也看见"
        action-text="写第一篇"
        @action="router.push('/note/new')"
      />
    </section>

    <!-- 关注动态 -->
    <section v-else class="pane">
      <div v-if="feed.length" class="feed">
        <router-link
          v-for="n in feed"
          :key="n.id"
          :to="`/note/${n.id}`"
          class="feed__item card tap"
        >
          <div class="feed__head">
            <AppAvatar :src="n.icon" :name="n.name" :size="26" />
            <span class="feed__name">{{ n.name || '食客' }}</span>
            <span class="feed__time">{{ relativeTime(n.createTime) }}</span>
          </div>
          <p class="feed__title clamp-2">{{ n.title }}</p>
          <div class="feed__imgs">
            <img
              v-for="(img, i) in (n.images || '').split(',').slice(0, 3)"
              :key="i"
              :src="img"
              alt=""
              loading="lazy"
            />
          </div>
        </router-link>
        <button class="more" :disabled="feedLoading" @click="loadFeed(false)">
          {{ feedFinished ? '没有更多了' : feedLoading ? '加载中…' : '加载更多' }}
        </button>
      </div>
      <EmptyState
        v-else-if="!feedLoading"
        icon="user"
        title="关注列表还很安静"
        desc="去关注几位爱分享的食客，这里会出现他们的新笔记"
        action-text="去逛逛"
        @action="router.push('/merchants')"
      />
      <div v-else class="feed">
        <div v-for="i in 2" :key="i" class="skeleton feed__ph" />
      </div>
    </section>

    <AppTabBar active="me" />
  </div>
</template>

<style scoped>
.me {
  background: var(--bg);
}

/* 账号安全 */
.security {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 18px 0;
  padding: 14px 16px;
}
.security__left {
  flex: 1;
  min-width: 0;
}
.security__title {
  font-size: 13.5px;
  font-weight: 650;
}
.security__sub {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--ink-3);
}
.security__btn {
  flex: none;
}

.pwd {
  margin: 10px 18px 0;
  padding: 16px;
}
.pwd__field {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 44px;
  margin-bottom: 10px;
  padding: 0 14px;
  border-radius: var(--r-sm);
  background: var(--surface-2);
}
.pwd__field span {
  flex: none;
  width: 52px;
  font-size: 12.5px;
  color: var(--ink-2);
}
.pwd__field input {
  flex: 1;
  min-width: 0;
  font-size: 14px;
}

/* Hero */
.hero {
  padding: 14px 18px 26px;
  background: linear-gradient(158deg, #241b15 0%, #3a2a1e 62%, #4a3324 100%);
  border-radius: 0 0 var(--r-xl) var(--r-xl);
  color: #f7efe4;
}
.hero__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hero__label {
  font-size: 13px;
  letter-spacing: 0.2em;
  color: rgba(247, 239, 228, 0.6);
}
.hero__actions {
  display: flex;
  gap: 8px;
}
.ghost-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 12px;
  border-radius: var(--r-pill);
  background: rgba(247, 239, 228, 0.12);
  color: rgba(247, 239, 228, 0.9);
  font-size: 11.5px;
}

.hero__user {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-top: 20px;
}
.hero__info {
  min-width: 0;
}
.hero__name {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.01em;
}
.hero__city {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-top: 4px;
  font-size: 11.5px;
  color: rgba(247, 239, 228, 0.55);
}

.hero__intro {
  margin-top: 14px;
  font-size: 12.5px;
  line-height: 1.7;
  color: rgba(247, 239, 228, 0.62);
}

.stats {
  display: flex;
  gap: 26px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(247, 239, 228, 0.12);
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.stat__num {
  font-family: var(--font-num);
  font-size: 17px;
  font-weight: 800;
}
.stat__label {
  font-size: 10.5px;
  letter-spacing: 0.1em;
  color: rgba(247, 239, 228, 0.45);
}

/* 打卡 */
.checkin {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -14px 14px 0;
  padding: 14px 16px;
  position: relative;
  z-index: 2;
}
.checkin__title {
  font-size: 14px;
  font-weight: 700;
}
.checkin__sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-3);
}
.checkin__sub b {
  font-family: var(--font-num);
  font-size: 14px;
  color: var(--brand);
}
.checkin__btn {
  flex: none;
  height: 34px;
  padding: 0 18px;
  font-size: 12.5px;
}

/* Tabs */
.tabs {
  display: flex;
  gap: 22px;
  padding: 20px 18px 12px;
}
.tab {
  position: relative;
  font-size: 14px;
  color: var(--ink-3);
  transition: color var(--dur) var(--ease);
}
.tab--on {
  color: var(--ink);
  font-weight: 700;
}
.tab--on::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -6px;
  width: 16px;
  height: 2px;
  border-radius: 2px;
  background: var(--brand);
  transform: translateX(-50%);
}

.pane {
  padding-bottom: 22px;
}

/* 笔记网格 */
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 0 14px;
}
.grid__ph {
  aspect-ratio: 3 / 4.6;
  border-radius: var(--r-md);
}

/* 关注动态 */
.feed {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 14px;
}
.feed__item {
  padding: 13px 14px;
}
.feed__head {
  display: flex;
  align-items: center;
  gap: 7px;
}
.feed__name {
  font-size: 12.5px;
  font-weight: 600;
}
.feed__time {
  margin-left: auto;
  font-size: 11px;
  color: var(--ink-3);
}
.feed__title {
  margin-top: 9px;
  font-size: 14.5px;
  font-weight: 650;
  line-height: 1.5;
}
.feed__imgs {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}
.feed__imgs img {
  width: 33.33%;
  aspect-ratio: 1;
  border-radius: var(--r-xs);
  object-fit: cover;
}
.feed__ph {
  height: 170px;
  border-radius: var(--r-md);
}
.more {
  padding: 14px 0;
  font-size: 12px;
  color: var(--ink-3);
}
</style>
