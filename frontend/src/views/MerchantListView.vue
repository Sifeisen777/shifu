<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppNavBar from '@/components/AppNavBar.vue'
import MerchantCard from '@/components/MerchantCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { fetchCategories, fetchMerchantsByType } from '@/api'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const HANGZHOU = { x: 120.149993, y: 30.334229 }

const categories = ref([])
const raw = ref([])
const current = ref(1)
const loading = ref(false)
const finished = ref(false)
const booting = ref(true)
const sortBy = ref('default')

const sentinel = ref(null)
let observer = null

const typeId = computed(() => Number(route.query.type) || 1)
const typeName = computed(() => route.query.name || '全部商户')

const SORTS = [
  { key: 'default', label: '综合' },
  { key: 'distance', label: '距离' },
  { key: 'score', label: '评分' },
  { key: 'popular', label: '人气' },
]

const list = computed(() => {
  const data = [...raw.value]
  if (sortBy.value === 'distance') {
    data.sort((a, b) => (a.distance ?? Infinity) - (b.distance ?? Infinity))
  } else if (sortBy.value === 'score') {
    data.sort((a, b) => (b.score ?? 0) - (a.score ?? 0))
  } else if (sortBy.value === 'popular') {
    data.sort((a, b) => (b.comments ?? 0) - (a.comments ?? 0))
  }
  return data
})

async function loadCategories() {
  try {
    const { data } = await fetchCategories()
    categories.value = data || []
  } catch (e) {
    toast.error(e.message)
  }
}

async function loadPage() {
  if (loading.value || finished.value) return
  loading.value = true
  try {
    const { data } = await fetchMerchantsByType({
      typeId: typeId.value,
      current: current.value,
      ...HANGZHOU,
    })
    const page = data || []
    raw.value = raw.value.concat(page)
    if (page.length < 10) finished.value = true
    else current.value += 1
  } catch (e) {
    finished.value = true
    if (current.value === 1) toast.error(e.message)
  } finally {
    loading.value = false
    booting.value = false
  }
}

function reset() {
  raw.value = []
  current.value = 1
  finished.value = false
  booting.value = true
  loadPage()
}

function switchType(cat) {
  if (cat.id === typeId.value) return
  router.replace({ name: 'merchants', query: { type: cat.id, name: cat.name } })
}

watch(typeId, () => reset())

onMounted(async () => {
  await loadCategories()
  await loadPage()
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0].isIntersecting) loadPage()
    },
    { rootMargin: '260px 0px' }
  )
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div class="page page--plain mlist">
    <AppNavBar :title="typeName" />

    <!-- 分类切换 -->
    <div class="chips no-scrollbar">
      <button
        v-for="c in categories"
        :key="c.id"
        class="chip"
        :class="{ 'chip--active': c.id === typeId }"
        @click="switchType(c)"
      >
        {{ c.name }}
      </button>
    </div>

    <!-- 排序 -->
    <div class="sorts">
      <button
        v-for="s in SORTS"
        :key="s.key"
        class="sort"
        :class="{ 'sort--active': sortBy === s.key }"
        @click="sortBy = s.key"
      >
        {{ s.label }}
      </button>
      <span class="sorts__count">{{ list.length }} 家</span>
    </div>

    <div class="list">
      <div v-if="booting" class="skeletons">
        <div v-for="i in 5" :key="i" class="skeleton skeleton--card" />
      </div>

      <template v-else-if="list.length">
        <MerchantCard
          v-for="m in list"
          :key="m.id"
          :merchant="m"
          class="list__item"
        />
      </template>

      <EmptyState
        v-else
        icon="location"
        title="这个分类还没有商户"
        desc="换一个分类看看，或者搜索商户名"
      />
    </div>

    <div ref="sentinel" class="sentinel">
      <span v-if="loading" class="sentinel__text">正在加载…</span>
      <span v-else-if="finished && raw.length" class="sentinel__text">没有更多了</span>
    </div>
  </div>
</template>

<style scoped>
.mlist {
  background: var(--bg);
}

.chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 4px 14px 12px;
}
.chip {
  flex: none;
  height: 30px;
  padding: 0 14px;
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font-size: 12.5px;
  letter-spacing: 0.02em;
  box-shadow: var(--shadow-1);
  transition: background-color var(--dur) var(--ease), color var(--dur) var(--ease);
}
.chip--active {
  background: var(--ink);
  color: #f7efe4;
  box-shadow: none;
}

.sorts {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px 12px;
  border-bottom: 1px solid var(--line);
}
.sort {
  position: relative;
  padding: 2px 0;
  font-size: 13px;
  color: var(--ink-3);
  transition: color var(--dur) var(--ease);
}
.sort--active {
  color: var(--ink);
  font-weight: 700;
}
.sort--active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -6px;
  width: 14px;
  height: 2px;
  border-radius: 2px;
  background: var(--brand);
  transform: translateX(-50%);
}
.sorts__count {
  margin-left: auto;
  font-size: 11.5px;
  color: var(--ink-3);
}

.list {
  padding: 12px 14px 0;
}
.list__item {
  margin-bottom: 10px;
}

.skeletons {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.skeleton--card {
  height: 116px;
  border-radius: var(--r-md);
}

.sentinel {
  padding: 18px 0 34px;
  text-align: center;
}
.sentinel__text {
  font-size: 11.5px;
  letter-spacing: 0.1em;
  color: var(--ink-3);
}
</style>
