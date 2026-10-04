import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  page.on('pageerror', error => { throw error })
})

async function login(page, username) {
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.getByRole('button', { name: '进入 JournalFlow' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

test('published list renders actual API metadata and keyword filtering', async ({ page }) => {
  const response = await page.request.get('/api/published?page=1&size=12')
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  expect(body.code).toBe(0)
  expect(body.data.list.length).toBeGreaterThan(0)
  const article = body.data.list[0]
  await page.goto('/')
  await expect.poll(async () => (await page.request.get('/api/reference/categories')).status()).toBe(200)
  const card = page.locator('.article-card').filter({ hasText: article.title })
  await expect(card).toContainText(article.abstract_ || article.abstract)
  await expect(card).toContainText(article.category_name || article.categoryName)
  await expect(page).toHaveURL(/\/$/)
  await page.getByPlaceholder('搜索标题、摘要或关键词').fill('不存在的检索结果-e2e')
  await page.getByRole('button', { name: '检索文献' }).click()
  await expect(page.getByText('未找到匹配的文章', { exact: true })).toBeVisible()
})

test('editor can load live workbench and AI task records', async ({ page }) => {
  await login(page, 'editor1')
  await page.getByRole('link', { name: '工作台', exact: true }).click()
  await expect(page.getByRole('heading', { name: '编辑工作台' })).toBeVisible()
  await expect(page.getByText('正在加载任务…', { exact: true })).toHaveCount(0)
  await expect(page.locator('.error-message')).toHaveCount(0)
  await page.getByRole('link', { name: 'AI 中心', exact: true }).click()
  await expect(page.getByRole('heading', { name: '大模型任务中心' })).toBeVisible()
  await expect(page.getByText('正在加载 AI 任务…', { exact: true })).toHaveCount(0)
  await expect(page.locator('.error-banner')).toHaveCount(0)
})

test('editor can compare versions and inspect original AI evidence', async ({ page }) => {
  await login(page, 'editor1')
  const token = await page.evaluate(() => localStorage.getItem('journalflow_token'))
  const headers = { Authorization: `Bearer ${token}` }
  const manuscripts = (await (await page.request.get('/api/manuscripts?page=1&size=100', { headers })).json()).data.list
  const manuscript = manuscripts.find(item => item.currentVersionNo >= 2 && item.title.startsWith('亮点功能演示：'))
  expect(manuscript).toBeTruthy()
  await page.goto(`/manuscripts/${manuscript.id}`)
  await expect(page.locator('.manuscript-hero')).toContainText('本文为功能演示用模拟数据')
  await expect(page.getByRole('heading', { name: '修回辅助工具' })).toBeVisible()
  await page.getByRole('button', { name: '比较正文', exact: true }).click()
  await expect(page.getByText(/新增 \d+ 行 \/ 删除 \d+ 行/)).toBeVisible()
  await expect(page.locator('.research-tools [role=alert]')).toHaveCount(0)
  const tasks = (await (await page.request.get(`/api/llm/tasks?page=1&size=20&scene=EXTRACT&manuscriptId=${manuscript.id}`, { headers })).json()).data.list
  const task = tasks.find(item => ['SUCCESS', 'CONFIRMED'].includes(item.status))
  expect(task).toBeTruthy()
  await page.goto('/ai-center')
  await page.locator('.filter-bar select').first().selectOption('EXTRACT')
  await page.getByRole('button', { name: '筛选', exact: true }).click()
  await page.locator('tr').filter({ hasText: `#${task.id} ·` }).getByRole('button', { name: '核对', exact: true }).click()
  await page.getByRole('button', { name: '查看字段依据', exact: true }).click()
  await expect(page.locator('.evidence-panel details').first()).toBeVisible()
  await page.locator('.evidence-panel summary').first().click()
  await expect(page.locator('.evidence-panel blockquote').first()).toBeVisible()
  await expect(page.locator('.evidence-panel [role=alert]')).toHaveCount(0)
  await page.screenshot({ path: 'test-results/research-evidence.png', fullPage: true })
})

test('anonymous reader can download the current published full text', async ({ page }) => {
  const body = await (await page.request.get('/api/published?page=1&size=12')).json()
  const article = body.data.list[0]
  await page.goto(`/articles/${article.id}`)
  const downloadEvent = page.waitForEvent('download')
  await page.getByRole('link', { name: '下载全文' }).click()
  const download = await downloadEvent
  expect(await download.failure()).toBeNull()
  expect(download.suggestedFilename()).toMatch(/\.(pdf|docx?|tex|zip)$/i)
})

test('demo author response is saved and editor confirmation remains optional', async ({ page }) => {
  await login(page, 'author1')
  const token = await page.evaluate(() => localStorage.getItem('journalflow_token'))
  const headers = { Authorization: `Bearer ${token}` }
  const manuscripts = (await (await page.request.get('/api/manuscripts/my?size=100', { headers })).json()).data.list
  const manuscript = manuscripts.find(item => item.title.startsWith('亮点功能演示：'))
  expect(manuscript).toBeTruthy()
  const stateBefore = manuscript.status
  await page.goto(`/manuscripts/${manuscript.id}`)
  const point = page.locator('.response-item').first()
  await point.getByLabel('逐条回复', { exact: true }).fill('已补充规则基线与对比实验，结果为模拟数据。')
  await point.getByLabel('修改位置', { exact: true }).fill('修回稿第3个非空文本段')
  const versionSelect = point.locator('select')
  const latest = await versionSelect.locator('option').last().getAttribute('value')
  await versionSelect.selectOption(latest)
  await point.getByRole('button', { name: '保存回应', exact: true }).click()
  await expect(point.getByText('已回应，待编辑确认', { exact: true })).toBeVisible()
  await expect(page.locator('.research-tools [role=alert]')).toHaveCount(0)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await login(page, 'editor1')
  await page.goto(`/manuscripts/${manuscript.id}`)
  await page.locator('.response-item').first().getByRole('button', { name: '确认此回应', exact: true }).click()
  await expect(page.locator('.response-item').first().getByText('编辑已确认', { exact: true })).toBeVisible()
  const after = (await (await page.request.get(`/api/manuscripts/${manuscript.id}`, { headers })).json()).data.manuscript
  expect(after.status).toBe(stateBefore)
})

test('author can view manuscripts and cannot navigate into admin tools', async ({ page }) => {
  await login(page, 'author1')
  await page.getByRole('link', { name: '稿件', exact: true }).click()
  await expect(page.getByRole('heading', { name: '稿件管理' })).toBeVisible()
  await expect(page.getByText('正在读取稿件…', { exact: true })).toHaveCount(0)
  await expect(page.locator('.error-message')).toHaveCount(0)
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/dashboard$/)
})

test('author creates and edits coauthors and tags, and sees a real upload rejection', async ({ page }) => {
  await login(page, 'author1')
  const token = await page.evaluate(() => localStorage.getItem('journalflow_token'))
  const headers = { Authorization: `Bearer ${token}` }
  const categories = (await (await page.request.get('/api/reference/categories', { headers })).json()).data
  const tags = (await (await page.request.get('/api/reference/tags', { headers })).json()).data
  const title = `浏览器草稿回归-${Date.now()}`
  let id
  try {
    await page.goto('/manuscripts')
    await page.getByRole('button', { name: '新建投稿', exact: true }).click()
    const modal = page.locator('.modal-card')
    await modal.getByLabel('稿件标题', { exact: true }).fill(title)
    await modal.getByLabel('学科分类').selectOption(String(categories[0].id))
    await modal.getByRole('button', { name: '添加共同作者' }).click()
    await modal.getByLabel('作者姓名', { exact: true }).fill('模拟合作者')
    await modal.getByLabel('作者单位', { exact: true }).fill('模拟研究单位')
    await modal.getByLabel('稿件标签', { exact: true }).selectOption(String(tags[0].id))
    const createdResponse = page.waitForResponse(response => response.url().endsWith('/api/manuscripts') && response.request().method() === 'POST')
    await modal.getByRole('button', { name: '保存草稿', exact: true }).click()
    const created = await (await createdResponse).json(); expect(created.code).toBe(0); id = created.data.id
    await expect(modal).toHaveCount(0)
    await page.getByPlaceholder('搜索稿件标题').fill(title)
    await page.getByRole('button', { name: '刷新', exact: true }).click()
    await page.locator('tr').filter({ hasText: title }).getByRole('button', { name: '编辑草稿' }).click()
    await expect(modal.getByLabel('作者姓名', { exact: true })).toHaveValue('模拟合作者')
    await modal.getByLabel('稿件标题', { exact: true }).fill(`${title}-修订`)
    await modal.getByRole('button', { name: '保存草稿', exact: true }).click()
    await expect(modal).toHaveCount(0)
    const detail = (await (await page.request.get(`/api/manuscripts/${id}`, { headers })).json()).data
    expect(detail.manuscript.title).toBe(`${title}-修订`)
    expect(detail.authors[1].authorName).toBe('模拟合作者')
    expect(detail.tags[0].id).toBe(tags[0].id)
    await page.locator('tr').filter({ hasText: `${title}-修订` }).getByRole('button', { name: '提交', exact: true }).click()
    await modal.locator('input[type=file]').setInputFiles({ name: 'invalid.pdf', mimeType: 'application/pdf', buffer: Buffer.from('not a PDF') })
    await modal.getByRole('button', { name: '确认提交', exact: true }).click()
    await expect(modal.getByRole('alert')).toContainText('文件内容与扩展名不匹配')
    const rejected = (await (await page.request.get(`/api/manuscripts/${id}`, { headers })).json()).data
    expect(rejected.manuscript.status).toBe('DRAFT')
    expect(rejected.versions).toHaveLength(0)
  } finally {
    if (id) expect((await page.request.delete(`/api/manuscripts/${id}`, { headers })).status()).toBe(200)
  }
})
