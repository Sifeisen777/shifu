<script setup>
import { computed } from 'vue'
import BaseIcon from './BaseIcon.vue'
import ScoreStars from './ScoreStars.vue'
import { distanceText, firstImage, imageUrl, yuan } from '@/utils/format'

const props = defineProps({
  merchant: { type: Object, required: true },
  showDistance: { type: Boolean, default: true },
})

const cover = computed(() => imageUrl(firstImage(props.merchant.images)))
const dist = computed(() => distanceText(props.merchant.distance))
</script>

<template>
  <router-link :to="`/merchant/${merchant.id}`" class="mc card tap">
    <div class="mc__cover">
      <img v-if="cover" :src="cover" :alt="merchant.name" loading="lazy" />
      <div v-else class="mc__cover-fallback" />
    </div>

    <div class="mc__body">
      <h3 class="mc__name clamp-1">{{ merchant.name }}</h3>

      <div class="mc__rate">
        <ScoreStars :score="merchant.score" :size="12" :count="merchant.comments" />
      </div>

      <div class="mc__meta">
        <span v-if="merchant.area" class="mc__chip">{{ merchant.area }}</span>
        <span v-if="showDistance && dist" class="mc__dist">
          <BaseIcon name="location" :size="12" />
          {{ dist }}
        </span>
      </div>

      <p class="mc__address clamp-1">{{ merchant.address }}</p>

      <div class="mc__price">
        <span class="mc__price-num">¥{{ yuan(merchant.avgPrice) }}</span>
        <span class="mc__price-unit">/人</span>
      </div>
    </div>
  </router-link>
</template>

<style scoped>
.mc {
  display: flex;
  gap: 12px;
  padding: 12px;
}

.mc__cover {
  flex: none;
  width: 92px;
  height: 92px;
  overflow: hidden;
  border-radius: var(--r-sm);
  background: #ede5d9;
}
.mc__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.mc__cover-fallback {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #efe7db, #e3d8c8);
}

.mc__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
}

.mc__name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.mc__rate {
  margin-top: -1px;
}

.mc__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--ink-3);
}
.mc__chip {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--surface-2);
  color: var(--ink-2);
}
.mc__dist {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.mc__address {
  font-size: 11.5px;
  color: var(--ink-3);
}

.mc__price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-top: auto;
}
.mc__price-num {
  font-family: var(--font-num);
  font-size: 15px;
  font-weight: 700;
  color: var(--brand);
}
.mc__price-unit {
  font-size: 11px;
  color: var(--ink-3);
}
</style>
