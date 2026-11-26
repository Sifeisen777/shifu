<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseIcon from '@/components/BaseIcon.vue'
import AppAvatar from '@/components/AppAvatar.vue'
import ScoreStars from '@/components/ScoreStars.vue'
import { fetchMerchant, fetchNote, fetchNoteLikes, follow, isFollowing, toggleLike } from '@/api'
import { useAuthStore } from '@/store/auth'
import { imageUrl, relativeTime, splitImages, yuan } from '@/utils/format'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const note = ref(null)
const merchant = ref(null)
const likers = ref([])
const followed = ref(false)
const loading = ref(true)
const error = ref('')
const activeSlide = ref(0)
const liking = ref(false)
const scroller = ref(null)

const images = computed(() => splitImages(note.value?.images))
const isMine = computed(() => auth.userId && note.value?.userId === auth.userId)
const merchantCover = computed(() => imageUrl(splitImages(merchant.value?.images)[0]))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await fetchNote(route.params.id)
    note.value = data
    if (data?.merchantId) loadMerchant(data.merchantId)
    // 点赞用户列表是受保护接口，游客不请求，否则会触发 401 跳登录
    if (auth.isLogin) loadLikers()
    if (auth.isLogin && data?.userId && data.userId !== auth.userId) loadFollow(data.userId)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadMerchant(id) {
  try {
    const { data } = await fetchMerchant(id)
    merchant.value = data
  } catch {
    merchant.value = null
  }
}

async function loadLikers() {
  try {
    const { data } = await fetchNoteLikes(route.params.id)
    likers.value = data || []
  } catch {
    likers.value = []
  }
}

async function loadFollow(userId) {
  try {
    const { data } = await isFollowing(userId)
    followed.value = Boolean(data)
  } catch {
    followed.value = false
  }
}

