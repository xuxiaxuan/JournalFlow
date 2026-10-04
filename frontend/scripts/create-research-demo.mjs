// Adds one explicitly labelled simulated manuscript through the normal application APIs.
// Existing manuscripts and attachments are never overwritten. Repeat runs reuse a complete demo.
const base = 'http://localhost:8080/api'
const title = '亮点功能演示：可追溯的审稿修回研究（模拟数据）'
async function request(path, token, body, method = body === undefined ? 'GET' : 'POST') {
  const multipart = body instanceof FormData
  const response = await fetch(base + path, {
    method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(!multipart && body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
    body: body === undefined ? undefined : multipart ? body : JSON.stringify(body)
  })
  const result = await response.json()
  if (!response.ok || result.code !== 0) throw new Error(`${path}: ${result.message || response.status}`)
  return result.data
}
async function login(username) { return (await request('/auth/login', null, { username, password: '123456' })).token }
const author = await login('author1'), editor = await login('editor1'), reviewer = await login('reviewer1')
const found = (await request(`/manuscripts/my?keyword=${encodeURIComponent(title)}&size=100`, author)).list.find(row => row.title === title)
let id
if (found) {
  if (found.currentVersionNo < 2) throw new Error(`演示稿件 #${found.id} 尚未完成；请检查该稿件，不会自动覆盖已有内容`)
  id = found.id
} else {
  const categories = await request('/reference/categories', author)
  const category = categories.find(row => row.name === '人工智能') || categories[0]
  const manuscript = await request('/manuscripts', author, {
    title, abstract_: '本文为功能演示用模拟数据，展示论文版本差异、审稿意见回应及大模型结果溯源。',
    keywords: '大模型;学术期刊;审稿', categoryId: category.id, tagIds: [], coAuthors: []
  })
  id = manuscript.id
  const first = `${title}\n研究目标：验证大模型辅助信息的可追溯性。\n实验：当前仅报告一组模拟结果。\n结论：辅助结果需人工核对。\n参考文献\n[1] 功能演示文献，模拟数据。`
  const second = `${title}\n研究目标：验证大模型辅助信息的可追溯性。\n实验：增加规则基线、人工核对及两组对比实验，所有数字仅为模拟。\n结论：辅助结果需人工核对，不能据此推断真实研究效果。\n参考文献\n[1] 功能演示文献，模拟数据。`
  const upload = (text, name) => { const form = new FormData(); form.append('file', new Blob([text], { type: 'text/plain' }), name); return form }
  await request(`/manuscripts/${id}/submit`, author, upload(first, 'research-demo-v1.tex'))
  await request(`/editor/manuscripts/${id}/format-review/start`, editor, {})
  await request(`/editor/manuscripts/${id}/format-review`, editor, { pass: true, reason: '模拟稿件用于辅助功能验收' })
  const people = await request('/editor/reviewers', editor)
  const reviewerUser = people.find(row => row.username === 'reviewer1')
  // Profile endpoint avoids depending on whether the reviewer directory includes usernames.
  const reviewerProfile = reviewerUser || (await request('/auth/me', reviewer)).user
  const assignments = await request(`/editor/manuscripts/${id}/assign-reviewers`, editor, { reviewerIds: [reviewerProfile.id] })
  await request(`/reviews/assignments/${assignments[0].id}/accept`, reviewer, {})
  await request(`/reviews/assignments/${assignments[0].id}/report`, reviewer, {
    scoreNovelty: 8, scoreQuality: 8, scoreClarity: 8, commentsToAuthor: '请补充规则基线与对比实验。\n请说明模拟结果的适用范围，避免过度推断。',
    commentsToEditor: '模拟审稿记录，仅用于功能演示。', recommendation: 'MINOR'
  })
  await request(`/editor/manuscripts/${id}/decision`, editor, { decision: 'REVISE', reason: '模拟退修：补充实验与适用范围' })
  const revision = upload(second, 'research-demo-v2.tex')
  revision.append('changeNote', '补充模拟实验与结论边界'); revision.append('responseToReviewers', '已逐项修订；可通过可选回应清单进一步关联修改位置。')
  await request(`/manuscripts/${id}/revisions`, author, revision)
}
let task = (await request(`/llm/tasks?scene=EXTRACT&manuscriptId=${id}&size=100`, editor)).list
  .find(row => ['SUCCESS', 'CONFIRMED', 'RUNNING'].includes(row.status))
if (!task) task = await request('/llm/tasks', editor, { manuscriptId: id, scene: 'EXTRACT' })
for (let attempt = 0; task.status === 'RUNNING' && attempt < 40; attempt++) {
  await new Promise(resolve => setTimeout(resolve, 250))
  task = await request(`/llm/tasks/${task.id}`, editor)
}
if (!['SUCCESS', 'CONFIRMED'].includes(task.status)) throw new Error(`演示稿件 #${id} 已保留，AI 任务 #${task.id} 状态：${task.status}`)
console.log(JSON.stringify({ manuscriptId: id, taskId: task.id, title, reused: !!found }))
