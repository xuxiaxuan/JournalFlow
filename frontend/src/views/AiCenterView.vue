<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Bot, CheckCircle2, Eye, Play, RefreshCw, ShieldCheck, X } from 'lucide-vue-next'
import api from '../api'
import { fetchAllPages } from '../data'
import { hasRole } from '../session'
import AiEvidence from '../components/AiEvidence.vue'

const tasks = ref([])
const manuscripts = ref([])
const selected = ref(null)
const showCreate = ref(false)
const form = ref({ scene: 'EXTRACT', manuscriptId: '' })
const confirmedOutput = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const filters = ref({ scene: '', status: '' })
const isEditor = computed(() => hasRole('EDITOR') || hasRole('ADMIN'))
const page = ref(1)
const total = ref(0)
const size = 20
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
let pollTimer = null

const scenes = { EXTRACT: '元数据抽取', SUMMARY: '审稿意见汇总', EXPLAIN: '审稿意见解释' }
const availableScenes = computed(() => isEditor.value ? scenes : { EXPLAIN: scenes.EXPLAIN })
const statuses = { RUNNING: '运行中', SUCCESS: '待人工确认', FAILED: '执行失败', CONFIRMED: '已确认' }

function pretty(value) {
  if (!value) return ''
  try { return JSON.stringify(typeof value === 'string' ? JSON.parse(value) : value, null, 2) } catch { return String(value) }
}

async function load(silent = false) {
  if (!silent) loading.value = true
  error.value = ''
  try {
    const [taskPage, manuscriptPage] = await Promise.all([
      api.get('/llm/tasks', { params: { page: page.value, size, scene: filters.value.scene || undefined, status: filters.value.status || undefined } }),
      silent ? Promise.resolve(manuscripts.value) : fetchAllPages(api, isEditor.value ? '/manuscripts' : '/manuscripts/my')
    ])
    tasks.value = taskPage.list || []
    total.value = taskPage.total || 0
    manuscripts.value = manuscriptPage
    if (selected.value?.status === 'RUNNING') {
      selected.value = await api.get(`/llm/tasks/${selected.value.id}`)
      confirmedOutput.value = pretty(selected.value.confirmedOutput || selected.value.parsedOutput || selected.value.rawOutput)
    }
  } catch (e) { error.value = e.message } finally {
    if (!silent) loading.value = false
    schedulePoll()
  }
}

function search() { page.value = 1; load() }
function move(delta) { const next = page.value + delta; if (next >= 1 && next <= pages.value) { page.value = next; load() } }

function schedulePoll() {
  if (pollTimer) clearTimeout(pollTimer)
  if (tasks.value.some((task) => task.status === 'RUNNING') || selected.value?.status === 'RUNNING') {
    pollTimer = setTimeout(() => load(true), 1500)
  }
}

