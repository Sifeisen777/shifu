<script setup>
import { computed } from 'vue'
import { discountText, money, plainText, seckillStatus, timeRange } from '@/utils/format'

const props = defineProps({
  benefit: { type: Object, required: true },
})
defineEmits(['seckill'])

const isSeckill = computed(() => Number(props.benefit.type) === 1)
const status = computed(() => seckillStatus(props.benefit.beginTime, props.benefit.endTime))
const stock = computed(() => Number(props.benefit.stock ?? 0))
const soldOut = computed(() => isSeckill.value && stock.value < 1)

const buttonText = computed(() => {
  if (!isSeckill.value) return '到店购买'
  if (status.value === 'not-start') return '即将开始'
  if (status.value === 'ended') return '已结束'
  if (soldOut.value) return '已抢光'
  return '限时抢购'
})

const disabled = computed(
  () => isSeckill.value && (status.value !== 'running' || soldOut.value)
)

const statusHint = computed(() => {
  if (!isSeckill.value) return ''
  if (status.value === 'not-start') return '未开始'
  if (status.value === 'ended') return '已结束'
  return `剩 ${stock.value} 张`
})
</script>

<template>
  <div class="bc" :class="{ 'bc--off': disabled }">
    <div class="bc__left">
      <div class="bc__title">{{ benefit.title }}</div>
      <div v-if="benefit.subTitle" class="bc__sub clamp-1">{{ benefit.subTitle }}</div>

      <div class="bc__price">
        <span class="bc__cur">¥</span>
        <span class="bc__num">{{ money(benefit.payValue) }}</span>
        <span class="bc__origin">¥{{ money(benefit.actualValue) }}</span>
        <span v-if="isSeckill" class="bc__discount">
          {{ discountText(benefit.payValue, benefit.actualValue) }}
        </span>
      </div>

      <div v-if="isSeckill" class="bc__time">{{ timeRange(benefit.beginTime, benefit.endTime) }}</div>
      <div v-else-if="benefit.rules" class="bc__rule clamp-1">{{ plainText(benefit.rules) }}</div>
    </div>

    <div class="bc__divider">
      <span class="bc__notch bc__notch--top" />
      <span class="bc__notch bc__notch--bottom" />
    </div>

    <div class="bc__right">
      <span v-if="isSeckill" class="bc__hint" :class="{ 'bc__hint--off': disabled }">
        {{ statusHint }}
      </span>
      <button
        class="bc__btn"
        :disabled="disabled"
        @click="$emit('seckill', benefit)"
      >
        {{ buttonText }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.bc {
  display: flex;
  align-items: stretch;
  overflow: hidden;
  background: linear-gradient(180deg, #fffdfa, #fff8f4);
  border-radius: var(--r-md);
  box-shadow: inset 0 0 0 1px var(--brand-line), var(--shadow-1);
}

.bc__left {
  flex: 1;
  min-width: 0;
  padding: 14px 4px 14px 14px;
}

.bc__title {
  font-size: 14.5px;
  font-weight: 700;
  letter-spacing: -0.005em;
}
.bc__sub {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-3);
}

.bc__price {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 8px;
}
.bc__cur {
  font-size: 12px;
  font-weight: 700;
  color: var(--brand);
}
.bc__num {
  font-family: var(--font-num);
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
  color: var(--brand);
  letter-spacing: -0.02em;
}
.bc__origin {
  font-size: 11.5px;
  color: var(--ink-3);
  text-decoration: line-through;
}
.bc__discount {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-size: 10.5px;
  font-weight: 700;
}

.bc__time,
.bc__rule {
  margin-top: 6px;
  font-size: 11px;
  color: var(--ink-3);
}

/* 票券分隔与凹口 */
.bc__divider {
  position: relative;
  width: 1px;
  margin: 10px 0;
  background-image: linear-gradient(
    to bottom,
    var(--brand-line) 0 5px,
    transparent 5px 10px
  );
  background-size: 1px 10px;
  background-repeat: repeat-y;
}
.bc__notch {
  position: absolute;
  left: -6px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--bg);
  box-shadow: inset 0 0 0 1px var(--brand-line);
}
.bc__notch--top {
  top: -16px;
}
.bc__notch--bottom {
  bottom: -16px;
}

.bc__right {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 96px;
  padding: 12px 10px;
}

.bc__hint {
  font-size: 10.5px;
  color: var(--brand);
}
.bc__hint--off {
  color: var(--ink-3);
}

.bc__btn {
  width: 100%;
  height: 32px;
  border-radius: var(--r-pill);
  background: var(--brand);
  color: #fff;
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: 0.02em;
  transition: transform 0.16s var(--ease), background-color var(--dur) var(--ease);
}
.bc__btn:active:not(:disabled) {
  transform: scale(0.95);
}
.bc__btn:disabled {
  background: #e6ddd2;
  color: #a89e93;
  cursor: not-allowed;
}

.bc--off {
  box-shadow: inset 0 0 0 1px var(--line), var(--shadow-1);
}
</style>
