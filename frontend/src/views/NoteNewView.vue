<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseIcon from '@/components/BaseIcon.vue'
import AppNavBar from '@/components/AppNavBar.vue'
import EmptyState from '@/components/EmptyState.vue'
import { deleteImage, publishNote, searchMerchants, uploadImage } from '@/api'
import toast from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const title = ref('')
const content = ref('')
const photos = ref([]) // { name, url }
const selected = ref(null)
const pickerOpen = ref(false)
const keyword = ref('')
const results = ref([])
const searching = ref(false)
const uploading = ref(false)
const submitting = ref(false)
const fileInput = ref(null)

const canSubmit = computed(
  () => photos.value.length > 0 && title.value.trim().length > 0 && content.value.trim().length > 0
)

function pickFile() {
  fileInput.value?.click()
}

async function onFileChange(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  if (photos.value.length + files.length > 9) {
    toast.error('最多上传 9 张照片')
  }
  const room = 9 - photos.value.length
  const queue = files.slice(0, room)
  uploading.value = true
  for (const file of queue) {
    try {
      const { data } = await uploadImage(file)
      photos.value.push({ name: data, url: `/imgs${data}` })
    } catch (err) {
      toast.error(err.message || '上传失败')
    }
  }
  uploading.value = false
}

async function removePhoto(index) {
  const photo = photos.value[index]
  photos.value.splice(index, 1)
  if (photo?.name) {
    try {
      await deleteImage(photo.name)
    } catch {
      /* 删除失败不影响前端展示 */
    }
  }
}

let timer = null
function onKeyword() {
  clearTimeout(timer)
  const kw = keyword.value.trim()
  if (!kw) {
    results.value = []
    return
  }
  timer = setTimeout(async () => {
    searching.value = true
    try {
      const { data } = await searchMerchants({ name: kw, current: 1 })
      results.value = data || []
    } catch (e) {
      toast.error(e.message)
    } finally {
      searching.value = false
    }
  }, 260)
}

function chooseMerchant(m) {
  selected.value = { id: m.id, name: m.name }
  pickerOpen.value = false
}

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

async function submit() {
  if (submitting.value) return
  if (!photos.value.length) return toast.error('至少上传一张照片')
  if (!title.value.trim()) return toast.error('请填写标题')
  if (!content.value.trim()) return toast.error('写点内容吧')

  submitting.value = true
  try {
    await publishNote({
      title: title.value.trim(),
      content: escapeHtml(content.value.trim()).replace(/\n/g, '<br/>'),
      images: photos.value.map((p) => p.url).join(','),
      merchantId: selected.value?.id ?? null,
    })
    toast.success('发布成功')
    setTimeout(() => router.replace('/me'), 320)
  } catch (e) {
    toast.error(e.message)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (route.query.merchantId) {
    selected.value = {
      id: Number(route.query.merchantId),
      name: route.query.merchantName || '已选商户',
    }
  }
})
</script>

<template>
  <div class="page page--plain editor">
    <AppNavBar title="发布笔记">
      <template #right>
        <button
          class="publish"
          :class="{ 'publish--on': canSubmit }"
          :disabled="submitting"
          @click="submit"
        >
          {{ submitting ? '发布中' : '发布' }}
        </button>
      </template>
    </AppNavBar>

    <!-- 图片 -->
    <section class="photos">
      <div v-for="(p, i) in photos" :key="p.name" class="photo">
        <img :src="p.url" alt="已上传照片" />
        <button class="photo__del" aria-label="删除" @click="removePhoto(i)">
          <BaseIcon name="close" :size="11" :stroke="2.4" />
        </button>
        <span v-if="i === 0" class="photo__cover">封面</span>
      </div>

      <button v-if="photos.length < 9" class="photo photo--add" @click="pickFile">
        <BaseIcon name="camera" :size="22" :stroke="1.5" />
        <span>{{ uploading ? '上传中' : '添加照片' }}</span>
      </button>

      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        multiple
        hidden
        @change="onFileChange"
      />
    </section>

    <p class="photos__hint">第一张作为封面 · 最多 9 张</p>

    <!-- 文字 -->
    <section class="words">
      <input
        v-model="title"
        class="title-input"
        type="text"
        maxlength="40"
        placeholder="写个标题，更容易被看到"
      />
      <div class="words__divider" />
      <textarea
        v-model="content"
        class="content-input"
        maxlength="800"
        rows="7"
        placeholder="这家店值得去吗？说说环境、口味、价格……"
      />
      <div class="counter">{{ content.length }} / 800</div>
    </section>

    <!-- 关联商户 -->
    <button class="link-row" @click="pickerOpen = true">
      <span class="link-row__label">
        <BaseIcon name="location" :size="16" />
        关联商户
      </span>
      <span class="link-row__value" :class="{ 'link-row__value--empty': !selected }">
        {{ selected?.name || '选择你到店的商户' }}
      </span>
      <BaseIcon name="arrow-right" :size="15" />
    </button>

    <p class="tips">
      真实到店的体验最有价值。发布即表示你确认内容为本人原创。
    </p>

    <!-- 商户选择抽屉 -->
    <transition name="fade">
      <div v-if="pickerOpen" class="mask" @click="pickerOpen = false" />
    </transition>
    <transition name="pop">
      <div v-if="pickerOpen" class="sheet">
        <div class="sheet__head">
          <h3>选择商户</h3>
          <button class="sheet__close" @click="pickerOpen = false">
            <BaseIcon name="close" :size="17" />
          </button>
        </div>

        <div class="sheet__search">
          <BaseIcon name="search" :size="16" />
          <input
            v-model="keyword"
            type="search"
            placeholder="搜索商户名称"
            @input="onKeyword"
          />
        </div>

        <div class="sheet__body no-scrollbar">
          <p v-if="!keyword" class="sheet__hint">输入关键字搜索商户</p>
          <p v-else-if="searching" class="sheet__hint">搜索中…</p>
          <template v-else-if="results.length">
            <button
              v-for="m in results"
              :key="m.id"
              class="option"
              :class="{ 'option--active': selected?.id === m.id }"
              @click="chooseMerchant(m)"
            >
              <span class="option__name">{{ m.name }}</span>
              <span class="option__area">{{ m.area }}</span>
            </button>
          </template>
          <EmptyState v-else icon="search" title="没有找到这家商户" desc="换个关键词试试" />
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.editor {
  background: var(--surface);
  min-height: 100vh;
}

