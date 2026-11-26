<script setup>
import { computed } from 'vue'
import { imageUrl } from '@/utils/format'

const props = defineProps({
  src: { type: String, default: '' },
  name: { type: String, default: '' },
  size: { type: [Number, String], default: 36 },
})

const url = computed(() => imageUrl(props.src))
const initial = computed(() => (props.name || '食').trim().slice(0, 1))
const style = computed(() => ({
  width: typeof props.size === 'number' ? `${props.size}px` : props.size,
  height: typeof props.size === 'number' ? `${props.size}px` : props.size,
  fontSize: `${Number.parseInt(props.size, 10) * 0.4}px`,
}))
</script>

<template>
  <div class="avatar" :style="style">
    <img v-if="url" :src="url" :alt="name" loading="lazy" />
    <span v-else class="avatar__initial">{{ initial }}</span>
  </div>
</template>

<style scoped>
.avatar {
  position: relative;
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-weight: 600;
  user-select: none;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar__initial {
  transform: translateY(-0.02em);
}
</style>
