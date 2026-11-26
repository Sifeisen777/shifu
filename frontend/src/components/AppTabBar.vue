<script setup>
import BaseIcon from './BaseIcon.vue'

defineProps({
  active: { type: String, default: 'home' },
})

const items = [
  { key: 'home', label: '首页', icon: 'home', to: '/' },
  { key: 'discover', label: '发现', icon: 'compass', to: '/merchants' },
  { key: 'publish', label: '发布', icon: 'plus', to: '/note/new' },
  { key: 'me', label: '我的', icon: 'user', to: '/me' },
]
</script>

<template>
  <nav class="tabbar">
    <router-link
      v-for="item in items"
      :key="item.key"
      :to="item.to"
      class="tab"
      :class="{ 'tab--active': active === item.key, 'tab--publish': item.key === 'publish' }"
    >
      <span class="tab__icon">
        <BaseIcon :name="item.icon" :size="22" :stroke="item.key === 'publish' ? 2.1 : 1.7" />
      </span>
      <span class="tab__label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<style scoped>
.tabbar {
  position: fixed;
  bottom: 0;
  left: 50%;
  z-index: 60;
  display: flex;
  align-items: stretch;
  width: 100%;
  max-width: var(--shell-w);
  height: calc(var(--tabbar-h) + var(--safe-b));
  padding-bottom: var(--safe-b);
  transform: translateX(-50%);
  background: rgba(255, 253, 250, 0.94);
  backdrop-filter: blur(14px);
  border-top: 1px solid var(--line);
}

.tab {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--ink-3);
  transition: color var(--dur) var(--ease);
}

.tab__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  transition: transform var(--dur) var(--ease);
}

.tab__label {
  font-size: 10.5px;
  letter-spacing: 0.06em;
}

.tab--active {
  color: var(--brand);
}
.tab--active .tab__icon {
  transform: translateY(-1px);
}

.tab--publish .tab__icon {
  width: 38px;
  height: 26px;
  border-radius: var(--r-pill);
  background: var(--brand);
  color: var(--ink-inverse);
  box-shadow: 0 4px 12px rgba(206, 74, 38, 0.32);
}
.tab--publish .tab__label {
  color: var(--ink-3);
}
</style>
