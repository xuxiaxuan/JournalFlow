import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import ExploreView from './ExploreView.vue'
import { normalizeData } from '../data'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api', () => ({ default: api }))
let app
afterEach(() => { app?.unmount(); document.body.innerHTML = '' })

describe('published list page', () => {
  it('keeps search results visible when recommendations fail', async () => {
    api.get.mockImplementation(async url => {
      if (url === '/published/recommend') throw new Error('推荐失败')
      if (url === '/reference/categories') return [{ id: 1, name: '计算机' }]
      return { total: 1, list: [{ id: 1, title: '仍可阅读的论文' }] }
    })
    const root = document.createElement('div'); document.body.appendChild(root)
    app = createApp(ExploreView); app.component('RouterLink', { template: '<a><slot /></a>' }); app.mount(root)
    await new Promise(resolve => setTimeout(resolve, 0)); await nextTick()
    expect(root.querySelector('.article-card').textContent).toContain('仍可阅读的论文')
    expect(root.querySelector('[role=status]').textContent).toContain('仍可检索')
    expect(root.querySelector('select').textContent).toContain('计算机')
  })
  it('renders database list values and requests the next page', async () => {
    api.get.mockImplementation(async (url, options) => {
      if (url === '/reference/categories') return []
      if (url === '/published/recommend') return []
      return normalizeData({ total: 13, list: [{ id: options.params.page, title: '研究论文', abstract_: '实际摘要', category_name: '人工智能', published_at: '2026-10-03', view_sum: 42 }] })
    })
    const root = document.createElement('div'); document.body.appendChild(root)
    app = createApp(ExploreView)
    app.component('RouterLink', { template: '<a><slot /></a>' })
    app.mount(root)
    await new Promise(resolve => setTimeout(resolve, 0)); await nextTick()
    expect(root.textContent).toContain('实际摘要')
    expect(root.textContent).toContain('人工智能')
    expect(root.textContent).toContain('2026-10-03')
    expect(root.textContent).toContain('42')
    const next = [...root.querySelectorAll('button')].find(button => button.textContent === '下一页')
    next.click()
    await new Promise(resolve => setTimeout(resolve, 0)); await nextTick()
    expect(api.get).toHaveBeenCalledWith('/published', expect.objectContaining({ params: expect.objectContaining({ page: 2 }) }))
    expect(next.disabled).toBe(true)
  })
})
