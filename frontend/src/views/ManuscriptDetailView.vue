<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Download, FileClock, FileText, History, ShieldCheck, Users } from 'lucide-vue-next'
import { useRoute } from 'vue-router'
import api from '../api'
import { session } from '../session'
import { hasRole } from '../session'
import ResearchTools from '../components/ResearchTools.vue'

const route = useRoute()
const detail = ref(null), timeline = ref([]), loading = ref(true), error = ref(''), downloading = ref(null)
const manuscript = computed(() => detail.value?.manuscript || {})
const isSubmitter = computed(() => session.user?.id === manuscript.value.submitterId)
const statusMap = { DRAFT: '草稿', SUBMITTED: '已投稿', IN_FORMAT_REVIEW: '形式审查', UNDER_REVIEW: '专家审稿', PENDING_DECISION: '待决定', REVISION_REQUIRED: '待修回', ACCEPTED: '已录用', IN_PRODUCTION: '出版加工', PUBLISHED: '已出版', REJECTED: '已退稿', WITHDRAWN: '已撤稿' }
const decisionMap = { PASS: '形式审查通过', FAIL: '形式审查未通过', ACCEPT: '录用', REVISE: '退修', REJECT: '退稿', OVERTURN: '申诉成立', RETRACT: '治理下架', PUBLISHED: '正式出版归档' }
const value = (item, camel, snake) => item?.[camel] ?? item?.[snake]

