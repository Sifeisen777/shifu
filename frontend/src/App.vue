<script setup>
import { toasts } from '@/utils/toast'
</script>

<template>
  <div class="shell">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>

    <div class="toaster" role="status" aria-live="polite">
      <transition-group name="toast">
        <div v-for="t in toasts" :key="t.id" class="toast" :class="`toast--${t.type}`">
          {{ t.message }}
        </div>
      </transition-group>
    </div>
  </div>
</template>

<style scoped>
.toaster {
  position: fixed;
  left: 50%;
  top: 42%;
  z-index: 999;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: min(80vw, 320px);
  transform: translate(-50%, -50%);
  pointer-events: none;
}
.toast {
  max-width: 100%;
  padding: 10px 18px;
  border-radius: var(--r-pill);
  background: rgba(23, 19, 16, 0.9);
  color: #fffdfa;
  font-size: 13.5px;
  line-height: 1.5;
  text-align: center;
  backdrop-filter: blur(8px);
  box-shadow: var(--shadow-2);
}
.toast--success {
  background: rgba(38, 92, 60, 0.94);
}
.toast--error {
  background: rgba(160, 40, 28, 0.94);
}
.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s var(--ease), transform 0.24s var(--ease);
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.96);
}
</style>
