<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppNavBar from '@/components/AppNavBar.vue'
import AppAvatar from '@/components/AppAvatar.vue'
import BaseIcon from '@/components/BaseIcon.vue'
import NoteCard from '@/components/NoteCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { fetchCommonFollows, fetchNotesOfUser, fetchUser, fetchUserProfile, follow, isFollowing } from '@/api'
import { useAuthStore } from '@/store/auth'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const user = ref(null)
const profile = ref(null)
const notes = ref([])
const commonFollows = ref([])
const followed = ref(false)
const tab = ref('notes')
const loading = ref(true)

const isMine = computed(() => auth.userId && user.value?.id === auth.userId)

async function load() {
  const id = route.params.id
  if (auth.userId && Number(id) === auth.userId) {
    router.replace('/me')
    return
  }
  loading.value = true
  try {
    const { data } = await fetchUser(id)
    user.value = data
    if (!data) {
      loading.value = false
      return
    }
    await Promise.all([loadProfile(id), loadNotes(id)])
    if (auth.isLogin) loadFollow(id)
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadProfile(id) {
  try {
    const { data } = await fetchUserProfile(id)
    profile.value = data
  } catch {
    profile.value = null
  }
}

async function loadNotes(id) {
  try {
    const { data } = await fetchNotesOfUser(id, 1)
    notes.value = data || []
  } catch {
    notes.value = []
  }
}

async function loadFollow(id) {
  try {
    const { data } = await isFollowing(id)
    followed.value = Boolean(data)
  } catch {
    followed.value = false
  }
}

async function onFollow() {
  if (!auth.isLogin) {
    toast.error('请先登录')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    await follow(user.value.id, !followed.value)
    followed.value = !followed.value
    toast.success(followed.value ? '已关注' : '已取消关注')
  } catch (e) {
    toast.error(e.message)
  }
}

async function switchTab(value) {
  tab.value = value
  if (value === 'common' && !commonFollows.value.length) {
    try {
      const { data } = await fetchCommonFollows(user.value.id)
      commonFollows.value = data || []
    } catch (e) {
      toast.error(e.message)
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="page page--plain up">
    <AppNavBar :title="user?.nickName || '个人主页'" />

    <div v-if="loading" class="loading">
      <div class="skeleton loading__hero" />
    </div>

    <template v-else-if="user">
      <header class="hero">
        <AppAvatar :src="user.icon" :name="user.nickName" :size="62" />
        <h1 class="hero__name">{{ user.nickName }}</h1>
        <p class="hero__city">
          <BaseIcon name="location" :size="12" />
          {{ profile?.city || '未知城市' }}
        </p>
        <p class="hero__intro clamp-2">
          {{ profile?.introduce || '这个人很懒，什么都没有留下' }}
        </p>

        <div class="hero__stats">
          <span><b>{{ profile?.followee ?? 0 }}</b> 关注</span>
          <span><b>{{ profile?.fans ?? 0 }}</b> 粉丝</span>
        </div>

        <button class="follow" :class="{ 'follow--on': followed }" @click="onFollow">
          {{ followed ? '已关注' : '关注 TA' }}
        </button>
      </header>

      <div class="tabs">
        <button class="tab" :class="{ 'tab--on': tab === 'notes' }" @click="switchTab('notes')">
          笔记 {{ notes.length ? `(${notes.length})` : '' }}
        </button>
        <button class="tab" :class="{ 'tab--on': tab === 'common' }" @click="switchTab('common')">
          共同关注
        </button>
      </div>

      <section v-if="tab === 'notes'" class="pane">
        <div v-if="notes.length" class="grid">
          <NoteCard v-for="n in notes" :key="n.id" :note="n" :show-author="false" />
        </div>
        <EmptyState v-else icon="edit" title="TA 还没有发布笔记" />
      </section>

      <section v-else class="pane">
        <div v-if="commonFollows.length" class="follows">
          <button
            v-for="u in commonFollows"
            :key="u.id"
            class="follow-item"
            @click="router.push(`/user/${u.id}`)"
          >
            <AppAvatar :src="u.icon" :name="u.nickName" :size="38" />
            <span class="follow-item__name">{{ u.nickName }}</span>
            <BaseIcon name="arrow-right" :size="15" />
          </button>
        </div>
        <EmptyState v-else icon="user" title="还没有共同关注" desc="关注同一位食客后，会在这里出现" />
      </section>
    </template>

    <EmptyState v-else icon="user" title="用户不存在" action-text="回首页" @action="router.replace('/')" />
  </div>
</template>

<style scoped>
.up {
  min-height: 100vh;
  background: var(--bg);
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 24px 26px;
  text-align: center;
}
.hero__name {
  margin-top: 12px;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: -0.01em;
}
.hero__city {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--ink-3);
}
.hero__intro {
  max-width: 300px;
  margin-top: 10px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ink-2);
}
.hero__stats {
  display: flex;
  gap: 22px;
  margin-top: 14px;
  font-size: 11.5px;
  color: var(--ink-3);
}
.hero__stats b {
  margin-right: 3px;
  font-family: var(--font-num);
  font-size: 15px;
  color: var(--ink);
}

.follow {
  width: 160px;
  height: 38px;
  margin-top: 16px;
  border-radius: var(--r-pill);
  background: var(--brand);
  color: #fff;
  font-size: 13.5px;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(206, 74, 38, 0.24);
  transition: all var(--dur) var(--ease);
}
.follow--on {
  background: var(--surface);
  color: var(--ink-3);
  box-shadow: inset 0 0 0 1px var(--line-strong);
}

.tabs {
  display: flex;
  gap: 22px;
  padding: 4px 18px 14px;
  border-bottom: 1px solid var(--line);
}
.tab {
  position: relative;
  font-size: 14px;
  color: var(--ink-3);
}
.tab--on {
  color: var(--ink);
  font-weight: 700;
}
.tab--on::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -15px;
  width: 16px;
  height: 2px;
  border-radius: 2px;
  background: var(--brand);
  transform: translateX(-50%);
}

.pane {
  padding: 14px 0 30px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 0 14px;
}

.follows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 14px;
}
.follow-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 11px 14px;
  border-radius: var(--r-md);
  background: var(--surface);
  box-shadow: var(--shadow-1);
  text-align: left;
}
.follow-item__name {
  flex: 1;
  font-size: 13.5px;
  font-weight: 600;
}
.follow-item :deep(.icon) {
  color: var(--ink-3);
}

.loading {
  padding: 0 14px;
}
.loading__hero {
  height: 200px;
  border-radius: var(--r-md);
}
</style>
