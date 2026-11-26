/** 图片字段是逗号分隔的字符串，统一拆成数组 */
export function splitImages(images) {
  if (!images) return []
  if (Array.isArray(images)) return images.filter(Boolean)
  return images.split(',').filter(Boolean)
}

/** 首个图片，用于列表缩略图 */
export function firstImage(images) {
  return splitImages(images)[0] || ''
}

/** 后端金额以「分」存储，这里转成元并保留两位 */
export function money(cents) {
  if (cents === null || cents === undefined || cents === '') return '--'
  const value = Number(cents) / 100
  if (Number.isNaN(value)) return '--'
  return value.toFixed(value % 1 === 0 ? 0 : 1)
}

/**
 * 单位本身就是「元」的字段（如商户人均 avgPrice），不能除以 100。
 * 注意：biz_merchant.avg_price 存的是元，biz_benefit.pay_value 存的是分，两者单位不同。
 */
export function yuan(value) {
  if (value === null || value === undefined || value === '') return '--'
  const num = Number(value)
  if (Number.isNaN(num)) return '--'
  return Number.isInteger(num) ? String(num) : num.toFixed(1)
}

/** 评分字段是 0~50 的整数，转成 0~5 星 */
export function scoreToStars(score) {
  const value = Number(score || 0) / 10
  return Math.round(value * 10) / 10
}

/** 距离展示 */
export function distanceText(meters) {
  if (meters === null || meters === undefined) return ''
  const m = Number(meters)
  if (!m && m !== 0) return ''
  if (m < 1000) return `${Math.round(m)}m`
  return `${(m / 1000).toFixed(1)}km`
}

function parseDate(input) {
  if (!input) return null
  if (input instanceof Date) return input
  // 兼容 "2026-11-26T12:00:00" 与 "2026-11-26 12:00:00"
  const normalized = String(input).replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 相对时间：刚刚 / 12分钟前 / 3小时前 / 昨天 / 11月20日 */
export function relativeTime(input) {
  const date = parseDate(input)
  if (!date) return ''
  const diff = Date.now() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)}分钟前`
  if (diff < day) return `${Math.floor(diff / hour)}小时前`
  if (diff < 2 * day) return '昨天'
  if (diff < 30 * day) return `${date.getMonth() + 1}月${date.getDate()}日`
  const y = date.getFullYear()
  const now = new Date().getFullYear()
  const md = `${date.getMonth() + 1}月${date.getDate()}日`
  return y === now ? md : `${y}年${md}`
}

/** 券的有效时段：11月26日 10:00 ~ 12:00 */
export function timeRange(begin, end) {
  const b = parseDate(begin)
  const e = parseDate(end)
  if (!b || !e) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${b.getMonth() + 1}月${b.getDate()}日 ${pad(b.getHours())}:${pad(
    b.getMinutes()
  )} ~ ${pad(e.getHours())}:${pad(e.getMinutes())}`
}

/** 秒杀状态：not-start / ended / running */
export function seckillStatus(begin, end) {
  const now = Date.now()
  const b = parseDate(begin)?.getTime() ?? 0
  const e = parseDate(end)?.getTime() ?? 0
  if (now < b) return 'not-start'
  if (now > e) return 'ended'
  return 'running'
}

/** 折扣：付 80 得 100 → 8.0 折 */
export function discountText(payValue, actualValue) {
  const pay = Number(payValue)
  const actual = Number(actualValue)
  if (!pay || !actual) return ''
  return `${((pay / actual) * 10).toFixed(1)}折`
}

/** 图片相对路径补前缀，绝对地址原样返回 */
export function imageUrl(path) {
  if (!path) return ''
  if (/^(https?:)?\/\//.test(path) || path.startsWith('data:')) return path
  if (path.startsWith('/imgs')) return path
  return `/imgs${path.startsWith('/') ? '' : '/'}${path}`
}

/** 数据里的换行是字面量 \n，展示前转成可读分隔 */
export function plainText(value) {
  if (!value) return ''
  return String(value)
    .replace(/\\n/g, '·')
    .replace(/\n/g, '·')
    // 原始数据里存在孤立反斜杠（历史录入笔误），一并归一
    .replace(/\\/g, '·')
    .replace(/·+/g, ' · ')
    .trim()
}
