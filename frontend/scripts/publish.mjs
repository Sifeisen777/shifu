import { cp, mkdir, readdir, rm, stat } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 把 vite 构建产物发布到 nginx 静态目录。
 *
 * 默认目标：../../nginx-1.18.0/html/shifu
 * 可通过环境变量覆盖：SHIFU_WEB_ROOT=D:/path/to/html/shifu
 *
 * 会清理旧版前端留下的 html 页面与 css/js 目录，但保留 imgs 静态资源。
 */

const here = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(here, '..')
const dist = path.join(projectRoot, 'dist')

const target = process.env.SHIFU_WEB_ROOT
  ? path.resolve(process.env.SHIFU_WEB_ROOT)
  : path.resolve(projectRoot, '..', '..', 'nginx-1.18.0', 'html', 'shifu')

/** 旧版前端遗留文件，发布时清掉，避免和新构建共存 */
const LEGACY_FILES = [
  'index.html',
  'shop-list.html',
  'shop-detail.html',
  'blog-detail.html',
  'blog-edit.html',
  'info.html',
  'info-edit.html',
  'other-info.html',
  'login.html',
  'login2.html',
]
const LEGACY_DIRS = ['css', 'js']

async function ensureDist() {
  try {
    await stat(path.join(dist, 'index.html'))
  } catch {
    console.error('[x] 未找到构建产物，请先执行 npm run build')
    process.exit(1)
  }
}

async function ensureTarget() {
  try {
    const info = await stat(target)
    if (!info.isDirectory()) throw new Error('not a directory')
  } catch {
    console.error(`[x] 目标目录不存在：${target}`)
    console.error('    请先确认 nginx 的静态目录，或用 SHIFU_WEB_ROOT 指定')
    process.exit(1)
  }
}

async function main() {
  await ensureDist()
  await ensureTarget()

  console.log(`构建产物：${dist}`)
  console.log(`发布目标：${target}`)

  // 清理旧版页面与资源目录
  let removed = 0
  for (const name of LEGACY_FILES) {
    try {
      await rm(path.join(target, name))
      removed += 1
    } catch {
      /* 不存在就跳过 */
    }
  }
  for (const dir of LEGACY_DIRS) {
    try {
      await rm(path.join(target, dir), { recursive: true, force: true })
      removed += 1
    } catch {
      /* 忽略 */
    }
  }
  if (removed) console.log(`已清理旧版文件 ${removed} 项`)

  // 复制新产物
  const entries = await readdir(dist, { withFileTypes: true })
  await mkdir(target, { recursive: true })
  for (const entry of entries) {
    const from = path.join(dist, entry.name)
    const to = path.join(target, entry.name)
    await rm(to, { recursive: true, force: true })
    await cp(from, to, { recursive: true })
  }

  console.log(`[√] 已发布 ${entries.length} 项（index.html + assets）`)
  console.log('    刷新浏览器时如果仍是旧页面，请 Ctrl+F5 强制刷新')
}

main()
