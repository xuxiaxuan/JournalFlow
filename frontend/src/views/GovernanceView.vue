<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Gavel, RefreshCw, ShieldAlert, X } from 'lucide-vue-next'
import api from '../api'
import { hasRole } from '../session'

const tab = ref('reports'), rows = ref([]), page = ref(1), total = ref(0), status = ref('')
const route = useRoute(), router = useRouter()
const loading = ref(false), error = ref(''), notice = ref('')
const dialog = ref(''), handling = ref(null)
const reportForm = ref({ targetType: 'MANUSCRIPT', targetId: '', reasonType: 'CONTENT', description: '' })
const appealForm = ref({ sourceType: 'REPORT', sourceId: '', content: '' })
const handleForm = ref({ status: 'RESOLVED', text: '' })
const size = 10
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const isStaff = computed(() => hasRole('EDITOR') || hasRole('ADMIN'))
const labels = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '成立/已处置', DISMISSED: '已驳回', MANUSCRIPT: '稿件', COMMENT: '评论', USER: '账号' }

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.get(tab.value === 'reports' ? '/reports' : '/appeals', { params: { page: page.value, size, status: status.value || undefined } })
    rows.value = data.list || []; total.value = data.total || 0
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function switchTab(value) { tab.value = value; page.value = 1; status.value = ''; load() }
async function createReport() {
  try { await api.post('/reports', { ...reportForm.value, targetId: Number(reportForm.value.targetId) }); dialog.value = ''; notice.value = '举报工单已提交。'; await load() }
  catch (e) { error.value = e.message }
}
async function createAppeal() {
  const body = { content: appealForm.value.content }
  body[appealForm.value.sourceType === 'REPORT' ? 'reportId' : 'decisionId'] = Number(appealForm.value.sourceId)
  try { await api.post('/appeals', body); dialog.value = ''; notice.value = '申诉已提交，等待管理员终审。'; await router.replace({ path: '/governance' }); switchTab('appeals') }
  catch (e) { error.value = e.message }
}
function openHandle(item) { handling.value = item; handleForm.value = { status: 'RESOLVED', text: '' } }
async function submitHandle() {
  const report = tab.value === 'reports'
  const body = report ? { status: handleForm.value.status, resolution: handleForm.value.text } : { status: handleForm.value.status, result: handleForm.value.text }
  try { await api.post(`/${report ? 'reports' : 'appeals'}/${handling.value.id}/handle`, body); handling.value = null; notice.value = '处理结果已保存并留痕。'; await load() }
  catch (e) { error.value = e.message }
}
function move(delta) { const next = page.value + delta; if (next >= 1 && next <= pages.value) { page.value = next; load() } }
onMounted(() => {
  if (route.query.appealReport) {
    appealForm.value = { sourceType: 'REPORT', sourceId: String(route.query.appealReport), content: '' }
    dialog.value = 'appeal'
  } else if (route.query.appealDecision) {
    appealForm.value = { sourceType: 'DECISION', sourceId: String(route.query.appealDecision), content: '' }
    dialog.value = 'appeal'
  }
  load()
})
</script>

