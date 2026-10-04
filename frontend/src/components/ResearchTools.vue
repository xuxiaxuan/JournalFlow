<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api'

const props = defineProps({ manuscriptId: { type: Number, required: true }, versions: { type: Array, default: () => [] }, canRespond: Boolean, canConfirm: Boolean })
const from = ref(props.versions.at(-2)?.id || props.versions[0]?.id || '')
const to = ref(props.versions.at(-1)?.id || '')
const comparison = ref(null), items = ref([]), busy = ref(false), error = ref(''), notice = ref(''), showSame = ref(false)
const stateNames = { TODO: '待回应', RESPONDED: '已回应，待编辑确认', CONFIRMED: '编辑已确认' }
const changes = computed(() => comparison.value?.changes.filter(row => showSame.value || row.type !== 'SAME') || [])
const stats = computed(() => ({ added: comparison.value?.changes.filter(row => row.type === 'ADDED').length || 0, removed: comparison.value?.changes.filter(row => row.type === 'REMOVED').length || 0 }))

async function load(savedItem = null) {
  const data = await api.get(`/manuscripts/${props.manuscriptId}/responses`)
  items.value = data.map(item => {
    const previous = items.value.find(row => row.reportId === item.reportId && row.issueIndex === item.issueIndex)
    // Keep other in-progress replies when saving one item, including their base revision.
    if (savedItem && previous && (item.reportId !== savedItem.reportId || item.issueIndex !== savedItem.issueIndex)) return previous
    return { ...item, responseText: item.latest?.responseText || '', changeLocation: item.latest?.changeLocation || '', versionId: item.latest?.versionId || '' }
  })
}
async function compare() {
  if (busy.value) return
  busy.value = true; error.value = ''; comparison.value = null
  try { comparison.value = await api.get(`/manuscripts/${props.manuscriptId}/compare`, { params: { from: from.value, to: to.value } }) }
  catch (e) { error.value = e.message } finally { busy.value = false }
}
async function act(item, confirm = false) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    if (confirm) await api.post(`/manuscripts/${props.manuscriptId}/responses/${item.latest.id}/confirm`)
    else await api.post(`/manuscripts/${props.manuscriptId}/responses`, {
      reportId: item.reportId, issueIndex: item.issueIndex, responseText: item.responseText,
      changeLocation: item.changeLocation, versionId: item.versionId ? Number(item.versionId) : null, baseId: item.latest?.id || null
    })
    notice.value = confirm ? '编辑确认已留痕' : '回应已保存；修改后需重新由编辑确认'
    await load(item)
  } catch (e) { error.value = e.message } finally { busy.value = false }
}
onMounted(async () => { try { await load() } catch (e) { error.value = e.message } })
</script>

<template>
  <section class="panel manuscript-section research-tools">
    <h2>修回辅助工具</h2>
    <p class="muted">版本对比与逐条回应仅供人工核对，不改变稿件流程，也不作为投稿、录用或出版的必填条件。</p>
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <h3>正文版本对比</h3>
    <div v-if="versions.length >= 2" class="compare-controls">
      <label>原版本<select v-model="from" aria-label="原版本"><option v-for="v in versions" :key="v.id" :value="v.id">v{{ v.versionNo }}</option></select></label>
      <label>新版本<select v-model="to" aria-label="新版本"><option v-for="v in versions" :key="v.id" :value="v.id">v{{ v.versionNo }}</option></select></label>
      <button class="button button-secondary" :disabled="busy || from === to" @click="compare">比较正文</button>
    </div>
    <p v-else class="muted">至少需要两个稿件版本才能对比正文。</p>
    <div v-if="comparison">
      <p>新增 {{ stats.added }} 行 / 删除 {{ stats.removed }} 行</p>
      <p class="muted">{{ comparison.scope }}{{ comparison.limited ? '；本次内容超出范围，对比结果已截断。' : '' }}</p>
      <label><input v-model="showSame" type="checkbox" />显示未改动行</label>
      <p v-if="!stats.added && !stats.removed">提取范围内正文没有变化。</p>
      <div class="diff-list"><p v-for="(row, index) in changes" :key="index" :class="row.type"><small>{{ row.type === 'ADDED' ? '+' : row.type === 'REMOVED' ? '−' : '=' }} {{ row.oldLine || '—' }} → {{ row.newLine || '—' }}</small>{{ row.text }}</p></div>
    </div>
    <h3>审稿意见逐条回应</h3>
    <p class="muted">按给作者的审稿意见换行拆分；意见原文保持不变。修改位置可填写章节、页码或段落。</p>
    <p v-if="!items.length" class="muted">当前身份暂无已公开的审稿意见。</p>
    <article v-for="item in items" :key="`${item.reportId}-${item.issueIndex}`" class="response-item">
      <strong>意见 #{{ item.reportId }} · 第 {{ item.issueIndex + 1 }} 条</strong>
      <blockquote>{{ item.issueText }}</blockquote>
      <span class="status-pill">{{ stateNames[item.latest?.status || 'TODO'] }}</span>
      <label>逐条回复<textarea v-model="item.responseText" :readonly="!canRespond || busy" maxlength="5000" rows="3" /></label>
      <label>修改位置<input v-model="item.changeLocation" :readonly="!canRespond || busy" maxlength="1000" /></label>
      <label>关联修改版本<select v-model="item.versionId" :disabled="!canRespond || busy"><option value="">暂不关联</option><option v-for="v in versions" :key="v.id" :value="v.id">v{{ v.versionNo }} · {{ v.fileName }}</option></select></label>
      <div class="row-actions"><button v-if="canRespond" class="button button-small button-primary" :disabled="busy" @click="act(item)">保存回应</button><button v-if="canConfirm && item.latest?.status === 'RESPONDED'" class="button button-small button-secondary" :disabled="busy" @click="act(item, true)">确认此回应</button></div>
      <details v-if="item.history.length"><summary>查看 {{ item.history.length }} 条历史记录</summary><div v-for="entry in item.history" :key="entry.id" class="history-entry"><small>{{ entry.createdAt }} · {{ stateNames[entry.status] }} · 版本 #{{ entry.versionId || '未关联' }}</small><p>{{ entry.responseText || '尚未填写' }}</p><p>{{ entry.changeLocation }}</p></div></details>
    </article>
  </section>
</template>

<style scoped>
.compare-controls{display:flex;flex-wrap:wrap;gap:12px;align-items:end;margin-bottom:16px}
label{display:flex;flex-direction:column;gap:6px;margin:12px 0;font-size:13px}
input,textarea,select{border:1px solid #d3dbd5;border-radius:8px;padding:9px;background:white;max-width:100%}
input[type=checkbox]{width:auto} .diff-list{max-height:450px;overflow:auto;margin:12px 0}
.diff-list p{padding:10px;white-space:pre-wrap;overflow-wrap:anywhere;margin:2px 0}
.diff-list small{display:block;color:#56645b;margin-bottom:4px}.ADDED{background:#e5f3e9}.REMOVED{background:#fbe8e3}.SAME{background:#f1f3f0}
.response-item{border-top:1px solid #dce3dc;padding:20px 0}.response-item blockquote{white-space:pre-wrap;margin:12px 0;border-left:3px solid #638775;padding-left:12px}
.history-entry{background:#f0f3ed;padding:12px;margin:8px 0;white-space:pre-wrap;overflow-wrap:anywhere}
</style>