async function load() {
  loading.value = true; error.value = ''
  try {
    const [data, events] = await Promise.all([api.get(`/manuscripts/${route.params.id}`), api.get(`/manuscripts/${route.params.id}/timeline`)])
    detail.value = data; timeline.value = events || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
async function downloadVersion(version) {
  downloading.value = version.id
  try {
    const response = await api.get(`/manuscripts/${route.params.id}/files/${version.id}`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = version.fileName || `version-${version.versionNo}`; anchor.click()
    URL.revokeObjectURL(url)
  } catch (e) { error.value = e.message } finally { downloading.value = null }
}
onMounted(load)
</script>

<template>
  <button class="text-button" @click="$router.back()"><ArrowLeft :size="16" />返回稿件列表</button>
  <div v-if="loading" class="empty-state">正在加载稿件完整记录…</div><p v-else-if="error" class="error-banner">{{ error }}</p>
  <template v-else-if="detail">
    <section class="manuscript-hero panel"><div><span class="overline">MANUSCRIPT #{{ manuscript.id }}</span><h1>{{ manuscript.title }}</h1><p>{{ manuscript.abstract_ || manuscript.abstract || '暂无摘要' }}</p><div class="keyword-row"><span v-for="word in (manuscript.keywords || '').split(/[;；,，]/).filter(Boolean)" :key="word">{{ word }}</span></div></div><aside><span :class="['status-pill', ['PUBLISHED','ACCEPTED'].includes(manuscript.status) ? 'green' : manuscript.status === 'REJECTED' ? 'red' : 'blue']">{{ statusMap[manuscript.status] }}</span><dl><div><dt>学科</dt><dd>{{ detail.category?.name || '—' }}</dd></div><div><dt>当前版本</dt><dd>v{{ detail.currentVersion?.versionNo || '—' }}</dd></div><div><dt>DOI</dt><dd>{{ manuscript.doi || '—' }}</dd></div><div><dt>出版信息</dt><dd>{{ manuscript.volume ? `${manuscript.volume}卷 ${manuscript.issue}期` : '—' }}</dd></div></dl></aside></section>

    <section class="detail-section-grid">
      <article class="panel"><div class="panel-title"><div><span class="overline">AUTHORS</span><h2>作者与标签</h2></div><Users /></div><div class="author-record" v-for="author in detail.authors || []" :key="author.id"><b>{{ author.authorOrder }}</b><span><strong>{{ author.authorName }}</strong><small>{{ author.affiliation || '未填写单位' }}{{ author.isCorresponding === 1 ? ' · 通讯作者' : '' }}</small></span></div><div class="keyword-row"><span v-for="tag in detail.tags || []" :key="tag.id">{{ tag.name }}</span></div></article>
      <article class="panel"><div class="panel-title"><div><span class="overline">COPYRIGHT</span><h2>版权存证</h2></div><ShieldCheck /></div><div v-for="record in detail.copyrightRecords || []" :key="record.id" class="record-line"><strong>{{ record.licenseType }}</strong><span>v{{ (detail.versions || []).find(v => v.id === record.versionId)?.versionNo || '?' }} · {{ record.signedAt }}</span><small>SHA-256：{{ record.agreementHash }}</small></div><p v-if="!detail.copyrightRecords?.length" class="muted">尚未签署版权协议</p></article>
    </section>

    <ResearchTools v-if="isSubmitter || hasRole('EDITOR') || hasRole('ADMIN') || detail.assignments?.length" :key="manuscript.id" :manuscript-id="manuscript.id" :versions="detail.versions || []" :can-respond="isSubmitter" :can-confirm="hasRole('EDITOR') || hasRole('ADMIN')" />

    <section class="panel manuscript-section"><div class="panel-title"><div><span class="overline">VERSIONS</span><h2>稿件版本</h2></div><FileClock /></div><div class="version-grid"><article v-for="version in detail.versions || []" :key="version.id"><span class="version-number">v{{ version.versionNo }}</span><div><strong>{{ version.fileName }}</strong><small>{{ version.createdAt }} · {{ version.fileSize }} bytes</small><p v-if="version.changeNote">{{ version.changeNote }}</p><code>{{ version.fileHash }}</code></div><button class="button button-small button-secondary" :disabled="downloading === version.id" @click="downloadVersion(version)"><Download :size="14" />{{ downloading === version.id ? '下载中' : '下载' }}</button></article></div></section>
    <section v-if="detail.metadataRevisions?.length" class="panel manuscript-section"><div class="panel-title"><div><span class="overline">AI METADATA TRACE</span><h2>元数据人工确认记录</h2></div><History /></div><div class="version-grid"><article v-for="record in detail.metadataRevisions" :key="record.id"><span class="version-number">AI</span><div><strong>任务 #{{ record.llmTaskId }} · 版本 #{{ record.versionId }}</strong><small>{{ record.createdAt }}</small><p>修改前：{{ record.beforeData }}</p><p>确认后：{{ record.afterData }}</p></div></article></div></section>

    <section class="detail-section-grid">
      <article class="panel"><div class="panel-title"><div><span class="overline">DECISIONS</span><h2>编辑决定</h2></div><FileText /></div><div v-for="item in detail.decisions || []" :key="item.id" class="record-line"><strong>{{ decisionMap[item.decision] || item.decision }}</strong><span>{{ value(item,'deciderName','decider_name') }} · {{ value(item,'decidedAt','decided_at') }}</span><p>{{ item.reason || '未填写原因' }}</p><small v-if="value(item,'summaryReport','summary_report')">审稿汇总：{{ value(item,'summaryReport','summary_report') }}</small><router-link v-if="isSubmitter && item.stage === 'REVIEW' && item.decision === 'REJECT'" class="button button-small button-secondary" :to="{ path: '/governance', query: { appealDecision: item.id } }">对此决定提出申诉</router-link></div><p v-if="!detail.decisions?.length" class="muted">暂无编辑决定</p></article>
      <article class="panel"><div class="panel-title"><div><span class="overline">REVIEWS</span><h2>可见审稿意见</h2></div><Users /></div><div v-for="item in detail.reports || []" :key="item.id" class="record-line"><strong>{{ item.reviewerName }} · {{ item.recommendationLabel || item.recommendation }}</strong><span>创新 {{ item.scoreNovelty }} / 质量 {{ item.scoreQuality }} / 清晰 {{ item.scoreClarity }}</span><p>{{ item.commentsToAuthor }}</p><small v-if="item.commentsToEditor">给编辑：{{ item.commentsToEditor }}</small></div><p v-if="!detail.reports?.length" class="muted">当前身份暂无可见审稿意见</p></article>
    </section>

    <section class="panel manuscript-section"><div class="panel-title"><div><span class="overline">AUDIT TIMELINE</span><h2>全流程时间线</h2></div><History /></div><div class="audit-timeline"><article v-for="(item,index) in timeline" :key="`${item.time}-${index}`"><i></i><div><span>{{ item.time }} · {{ item.type }}</span><strong>{{ item.title }}</strong><p>{{ item.detail || '—' }}</p></div></article><p v-if="!timeline.length" class="muted">暂无流程记录</p></div></section>
  </template>
</template>