function requireLogin() {
  if (auth.isLogin) return true
  toast.error('请先登录')
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function onLike() {
  if (!requireLogin() || liking.value) return
  liking.value = true
  // 乐观更新，失败再回滚
  const prevLiked = note.value.liked
  const prevIsLike = note.value.isLike
  note.value.liked = (prevLiked || 0) + (prevIsLike ? -1 : 1)
  note.value.isLike = !prevIsLike
  try {
    await toggleLike(note.value.id)
    loadLikers()
  } catch (e) {
    note.value.liked = prevLiked
    note.value.isLike = prevIsLike
    toast.error(e.message)
  } finally {
    liking.value = false
  }
}

async function onFollow() {
  if (!requireLogin()) return
  try {
    await follow(note.value.userId, !followed.value)
    followed.value = !followed.value
    toast.success(followed.value ? '已关注' : '已取消关注')
  } catch (e) {
    toast.error(e.message)
  }
}

function goAuthor() {
  if (!note.value?.userId) return
  if (isMine.value) router.push('/me')
  else router.push(`/user/${note.value.userId}`)
}

function onScroll() {
  const el = scroller.value
  if (!el || !el.clientWidth) return
  activeSlide.value = Math.round(el.scrollLeft / el.clientWidth)
}

function toSlide(i) {
  const el = scroller.value
  if (!el) return
  el.scrollTo({ left: i * el.clientWidth, behavior: 'smooth' })
}

onMounted(load)
</script>

<template>
  <div class="page page--plain nd">
    <div class="topbar">
      <button class="round-btn" aria-label="返回" @click="router.back()">
        <BaseIcon name="back" :size="21" />
      </button>
      <button class="round-btn" aria-label="分享" @click="toast('链接已复制')">
        <BaseIcon name="share" :size="18" />
      </button>
    </div>

    <div v-if="loading" class="loading">
      <div class="skeleton loading__hero" />
      <div class="loading__body">
        <div class="skeleton loading__line" />
        <div class="skeleton loading__line loading__line--short" />
      </div>
    </div>

    <div v-else-if="error" class="error">
      <p>{{ error }}</p>
      <button class="btn btn--soft" @click="load">重新加载</button>
    </div>

    <template v-else-if="note">
      <!-- 图片 -->
      <div v-if="images.length" class="gallery-wrap">
        <div ref="scroller" class="gallery no-scrollbar" @scroll.passive="onScroll">
          <div v-for="(img, i) in images" :key="i" class="gallery__item">
            <img :src="img" :alt="`${note.title} 配图 ${i + 1}`" />
          </div>
        </div>
        <div v-if="images.length > 1" class="dots">
          <button
            v-for="(img, i) in images"
            :key="i"
            class="dot"
            :class="{ 'dot--active': i === activeSlide }"
            @click="toSlide(i)"
          />
        </div>
        <span v-if="images.length > 1" class="counter">
          {{ activeSlide + 1 }} / {{ images.length }}
        </span>
      </div>

      <!-- 作者 -->
      <section class="author">
        <button class="author__main" @click="goAuthor">
          <AppAvatar :src="note.icon" :name="note.name" :size="40" />
          <span class="author__info">
            <span class="author__name">{{ note.name || '食客' }}</span>
            <span class="author__time">{{ relativeTime(note.createTime) }}</span>
          </span>
        </button>
        <button
          v-if="auth.isLogin && !isMine"
          class="follow-btn"
          :class="{ 'follow-btn--on': followed }"
          @click="onFollow"
        >
          {{ followed ? '已关注' : '关注' }}
        </button>
        <span v-else-if="isMine" class="author__self">我的笔记</span>
      </section>

      <!-- 正文 -->
      <article class="body">
        <h1 class="body__title">{{ note.title }}</h1>
        <div class="body__content" v-html="note.content" />
      </article>

      <!-- 关联商户 -->
      <router-link
        v-if="merchant"
        :to="`/merchant/${merchant.id}`"
        class="shop card tap"
      >
        <img v-if="merchantCover" :src="merchantCover" class="shop__img" :alt="merchant.name" />
        <div class="shop__info">
          <span class="shop__label">笔记提到</span>
          <span class="shop__name clamp-1">{{ merchant.name }}</span>
          <ScoreStars :score="merchant.score" :size="11" :show-value="false" />
        </div>
        <div class="shop__right">
          <span class="shop__price">¥{{ yuan(merchant.avgPrice) }}</span>
          <BaseIcon name="arrow-right" :size="15" />
        </div>
      </router-link>

      <!-- 点赞用户 -->
      <section class="likes">
        <div class="likes__avatars">
          <AppAvatar
            v-for="u in likers.slice(0, 5)"
            :key="u.id"
            :src="u.icon"
            :name="u.nickName"
            :size="24"
            class="likes__avatar"
          />
          <span v-if="!likers.length" class="likes__empty">还没有人点赞</span>
        </div>
        <span class="likes__count">{{ note.liked || 0 }} 人点赞</span>
      </section>

      <p class="footnote">食赴 · 内容由用户发布，仅供参考</p>

      <!-- 底部操作 -->
      <div class="actionbar">
        <button class="act" :class="{ 'act--on': note.isLike }" @click="onLike">
          <BaseIcon name="heart" :size="21" :filled="!!note.isLike" />
          <span>{{ note.liked || 0 }}</span>
        </button>
        <button class="act" @click="toast(`共 ${note.comments || 0} 条评论`)">
          <BaseIcon name="chat" :size="21" />
          <span>{{ note.comments || 0 }}</span>
        </button>
        <button class="act" @click="toast('链接已复制')">
          <BaseIcon name="share" :size="20" />
          <span>分享</span>
        </button>
        <router-link v-if="merchant" :to="`/merchant/${merchant.id}`" class="btn btn--primary act__cta">
          到店看看
        </router-link>
      </div>
    </template>
  </div>
</template>

<style scoped>
.nd {
  padding-bottom: calc(74px + var(--safe-b));
  background: var(--surface);
}

.topbar {
  position: fixed;
  top: 12px;
  left: 50%;
  z-index: 40;
  display: flex;
  justify-content: space-between;
  width: 100%;
  max-width: var(--shell-w);
  padding: 0 14px;
  transform: translateX(-50%);
  pointer-events: none;
}
.round-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(23, 19, 16, 0.42);
  color: #fff;
  backdrop-filter: blur(10px);
  pointer-events: auto;
  transition: transform 0.16s var(--ease);
}
.round-btn:active {
  transform: scale(0.94);
}

