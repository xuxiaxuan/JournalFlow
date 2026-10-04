<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { FilePlus2, RefreshCw, Search, Trash2, Undo2, Upload, X } from 'lucide-vue-next'
import api from '../api'
import { hasRole } from '../session'

const rows = ref([])
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const error = ref('')
const notice = ref('')
const categories = ref([])
const tags = ref([])
const showCreate = ref(false)
const uploadTarget = ref(null)
const copyrightTarget = ref(null)
const file = ref(null)
const saving = ref(false)
const dialogError = ref('')
const editingId = ref(null)
const uploadProgress = ref(null)
const related = ref({ tagIds: [], coAuthors: [], submitterCorresponding: true })
const page = ref(1)
const total = ref(0)
const size = 15
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))
const form = ref({ title: '', abstract_: '', keywords: '', categoryId: '' })
const revision = ref({ changeNote: '', responseToReviewers: '' })
const copyrightAgreement = ref('本人确认拥有该稿件的合法著作权，同意以 CC-BY-4.0 许可协议发表，并授权期刊进行编辑、汇编及数字化传播。')
const endpoint = computed(() => hasRole('AUTHOR') && !hasRole('EDITOR') && !hasRole('ADMIN') ? '/manuscripts/my' : '/manuscripts')

const statusMap = {
  DRAFT: '草稿', SUBMITTED: '已投稿', IN_FORMAT_REVIEW: '形式审查', UNDER_REVIEW: '专家审稿',
  PENDING_DECISION: '待决定', REVISION_REQUIRED: '待修回', ACCEPTED: '已录用',
  IN_PRODUCTION: '出版加工', PUBLISHED: '已出版', REJECTED: '已退稿', WITHDRAWN: '已撤稿'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await api.get(endpoint.value, { params: { page: page.value, size, keyword: keyword.value || undefined, status: status.value || undefined } })
    rows.value = data.list || []
    total.value = data.total || 0
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function search() { page.value = 1; load() }
function move(delta) { const next = page.value + delta; if (next >= 1 && next <= pages.value) { page.value = next; load() } }

async function loadCategories() {
  if (hasRole('AUTHOR')) {
    categories.value = await api.get('/reference/categories') || []
    try { tags.value = await api.get('/reference/tags') || [] }
    catch (e) { notice.value = `标签暂不可用：${e.message}` }
  }
}

async function createManuscript() {
  if (saving.value) return
  saving.value = true
  dialogError.value = ''
  try {
    if (file.value) validateFile(file.value)
    const payload = { ...form.value, categoryId: Number(form.value.categoryId), ...related.value }
    if (editingId.value) await api.put(`/manuscripts/${editingId.value}`, payload)
    else editingId.value = (await api.post('/manuscripts', payload)).id
    if (file.value) {
      try { await uploadVersion(editingId.value, false) }
      catch (e) {
        uploadTarget.value = { id: editingId.value, title: form.value.title, status: 'DRAFT' }
        showCreate.value = false
        dialogError.value = `草稿已保存，上传未确认成功：${e.message}。可重试；若提示状态已变更，请关闭弹窗刷新列表。`
        await load()
        return
      }
    }
    notice.value = file.value ? '稿件已提交' : '草稿已保存'
    showCreate.value = false
    form.value = { title: '', abstract_: '', keywords: '', categoryId: '' }
    file.value = null
    await load()
  } catch (e) { dialogError.value = e.message } finally { saving.value = false }
}

function openCreate() {
  editingId.value = null; file.value = null; dialogError.value = ''; uploadProgress.value = null
  related.value = { tagIds: [], coAuthors: [], submitterCorresponding: true }
  form.value = { title: '', abstract_: '', keywords: '', categoryId: '' }
  showCreate.value = true
}

async function editDraft(row) {
  try {
    const detail = await api.get(`/manuscripts/${row.id}`)
    const m = detail.manuscript
    openCreate()
    editingId.value = row.id
    form.value = { title: m.title, abstract_: m.abstract || m.abstract_ || '', keywords: m.keywords || '', categoryId: m.categoryId }
    related.value = {
      tagIds: (detail.tags || []).map(tag => tag.id),
      submitterCorresponding: (detail.authors || []).find(author => author.authorOrder === 1)?.isCorresponding !== 0,
      coAuthors: (detail.authors || []).filter(author => author.authorOrder !== 1).map(author => ({
        userId: author.userId, name: author.authorName, affiliation: author.affiliation, corresponding: author.isCorresponding === 1
      }))
    }
  } catch (e) { error.value = e.message }
}

function validateFile(selected) {
  if (!selected?.size) throw new Error('请选择非空稿件文件')
  if (selected.size > 20 * 1024 * 1024) throw new Error('文件大小不能超过 20MB')
  if (!/\.(pdf|doc|docx|tex|zip)$/i.test(selected.name)) throw new Error('支持 PDF、DOC、DOCX、TeX、ZIP 文件')
}

function selectFile(event) {
  file.value = null; dialogError.value = ''; uploadProgress.value = null
  const selected = event.target.files[0]
  if (!selected) return
  try { validateFile(selected); file.value = selected }
  catch (e) { dialogError.value = e.message; event.target.value = '' }
}

function openUpload(row) {
  dialogError.value = ''; uploadProgress.value = null
  uploadTarget.value = row
  file.value = null
  revision.value = { changeNote: '', responseToReviewers: '' }
}

async function uploadVersion(id = uploadTarget.value?.id, isRevision = uploadTarget.value?.status === 'REVISION_REQUIRED') {
  if (!file.value) throw new Error('请选择稿件文件')
  validateFile(file.value)
  const selected = file.value
  let hash = null
  if (globalThis.crypto?.subtle) {
    const bytes = await new Promise((resolve, reject) => {
      const reader = new FileReader(); reader.onload = () => resolve(reader.result)
      reader.onerror = () => reject(new Error('无法读取所选文件')); reader.readAsArrayBuffer(selected)
    })
    hash = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), value => value.toString(16).padStart(2, '0')).join('')
  }
  const before = await api.get(`/manuscripts/${id}`)
  const expectedStatus = isRevision ? 'REVISION_REQUIRED' : 'DRAFT'
  const matches = detail => hash && detail.currentVersion?.fileHash?.toLowerCase() === hash
    && detail.currentVersion?.fileSize === selected.size
  if (before.manuscript?.status !== expectedStatus) {
    if (matches(before) && !['DRAFT', 'REVISION_REQUIRED', 'WITHDRAWN'].includes(before.manuscript?.status)) return
    throw new Error('稿件状态已变更，请关闭弹窗并刷新列表')
  }
  uploadProgress.value = 0
  const config = { timeout: 120000, onUploadProgress: event => {
    if (event.total) uploadProgress.value = Math.round(event.loaded * 100 / event.total)
  } }
  const data = new FormData()
  data.append('file', file.value)
  try {
    if (isRevision) {
      data.append('changeNote', revision.value.changeNote)
      data.append('responseToReviewers', revision.value.responseToReviewers)
      await api.post(`/manuscripts/${id}/revisions`, data, config)
    } else {
      await api.post(`/manuscripts/${id}/submit`, data, config)
    }
  } catch (e) {
    try {
      const after = await api.get(`/manuscripts/${id}`)
      if (matches(after) && after.currentVersion.id !== before.currentVersion?.id
          && !['DRAFT', 'REVISION_REQUIRED', 'WITHDRAWN'].includes(after.manuscript?.status)) return
    } catch { /* Keep the original error if the status check is unavailable. */ }
    throw new Error(`${e.message}；尚未确认上传成功，重试前将自动核对稿件状态`)
  }
}

