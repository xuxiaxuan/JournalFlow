<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { Activity, Bot, FileCheck2, FileText, Flag, Users } from 'lucide-vue-next'
import api from '../api'

use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const overview = ref({})
const topAuthors = ref([])
const loading = ref(true)
const error = ref('')
const trendEl = ref(null)
const cycleEl = ref(null)
const reportEl = ref(null)
const charts = []

const value = (row, camel, snake) => row?.[camel] ?? row?.[snake] ?? 0

async function load() {
  loading.value = true
  try {
    const [summary, trend, cycle, reports, authors] = await Promise.all([
      api.get('/stats/overview'), api.get('/stats/submission-trend', { params: { months: 12 } }),
      api.get('/stats/review-cycle', { params: { months: 12 } }), api.get('/stats/report-handling'),
      api.get('/stats/top-authors', { params: { top: 8 } })
    ])
    overview.value = summary || {}
    topAuthors.value = authors || []
    await nextTick()
    renderCharts(trend || [], cycle || [], reports || [])
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function baseChart(el) {
  const chart = init(el)
  charts.push(chart)
  return chart
}

function renderCharts(trend, cycle, reports) {
  charts.splice(0).forEach((c) => c.dispose())
  baseChart(trendEl.value).setOption({
    tooltip: { trigger: 'axis' }, grid: { left: 42, right: 18, top: 25, bottom: 30 },
    xAxis: { type: 'category', data: trend.map((r) => value(r, 'ym', 'ym')), axisLine: { lineStyle: { color: '#d9dfda' } } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf0ed' } } },
    series: [{ type: 'line', smooth: true, symbolSize: 7, data: trend.map((r) => value(r, 'cnt', 'cnt')), lineStyle: { color: '#24614d', width: 3 }, itemStyle: { color: '#24614d' }, areaStyle: { color: 'rgba(36,97,77,.12)' } }]
  })
  baseChart(cycleEl.value).setOption({
    tooltip: { trigger: 'axis' }, legend: { bottom: 0, data: ['录用', '退修', '退稿'] }, grid: { left: 42, right: 18, top: 20, bottom: 45 },
    xAxis: { type: 'category', data: cycle.map((r) => value(r, 'ym', 'ym')) }, yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf0ed' } } },
    series: [['录用','acceptCnt','accept_cnt','#377b60'],['退修','reviseCnt','revise_cnt','#d68b45'],['退稿','rejectCnt','reject_cnt','#b95d4b']].map(([name,camel,snake,color]) => ({ name, type: 'bar', stack: 'decision', data: cycle.map((r) => value(r, camel, snake)), itemStyle: { color } }))
  })
  baseChart(reportEl.value).setOption({
    tooltip: { trigger: 'item' }, legend: { bottom: 0 },
    series: [{ type: 'pie', radius: ['45%', '68%'], center: ['50%', '44%'], label: { formatter: '{b}\n{c}' }, data: reports.map((r) => ({ name: ({ PENDING:'待处理', PROCESSING:'处理中', RESOLVED:'已处置', DISMISSED:'已驳回' })[r.status] || r.status, value: value(r, 'cnt', 'cnt') })), color: ['#d68b45','#547d9b','#377b60','#9aa39e'] }]
  })
}

function resize() { charts.forEach((chart) => chart.resize()) }
onMounted(() => { load(); window.addEventListener('resize', resize) })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); charts.forEach((c) => c.dispose()) })
</script>

<template>
  <section class="page-heading"><div><span class="overline">OPERATION ANALYTICS</span><h1>内容质量与运营分析</h1><p>基于投稿、审稿、互动和治理记录实时聚合。</p></div><span class="status-pill green"><Activity :size="13" />实时数据</span></section>
  <div v-if="error" class="error-banner">{{ error }}</div>
  <div v-if="loading" class="empty-state">正在计算运营指标…</div>
  <template v-else>
    <section class="analytics-metrics">
      <article><Users /><span>平台注册用户</span><strong>{{ value(overview,'userCount','user_count') }}</strong></article>
      <article><FileText /><span>累计稿件</span><strong>{{ value(overview,'manuscriptCount','manuscript_count') }}</strong></article>
      <article><FileCheck2 /><span>已出版</span><strong>{{ value(overview,'publishedCount','published_count') }}</strong></article>
      <article><Bot /><span>AI 待确认</span><strong>{{ value(overview,'llmPendingConfirmCount','llm_pending_confirm_count') }}</strong></article>
      <article><Flag /><span>待处理举报</span><strong>{{ value(overview,'pendingReportCount','pending_report_count') }}</strong></article>
    </section>
    <section class="chart-grid">
      <article class="panel chart-wide"><div class="chart-title"><div><span class="overline">SUBMISSIONS</span><h2>投稿量趋势</h2></div><span>近 12 个月</span></div><div ref="trendEl" class="chart"></div></article>
      <article class="panel"><div class="chart-title"><div><span class="overline">GOVERNANCE</span><h2>举报处置状态</h2></div></div><div ref="reportEl" class="chart"></div></article>
      <article class="panel chart-wide"><div class="chart-title"><div><span class="overline">DECISIONS</span><h2>审稿决定分布</h2></div><span>录用率 {{ value(overview,'acceptanceRate','acceptance_rate') }}%</span></div><div ref="cycleEl" class="chart"></div></article>
      <article class="panel"><div class="chart-title"><div><span class="overline">AUTHORS</span><h2>活跃作者</h2></div></div><div class="author-ranking"><div v-for="(author,index) in topAuthors" :key="author.authorId || author.author_id"><b>{{ String(index+1).padStart(2,'0') }}</b><span><strong>{{ author.realName || author.real_name }}</strong><small>{{ author.affiliation }}</small></span><em>{{ value(author,'publishedCnt','published_cnt') }} 篇</em></div><p v-if="!topAuthors.length" class="muted">暂无数据</p></div></article>
    </section>
  </template>
</template>
