<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Bot, Check, Clock3, RefreshCw, UserCheck, X } from 'lucide-vue-next'
import api from '../api'
import { fetchAllPages } from '../data'
import { hasRole } from '../session'

const items = ref([])
const page = ref(1)
const size = 15
const pages = computed(() => Math.max(1, Math.ceil(items.value.length / size)))
const visibleItems = computed(() => items.value.slice((page.value - 1) * size, page.value * size))
const loading = ref(false)
const error = ref('')
const notice = ref('')
const selected = ref(null)
watch(selected, value => { if (value) error.value = '' })
const editorAction = ref('')
const reviewers = ref([])
const summaryTasks = ref([])
const editorForm = ref({ pass: true, reason: '', reviewerIds: [], dueAt: '', decision: 'REVISE', summaryReport: '', llmTaskId: '', volume: '', issue: '', pages: '', doi: '' })
const saving = ref(false)
const reviewForm = ref({ scoreNovelty: 8, scoreQuality: 8, scoreClarity: 8, commentsToEditor: '', commentsToAuthor: '', recommendation: 'MINOR' })
const mode = computed(() => hasRole('EDITOR') || hasRole('ADMIN') ? 'editor' : hasRole('REVIEWER') ? 'reviewer' : 'author')
const title = computed(() => ({ editor: '编辑工作台', reviewer: '审稿工作台', author: '作者任务中心' })[mode.value])

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (mode.value === 'editor') {
      items.value = (await fetchAllPages(api, '/manuscripts')).filter(item => ['SUBMITTED','IN_FORMAT_REVIEW','UNDER_REVIEW','PENDING_DECISION','ACCEPTED','IN_PRODUCTION'].includes(item.status))
    } else if (mode.value === 'reviewer') {
      items.value = await api.get('/reviews/my-assignments') || []
    } else {
      items.value = (await fetchAllPages(api, '/manuscripts/my')).filter((item) => ['REVISION_REQUIRED', 'DRAFT'].includes(item.status))
    }
    page.value = Math.min(page.value, pages.value)
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

onMounted(load)

async function accept(item) {
  saving.value = true
  try { await api.post(`/reviews/assignments/${item.id}/accept`); notice.value = '已接受审稿邀请'; await load() }
  catch (e) { error.value = e.message } finally { saving.value = false }
}

async function decline(item) {
  const reason = window.prompt('请填写拒绝原因（可选）')
  if (reason === null) return
  saving.value = true
  try { await api.post(`/reviews/assignments/${item.id}/decline`, { reason }); notice.value = '已拒绝审稿邀请'; await load() }
  catch (e) { error.value = e.message } finally { saving.value = false }
}

function openReview(item) {
  selected.value = item
  reviewForm.value = { scoreNovelty: 8, scoreQuality: 8, scoreClarity: 8, commentsToEditor: '', commentsToAuthor: '', recommendation: 'MINOR' }
}

