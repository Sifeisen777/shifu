<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BaseIcon from '@/components/BaseIcon.vue'
import AppTabBar from '@/components/AppTabBar.vue'
import NoteCard from '@/components/NoteCard.vue'
import MerchantCard from '@/components/MerchantCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { fetchCategories, fetchHotNotes, searchMerchants } from '@/api'
import { imageUrl } from '@/utils/format'
import toast from '@/utils/toast'

const router = useRouter()

const categories = ref([])
const notes = ref([])
const current = ref(1)
const loading = ref(false)
const finished = ref(false)
const booting = ref(true)

const sentinel = ref(null)
let observer = null

/* ---------------- 搜索 ---------------- */
const searchOpen = ref(false)
const keyword = ref('')
const searchResults = ref([])
const searching = ref(false)

async function loadCategories() {
  try {
    const { data } = await fetchCategories()
    categories.value = data || []
  } catch (e) {
    toast.error(e.message)
  }
}

async function loadNotes() {
  if (loading.value || finished.value) return
  loading.value = true
  try {
    const { data } = await fetchHotNotes(current.value)
    const list = data || []
    notes.value = notes.value.concat(list)
    if (list.length < 10) finished.value = true
    else current.value += 1
  } catch (e) {
    finished.value = true
    if (current.value === 1) toast.error(e.message)
  } finally {
    loading.value = false
    booting.value = false
  }
}

function goCategory(cat) {
  router.push({ name: 'merchants', query: { type: cat.id, name: cat.name } })
}

function openSearch() {
  searchOpen.value = true
}

function closeSearch() {
  searchOpen.value = false
  keyword.value = ''
  searchResults.value = []
}

let searchTimer = null
function onKeywordInput() {
  clearTimeout(searchTimer)
  const kw = keyword.value.trim()
  if (!kw) {
    searchResults.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    searching.value = true
    try {
      const { data } = await searchMerchants({ name: kw, current: 1 })
      searchResults.value = data || []
    } catch (e) {
      toast.error(e.message)
    } finally {
      searching.value = false
    }
  }, 260)
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadNotes()])
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) loadNotes()
    },
    { rootMargin: '320px 0px' }
  )
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  clearTimeout(searchTimer)
})
</script>

<template>
  <div class="page home">
    <!-- 顶部：深咖啡底，压住食物图的暖色，也和米色列表拉开层次 -->
    <header class="hero">
      <div class="hero__row">
        <div class="brand">
          <span class="brand__mark">食赴</span>
          <span class="brand__dot" />
          <span class="brand__tag">循味而去</span>
        </div>
        <button class="city">
          杭州
          <BaseIcon name="chevron-down" :size="13" :stroke="2" />
        </button>
      </div>

      <p class="hero__slogan">今天，赴一场好食</p>

      <button class="search" @click="openSearch">
        <BaseIcon name="search" :size="17" />
        <span>搜商户、地点、口味</span>
      </button>
    </header>

    <!-- 分类：横向滑动，比九宫格更省纵向空间 -->
    <section class="cats no-scrollbar">
      <button
        v-for="c in categories"
        :key="c.id"
        class="cat tap"
        @click="goCategory(c)"
      >
        <span class="cat__icon">
          <img :src="imageUrl(c.icon)" :alt="c.name" loading="lazy" />
        </span>
        <span class="cat__name">{{ c.name }}</span>
      </button>
      <div v-if="!categories.length && booting" class="cats__skeleton">
        <span v-for="i in 5" :key="i" class="skeleton" />
      </div>
    </section>

    <!-- 热门笔记 -->
    <div class="section-head">
      <h2 class="section-title">热门笔记</h2>
      <span class="section-sub">真实到店 · 实拍</span>
    </div>

    <div v-if="notes.length" class="grid">
      <NoteCard v-for="n in notes" :key="n.id" :note="n" variant="grid" />
    </div>

    <div v-else-if="booting" class="grid">
      <div v-for="i in 4" :key="i" class="ph">
        <div class="skeleton ph__img" />
        <div class="skeleton ph__line" />
        <div class="skeleton ph__line ph__line--short" />
      </div>
    </div>

    <EmptyState
      v-else
      icon="edit"
      title="还没有人发布笔记"
      desc="成为第一个分享到店体验的人"
      action-text="去发布"
      @action="router.push('/note/new')"
    />

    <div ref="sentinel" class="sentinel">
      <span v-if="loading" class="sentinel__text">正在加载…</span>
      <span v-else-if="finished && notes.length" class="sentinel__text">已经到底啦</span>
    </div>

    <AppTabBar active="home" />

    <!-- 搜索面板 -->
    <transition name="fade">
      <div v-if="searchOpen" class="search-sheet">
        <div class="search-sheet__bar">
          <div class="search search--active">
            <BaseIcon name="search" :size="17" />
            <input
              v-model="keyword"
              type="search"
              placeholder="搜商户、地点、口味"
              autofocus
              @input="onKeywordInput"
            />
            <button v-if="keyword" class="search__clear" @click="keyword = ''; searchResults = []">
              <BaseIcon name="close" :size="13" :stroke="2.2" />
            </button>
          </div>
          <button class="search-sheet__cancel" @click="closeSearch">取消</button>
        </div>

        <div class="search-sheet__body no-scrollbar">
          <p v-if="!keyword" class="search-hint">输入商户名称试试，例如「茶」「烤肉」</p>

          <div v-else-if="searching" class="search-hint">搜索中…</div>

          <template v-else-if="searchResults.length">
            <MerchantCard
              v-for="m in searchResults"
              :key="m.id"
              :merchant="m"
              class="search-item"
              @click="closeSearch"
            />
          </template>

          <EmptyState
            v-else
            icon="search"
            title="没有找到相关商户"
            desc="换个关键词，或者从分类里逛逛"
          />
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.home {
  background: var(--bg);
}

