import { afterEach, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import ResearchTools from './ResearchTools.vue'
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../api', () => ({ default: api }))
let app, root
const flush = async () => { await new Promise(resolve => setTimeout(resolve, 0)); await nextTick() }
const versions = [{ id: 1, versionNo: 1 }, { id: 2, versionNo: 2 }]
async function mount(props = {}) {
  root = document.createElement('div'); document.body.appendChild(root)
  app = createApp(ResearchTools, { manuscriptId: 1, versions, canRespond: true, ...props }); app.mount(root); await flush()
}
afterEach(() => { app?.unmount(); document.body.innerHTML = ''; vi.resetAllMocks() })
it('saves one response without discarding another unsaved response', async () => {
  const rows = [0, 1].map(index => ({ reportId: 8, issueIndex: index, issueText: `意见${index}`, latest: null, history: [] }))
  api.get.mockResolvedValue(rows); api.post.mockResolvedValue({ id: 3 })
  await mount()
  const inputs = root.querySelectorAll('textarea')
  for (let index = 0; index < inputs.length; index++) { inputs[index].value = `我的回复${index}`; inputs[index].dispatchEvent(new Event('input', { bubbles: true })) }
  await nextTick()
  const record = { id: 3, responseText: '我的回复0', changeLocation: '', versionId: 2, status: 'RESPONDED' }
  api.get.mockResolvedValue([{ ...rows[0], latest: record, history: [record] }, rows[1]])
  root.querySelector('.response-item button').click(); await flush()
  expect(api.post).toHaveBeenCalledWith('/manuscripts/1/responses', expect.objectContaining({ reportId: 8, issueIndex: 0, responseText: '我的回复0', baseId: null }))
  expect(root.querySelectorAll('textarea')[1].value).toBe('我的回复1')
  expect(root.textContent).toContain('查看 1 条历史记录')
})
it('shows bounded comparison output and hides unchanged lines by default', async () => {
  api.get.mockImplementation(async url => url.endsWith('/responses') ? [] : { scope: '文本行对比', limited: true, changes: [
    { type: 'ADDED', text: '补充实验', newLine: 2 }, { type: 'REMOVED', text: '旧实验', oldLine: 2 }, { type: 'SAME', text: '共同结论', oldLine: 3, newLine: 3 }
  ] })
  await mount(); [...root.querySelectorAll('button')].find(b => b.textContent === '比较正文').click(); await flush()
  expect(root.querySelector('.ADDED').textContent).toContain('补充实验')
  expect(root.querySelector('.REMOVED').textContent).toContain('旧实验')
  expect(root.querySelector('.SAME')).toBeNull()
  expect(root.textContent).toContain('已截断')
})
it('keeps a failed editor confirmation visible without reporting success', async () => {
  api.get.mockResolvedValue([{ reportId: 8, issueIndex: 0, issueText: '意见', latest: { id: 3, responseText: '回复', status: 'RESPONDED' }, history: [] }])
  api.post.mockRejectedValue(new Error('请确认最新回应'))
  await mount({ canRespond: false, canConfirm: true })
  root.querySelector('.response-item button').click(); await flush()
  expect(root.querySelector('[role=alert]').textContent).toContain('请确认最新回应')
  expect(root.querySelector('[role=status]')).toBeNull()
  expect(root.querySelector('textarea').readOnly).toBe(true)
})
