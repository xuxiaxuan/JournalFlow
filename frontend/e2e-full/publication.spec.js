import { test, expect } from '@playwright/test'
import { readFile } from 'node:fs/promises'

async function login(page, username) {
  await page.goto('/login')
  await page.getByLabel('用户名', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill('123456')
  await page.getByRole('button', { name: '进入 JournalFlow' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
}

async function task(page, title, action) {
  await page.goto('/workbench')
  await expect(page.getByText('正在加载任务…', { exact: true })).toHaveCount(0)
  for (let count = 0; count < 100; count++) {
    const row = page.locator('.task-row').filter({ hasText: title })
      .filter({ has: page.getByRole('button', { name: action, exact: true }) })
    if (await row.count()) return row.first()
    const next = page.getByRole('button', { name: '下一页', exact: true })
    if (!(await next.count()) || await next.isDisabled()) break
    await next.click()
  }
  throw new Error(`Cannot find ${action} for ${title}`)
}

async function submit(page) {
  const modal = page.locator('.modal-card')
  await modal.getByRole('button', { name: '确认提交', exact: true }).click()
  await expect(modal).toHaveCount(0)
}

test('three-role UI workflow: upload, review, revise, re-review, copyright, publish and download', async ({ browser }, testInfo) => {
  const contexts = await Promise.all(['author', 'editor', 'reviewer'].map(() => browser.newContext()))
  const [author, editor, reviewer] = await Promise.all(contexts.map(context => context.newPage()))
  const errors = []
  for (const page of [author, editor, reviewer]) page.on('pageerror', error => errors.push(error.message))
  const title = `浏览器全流程验收（模拟数据）-${Date.now()}`
  const original = Buffer.from('\\documentclass{article}\n\\begin{document}\nSimulated journal workflow experiment.\nBaseline pending.\n\\end{document}')
  const revised = Buffer.from('\\documentclass{article}\n\\begin{document}\nSimulated journal workflow experiment.\nAdded simulated baseline and reproducibility details.\n\\end{document}')
  let id
  try {
    await Promise.all([login(author, 'author1'), login(editor, 'editor1'), login(reviewer, 'reviewer1')])
    const token = await reviewer.evaluate(() => localStorage.getItem('journalflow_token'))
    const me = (await (await reviewer.request.get('/api/auth/me', { headers: { Authorization: `Bearer ${token}` } })).json()).data
    const reviewerName = (me.user || me).realName
    await author.goto('/manuscripts')
    await author.getByRole('button', { name: '新建投稿', exact: true }).click()
    const modal = author.locator('.modal-card')
    await modal.getByLabel('稿件标题', { exact: true }).fill(title)
    await modal.getByLabel('摘要', { exact: true }).fill('仅用于本地浏览器流程验收的模拟研究，不代表真实研究结果。')
    await modal.getByLabel('关键词', { exact: true }).fill('模拟数据;流程验收')
    await expect(modal.getByLabel('学科分类').locator('option')).not.toHaveCount(1)
    await modal.getByLabel('学科分类').selectOption({ index: 1 })
    await modal.locator('input[type=file]').setInputFiles({ name: 'workflow-v1.tex', mimeType: 'application/x-tex', buffer: original })
    const creation = author.waitForResponse(r => r.url().endsWith('/api/manuscripts') && r.request().method() === 'POST')
    await modal.getByRole('button', { name: '保存并投稿', exact: true }).click()
    const result = await (await creation).json()
    expect(result.code).toBe(0)
    id = result.data.id
    await expect(modal).toHaveCount(0)

    await (await task(editor, title, '开始审查')).getByRole('button', { name: '开始审查', exact: true }).click()
    await expect(editor.getByText('已开始形式审查', { exact: true })).toBeVisible()
    await (await task(editor, title, '填写结论')).getByRole('button', { name: '填写结论', exact: true }).click()
    await editor.getByLabel('审查说明', { exact: true }).fill('模拟稿件材料齐全，允许进入审稿。')
    await submit(editor)

    async function reviewRound(recommendation) {
      if (recommendation === 'MINOR') {
        await (await task(editor, title, '邀请专家')).getByRole('button', { name: '邀请专家', exact: true }).click()
        await editor.locator('.reviewer-options label').filter({ hasText: reviewerName }).getByRole('checkbox').check()
        await submit(editor)
      }
      await (await task(reviewer, title, '接受')).getByRole('button', { name: '接受', exact: true }).click()
      await expect(reviewer.getByText('已接受审稿邀请', { exact: true })).toBeVisible()
      await (await task(reviewer, title, '填写意见')).getByRole('button', { name: '填写意见', exact: true }).click()
      await reviewer.getByLabel('建议等级').selectOption(recommendation)
      await reviewer.getByLabel('给作者的意见', { exact: true }).fill(recommendation === 'MINOR' ? '请补充基线实验。\n请说明复现步骤。' : '修订已回应问题，建议录用（模拟审稿）。')
      await reviewer.getByLabel('仅给编辑的意见', { exact: true }).fill('仅用于模拟验收的私密意见。')
      await submit(reviewer)
      await (await task(editor, title, '作出决定')).getByRole('button', { name: '作出决定', exact: true }).click()
      await editor.getByLabel('编辑决定').selectOption(recommendation === 'MINOR' ? 'REVISE' : 'ACCEPT')
      await editor.getByLabel('决定理由', { exact: true }).fill('根据本轮模拟专家意见作出人工决定。')
      await submit(editor)
    }
    await reviewRound('MINOR')
    await author.goto('/manuscripts')
    await author.getByPlaceholder('搜索稿件标题').fill(title)
    await author.getByRole('button', { name: '刷新', exact: true }).click()
    await author.locator('tr').filter({ hasText: title }).getByRole('button', { name: '修回', exact: true }).click()
    await modal.locator('input[type=file]').setInputFiles({ name: 'workflow-v2.tex', mimeType: 'application/x-tex', buffer: revised })
    await modal.getByLabel('修改说明', { exact: true }).fill('增加模拟基线与复现说明。')
    await modal.getByLabel('给审稿人的回复', { exact: true }).fill('已在修回稿中逐项补充。')
    await submit(author)
    await reviewRound('ACCEPT')

    await author.getByRole('button', { name: '刷新', exact: true }).click()
    await author.locator('tr').filter({ hasText: title }).getByRole('button', { name: '签署版权', exact: true }).click()
    await modal.getByRole('checkbox').check()
    await modal.getByRole('button', { name: '确认签署', exact: true }).click()
    await expect(modal).toHaveCount(0)
    await (await task(editor, title, '版权与出版')).getByRole('button', { name: '版权与出版', exact: true }).click()
    for (const [label, value] of [['卷号', '2026'], ['期号', '1'], ['页码', '1-2'], ['DOI', `10.99999/simulated-${id}`]]) {
      await editor.getByLabel(label, { exact: true }).fill(value)
    }
    await submit(editor)
    editor.once('dialog', dialog => dialog.accept())
    await (await task(editor, title, '正式发布')).getByRole('button', { name: '正式发布', exact: true }).click()
    await expect(editor.getByText('文章已正式出版归档', { exact: true })).toBeVisible()
    await author.getByRole('button', { name: '退出登录', exact: true }).click()
    await author.goto('/')
    await author.getByPlaceholder('搜索标题、摘要或关键词').fill(title)
    await author.getByRole('button', { name: '检索文献', exact: true }).click()
    await author.locator('.article-card').filter({ hasText: title }).getByRole('link').first().click()
    await expect(author).toHaveURL(new RegExp(`/articles/${id}$`))
    const downloading = author.waitForEvent('download')
    await author.getByRole('link', { name: '下载全文', exact: true }).click()
    const download = await downloading
    expect(await download.failure()).toBeNull()
    expect(await readFile(await download.path())).toEqual(revised)
    expect(errors).toEqual([])
    await author.screenshot({ path: testInfo.outputPath('published.png'), fullPage: true })
    await testInfo.attach('publication', { body: JSON.stringify({ id, title, status: 'PUBLISHED', downloadBytesVerified: true }), contentType: 'application/json' })
    console.log(`Retained simulated publication: ${id} / ${title}`)
  } finally {
    if (id) console.log(`Workflow fixture manuscript ID: ${id} (retained for audit, including on failure)`)
    await Promise.all(contexts.map(context => context.close()))
  }
})
