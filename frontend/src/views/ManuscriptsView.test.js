import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import ManuscriptsView from './ManuscriptsView.vue'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../api', () => ({ default: api }))
vi.mock('../session', () => ({ hasRole: role => role === 'AUTHOR' }))
let app, root
const flush = async () => { await new Promise(resolve => setTimeout(resolve, 0)); await nextTick() }
const click = async text => { [...root.querySelectorAll('button')].find(button => button.textContent.trim() === text).click(); await flush() }
const input = async (selector, value) => {
  const element = root.querySelector(selector); element.value = value
  element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true })); await flush()
}
const selectFile = async file => {
  const element = root.querySelector('input[type=file]')
  Object.defineProperty(element, 'files', { configurable: true, value: [file] })
  element.dispatchEvent(new Event('change', { bubbles: true })); await flush()
}
beforeEach(async () => {
  vi.resetAllMocks()
  vi.stubGlobal('crypto', {})
  api.get.mockImplementation(async url => {
    if (url === '/reference/categories') return [{ id: 1, name: '计算机' }]
    if (url === '/reference/tags') return [{ id: 9, name: '人工智能' }]
    if (url === '/manuscripts/88') return { manuscript: { status: 'DRAFT' }, currentVersion: null }
    return { list: [], total: 0 }
  })
  root = document.createElement('div'); document.body.appendChild(root)
  app = createApp(ManuscriptsView); app.component('RouterLink', { template: '<a><slot /></a>' }); app.mount(root)
  await flush()
})
afterEach(() => { app.unmount(); document.body.innerHTML = ''; vi.unstubAllGlobals() })

it('retries a failed upload on the saved draft without creating another manuscript', async () => {
  api.post.mockImplementation(async url => {
    if (url === '/manuscripts') return { id: 88 }
    throw new Error('上传失败')
  })
  await click('新建投稿')
  await input('input[placeholder="请输入完整论文标题"]', '测试论文')
  await input('.modal-card select', '1')
  await selectFile(new File(['正文'], 'paper.tex'))
  root.querySelector('.modal-card').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await flush()
  await vi.waitFor(() => expect(root.querySelector('[role=alert]')?.textContent).toContain('草稿已保存'))
  expect(root.querySelector('.modal-card').textContent).toContain('paper.tex')
  api.post.mockResolvedValue({ id: 1 })
  root.querySelector('.modal-card').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await flush()
  expect(api.post.mock.calls.filter(([url]) => url === '/manuscripts')).toHaveLength(1)
  expect(api.post.mock.calls.filter(([url]) => url === '/manuscripts/88/submit')).toHaveLength(2)
  expect(api.post).toHaveBeenLastCalledWith('/manuscripts/88/submit', expect.any(FormData), expect.objectContaining({ timeout: 120000 }))
  await vi.waitFor(() => expect(root.querySelector('.modal-card')).toBeNull())
})

it('shows oversized and unsupported file errors inside the dialog before creating a draft', async () => {
  await click('新建投稿')
  await selectFile(new File(['bad'], 'paper.exe'))
  expect(root.querySelector('[role=alert]').textContent).toContain('支持 PDF')
  const large = new File(['data'], 'paper.pdf'); Object.defineProperty(large, 'size', { value: 21 * 1024 * 1024 })
  await selectFile(large)
  expect(root.querySelector('[role=alert]').textContent).toContain('20MB')
  expect(api.post).not.toHaveBeenCalled()
})

it('edits a saved draft while preserving its coauthors and tags', async () => {
  api.get.mockImplementation(async url => {
    if (url === '/manuscripts/88') return {
      manuscript: { title: '旧标题', abstract: '原摘要', categoryId: 1 }, tags: [{ id: 9 }],
      authors: [{ authorOrder: 1, userId: 8 }, { authorOrder: 2, userId: 9, authorName: '合作者', affiliation: '学校', isCorresponding: 1 }]
    }
    if (url === '/reference/tags') return [{ id: 9, name: '人工智能' }]
    return { list: [{ id: 88, title: '旧标题', status: 'DRAFT' }], total: 1 }
  })
  await click('刷新'); await click('编辑草稿')
  await input('input[placeholder="请输入完整论文标题"]', '新标题')
  root.querySelector('.modal-card').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await flush()
  expect(api.put).toHaveBeenCalledWith('/manuscripts/88', expect.objectContaining({ title: '新标题', abstract_: '原摘要', tagIds: [9], coAuthors: [expect.objectContaining({ userId: 9, corresponding: true })] }))
  expect(api.post).not.toHaveBeenCalled()
})

it('recognizes an upload saved by the server even if the response times out', async () => {
  vi.stubGlobal('crypto', { subtle: { digest: async () => new Uint8Array([1]).buffer } })
  let saved = false
  api.get.mockImplementation(async url => {
    if (url.startsWith('/reference/')) return []
    if (url === '/manuscripts/88') return saved
      ? { manuscript: { status: 'SUBMITTED' }, currentVersion: { id: 99, fileHash: '01', fileSize: 6 } }
      : { manuscript: { status: 'DRAFT' }, currentVersion: null }
    return { list: [], total: 0 }
  })
  api.post.mockImplementation(async url => {
    if (url === '/manuscripts') return { id: 88 }
    saved = true; throw new Error('timeout')
  })
  await click('新建投稿')
  await selectFile(new File(['正文'], 'paper.tex'))
  root.querySelector('.modal-card').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
  await flush(); await flush()
  expect(root.querySelector('.modal-card')).toBeNull()
  expect(root.textContent).toContain('稿件已提交')
  expect(api.post.mock.calls.filter(([url]) => url.includes('/submit'))).toHaveLength(1)
})

it('adds a coauthor and selects tags in the submission form', async () => {
  api.post.mockResolvedValue({ id: 88 })
  await click('新建投稿'); await click('添加共同作者')
  await input('.author-editor input', '王研究员')
  const tag = root.querySelector('select[multiple]'); tag.options[0].selected = true
  tag.dispatchEvent(new Event('change', { bubbles: true })); await flush()
  root.querySelector('.modal-card').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })); await flush()
  expect(api.post).toHaveBeenCalledWith('/manuscripts', expect.objectContaining({ tagIds: [9], coAuthors: [expect.objectContaining({ name: '王研究员' })] }))
})