/* 图片区 */
.gallery-wrap {
  position: relative;
}
.gallery {
  display: flex;
  height: 420px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
  background: #efe7db;
}
.gallery__item {
  flex: none;
  width: 100%;
  height: 100%;
  scroll-snap-align: start;
}
.gallery__item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.dots {
  position: absolute;
  bottom: 14px;
  left: 50%;
  display: flex;
  gap: 5px;
  transform: translateX(-50%);
}
.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  transition: all var(--dur) var(--ease);
}
.dot--active {
  width: 15px;
  background: #fff;
}
.counter {
  position: absolute;
  top: 52px;
  right: 14px;
  padding: 2px 9px;
  border-radius: var(--r-pill);
  background: rgba(23, 19, 16, 0.42);
  color: #fff;
  font-family: var(--font-num);
  font-size: 11px;
  letter-spacing: 0.06em;
  backdrop-filter: blur(8px);
}

/* 作者 */
.author {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 18px 6px;
}
.author__main {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
  text-align: left;
}
.author__info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.author__name {
  font-size: 14px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.author__time {
  font-size: 11px;
  color: var(--ink-3);
}
.follow-btn {
  flex: none;
  height: 30px;
  padding: 0 16px;
  border-radius: var(--r-pill);
  background: var(--brand);
  color: #fff;
  font-size: 12.5px;
  font-weight: 600;
}
.follow-btn--on {
  background: var(--surface);
  color: var(--ink-3);
  box-shadow: inset 0 0 0 1px var(--line-strong);
}
.author__self {
  flex: none;
  font-size: 11.5px;
  color: var(--ink-3);
}

/* 正文 */
.body {
  padding: 12px 18px 6px;
}
.body__title {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.4;
  letter-spacing: -0.02em;
}
.body__content {
  margin-top: 12px;
  font-size: 15px;
  line-height: 1.92;
  color: var(--ink-2);
  word-break: break-word;
}
.body__content :deep(br) {
  line-height: 1.92;
}

/* 关联商户 */
.shop {
  display: flex;
  align-items: center;
  gap: 11px;
  margin: 16px 18px;
  padding: 10px;
}
.shop__img {
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: var(--r-xs);
  object-fit: cover;
}
.shop__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.shop__label {
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--brand);
}
.shop__name {
  font-size: 13.5px;
  font-weight: 600;
}
.shop__right {
  display: flex;
  flex: none;
  align-items: center;
  gap: 4px;
  color: var(--ink-3);
}
.shop__price {
  font-family: var(--font-num);
  font-size: 13px;
  font-weight: 700;
  color: var(--brand);
}

/* 点赞 */
.likes {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-top: 1px solid var(--line);
}
.likes__avatars {
  display: flex;
  align-items: center;
  gap: 0;
}
.likes__avatar {
  margin-right: -6px;
  box-shadow: 0 0 0 2px var(--surface);
}
.likes__empty {
  font-size: 12px;
  color: var(--ink-3);
}
.likes__count {
  font-size: 12px;
  color: var(--ink-3);
}

.footnote {
  padding: 8px 0 26px;
  font-size: 10.5px;
  letter-spacing: 0.14em;
  color: var(--ink-3);
  text-align: center;
}

/* 底部操作条 */
.actionbar {
  position: fixed;
  bottom: 0;
  left: 50%;
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  max-width: var(--shell-w);
  padding: 8px 12px calc(8px + var(--safe-b));
  transform: translateX(-50%);
  background: rgba(255, 253, 250, 0.96);
  backdrop-filter: blur(14px);
  border-top: 1px solid var(--line);
}
.act {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1px;
  width: 52px;
  height: 44px;
  color: var(--ink-2);
  transition: color var(--dur) var(--ease), transform 0.16s var(--ease);
}
.act:active {
  transform: scale(0.92);
}
.act span {
  font-family: var(--font-num);
  font-size: 10px;
}
.act--on {
  color: var(--brand);
}
.act__cta {
  flex: 1;
  height: 42px;
  margin-left: 6px;
  font-size: 14px;
}

.loading__hero {
  height: 380px;
  border-radius: 0;
}
.loading__body {
  padding: 20px 18px;
}
.loading__line {
  height: 15px;
  margin-bottom: 10px;
}
.loading__line--short {
  width: 50%;
}
.error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 100px 24px;
  color: var(--ink-3);
  font-size: 13.5px;
}
</style>
