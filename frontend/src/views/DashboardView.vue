<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowRight, Bot, Clock3, FileCheck2, FileText, Inbox, ListChecks } from 'lucide-vue-next'
import api from '../api'
import { fetchAllPages } from '../data'
import { session } from '../session'

const data = ref({ manuscripts: [], assignments: [], aiTasks: [] })
const loading = ref(true), error = ref('')
const primaryRole = computed(() => session.user?.roles?.[0] || 'USER')
const roleName = { AUTHOR: '作者', REVIEWER: '审稿专家', EDITOR: '编辑', ADMIN: '管理员' }
const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const steps = ['投稿', '形式审查', '专家审稿', '编辑决定', '出版归档']

const metrics = computed(() => {
  if (primaryRole.value === 'AUTHOR') {
    const list = data.value.manuscripts
    return [['我的稿件', list.length, FileText, '/manuscripts'], ['待修回', list.filter(x => x.status === 'REVISION_REQUIRED').length, Clock3, '/workbench'], ['已出版', list.filter(x => x.status === 'PUBLISHED').length, FileCheck2, '/manuscripts'], ['未读消息', session.unreadCount || 0, Inbox, '/account']]
  }
  if (primaryRole.value === 'REVIEWER') {
    const list = data.value.assignments
    return [['审稿邀请', list.filter(x => x.status === 'PENDING').length, Inbox, '/workbench'], ['进行中', list.filter(x => x.status === 'ACCEPTED').length, Clock3, '/workbench'], ['已完成', list.filter(x => x.status === 'COMPLETED').length, FileCheck2, '/workbench'], ['全部任务', list.length, ListChecks, '/workbench']]
  }
  const list = data.value.manuscripts
  const month = new Date().toISOString().slice(0, 7)
  return [['待形式审查', list.filter(x => ['SUBMITTED','IN_FORMAT_REVIEW'].includes(x.status)).length, FileText, '/workbench'], ['待作决定', list.filter(x => x.status === 'PENDING_DECISION').length, Clock3, '/workbench'], ['本月出版', list.filter(x => x.status === 'PUBLISHED' && x.publishedAt?.startsWith(month)).length, FileCheck2, '/manuscripts'], ['AI 待确认', data.value.aiTasks.filter(x => x.status === 'SUCCESS').length, Bot, '/ai-center']]
})
const latest = computed(() => data.value.manuscripts[0])
const progress = computed(() => {
  if (primaryRole.value === 'REVIEWER') return 2
  const status = latest.value?.status
  if (status === 'DRAFT') return 0
  if (['SUBMITTED','IN_FORMAT_REVIEW'].includes(status)) return 1
  if (status === 'UNDER_REVIEW') return 2
  if (['PENDING_DECISION','REVISION_REQUIRED'].includes(status)) return 3
  if (['ACCEPTED','IN_PRODUCTION','PUBLISHED'].includes(status)) return 4
  return -1
})

async function load() {
  loading.value = true; error.value = ''
  try {
    if (primaryRole.value === 'AUTHOR') {
      data.value.manuscripts = await fetchAllPages(api, '/manuscripts/my')
    } else if (primaryRole.value === 'REVIEWER') data.value.assignments = await api.get('/reviews/my-assignments') || []
    else {
      const [manuscripts, tasks] = await Promise.all([fetchAllPages(api, '/manuscripts'), fetchAllPages(api, '/llm/tasks', { status: 'SUCCESS' })])
      data.value.manuscripts = manuscripts; data.value.aiTasks = tasks
    }
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <section class="dashboard-head"><div><span class="overline">{{ today }}</span><h1>{{ session.user?.realName }}，欢迎回来</h1><p>这是你的{{ roleName[primaryRole] || '个人' }}实时工作概览。</p></div><router-link class="button button-primary" to="/workbench">进入工作台 <ArrowRight :size="17" /></router-link></section>
  <p v-if="error" class="error-banner">{{ error }}</p>
  <section class="metric-grid"><router-link v-for="metric in metrics" :key="metric[0]" class="metric-card" :to="metric[3]"><component :is="metric[2]" :size="21" /><span>{{ metric[0] }}</span><strong>{{ loading ? '—' : metric[1] }}</strong><small>查看详情 →</small></router-link></section>
  <section class="dashboard-grid">
    <article class="panel wide-panel"><div class="panel-title"><div><span class="overline">WORKFLOW</span><h2>出版流程进度</h2></div><span class="status-pill blue">实时数据</span></div><p class="workflow-caption">{{ latest?.title || (primaryRole === 'REVIEWER' ? '当前审稿工作所在环节' : '暂无可展示稿件，以下为标准出版流程') }}</p><div class="pipeline"><template v-for="(step,index) in steps" :key="step"><div :class="{ done: progress >= 0 && index < progress, active: index === progress }"><b>0{{ index + 1 }}</b><span>{{ step }}</span></div><i v-if="index < steps.length - 1"></i></template></div></article>
    <article class="panel ai-panel"><Bot :size="28" /><span class="overline">AI ASSISTANT</span><h2>大模型辅助，但不替代判断</h2><p>抽取、汇总与解释结果均保留原始输出，经人工确认后才能进入正式流程。</p><router-link :to="['EDITOR','ADMIN'].includes(primaryRole) ? '/ai-center' : '/workbench'">查看相关任务 <ArrowRight :size="15" /></router-link></article>
  </section>
</template>
