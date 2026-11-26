<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseIcon from '@/components/BaseIcon.vue'
import ScoreStars from '@/components/ScoreStars.vue'
import BenefitCard from '@/components/BenefitCard.vue'
import { fetchBenefits, fetchMerchant, seckill } from '@/api'
import { useAuthStore } from '@/store/auth'
import { distanceText, splitImages, yuan } from '@/utils/format'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const merchant = ref(null)
const benefits = ref([])
const loading = ref(true)
const error = ref('')
const activeSlide = ref(0)
const seckilling = ref(false)
const scroller = ref(null)

const images = computed(() => splitImages(merchant.value?.images))
const dist = computed(() => distanceText(merchant.value?.distance))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await fetchMerchant(route.params.id)
    merchant.value = data
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadBenefits() {
  try {
    const { data } = await fetchBenefits(route.params.id)
    benefits.value = data || []
  } catch {
    benefits.value = []
  }
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

function goLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

async function onSeckill(benefit) {
  // 普通权益卡没有线上库存，走门店核销
  if (Number(benefit.type) !== 1) {
    toast('该权益卡到店出示即可购买')
    return
  }
  if (!auth.isLogin) {
    toast.error('请先登录')
    setTimeout(goLogin, 300)
    return
  }
  if (seckilling.value) return
  seckilling.value = true
  try {
    const { data } = await seckill(benefit.id)
    toast.success(`抢购成功，订单号 ${data}`)
    await loadBenefits()
  } catch (e) {
    toast.error(e.message)
  } finally {
    seckilling.value = false
  }
}

function writeNote() {
  if (!auth.isLogin) {
    toast.error('请先登录')
    setTimeout(goLogin, 300)
    return
  }
  router.push({
    name: 'note-new',
    query: { merchantId: merchant.value?.id, merchantName: merchant.value?.name },
  })
}

onMounted(() => {
  load()
  loadBenefits()
})
</script>

<template>
  <div class="page page--plain detail">
    <!-- 悬浮返回 -->
    <div class="topbar">
      <button class="round-btn" aria-label="返回" @click="router.back()">
        <BaseIcon name="back" :size="21" />
      </button>
      <button class="round-btn" aria-label="分享" @click="toast('分享链接已复制')">
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

    <template v-else-if="merchant">
      <!-- 图片轮播 -->
      <div ref="scroller" class="gallery no-scrollbar" @scroll.passive="onScroll">
        <div v-for="(img, i) in images" :key="i" class="gallery__item">
          <img :src="img" :alt="`${merchant.name} 环境图 ${i + 1}`" />
        </div>
        <div v-if="!images.length" class="gallery__empty" />
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

      <!-- 主体信息 -->
      <section class="panel">
        <h1 class="name">{{ merchant.name }}</h1>

        <div class="meta">
          <ScoreStars :score="merchant.score" :size="14" :count="merchant.comments" />
          <span class="meta__sep" />
          <span class="meta__price">¥{{ yuan(merchant.avgPrice) }}<i>/人</i></span>
        </div>

        <div class="tags">
          <span v-if="merchant.area" class="tag">{{ merchant.area }}</span>
          <span v-if="dist" class="tag tag--ghost">{{ dist }}</span>
          <span v-if="merchant.sold" class="tag tag--ghost">已售 {{ merchant.sold }}</span>
        </div>

        <div class="rows">
          <div class="row">
            <BaseIcon name="location" :size="16" />
            <span class="row__text">{{ merchant.address || '暂无地址' }}</span>
          </div>
          <div class="row">
            <BaseIcon name="clock" :size="16" />
            <span class="row__text">{{ merchant.openHours || '营业时间待补充' }}</span>
          </div>
        </div>
      </section>

      <!-- 权益卡 -->
      <section v-if="benefits.length" class="section">
        <div class="section-head section-head--tight">
          <h2 class="section-title">到店权益</h2>
          <span class="section-sub">{{ benefits.length }} 张可领</span>
        </div>
        <div class="benefits">
          <BenefitCard
            v-for="b in benefits"
            :key="b.id"
            :benefit="b"
            @seckill="onSeckill"
          />
        </div>
      </section>

      <p class="footnote">食赴 · 到店前先看一眼真实评价</p>
    </template>

    <!-- 底部操作 -->
    <div v-if="merchant" class="actionbar">
      <button class="btn btn--ghost actionbar__ghost" @click="writeNote">
        <BaseIcon name="edit" :size="16" />
        写笔记
      </button>
      <button class="btn btn--primary actionbar__main" @click="writeNote">
        去打卡 · 发布到店体验
      </button>
    </div>
  </div>
</template>

<style scoped>
.detail {
  padding-bottom: calc(84px + var(--safe-b));
  background: var(--bg);
}

/* 悬浮按钮 */
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
  background: rgba(255, 253, 250, 0.86);
  color: var(--ink);
  backdrop-filter: blur(10px);
  box-shadow: var(--shadow-1);
  pointer-events: auto;
  transition: transform 0.16s var(--ease);
}
.round-btn:active {
  transform: scale(0.94);
}

/* 轮播 */
.gallery {
  display: flex;
  height: 260px;
  overflow-x: auto;
  scroll-snap-type: x mandatory;
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
.gallery__empty {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #3a2a1e, #241b15);
}

.dots {
  display: flex;
  justify-content: center;
  gap: 5px;
  margin-top: -18px;
  margin-bottom: 10px;
  position: relative;
  z-index: 2;
}
.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.45);
  transition: all var(--dur) var(--ease);
}
.dot--active {
  width: 16px;
  background: #fff;
}

/* 信息面板：向上叠压图片，形成层次 */
.panel {
  margin-top: -22px;
  padding: 20px 18px 18px;
  background: var(--surface);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  position: relative;
  z-index: 2;
}

.name {
  font-size: 21px;
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 9px;
}
.meta__sep {
  width: 1px;
  height: 12px;
  background: var(--line-strong);
}
.meta__price {
  font-family: var(--font-num);
  font-size: 15px;
  font-weight: 700;
  color: var(--brand);
}
.meta__price i {
  font-style: normal;
  font-size: 11px;
  font-weight: 400;
  color: var(--ink-3);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}
.tag {
  padding: 3px 9px;
  border-radius: 6px;
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-size: 11.5px;
}
.tag--ghost {
  background: var(--surface-2);
  color: var(--ink-2);
}

.rows {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin-top: 16px;
  padding-top: 15px;
  border-top: 1px solid var(--line);
}
.row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  color: var(--ink-2);
}
.row__text {
  font-size: 13px;
  line-height: 1.5;
}

/* 权益区 */
.section {
  margin-top: 10px;
  padding-bottom: 6px;
  background: var(--surface);
}
.section-head--tight {
  padding: 16px 18px 10px;
}
.benefits {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 14px 12px;
}

.footnote {
  padding: 24px 0 8px;
  font-size: 11px;
  letter-spacing: 0.16em;
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
  gap: 10px;
  width: 100%;
  max-width: var(--shell-w);
  padding: 10px 14px calc(10px + var(--safe-b));
  transform: translateX(-50%);
  background: rgba(255, 253, 250, 0.95);
  backdrop-filter: blur(14px);
  border-top: 1px solid var(--line);
}
.actionbar__ghost {
  flex: none;
  height: 44px;
  padding: 0 16px;
  font-size: 13.5px;
}
.actionbar__main {
  flex: 1;
  height: 44px;
  font-size: 14px;
}

/* 加载与错误 */
.loading__hero {
  height: 260px;
  border-radius: 0;
}
.loading__body {
  padding: 20px 18px;
  background: var(--surface);
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
