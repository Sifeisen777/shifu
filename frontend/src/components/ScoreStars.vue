<script setup>
import { computed } from 'vue'
import BaseIcon from './BaseIcon.vue'

const props = defineProps({
  score: { type: [Number, String], default: 0 }, // 后端为 0~50
  size: { type: Number, default: 13 },
  showValue: { type: Boolean, default: true },
  count: { type: [Number, String], default: null },
})

const stars = computed(() => {
  const value = Number(props.score || 0) / 10
  return Math.min(5, Math.max(0, value))
})
const percent = computed(() => (stars.value / 5) * 100)
const label = computed(() => stars.value.toFixed(1))
</script>

<template>
  <div class="rating">
    <div class="rating__stars" :style="{ '--star-size': `${size}px` }">
      <div class="rating__layer">
        <BaseIcon v-for="i in 5" :key="`b${i}`" name="star" :size="size" filled />
      </div>
      <div class="rating__layer rating__layer--fg" :style="{ width: `${percent}%` }">
        <BaseIcon v-for="i in 5" :key="`f${i}`" name="star" :size="size" filled />
      </div>
    </div>
    <span v-if="showValue" class="rating__value">{{ label }}</span>
    <span v-if="count !== null" class="rating__count">{{ count }} 条</span>
  </div>
</template>

<style scoped>
.rating {
  display: flex;
  align-items: center;
  gap: 6px;
}
.rating__stars {
  position: relative;
  display: flex;
  width: calc(var(--star-size) * 5 + 8px);
  height: calc(var(--star-size) + 2px);
  overflow: hidden;
}
.rating__layer {
  display: flex;
  flex: none;
  gap: 2px;
  color: #ddd3c4;
}
.rating__layer--fg {
  position: absolute;
  top: 0;
  left: 0;
  overflow: hidden;
  color: var(--gold);
  white-space: nowrap;
}
.rating__value {
  font-family: var(--font-num);
  font-size: 12.5px;
  font-weight: 700;
  color: var(--gold);
}
.rating__count {
  font-size: 11.5px;
  color: var(--ink-3);
}
</style>