async function createTask() {
  saving.value = true
  error.value = ''
  try {
    const task = await api.post('/llm/tasks', { scene: form.value.scene, manuscriptId: Number(form.value.manuscriptId) })
    showCreate.value = false
    notice.value = `AI 任务 #${task.id} 已进入后台处理，完成后会发送通知`
    await load()
    await openTask(task.id)
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function openTask(id) {
  try {
    selected.value = await api.get(`/llm/tasks/${id}`)
    confirmedOutput.value = pretty(selected.value.confirmedOutput || selected.value.parsedOutput || selected.value.rawOutput)
  } catch (e) { error.value = e.message }
}

async function confirmTask() {
  saving.value = true
  try {
    selected.value = await api.post(`/llm/tasks/${selected.value.id}/confirm`, { confirmedOutput: confirmedOutput.value })
    notice.value = '人工确认已保存；元数据抽取结果已同步到稿件'
    await load()
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function retryTask(task) {
  saving.value = true
  error.value = ''
  try {
    const retried = await api.post(`/llm/tasks/${task.id}/retry`)
    notice.value = `已创建重试任务 #${retried.id}`
    await load()
    await openTask(retried.id)
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

onMounted(() => { if (!isEditor.value) { form.value.scene = 'EXPLAIN'; filters.value.scene = 'EXPLAIN' }; load() })
onBeforeUnmount(() => { if (pollTimer) clearTimeout(pollTimer) })
</script>

<template>
  <section class="page-heading">
    <div><span class="overline">HUMAN IN THE LOOP</span><h1>大模型任务中心</h1><p>{{ isEditor ? '所有模型输出都需经过人工核对，原始结果与修订结果分别留存。' : '将审稿意见转换为通俗解释与修改清单，结果仅供作者参考。' }}</p></div>
    <button class="button button-primary" @click="showCreate = true"><Play :size="16" />发起 AI 任务</button>
  </section>
  <p v-if="notice" class="success-banner">{{ notice }}</p>
  <p v-if="error" class="error-banner">{{ error }}</p>
  <section class="ai-principles">
    <span><Bot />模板约束输出<small>固定输入模板与字段结构</small></span><i></i><span><ShieldCheck />保留原始结果<small>模型输出永不被覆盖</small></span><i></i><span><CheckCircle2 />人工确认生效<small>确认后才进入业务数据</small></span>
  </section>
  <section class="panel table-panel">
    <form class="filter-bar" @submit.prevent="search"><select v-model="filters.scene"><option v-if="isEditor" value="">全部场景</option><option v-for="(label,key) in availableScenes" :key="key" :value="key">{{ label }}</option></select><select v-model="filters.status"><option value="">全部状态</option><option v-for="(label,key) in statuses" :key="key" :value="key">{{ label }}</option></select><button class="button button-secondary"><RefreshCw :size="15" />筛选</button></form>
    <div v-if="loading" class="empty-state">正在加载 AI 任务…</div>
    <div v-else class="table-wrap"><table><thead><tr><th>任务</th><th>稿件</th><th>模型来源</th><th>状态</th><th>耗时</th><th>操作</th></tr></thead><tbody>
      <tr v-for="task in tasks" :key="task.id"><td><strong>{{ task.sceneLabel || scenes[task.scene] }}</strong><small>#{{ task.id }} · {{ task.templateId }}</small></td><td>{{ task.manuscriptTitle }}</td><td>{{ task.provider }} / {{ task.model }}</td><td><span :class="['status-pill', task.status === 'CONFIRMED' ? 'green' : task.status === 'FAILED' ? 'red' : 'blue']">{{ statuses[task.status] || task.status }}</span></td><td>{{ task.latencyMs == null ? '—' : `${task.latencyMs} ms` }}</td><td><div class="row-actions"><button class="button button-small button-secondary" @click="openTask(task.id)"><Eye :size="14" />核对</button><button v-if="task.status === 'FAILED'" class="button button-small button-primary" :disabled="saving" @click="retryTask(task)">重试</button></div></td></tr>
      <tr v-if="!tasks.length"><td colspan="6" class="empty-cell">暂无 AI 任务</td></tr>
    </tbody></table></div>
    <div v-if="total > size" class="pager"><span>共 {{ total }} 条 · 第 {{ page }}/{{ pages }} 页</span><div><button class="button button-small button-secondary" :disabled="page <= 1" @click="move(-1)">上一页</button><button class="button button-small button-secondary" :disabled="page >= pages" @click="move(1)">下一页</button></div></div>
  </section>

  <div v-if="showCreate" class="modal-backdrop" @click.self="showCreate = false"><form class="modal-card compact-modal" @submit.prevent="createTask"><div class="modal-title"><div><span class="overline">NEW AI TASK</span><h2>发起辅助任务</h2></div><button type="button" class="icon-button" @click="showCreate = false"><X /></button></div><label>处理场景<select v-model="form.scene"><option v-for="(label,key) in availableScenes" :key="key" :value="key">{{ label }}</option></select></label><label>目标稿件<select v-model="form.manuscriptId" required><option value="" disabled>请选择稿件</option><option v-for="m in manuscripts" :key="m.id" :value="m.id">#{{ m.id }} {{ m.title }}</option></select></label><p class="form-hint">{{ isEditor ? '抽取和汇总仅供编辑使用；模型结果不会直接改变编辑决定。' : '仅能解释本人稿件已有的审稿意见，不会自动修改稿件或影响编辑决定。' }}</p><div class="modal-actions"><button type="button" class="button button-secondary" @click="showCreate = false">取消</button><button class="button button-primary" :disabled="saving">{{ saving ? '模型处理中…' : '开始处理' }}</button></div></form></div>

  <div v-if="selected" class="modal-backdrop" @click.self="selected = null"><section class="modal-card ai-review-modal"><div class="modal-title"><div><span class="overline">TASK #{{ selected.id }} · {{ selected.templateId }}</span><h2>{{ scenes[selected.scene] }}结果{{ isEditor ? '核对' : '' }}</h2></div><button class="icon-button" @click="selected = null"><X /></button></div><div class="trace-meta"><span>Provider<strong>{{ selected.provider }}</strong></span><span>Model<strong>{{ selected.model }}</strong></span><span>Latency<strong>{{ selected.latencyMs }} ms</strong></span><span>Status<strong>{{ statuses[selected.status] }}</strong></span></div><div :class="isEditor ? 'ai-compare' : ''"><label>模型原始输出<textarea :value="pretty(selected.rawOutput)" rows="18" readonly></textarea></label><label v-if="isEditor">人工确认 / 修订结果<textarea v-model="confirmedOutput" rows="18" :readonly="selected.status !== 'SUCCESS'"></textarea></label></div><AiEvidence v-if="isEditor && selected.scene === 'EXTRACT'" :key="selected.id" :task-id="selected.id" /><p v-if="selected.errorMsg" class="error-banner">{{ selected.errorMsg }}</p><div class="modal-actions"><button class="button button-secondary" @click="selected = null">关闭</button><button v-if="isEditor && selected.status === 'SUCCESS'" class="button button-primary" :disabled="saving" @click="confirmTask"><CheckCircle2 :size="16" />{{ saving ? '正在确认…' : '确认并留痕' }}</button></div></section></div>
</template>