async function submitReview() {
  if (saving.value) return
  saving.value = true
  try {
    await api.post(`/reviews/assignments/${selected.value.id}/report`, reviewForm.value)
    notice.value = '审稿意见已提交，且不可重复提交'
    selected.value = null
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function startFormat(item) {
  saving.value = true
  try { await api.post(`/editor/manuscripts/${item.id}/format-review/start`); notice.value = '已开始形式审查'; await load() }
  catch (e) { error.value = e.message } finally { saving.value = false }
}

async function archive(item) {
  if (!window.confirm(`确认正式发布《${item.title}》吗？`)) return
  saving.value = true
  try { await api.post(`/editor/manuscripts/${item.id}/archive`); notice.value = '文章已正式出版归档'; await load() }
  catch (e) { error.value = e.message } finally { saving.value = false }
}

async function openEditorAction(item, action) {
  selected.value = item
  editorAction.value = action
  editorForm.value = { pass: true, reason: '', reviewerIds: [], dueAt: '', decision: 'REVISE', summaryReport: '', llmTaskId: '', volume: '', issue: '', pages: '', doi: '' }
  try {
    if (action === 'assign' && !reviewers.value.length) reviewers.value = await api.get('/editor/reviewers') || []
    if (action === 'decision') {
      summaryTasks.value = []
      summaryTasks.value = (await fetchAllPages(api, '/llm/tasks', { scene: 'SUMMARY', status: 'CONFIRMED', manuscriptId: item.id }))
        .filter(task => task.versionId === item.currentVersionId)
    }
  } catch (e) { error.value = e.message }
}

async function applySummaryTask() {
  if (!editorForm.value.llmTaskId) return
  try {
    const task = await api.get(`/llm/tasks/${editorForm.value.llmTaskId}`)
    editorForm.value.summaryReport = task.confirmedOutput || ''
  } catch (e) { error.value = e.message }
}

function closeEditorAction() {
  if (saving.value) return
  selected.value = null
  editorAction.value = ''
}

async function submitEditorAction() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    const id = selected.value.id
    if (editorAction.value === 'format') {
      await api.post(`/editor/manuscripts/${id}/format-review`, { pass: editorForm.value.pass, reason: editorForm.value.reason })
      notice.value = '形式审查结论已提交'
    } else if (editorAction.value === 'assign') {
      await api.post(`/editor/manuscripts/${id}/assign-reviewers`, { reviewerIds: editorForm.value.reviewerIds.map(Number), dueAt: editorForm.value.dueAt || null })
      notice.value = '审稿邀请已发送'
    } else if (editorAction.value === 'decision') {
      await api.post(`/editor/manuscripts/${id}/decision`, { decision: editorForm.value.decision, reason: editorForm.value.reason, summaryReport: editorForm.value.summaryReport, llmTaskId: editorForm.value.llmTaskId ? Number(editorForm.value.llmTaskId) : null })
      notice.value = '编辑决定已提交'
    } else if (editorAction.value === 'publish') {
      await api.post(`/editor/manuscripts/${id}/publish`, { volume: editorForm.value.volume, issue: editorForm.value.issue, pages: editorForm.value.pages, doi: editorForm.value.doi })
      notice.value = '已核验作者版权签署记录，稿件进入出版加工'
    }
    selected.value = null
    editorAction.value = ''
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}
</script>

<template>
  <section class="page-heading">
    <div><span class="overline">ROLE WORKBENCH</span><h1>{{ title }}</h1><p>聚焦当前角色需要处理的事项，减少流程切换成本。</p></div>
    <button class="button button-secondary" @click="load"><RefreshCw :size="16" />刷新任务</button>
  </section>
  <p v-if="notice" class="success-banner">{{ notice }}</p>
  <section class="workbench-grid">
    <article class="panel task-panel">
      <div class="panel-title"><div><span class="overline">TO DO</span><h2>待办事项</h2></div><span class="counter">{{ items.length }}</span></div>
      <div v-if="loading" class="empty-state">正在加载任务…</div>
      <div v-else-if="error" class="empty-state error-message">{{ error }}</div>
      <div v-else-if="!items.length" class="empty-state"><Check :size="28" /><strong>当前没有待办</strong><small>所有事项都已处理完成</small></div>
      <div v-else class="task-list">
        <div v-for="item in visibleItems" :key="item.id" class="task-row">
          <span class="task-icon"><component :is="mode === 'reviewer' ? UserCheck : Clock3" :size="19" /></span>
          <div><strong>{{ item.title || item.manuscriptTitle || `任务 #${item.id}` }}</strong><small>{{ item.status }} · {{ item.updatedAt || item.dueAt || item.createdAt || '等待处理' }}</small></div>
          <div v-if="mode === 'reviewer'" class="task-actions">
            <template v-if="item.status === 'PENDING'"><button class="button button-small button-primary" :disabled="saving" @click="accept(item)">接受</button><button class="button button-small button-secondary" :disabled="saving" @click="decline(item)">拒绝</button></template>
            <button v-else-if="item.status === 'ACCEPTED'" class="button button-small button-primary" @click="openReview(item)">填写意见</button>
            <span v-else class="status-pill green">{{ item.status === 'COMPLETED' ? '已完成' : '已处理' }}</span>
          </div>
          <router-link v-else-if="mode === 'author'" class="button button-small button-secondary" to="/manuscripts">处理</router-link>
          <div v-else class="task-actions">
            <button v-if="item.status === 'SUBMITTED'" class="button button-small button-primary" @click="startFormat(item)">开始审查</button>
            <button v-else-if="item.status === 'IN_FORMAT_REVIEW'" class="button button-small button-primary" @click="openEditorAction(item, 'format')">填写结论</button>
            <button v-else-if="item.status === 'UNDER_REVIEW'" class="button button-small button-secondary" @click="openEditorAction(item, 'assign')">邀请专家</button>
            <button v-else-if="item.status === 'PENDING_DECISION'" class="button button-small button-primary" @click="openEditorAction(item, 'decision')">作出决定</button>
            <button v-else-if="item.status === 'ACCEPTED'" class="button button-small button-primary" @click="openEditorAction(item, 'publish')">版权与出版</button>
            <button v-else-if="item.status === 'IN_PRODUCTION'" class="button button-small button-primary" @click="archive(item)">正式发布</button>
            <span v-else class="status-pill" :class="item.status === 'PUBLISHED' ? 'green' : item.status === 'REJECTED' ? 'red' : 'blue'">{{ item.status }}</span>
          </div>
        </div>
      </div>
      <div v-if="pages > 1" class="pager"><span>第 {{ page }}/{{ pages }} 页</span><button class="button button-secondary" :disabled="page <= 1" @click="page--">上一页</button><button class="button button-secondary" :disabled="page >= pages" @click="page++">下一页</button></div>
    </article>
    <aside class="panel ai-trace-card">
      <span class="ai-orb"><Bot :size="25" /></span><span class="overline">TRACEABLE AI</span><h2>AI 辅助记录</h2><p>每次结构化抽取、意见汇总与修改解释都会记录模型、模板、原始输出和人工修订结果。</p>
      <div class="trace-steps"><span class="done">输入模板</span><i></i><span class="done">模型输出</span><i></i><span>人工确认</span></div>
      <router-link v-if="mode === 'editor'" to="/ai-center" class="button button-primary full-width">查看待确认任务</router-link>
    </aside>
  </section>

  <div v-if="selected" class="modal-backdrop" @click.self="!saving && (selected = null)">
    <form v-if="mode === 'reviewer'" class="modal-card review-modal" @submit.prevent="submitReview">
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <div class="modal-title"><div><span class="overline">PEER REVIEW</span><h2>提交审稿意见</h2></div><button type="button" class="icon-button" :disabled="saving" @click="selected = null"><X /></button></div>
      <p class="muted">{{ selected.title || selected.manuscriptTitle }}</p>
      <div class="score-grid"><label>创新性<input v-model.number="reviewForm.scoreNovelty" type="number" min="1" max="10" required /></label><label>学术质量<input v-model.number="reviewForm.scoreQuality" type="number" min="1" max="10" required /></label><label>表达清晰度<input v-model.number="reviewForm.scoreClarity" type="number" min="1" max="10" required /></label></div>
      <label>建议等级<select v-model="reviewForm.recommendation"><option value="ACCEPT">录用</option><option value="MINOR">小修</option><option value="MAJOR">大修</option><option value="REJECT">退稿</option></select></label>
      <label>给作者的意见<textarea v-model="reviewForm.commentsToAuthor" rows="5" required placeholder="请提供具体、建设性的修改意见"></textarea></label>
      <label>仅给编辑的意见<textarea v-model="reviewForm.commentsToEditor" rows="3" placeholder="此内容不会展示给作者"></textarea></label>
      <div class="modal-actions"><button type="button" class="button button-secondary" :disabled="saving" @click="selected = null">取消</button><button class="button button-primary" :disabled="saving">{{ saving ? '正在提交…' : '确认提交' }}</button></div>
    </form>
    <form v-else class="modal-card review-modal" @submit.prevent="submitEditorAction">
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <div class="modal-title"><div><span class="overline">EDITORIAL ACTION</span><h2>{{ { format:'形式审查结论', assign:'邀请审稿专家', decision:'编辑决定', publish:'版权与出版' }[editorAction] }}</h2></div><button type="button" class="icon-button" @click="closeEditorAction"><X /></button></div>
      <p class="muted">{{ selected.title }}</p>
      <template v-if="editorAction === 'format'">
        <label>审查结论<select v-model="editorForm.pass"><option :value="true">通过，进入专家审稿</option><option :value="false">不通过并退稿</option></select></label>
        <label>审查说明<textarea v-model="editorForm.reason" rows="4" placeholder="说明格式、材料或合规性问题"></textarea></label>
      </template>
      <template v-else-if="editorAction === 'assign'">
        <label>选择审稿专家<div class="reviewer-options"><label v-for="person in reviewers" :key="person.id"><input v-model="editorForm.reviewerIds" type="checkbox" :value="person.id" /><span><strong>{{ person.realName }}</strong><small>{{ person.affiliation }} · {{ person.researchFields }}</small></span></label></div></label>
        <label>审稿截止时间<input v-model="editorForm.dueAt" type="datetime-local" /></label>
      </template>
      <template v-else-if="editorAction === 'decision'">
        <label>编辑决定<select v-model="editorForm.decision"><option value="ACCEPT">录用</option><option value="REVISE">退修</option><option value="REJECT">退稿</option></select></label>
        <label>决定理由<textarea v-model="editorForm.reason" rows="3" required></textarea></label>
        <label>关联已确认的 AI 审稿汇总<select v-model="editorForm.llmTaskId" @change="applySummaryTask"><option value="">不关联 AI 任务</option><option v-for="task in summaryTasks" :key="task.id" :value="task.id">#{{ task.id }} {{ task.templateId }} · {{ task.confirmedAt || task.createdAt }}</option></select></label>
        <label>审稿综合意见<textarea v-model="editorForm.summaryReport" rows="5" placeholder="归纳主要问题、共识与分歧"></textarea></label>
      </template>
      <template v-else-if="editorAction === 'publish'">
        <p class="form-hint">系统将在提交时核验作者对当前稿件版本的版权签署记录；未签署时不能进入出版加工。</p>
        <div class="form-grid"><label>卷号<input v-model.trim="editorForm.volume" maxlength="20" required /></label><label>期号<input v-model.trim="editorForm.issue" maxlength="20" required /></label><label>页码<input v-model.trim="editorForm.pages" maxlength="30" placeholder="1-15" required /></label><label>DOI<input v-model.trim="editorForm.doi" maxlength="100" pattern="10\.\d{4,9}/\S+" placeholder="10.xxxx/xxxx" required /></label></div>
      </template>
      <div class="modal-actions"><button type="button" class="button button-secondary" @click="closeEditorAction">取消</button><button class="button button-primary" :disabled="saving || (editorAction === 'assign' && !editorForm.reviewerIds.length)">{{ saving ? '正在提交…' : '确认提交' }}</button></div>
    </form>
  </div>
</template>