async function submitUpload() {
  if (saving.value) return
  saving.value = true
  dialogError.value = ''
  try {
    await uploadVersion()
    notice.value = uploadTarget.value.status === 'REVISION_REQUIRED' ? '修回稿已提交' : '稿件已正式提交'
    uploadTarget.value = null
    await load()
  } catch (e) { dialogError.value = e.message } finally { saving.value = false }
}

async function signCopyright() {
  if (saving.value) return
  saving.value = true
  dialogError.value = ''
  try {
    await api.post(`/manuscripts/${copyrightTarget.value.id}/copyright`, { licenseType: 'CC-BY-4.0', agreementText: copyrightAgreement.value })
    notice.value = '版权协议已由作者本人签署并完成哈希留痕'
    copyrightTarget.value = null
    await load()
  } catch (e) { dialogError.value = e.message } finally { saving.value = false }
}

watch(copyrightTarget, value => { if (value) dialogError.value = '' })

function moveAuthor(index, delta) {
  const target = index + delta
  if (target < 0 || target >= related.value.coAuthors.length) return
  const author = related.value.coAuthors.splice(index, 1)[0]
  related.value.coAuthors.splice(target, 0, author)
}

async function deleteDraft(row) {
  if (!confirm(`确定删除草稿《${row.title}》？`)) return
  try { await api.delete(`/manuscripts/${row.id}`); notice.value = '草稿已删除'; await load() }
  catch (e) { error.value = e.message }
}

