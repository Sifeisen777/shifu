<script setup>
import { useRouter } from 'vue-router'
import BaseIcon from './BaseIcon.vue'

const props = defineProps({
  title: { type: String, default: '' },
  showBack: { type: Boolean, default: true },
  transparent: { type: Boolean, default: false },
  fixed: { type: Boolean, default: false },
})

const router = useRouter()

function goBack() {
  if (window.history.state?.back) router.back()
  else router.replace('/')
}
</script>

<template>
  <header class="navbar" :class="{ 'navbar--transparent': transparent, 'navbar--fixed': fixed }">
    <button v-if="showBack" class="navbar__btn" aria-label="返回" @click="goBack">
      <BaseIcon name="back" :size="21" />
    </button>
    <div v-else class="navbar__placeholder" />

    <h1 class="navbar__title">{{ title }}</h1>

    <div class="navbar__right">
      <slot name="right" />
    </div>
  </header>
</template>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  height: var(--navbar-h);
  padding: 0 6px;
  background: rgba(245, 240, 232, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid transparent;
  transition: border-color var(--dur) var(--ease);
}
.navbar--fixed {
  position: fixed;
  left: 50%;
  width: 100%;
  max-width: var(--shell-w);
  transform: translateX(-50%);
}
.navbar--transparent {
  background: transparent;
  backdrop-filter: none;
}

.navbar__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  color: var(--ink);
  transition: background-color var(--dur) var(--ease);
}
.navbar__btn:active {
  background: rgba(0, 0, 0, 0.05);
}

.navbar__placeholder {
  width: 40px;
}

.navbar__title {
  flex: 1;
  overflow: hidden;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.01em;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.navbar__right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 40px;
  padding-right: 6px;
}
</style>
