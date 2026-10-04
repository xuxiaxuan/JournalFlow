<script setup>
import { computed, onMounted, ref } from 'vue'
import { ArrowUpRight, BookMarked, Calendar, Eye, Search, Sparkles } from 'lucide-vue-next'
import api from '../api'

const keyword = ref('')
const categories = ref([])
const recommended = ref([])
const filters = ref({ categoryId: '', dateFrom: '', dateTo: '', sortBy: 'new' })
const articles = ref([])
const loading = ref(true)
const error = ref('')
const auxiliaryError = ref('')
const page = ref(1)
const total = ref(0)
const size = 12
const pages = computed(() => Math.max(1, Math.ceil(total.value / size)))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = Object.fromEntries(Object.entries(filters.value).filter(([, value]) => value !== ''))
    if (filters.value.dateTo) {
      const end = new Date(`${filters.value.dateTo}T00:00:00Z`)
      end.setUTCDate(end.getUTCDate() + 1)
      params.dateTo = end.toISOString().slice(0, 10)
    }
    const data = await api.get('/published', { params: { ...params, page: page.value, size, keyword: keyword.value || undefined } })
    articles.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }
function move(delta) { const next = page.value + delta; if (next >= 1 && next <= pages.value) { page.value = next; load() } }

onMounted(async () => {
  await load()
  const results = await Promise.allSettled([api.get('/reference/categories'), api.get('/published/recommend')])
  if (results[0].status === 'fulfilled') categories.value = results[0].value || []
  if (results[1].status === 'fulfilled') recommended.value = results[1].value || []
  if (results.some(result => result.status === 'rejected')) auxiliaryError.value = '部分分类或推荐暂不可用，仍可检索站内论文。'
})
</script>

<template>
  <section class="hero">
    <div class="hero-copy">
      <span class="eyebrow"><Sparkles :size="15" /> OPEN KNOWLEDGE, TRUSTED PROCESS</span>
      <h1>发现值得阅读的<br><em>学术成果</em></h1>
      <p>汇集经同行评议与编辑审核的研究成果，让知识传播的每一步都有迹可循。</p>
      <p class="muted">检索范围：本系统已出版论文，不包含外部文献库。</p>
      <form class="search-bar" @submit.prevent="search">
        <Search :size="20" /><input v-model="keyword" placeholder="搜索标题、摘要或关键词" />
        <button class="button button-primary">检索文献</button>
      </form>
    </div>
    <div class="hero-stat"><strong>全流程</strong><span>投稿 · 审稿 · 出版</span><i></i><strong>Human in the loop</strong><span>AI 结果必须人工确认</span></div>
  </section>

  <section class="content-section">
    <p v-if="auxiliaryError" role="status" class="muted">{{ auxiliaryError }}</p>
    <form class="filter-bar" @submit.prevent="search">
      <select v-model="filters.categoryId" aria-label="学科"><option value="">全部学科</option><option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option></select>
      <label>出版起日<input v-model="filters.dateFrom" type="date" /></label><label>出版止日<input v-model="filters.dateTo" type="date" :min="filters.dateFrom" /></label>
      <select v-model="filters.sortBy" aria-label="排序"><option value="new">最新出版</option><option value="hot">阅读热度</option></select><button class="button button-secondary">筛选</button>
    </form>
    <aside v-if="recommended.length" class="panel"><h2>推荐阅读</h2><p v-for="item in recommended" :key="item.id"><router-link :to="`/articles/${item.id}`">{{ item.title }}</router-link></p></aside>
    <div class="section-heading"><div><span class="overline">LATEST RESEARCH</span><h2>最新出版</h2></div><span class="muted">共 {{ total }} 篇结果</span></div>
    <div v-if="loading" class="empty-state">正在加载出版内容…</div>
    <div v-else-if="error" class="empty-state error-message">{{ error }}<small>请确认后端服务已在 8080 端口启动</small></div>
    <div v-else-if="!articles.length" class="empty-state">未找到匹配的文章</div>
    <div v-else class="article-grid">
      <article v-for="article in articles" :key="article.id" class="article-card">
        <div class="article-meta"><span>{{ article.categoryName || '综合研究' }}</span><span><Calendar :size="14" /> {{ article.publishedAt?.slice(0, 10) }}</span></div>
        <router-link :to="`/articles/${article.id}`"><h3>{{ article.title }}</h3></router-link>
        <p>{{ article.abstract || '暂无摘要' }}</p>
        <div class="keyword-row"><span v-for="tag in (article.keywords || '').split(/[;；,，]/).filter(Boolean).slice(0, 3)" :key="tag">{{ tag }}</span></div>
        <div class="article-footer"><span><Eye :size="15" /> {{ article.viewSum ?? article.viewCount ?? article.viewTotal ?? 0 }}</span><span><BookMarked :size="15" /> {{ article.doi || '待分配 DOI' }}</span><router-link :to="`/articles/${article.id}`" aria-label="查看文章"><ArrowUpRight :size="18" /></router-link></div>
      </article>
    </div>
    <div v-if="total > size" class="pager"><span>第 {{ page }}/{{ pages }} 页</span><div><button class="button button-small button-secondary" :disabled="page <= 1" @click="move(-1)">上一页</button><button class="button button-small button-secondary" :disabled="page >= pages" @click="move(1)">下一页</button></div></div>
  </section>
</template>