.publish {
  height: 30px;
  padding: 0 14px;
  border-radius: var(--r-pill);
  background: var(--line);
  color: var(--ink-3);
  font-size: 13px;
  font-weight: 600;
  transition: all var(--dur) var(--ease);
}
.publish--on {
  background: var(--brand);
  color: #fff;
  box-shadow: 0 4px 12px rgba(206, 74, 38, 0.26);
}

/* 图片 */
.photos {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 16px 18px 0;
}
.photo {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: var(--r-sm);
  background: var(--surface-2);
}
.photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.photo__del {
  position: absolute;
  top: 4px;
  right: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  background: rgba(23, 19, 16, 0.6);
  color: #fff;
}
.photo__cover {
  position: absolute;
  left: 0;
  bottom: 0;
  padding: 1px 6px;
  border-radius: 0 var(--r-xs) 0 var(--r-xs);
  background: rgba(23, 19, 16, 0.55);
  color: #fff;
  font-size: 9.5px;
  letter-spacing: 0.06em;
}
.photo--add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px dashed var(--line-strong);
  background: var(--surface-2);
  color: var(--ink-3);
  font-size: 10.5px;
}

.photos__hint {
  padding: 8px 18px 0;
  font-size: 11px;
  color: var(--ink-3);
}

/* 文字 */
.words {
  padding: 16px 18px 0;
}
.title-input {
  width: 100%;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.words__divider {
  height: 1px;
  margin: 14px 0;
  background: var(--line);
}
.content-input {
  width: 100%;
  font-size: 14.5px;
  line-height: 1.85;
  resize: none;
}
.counter {
  padding-top: 6px;
  font-family: var(--font-num);
  font-size: 11px;
  color: var(--ink-3);
  text-align: right;
}

/* 关联商户 */
.link-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: calc(100% - 36px);
  margin: 18px 18px 0;
  padding: 14px 14px;
  border-radius: var(--r-md);
  background: var(--surface-2);
  text-align: left;
}
.link-row__label {
  display: flex;
  flex: none;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  font-weight: 600;
}
.link-row__value {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  color: var(--ink-2);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.link-row__value--empty {
  color: var(--ink-3);
}

.tips {
  padding: 18px 20px 40px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--ink-3);
}

/* 抽屉 */
.mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(23, 19, 16, 0.42);
}
.sheet {
  position: fixed;
  bottom: 0;
  left: 50%;
  z-index: 85;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: var(--shell-w);
  max-height: 76vh;
  padding-bottom: var(--safe-b);
  transform: translateX(-50%);
  background: var(--surface);
  border-radius: var(--r-xl) var(--r-xl) 0 0;
  box-shadow: var(--shadow-3);
}
.sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 18px 12px;
}
.sheet__head h3 {
  font-size: 16px;
  font-weight: 700;
}
.sheet__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--surface-2);
  color: var(--ink-3);
}
.sheet__search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  margin: 0 18px 12px;
  padding: 0 12px;
  border-radius: var(--r-pill);
  background: var(--surface-2);
  color: var(--ink-3);
}
.sheet__search input {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--ink);
}
.sheet__body {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 18px;
}
.sheet__hint {
  padding: 34px 0;
  font-size: 12.5px;
  color: var(--ink-3);
  text-align: center;
}

.option {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 13px 12px;
  border-radius: var(--r-sm);
  text-align: left;
  transition: background-color var(--dur) var(--ease);
}
.option:active {
  background: var(--surface-2);
}
.option--active {
  background: var(--brand-soft);
}
.option__name {
  font-size: 13.5px;
  font-weight: 600;
}
.option__area {
  flex: none;
  font-size: 11.5px;
  color: var(--ink-3);
}
</style>
