<script setup>
/**
 * 线性图标集：统一 24x24 视框、1.7 描边、圆角端点。
 * 全部内置为路径，不引入图标库，保证首屏体积与视觉一致性。
 */
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 20 },
  filled: { type: Boolean, default: false },
  stroke: { type: [Number, String], default: 1.7 },
})

const ICONS = {
  search: ['M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14', 'M16.2 16.2 20 20'],
  back: ['M15 5 8 12l7 7'],
  'arrow-right': ['M9 5l7 7-7 7'],
  'chevron-down': ['M6 9.5 12 15.5 18 9.5'],
  home: ['M4 10.6 12 4l8 6.6V19a1 1 0 0 1-1 1h-4.6v-5.4h-4.8V20H5a1 1 0 0 1-1-1z'],
  compass: [
    'M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17',
    'M15.6 8.4 13.7 13.7 8.4 15.6 10.3 10.3z',
  ],
  plus: ['M12 5v14', 'M5 12h14'],
  send: ['M12 19.2V5.2', 'M6.2 10.8 12 5l5.8 5.8'],
  message: [
    'M20 11.6a7.6 7.6 0 0 1-11 6.8L4 20l1.5-4.8A7.6 7.6 0 1 1 20 11.6',
  ],
  user: [
    'M12 4.2a3.7 3.7 0 1 0 0 7.4 3.7 3.7 0 0 0 0-7.4',
    'M4.6 20c0-3.6 3.4-5.7 7.4-5.7s7.4 2.1 7.4 5.7',
  ],
  heart: [
    'M12 20.2S4.2 15.4 4.2 10.4A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.8 3c0 5-7.8 9.8-7.8 9.8',
  ],
  chat: ['M4.5 6.5h15v9.4H9.6L5 19.6V16h-.5z'],
  location: [
    'M12 20.8s6.4-6 6.4-10.8a6.4 6.4 0 1 0-12.8 0C5.6 14.8 12 20.8 12 20.8',
    'M12 7.6a2.4 2.4 0 1 0 0 4.8 2.4 2.4 0 0 0 0-4.8',
  ],
  clock: ['M12 3.6a8.4 8.4 0 1 0 0 16.8 8.4 8.4 0 0 0 0-16.8', 'M12 7.6V12l3 1.9'],
  camera: [
    'M4 8.2h2.8L8.6 5.6h6.8l1.8 2.6H20a1 1 0 0 1 1 1v8.6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.2a1 1 0 0 1 1-1',
    'M12 9.8a3.3 3.3 0 1 0 0 6.6 3.3 3.3 0 0 0 0-6.6',
  ],
  close: ['M6.5 6.5l11 11', 'M17.5 6.5l-11 11'],
  star: [
    'M12 4.4l2.3 4.8 5.1.7-3.7 3.6.9 5.2-4.6-2.5-4.6 2.5.9-5.2L4.6 9.9l5.1-.7z',
  ],
  ticket: [
    'M4.2 9.2a2 2 0 0 1 2-2h11.6a2 2 0 0 1 2 2 2.1 2.1 0 0 0 0 4.2 2 2 0 0 1-2 2H6.2a2 2 0 0 1-2-2 2.1 2.1 0 0 0 0-4.2',
    'M13.6 8.4v1.6',
    'M13.6 13.6V15',
  ],
  edit: ['M5 19h3.8L19 8.8 15.2 5 5 15.2z'],
  check: ['M5.5 12.8l4.2 4.2L18.6 8'],
  logout: ['M15 4.8h3.2a1 1 0 0 1 1 1v12.4a1 1 0 0 1-1 1H15', 'M10 8l-4 4 4 4', 'M6 12h8.6'],
  share: ['M12 16.2V4.8', 'M8.2 8.6 12 4.8l3.8 3.8', 'M5 13.8v5.4a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5.4'],
  calendar: [
    'M5 6.4h14a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1',
    'M8.2 3.8V7',
    'M15.8 3.8V7',
    'M4 10.6h16',
  ],
  fire: [
    'M12 20.4c3.5 0 6-2.3 6-5.4 0-4-4.2-5.4-4.2-9.6-2 1-3 2.8-3 4.4 0 1.2-.6 1.9-1.4 1.9-1 0-1.6-.9-1.6-2.2-1.2 1.2-1.8 3-1.8 5.1 0 3.3 2.5 5.8 6 5.8',
  ],
  grid: ['M4.4 4.4h6v6h-6z', 'M13.6 4.4h6v6h-6z', 'M4.4 13.6h6v6h-6z', 'M13.6 13.6h6v6h-6z'],
}

const paths = computed(() => ICONS[props.name] || [])
const isFilled = computed(() => props.filled)
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    :fill="isFilled ? 'currentColor' : 'none'"
    stroke="currentColor"
    :stroke-width="isFilled ? 1.2 : stroke"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="icon"
  >
    <path v-for="(d, i) in paths" :key="i" :d="d" />
  </svg>
</template>

<style scoped>
.icon {
  display: block;
  flex: none;
}
</style>
