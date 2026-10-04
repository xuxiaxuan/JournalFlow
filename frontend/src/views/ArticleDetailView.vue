<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Bookmark, Calendar, Download, Eye, MessageSquare, Send, UserRound } from 'lucide-vue-next'
import api from '../api'
import { isLoggedIn, session } from '../session'

const route = useRoute()
const router = useRouter()
const article = ref(null)
const comments = ref([])
const comment = ref('')
const replyTo = ref(null)
const loading = ref(true)
const actionLoading = ref(false)
const error = ref('')
const notice = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [detail, commentRows] = await Promise.all([
      api.get(`/published/${route.params.id}`),
      api.get(`/published/${route.params.id}/comments`)
    ])
    article.value = detail
    comments.value = commentRows || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function requireLogin() {
  if (isLoggedIn.value) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function toggleFavorite() {
  if (!requireLogin()) return
  actionLoading.value = true
  try {
    if (article.value.favorited) await api.delete(`/favorites/${article.value.id}`)
    else await api.post('/favorites', { manuscriptId: article.value.id })
    article.value.favorited = !article.value.favorited
    notice.value = article.value.favorited ? '已加入收藏' : '已取消收藏'
  } catch (e) { notice.value = e.message } finally { actionLoading.value = false }
}

async function submitComment() {
  if (!requireLogin() || !comment.value.trim()) return
  actionLoading.value = true
  try {
    await api.post('/comments', { manuscriptId: Number(route.params.id), content: comment.value.trim(), parentId: replyTo.value?.id || null })
    comment.value = ''
    replyTo.value = null
    comments.value = await api.get(`/published/${route.params.id}/comments`) || []
    notice.value = '评论已发布'
  } catch (e) { notice.value = e.message } finally { actionLoading.value = false }
}

async function toggleFollow() {
  if (!requireLogin()) return
  actionLoading.value = true
  try {
    const url = `/follows/${article.value.submitterId}`
    if (article.value.followingAuthor) await api.delete(url)
    else await api.post(url)
    article.value.followingAuthor = !article.value.followingAuthor
  } catch (e) { notice.value = e.message } finally { actionLoading.value = false }
}
async function deleteComment(item) {
  if (!window.confirm('确认删除这条评论？')) return
  actionLoading.value = true
  try {
    await api.delete(`/comments/${item.id}`)
    comments.value = await api.get(`/published/${route.params.id}/comments`) || []
    if (replyTo.value?.id === item.id) replyTo.value = null
  } catch (e) { notice.value = e.message } finally { actionLoading.value = false }
}
onMounted(load)
</script>

<template>
  <div v-if="loading" class="empty-state">正在加载文章…</div>
  <div v-else-if="error" class="empty-state error-message">{{ error }}</div>
  <template v-else-if="article">
    <button class="text-button" @click="router.back()"><ArrowLeft :size="16" />返回文章列表</button>
    <article class="detail-layout">
      <section class="paper-card">
        <div class="detail-meta"><span>{{ article.category?.name || '综合研究' }}</span><span><Calendar :size="14" />{{ article.publishedAt }}</span></div>
        <h1>{{ article.title }}</h1>
        <div class="author-list"><UserRound :size="17" /><span v-for="author in article.authors" :key="author.id || author.authorName">{{ author.authorName }}<small>{{ author.affiliation }}</small></span></div>
        <div class="paper-stats"><span><Eye :size="16" /> {{ article.viewTotal || 0 }} 次阅读</span><span><Download :size="16" /> {{ article.downloadTotal || 0 }} 次下载</span><span>DOI：{{ article.doi || '待分配' }}</span></div>
        <div class="abstract-block"><span class="overline">ABSTRACT</span><h2>摘要</h2><p>{{ article.abstract }}</p></div>
        <div class="keyword-row"><span v-for="tag in (article.keywords || '').split(/[;；,，]/).filter(Boolean)" :key="tag">{{ tag }}</span></div>
      </section>
      <aside class="detail-actions panel">
        <a class="button button-primary full-width" :href="`/api/published/${article.id}/file`"><Download :size="17" />下载全文</a>
        <button class="button button-secondary full-width" :disabled="actionLoading" @click="toggleFavorite"><Bookmark :size="17" :fill="article.favorited ? 'currentColor' : 'none'" />{{ article.favorited ? '已收藏' : '收藏文章' }}</button>
        <small v-if="notice" class="action-notice">{{ notice }}</small>
        <button v-if="session.user?.id !== article.submitterId" class="button button-secondary full-width" :disabled="actionLoading" @click="toggleFollow">{{ article.followingAuthor ? '取消关注作者' : '关注作者' }}</button>
        <dl><div><dt>卷期</dt><dd>{{ article.volume || '—' }} 卷 {{ article.issue || '—' }} 期</dd></div><div><dt>页码</dt><dd>{{ article.pages || '—' }}</dd></div><div><dt>许可</dt><dd>CC-BY-4.0</dd></div></dl>
      </aside>
    </article>
    <section class="comments-panel panel">
      <div class="panel-title"><div><span class="overline">DISCUSSION</span><h2>读者评论</h2></div><span class="counter">{{ comments.length }}</span></div>
      <form class="comment-form" @submit.prevent="submitComment"><textarea v-model="comment" maxlength="2000" placeholder="分享你的问题或见解…"></textarea><button class="button button-primary" :disabled="actionLoading || !comment.trim()"><Send :size="16" />发布评论</button></form>
      <div class="comment-list">
        <p v-if="replyTo">正在回复 {{ replyTo.userName || '读者' }}：{{ replyTo.content }} <button class="text-button" @click="replyTo = null">取消回复</button></p>
        <div v-for="item in comments" :key="item.id" class="comment-item"><span class="comment-avatar"><MessageSquare :size="17" /></span><div><strong>{{ item.realName || item.userName || '读者' }}</strong><small>{{ item.createdAt }}</small><blockquote v-if="item.parentId">{{ item.parentContent || '原评论已不可见' }}</blockquote><p>{{ item.content }}</p><button v-if="!item.parentId" class="text-button" :disabled="actionLoading" @click="requireLogin() && (replyTo = item)">回复</button><button v-if="session.user?.id === item.userId" class="text-button" :disabled="actionLoading" @click="deleteComment(item)">删除</button></div></div>
        <div v-if="!comments.length" class="empty-state">还没有评论，来发表第一条见解吧</div>
      </div>
    </section>
  </template>
</template>
