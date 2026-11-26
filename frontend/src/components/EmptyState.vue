<script setup>
import BaseIcon from './BaseIcon.vue'

defineProps({
  icon: { type: String, default: 'compass' },
  title: { type: String, default: '这里还什么都没有' },
  desc: { type: String, default: '' },
  actionText: { type: String, default: '' },
})
defineEmits(['action'])
</script>

<template>
  <div class="empty">
    <div class="empty__icon">
      <BaseIcon :name="icon" :size="26" :stroke="1.4" />
    </div>
    <p class="empty__title">{{ title }}</p>
    <p v-if="desc" class="empty__desc">{{ desc }}</p>
    <button v-if="actionText" class="btn btn--soft empty__action" @click="$emit('action')">
      {{ actionText }}
    </button>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 32px;
  text-align: center;
}
.empty__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 62px;
  margin-bottom: 14px;
  border-radius: 50%;
  background: var(--surface);
  color: var(--ink-3);
  box-shadow: var(--shadow-1);
}
.empty__title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--ink-2);
}
.empty__desc {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--ink-3);
  line-height: 1.7;
}
.empty__action {
  height: 38px;
  margin-top: 18px;
  padding: 0 22px;
  font-size: 13.5px;
}
</style>
