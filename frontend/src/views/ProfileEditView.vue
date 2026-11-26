<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppNavBar from '@/components/AppNavBar.vue'
import AppAvatar from '@/components/AppAvatar.vue'
import BaseIcon from '@/components/BaseIcon.vue'
import { fetchUserProfile, updateProfile } from '@/api'
import { useAuthStore } from '@/store/auth'
import toast from '@/utils/toast'

const router = useRouter()
const auth = useAuthStore()

const form = ref({
  nickName: '',
  introduce: '',
  city: '',
  gender: null,
  birthday: '',
})
const saving = ref(false)
const loading = ref(true)

const CITIES = ['杭州', '上海', '北京', '广州', '深圳', '成都', '南京', '苏州']

onMounted(async () => {
  try {
    const user = await auth.refreshUser()
    const { data } = await fetchUserProfile(user.id)
    form.value = {
      nickName: user.nickName || '',
      icon: user.icon || '',
      introduce: data?.introduce || '',
      city: data?.city || '',
      gender: typeof data?.gender === 'boolean' ? data.gender : null,
      birthday: data?.birthday || '',
    }
  } catch (e) {
    toast.error(e.message)
  } finally {
    loading.value = false
  }
})

async function save() {
  if (saving.value) return
  if (!form.value.nickName.trim()) return toast.error('昵称不能为空')
  saving.value = true
  try {
    await updateProfile({
      nickName: form.value.nickName.trim(),
      icon: form.value.icon,
      introduce: form.value.introduce.trim(),
      city: form.value.city,
      gender: form.value.gender,
      birthday: form.value.birthday || null,
    })
    await auth.refreshUser()
    toast.success('已保存')
    setTimeout(() => router.back(), 300)
  } catch (e) {
    toast.error(e.message)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="page page--plain edit">
    <AppNavBar title="编辑资料" />

    <div v-if="loading" class="loading">
      <div v-for="i in 3" :key="i" class="skeleton loading__row" />
    </div>

    <template v-else>
      <section class="card block">
        <div class="avatar-row">
          <AppAvatar :src="form.icon" :name="form.nickName" :size="58" />
          <div class="avatar-row__info">
            <span class="avatar-row__title">头像</span>
            <span class="avatar-row__hint">头像暂不支持修改</span>
          </div>
        </div>
      </section>

      <section class="card block">
        <label class="field">
          <span class="field__label">昵称</span>
          <input v-model="form.nickName" maxlength="20" placeholder="给自己起个名字" />
        </label>

        <label class="field field--column">
          <span class="field__label">个人简介</span>
          <textarea
            v-model="form.introduce"
            maxlength="120"
            rows="3"
            placeholder="说说你的口味偏好，比如「嗜辣，爱吃苍蝇小馆」"
          />
          <span class="field__count">{{ form.introduce.length }} / 120</span>
        </label>

        <div class="field field--column">
          <span class="field__label">城市</span>
          <div class="chips">
            <button
              v-for="c in CITIES"
              :key="c"
              class="chip"
              :class="{ 'chip--on': form.city === c }"
              @click="form.city = form.city === c ? '' : c"
            >
              {{ c }}
            </button>
          </div>
        </div>

        <div class="field field--column">
          <span class="field__label">性别</span>
          <div class="chips">
            <button
              class="chip"
              :class="{ 'chip--on': form.gender === true }"
              @click="form.gender = form.gender === true ? null : true"
            >
              男
            </button>
            <button
              class="chip"
              :class="{ 'chip--on': form.gender === false }"
              @click="form.gender = form.gender === false ? null : false"
            >
              女
            </button>
          </div>
        </div>

        <label class="field">
          <span class="field__label">生日</span>
          <input v-model="form.birthday" type="date" class="field__date" />
        </label>
      </section>

      <div class="save-wrap">
        <button class="btn btn--primary btn--block" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
      </div>

      <p class="note">
        <BaseIcon name="check" :size="12" />
        资料会展示在你的个人主页，帮助别人更了解你的口味
      </p>
    </template>
  </div>
</template>

<style scoped>
.edit {
  min-height: 100vh;
  background: var(--bg);
}

.block {
  margin: 12px 14px 0;
  padding: 4px 16px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 0;
}
.avatar-row__info {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.avatar-row__title {
  font-size: 14px;
  font-weight: 600;
}
.avatar-row__hint {
  font-size: 11px;
  color: var(--ink-3);
}

.field {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 54px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}
.field:last-child {
  border-bottom: 0;
}
.field--column {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}
.field__label {
  flex: none;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ink-2);
}
.field input,
.field textarea {
  flex: 1;
  min-width: 0;
  font-size: 14.5px;
  line-height: 1.7;
  resize: none;
}
/* 纵向布局下 textarea 不能被 flex 拉伸 */
.field--column textarea {
  flex: none;
  min-height: 72px;
}
.field__date {
  text-align: right;
  color: var(--ink-2);
}
.field__count {
  font-family: var(--font-num);
  font-size: 11px;
  color: var(--ink-3);
  text-align: right;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  height: 30px;
  padding: 0 15px;
  border-radius: var(--r-pill);
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: 12.5px;
  transition: all var(--dur) var(--ease);
}
.chip--on {
  background: var(--ink);
  color: #f7efe4;
}

.save-wrap {
  padding: 24px 16px 0;
}

.note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 18px 24px 40px;
  font-size: 11px;
  color: var(--ink-3);
  text-align: center;
}

.loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px 14px;
}
.loading__row {
  height: 64px;
  border-radius: var(--r-md);
}
</style>