/* ---------- Hero ---------- */
.hero {
  padding: 18px 18px 22px;
  background: linear-gradient(158deg, #241b15 0%, #3a2a1e 62%, #4a3324 100%);
  border-radius: 0 0 var(--r-xl) var(--r-xl);
  color: #f7efe4;
}

.hero__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 5px;
}
.brand__mark {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: 0.14em;
}
.brand__dot {
  width: 5px;
  height: 5px;
  margin-bottom: 5px;
  border-radius: 50%;
  background: var(--brand);
}
.brand__tag {
  font-size: 10.5px;
  letter-spacing: 0.22em;
  color: rgba(247, 239, 228, 0.55);
}

.city {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 11px;
  border-radius: var(--r-pill);
  background: rgba(247, 239, 228, 0.1);
  color: rgba(247, 239, 228, 0.9);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.hero__slogan {
  margin-top: 20px;
  font-size: 25px;
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 42px;
  margin-top: 16px;
  padding: 0 14px;
  border-radius: var(--r-pill);
  background: rgba(247, 239, 228, 0.96);
  color: var(--ink-3);
  font-size: 13px;
  text-align: left;
}
.search--active {
  margin-top: 0;
  background: var(--surface);
  color: var(--ink-2);
  box-shadow: inset 0 0 0 1px var(--line);
}
.search input {
  flex: 1;
  min-width: 0;
  font-size: 13.5px;
  color: var(--ink);
}
.search__clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--line-strong);
  color: var(--surface);
}

/* ---------- 分类 ---------- */
.cats {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  padding: 18px 12px 6px;
  /* 右边缘淡出，暗示可横向滑动 */
  -webkit-mask-image: linear-gradient(to right, #000 86%, transparent 100%);
  mask-image: linear-gradient(to right, #000 86%, transparent 100%);
}
.cat {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  gap: 7px;
  width: 66px;
  padding: 4px 0;
}
.cat__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow-1);
}
.cat__icon img {
  width: 26px;
  height: 26px;
  object-fit: contain;
  background: transparent;
}
.cat__name {
  font-size: 11.5px;
  color: var(--ink-2);
  letter-spacing: 0.02em;
  white-space: nowrap;
}
.cats__skeleton {
  display: flex;
  gap: 14px;
  padding: 4px 6px;
}
.cats__skeleton span {
  width: 44px;
  height: 44px;
  border-radius: 14px;
}

/* ---------- 笔记瀑布流（双列，纯 CSS 分栏） ---------- */
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 0 14px;
}

.ph {
  overflow: hidden;
  padding-bottom: 10px;
  border-radius: var(--r-md);
  background: var(--surface);
  box-shadow: var(--shadow-1);
}
.ph__img {
  aspect-ratio: 3 / 4;
  border-radius: 0;
}
.ph__line {
  height: 11px;
  margin: 10px 10px 0;
}
.ph__line--short {
  width: 46%;
}

.sentinel {
  padding: 22px 0 calc(var(--tabbar-h) + 18px);
  text-align: center;
}
.sentinel__text {
  font-size: 11.5px;
  letter-spacing: 0.1em;
  color: var(--ink-3);
}

/* ---------- 搜索面板 ---------- */
.search-sheet {
  position: fixed;
  inset: 0;
  z-index: 90;
  left: 50%;
  width: 100%;
  max-width: var(--shell-w);
  transform: translateX(-50%);
  background: var(--bg);
}
.search-sheet__bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--bg);
}
.search-sheet__bar .search {
  flex: 1;
}
.search-sheet__cancel {
  flex: none;
  font-size: 13.5px;
  color: var(--ink-2);
}
.search-sheet__body {
  height: calc(100% - 66px);
  overflow-y: auto;
  padding: 4px 14px 40px;
}
.search-item {
  margin-bottom: 10px;
}
.search-hint {
  padding: 40px 0;
  font-size: 13px;
  color: var(--ink-3);
  text-align: center;
}
</style>
