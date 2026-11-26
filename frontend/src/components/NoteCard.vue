<script setup>
import { computed } from 'vue'
import BaseIcon from './BaseIcon.vue'
import AppAvatar from './AppAvatar.vue'
import { firstImage, relativeTime } from '@/utils/format'

const props = defineProps({
  note: { type: Object, required: true },
  variant: { type: String, default: 'grid' }, // grid | row
  // 个人主页里所有卡片属于同一个人，作者信息冗余，可关闭
  showAuthor: { type: Boolean, default: true },
})

const cover = computed(() => firstImage(props.note.images))
const liked = computed(() => props.note.liked ?? 0)
</script>

<template>
  <router-link
    :to="`/note/${note.id}`"
    class="note tap"
    :class="`note--${variant}`"
  >
    <div class="note__cover">
      <img v-if="cover" :src="cover" :alt="note.title" loading="lazy" />
      <div v-else class="note__cover-fallback" />
      <span v-if="note.isLike" class="note__badge">已赞</span>
    </div>

    <div class="note__body">
      <p class="note__title clamp-2">{{ note.title || '无标题笔记' }}</p>
      <div class="note__foot">
        <div v-if="showAuthor" class="note__author">
          <AppAvatar :src="note.icon" :name="note.name" :size="18" />
          <span class="note__name clamp-1">{{ note.name || '食客' }}</span>
        </div>
        <span v-else class="note__time-inline">{{ relativeTime(note.createTime) }}</span>
        <span class="note__like">
          <BaseIcon name="heart" :size="13" :filled="!!note.isLike" />
          {{ liked }}
        </span>
      </div>
      <span v-if="variant === 'row' && note.createTime" class="note__time">
        {{ relativeTime(note.createTime) }}
      </span>
    </div>
  </router-link>
</template>

<style scoped>
.note {
  display: block;
  overflow: hidden;
  background: var(--surface);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-1);
}

.note__cover {
  position: relative;
  overflow: hidden;
  background: #ede5d9;
}
.note__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s var(--ease);
}
.note:active .note__cover img {
  transform: scale(1.03);
}
.note__cover-fallback {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #efe7db, #e3d8c8);
}
.note__badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 7px;
  border-radius: var(--r-pill);
  background: rgba(206, 74, 38, 0.92);
  color: #fff;
  font-size: 10px;
  letter-spacing: 0.05em;
}

.note__body {
  padding: 10px 11px 11px;
}
.note__title {
  font-size: 13.5px;
  font-weight: 600;
  line-height: 1.45;
  letter-spacing: -0.005em;
}

.note__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: 9px;
}
.note__author {
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}
.note__name {
  font-size: 11.5px;
  color: var(--ink-3);
}
.note__time-inline {
  font-size: 11px;
  color: var(--ink-3);
}
.note__like {
  display: flex;
  align-items: center;
  gap: 3px;
  flex: none;
  color: var(--ink-3);
  font-family: var(--font-num);
  font-size: 11.5px;
}
.note__time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--ink-3);
}

/* 网格卡片：3:4 竖图 */
.note--grid .note__cover {
  aspect-ratio: 3 / 4;
}

/* 流式卡片：横图 16:9 */
.note--row .note__cover {
  aspect-ratio: 16 / 9;
}
.note--row .note__title {
  font-size: 15px;
}
</style>