async function withdraw(row) {
  if (!confirm(`确定撤回稿件《${row.title}》？撤回后不能恢复。`)) return
  try { await api.post(`/manuscripts/${row.id}/withdraw`); notice.value = '稿件已撤回'; await load() }
  catch (e) { error.value = e.message }
}

onMounted(() => Promise.all([load(), loadCategories()]).catch((e) => { error.value = e.message }))
</script>

<template>
  <section class="page-heading">
    <div><span class="overline">MANUSCRIPTS</span><h1>稿件管理</h1><p>查看稿件当前环节、版本与最近更新时间。</p></div>
    <button v-if="hasRole('AUTHOR')" class="button button-primary" @click="openCreate"><FilePlus2 :size="17" />新建投稿</button>
  </section>
  <p v-if="notice" class="success-banner">{{ notice }}</p>
  <section class="panel table-panel">
    <form class="filter-bar" @submit.prevent="search">
      <div class="filter-input"><Search :size="17" /><input v-model="keyword" placeholder="搜索稿件标题" /></div>
      <select v-model="status"><option value="">全部状态</option><option v-for="(label, value) in statusMap" :key="value" :value="value">{{ label }}</option></select>
      <button class="button button-secondary"><RefreshCw :size="16" />刷新</button>
    </form>
    <div v-if="loading" class="empty-state">正在读取稿件…</div>
    <div v-else-if="error" class="empty-state error-message">{{ error }}</div>
    <div v-else class="table-wrap">
      <table>
        <thead><tr><th>稿件</th><th>分类</th><th>当前状态</th><th>版本</th><th>更新时间</th><th v-if="hasRole('AUTHOR')">操作</th></tr></thead>
        <tbody>
          <tr v-for="row in rows" :key="row.id">
            <td><router-link class="manuscript-link" :to="`/manuscripts/${row.id}`"><strong>{{ row.title }}</strong></router-link><small>#{{ String(row.id).padStart(4, '0') }}</small></td>
            <td>{{ row.categoryName || '—' }}</td>
            <td><span :class="['status-pill', ['PUBLISHED','ACCEPTED'].includes(row.status) ? 'green' : row.status === 'REJECTED' ? 'red' : 'blue']">{{ statusMap[row.status] || row.status }}</span></td>
            <td>v{{ row.versionNo || row.currentVersionNo || 1 }}</td>
            <td>{{ row.updatedAt || row.createdAt || '—' }}</td>
            <td v-if="hasRole('AUTHOR')">
              <button v-if="row.status === 'DRAFT'" class="button button-small button-secondary" @click="editDraft(row)">编辑草稿</button>
              <div class="row-actions"><button v-if="row.status === 'DRAFT' || row.status === 'REVISION_REQUIRED'" class="button button-small button-secondary" @click="openUpload(row)"><Upload :size="14" />{{ row.status === 'DRAFT' ? '提交' : '修回' }}</button><button v-if="row.status === 'DRAFT'" class="button button-small button-danger" @click="deleteDraft(row)"><Trash2 :size="14" />删除</button><button v-if="['SUBMITTED','IN_FORMAT_REVIEW','UNDER_REVIEW','PENDING_DECISION','REVISION_REQUIRED'].includes(row.status)" class="button button-small button-danger" @click="withdraw(row)"><Undo2 :size="14" />撤回</button><button v-if="row.status === 'ACCEPTED' && !row.copyrightSigned" class="button button-small button-primary" @click="copyrightTarget = row">签署版权</button><span v-if="row.copyrightSigned" class="status-pill green">版权已签署</span></div>
            </td>
          </tr>
          <tr v-if="!rows.length"><td :colspan="hasRole('AUTHOR') ? 6 : 5" class="empty-cell">暂无符合条件的稿件</td></tr>
        </tbody>
      </table>
    </div>
    <div v-if="total > size" class="pager"><span>共 {{ total }} 条 · 第 {{ page }}/{{ pages }} 页</span><div><button class="button button-small button-secondary" :disabled="page <= 1" @click="move(-1)">上一页</button><button class="button button-small button-secondary" :disabled="page >= pages" @click="move(1)">下一页</button></div></div>
  </section>

  <div v-if="showCreate" class="modal-backdrop" @click.self="!saving && (showCreate = false)">
    <form class="modal-card" @submit.prevent="createManuscript">
      <div class="modal-title"><div><span class="overline">SUBMISSION</span><h2>{{ editingId ? '编辑草稿' : '新建投稿' }}</h2></div><button type="button" class="icon-button" :disabled="saving" @click="showCreate = false"><X /></button></div>
      <p v-if="dialogError" role="alert" class="error-message">{{ dialogError }}</p>
      <p v-if="saving && uploadProgress !== null" role="status">已传输 {{ uploadProgress }}%，等待服务器确认…</p>
      <fieldset :disabled="saving" style="border:0;padding:0;margin:0;display:contents">
      <label>稿件标题<input v-model="form.title" required maxlength="200" placeholder="请输入完整论文标题" /></label>
      <label>摘要<textarea v-model="form.abstract_" rows="5" placeholder="请输入论文摘要"></textarea></label>
      <div class="form-grid"><label>关键词<input v-model="form.keywords" maxlength="500" placeholder="使用分号分隔" /></label><label>学科分类<select v-model="form.categoryId" required><option value="" disabled>请选择分类</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select></label></div>
      <label class="consent-check"><input v-model="related.submitterCorresponding" type="checkbox" />投稿人为通讯作者</label>
      <div class="panel">
        <p>共同作者（投稿人为第一作者，以下按排列顺序署名）</p>
        <div v-for="(author, index) in related.coAuthors" :key="index" class="author-editor">
          <div class="form-grid"><label>作者姓名<input v-model.trim="author.name" :readonly="!!author.userId" required maxlength="100" /></label><label>作者单位<input v-model.trim="author.affiliation" maxlength="200" /></label></div>
          <label class="consent-check"><input v-model="author.corresponding" type="checkbox" />通讯作者</label>
          <div class="row-actions"><button type="button" class="button button-small button-secondary" :disabled="index === 0" @click="moveAuthor(index, -1)">上移</button><button type="button" class="button button-small button-secondary" :disabled="index === related.coAuthors.length - 1" @click="moveAuthor(index, 1)">下移</button><button type="button" class="button button-small button-danger" @click="related.coAuthors.splice(index, 1)">移除作者</button></div>
        </div>
        <button type="button" class="button button-small button-secondary" :disabled="related.coAuthors.length >= 30" @click="related.coAuthors.push({ name: '', affiliation: '', corresponding: false })">添加共同作者</button>
      </div>
      <label>标签（可多选）<select v-model="related.tagIds" multiple aria-label="稿件标签"><option v-for="tag in tags" :key="tag.id" :value="tag.id">{{ tag.name }}</option></select></label>
      <label>稿件文件（可稍后上传）<input type="file" accept=".pdf,.doc,.docx,.tex,.zip" @change="selectFile" /></label>
      <small>最大 20MB；DOC、ZIP 不支持直接提取正文，AI 将使用表单信息。</small>
      </fieldset>
      <div class="modal-actions"><button type="button" class="button button-secondary" :disabled="saving" @click="showCreate = false">取消</button><button class="button button-primary" :disabled="saving">{{ saving ? '正在保存…' : file ? '保存并投稿' : '保存草稿' }}</button></div>
    </form>
  </div>

  <div v-if="uploadTarget" class="modal-backdrop" @click.self="!saving && (uploadTarget = null)">
    <form class="modal-card compact-modal" @submit.prevent="submitUpload">
      <div class="modal-title"><div><span class="overline">UPLOAD VERSION</span><h2>{{ uploadTarget.status === 'REVISION_REQUIRED' ? '提交修回稿' : '正式投稿' }}</h2></div><button type="button" class="icon-button" :disabled="saving" @click="uploadTarget = null"><X /></button></div>
      <p v-if="dialogError" role="alert" class="error-message">{{ dialogError }}</p>
      <p v-if="saving && uploadProgress !== null" role="status">已传输 {{ uploadProgress }}%，等待服务器确认…</p>
      <p class="muted">{{ uploadTarget.title }}</p>
      <label>稿件文件<input type="file" :required="!file" :disabled="saving" accept=".pdf,.doc,.docx,.tex,.zip" @change="selectFile" /></label>
      <small>最大 20MB；当前选择：{{ file?.name || '未选择' }}。DOC、ZIP 不支持直接提取正文。</small>
      <template v-if="uploadTarget.status === 'REVISION_REQUIRED'"><label>修改说明<input v-model="revision.changeNote" placeholder="概括本次修改内容" /></label><label>给审稿人的回复<textarea v-model="revision.responseToReviewers" rows="5" placeholder="逐条回应审稿意见"></textarea></label></template>
      <div class="modal-actions"><button type="button" class="button button-secondary" :disabled="saving" @click="uploadTarget = null">取消</button><button class="button button-primary" :disabled="saving"><Upload :size="16" />{{ saving ? '正在上传…' : '确认提交' }}</button></div>
    </form>
  </div>

  <div v-if="copyrightTarget" class="modal-backdrop" @click.self="!saving && (copyrightTarget = null)">
    <form class="modal-card compact-modal" @submit.prevent="signCopyright">
      <div class="modal-title"><div><span class="overline">COPYRIGHT AGREEMENT</span><h2>作者版权签署</h2></div><button type="button" class="icon-button" :disabled="saving" @click="copyrightTarget = null"><X /></button></div>
      <p v-if="dialogError" class="error-message" role="alert">{{ dialogError }}</p>
      <p class="muted">{{ copyrightTarget.title }}</p>
      <div class="copyright-note"><strong>CC-BY-4.0</strong><span>允许他人在署名的前提下共享与演绎本文。</span></div>
      <label>协议内容<textarea v-model="copyrightAgreement" rows="7" required></textarea></label>
      <label class="consent-check"><input type="checkbox" required />我确认以上内容真实有效，并由本人完成签署</label>
      <div class="modal-actions"><button type="button" class="button button-secondary" :disabled="saving" @click="copyrightTarget = null">取消</button><button class="button button-primary" :disabled="saving">{{ saving ? '正在签署…' : '确认签署' }}</button></div>
    </form>
  </div>
</template>