<template>
  <section class="page-heading"><div><span class="overline">CONTENT GOVERNANCE</span><h1>举报与申诉</h1><p>举报、处置、申诉和终审全程留痕。</p></div><div class="heading-actions"><button class="button button-secondary" @click="dialog = 'appeal'"><Gavel :size="16" />提交申诉</button><button class="button button-primary" @click="dialog = 'report'"><ShieldAlert :size="16" />发起举报</button></div></section>
  <p v-if="notice" class="success-banner">{{ notice }}</p><p v-if="error" class="error-banner">{{ error }}</p>
  <div class="tab-strip"><button :class="{ active: tab === 'reports' }" @click="switchTab('reports')">举报工单</button><button :class="{ active: tab === 'appeals' }" @click="switchTab('appeals')">申诉记录</button></div>
  <section class="panel table-panel">
    <form class="filter-bar" @submit.prevent="page = 1; load()"><select v-model="status"><option value="">全部状态</option><option value="PENDING">待处理</option><option value="PROCESSING">处理中</option><option value="RESOLVED">已处置/成立</option><option value="DISMISSED">已驳回</option></select><button class="button button-secondary"><RefreshCw :size="15" />筛选</button></form>
    <div v-if="loading" class="empty-state">正在加载治理记录…</div><div v-else class="table-wrap">
      <table v-if="tab === 'reports'"><thead><tr><th>工单</th><th>对象</th><th>举报内容</th><th>状态</th><th>处理结果</th><th>操作</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td><strong>#{{ item.id }} {{ item.reporterName || item.reporter_name || '' }}</strong><small>{{ item.createdAt || item.created_at }}</small></td><td>{{ labels[item.targetType || item.target_type] }} #{{ item.targetId || item.target_id }}</td><td>{{ item.reasonType || item.reason_type }}<small>{{ item.description }}</small></td><td><span :class="['status-pill', item.status === 'RESOLVED' ? 'green' : item.status === 'DISMISSED' ? 'red' : 'blue']">{{ labels[item.status] }}</span></td><td>{{ item.resolution || '—' }}</td><td><button v-if="isStaff && ['PENDING','PROCESSING'].includes(item.status)" class="button button-small button-primary" @click="openHandle(item)">处置</button></td></tr><tr v-if="!rows.length"><td colspan="6" class="empty-cell">暂无举报记录</td></tr></tbody></table>
      <table v-else><thead><tr><th>申诉</th><th>来源</th><th>申诉内容</th><th>状态</th><th>终审结果</th><th>操作</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td><strong>#{{ item.id }}</strong><small>{{ item.createdAt || item.created_at }}</small></td><td>{{ (item.reportId || item.report_id) ? `举报 #${item.reportId || item.report_id}` : `决定 #${item.decisionId || item.decision_id}` }}</td><td>{{ item.content }}</td><td><span :class="['status-pill', item.status === 'RESOLVED' ? 'green' : item.status === 'DISMISSED' ? 'red' : 'blue']">{{ labels[item.status] }}</span></td><td>{{ item.result || '—' }}</td><td><button v-if="hasRole('ADMIN') && item.status === 'PENDING'" class="button button-small button-primary" @click="openHandle(item)">终审</button></td></tr><tr v-if="!rows.length"><td colspan="6" class="empty-cell">暂无申诉记录</td></tr></tbody></table>
    </div><div class="pager"><span>共 {{ total }} 条 · 第 {{ page }}/{{ pages }} 页</span><div><button class="button button-small button-secondary" :disabled="page <= 1" @click="move(-1)">上一页</button><button class="button button-small button-secondary" :disabled="page >= pages" @click="move(1)">下一页</button></div></div>
  </section>
  <div v-if="dialog === 'report'" class="modal-backdrop" @click.self="dialog = ''"><form class="modal-card compact-modal" @submit.prevent="createReport"><div class="modal-title"><div><span class="overline">NEW REPORT</span><h2>发起举报</h2></div><button type="button" class="icon-button" @click="dialog = ''"><X /></button></div><div class="form-grid"><label>对象类型<select v-model="reportForm.targetType"><option value="MANUSCRIPT">稿件</option><option value="COMMENT">评论</option><option value="USER">用户</option></select></label><label>对象 ID<input v-model="reportForm.targetId" type="number" min="1" required /></label></div><label>原因<select v-model="reportForm.reasonType"><option value="CONTENT">内容违规</option><option value="COPYRIGHT">版权问题</option><option value="PLAGIARISM">疑似抄袭</option><option value="ABUSE">不当言论</option><option value="OTHER">其他</option></select></label><label>事实描述<textarea v-model.trim="reportForm.description" rows="5" maxlength="2000" required></textarea></label><div class="modal-actions"><button type="button" class="button button-secondary" @click="dialog = ''">取消</button><button class="button button-primary">提交举报</button></div></form></div>
  <div v-if="dialog === 'appeal'" class="modal-backdrop" @click.self="dialog = ''"><form class="modal-card compact-modal" @submit.prevent="createAppeal"><div class="modal-title"><div><span class="overline">NEW APPEAL</span><h2>提交申诉</h2></div><button type="button" class="icon-button" @click="dialog = ''"><X /></button></div><div class="form-grid"><label>来源<select v-model="appealForm.sourceType"><option value="REPORT">举报处置</option><option value="DECISION">退稿决定</option></select></label><label>来源 ID<input v-model="appealForm.sourceId" type="number" min="1" required /></label></div><label>申诉理由<textarea v-model.trim="appealForm.content" rows="6" maxlength="2000" required></textarea></label><p class="form-hint">退稿决定仅有一次申诉机会；举报须形成最终处置结果后方可申诉。</p><div class="modal-actions"><button type="button" class="button button-secondary" @click="dialog = ''">取消</button><button class="button button-primary">提交申诉</button></div></form></div>
  <div v-if="handling" class="modal-backdrop" @click.self="handling = null"><form class="modal-card compact-modal" @submit.prevent="submitHandle"><div class="modal-title"><div><span class="overline">FINAL ACTION</span><h2>{{ tab === 'reports' ? '举报处置' : '申诉终审' }}</h2></div><button type="button" class="icon-button" @click="handling = null"><X /></button></div><label>处理结论<select v-model="handleForm.status"><option value="RESOLVED">成立</option><option value="DISMISSED">驳回</option></select></label><label>处理说明<textarea v-model.trim="handleForm.text" rows="5" maxlength="2000" required></textarea></label><div class="modal-actions"><button type="button" class="button button-secondary" @click="handling = null">取消</button><button class="button button-primary">确认并留痕</button></div></form></div>
</template>
