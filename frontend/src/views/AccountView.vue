<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Bookmark, CheckCheck, KeyRound, RefreshCw, Rss, UserRound, UsersRound } from 'lucide-vue-next'
import api from '../api'
import { refreshSession, session } from '../session'

const tab = ref('notifications'), rows = ref([]), loading = ref(false), error = ref(''), notice = ref('')
const router = useRouter()
const unreadOnly = ref(false), page = ref(1), total = ref(0), categories = ref([])
const profile = ref({ realName: '', email: '', affiliation: '', researchFields: '' })
const passwords = ref({ oldPassword: '', newPassword: '' })
const subscription = ref({ categoryId: '', keyword: '' })
const tabs = [
  ['notifications', '通知', Bell], ['profile', '个人资料', UserRound], ['favorites', '收藏', Bookmark],
  ['subscriptions', '订阅', Rss], ['follows', '关注', UsersRound]
]

function mapValue(item, camel, snake) { return item[camel] ?? item[snake] }
async function load() {
  error.value = ''; loading.value = true
  try {
    if (tab.value === 'notifications') {
      const data = await api.get('/notifications', { params: { page: page.value, size: 10, unreadOnly: unreadOnly.value } })
      rows.value = data.list || []; total.value = data.total || 0
    } else if (tab.value === 'favorites') rows.value = await api.get('/favorites/my') || []
    else if (tab.value === 'subscriptions') {
      const [items, refs] = await Promise.all([api.get('/subscriptions/my'), api.get('/reference/categories')])
      rows.value = items || []; categories.value = refs || []
    } else if (tab.value === 'follows') rows.value = await api.get('/follows/my') || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function switchTab(value) { tab.value = value; page.value = 1; notice.value = ''; if (value !== 'profile') load() }
async function markRead(item) { try { await api.put(`/notifications/${item.id}/read`); await refreshSession(); await load() } catch (e) { error.value = e.message } }
async function markAllRead() { try { await api.put('/notifications/read-all'); await refreshSession(); notice.value = '全部通知已标记为已读。'; await load() } catch (e) { error.value = e.message } }
async function openNotification(item) {
  try {
    if (!item.readAt) await api.put(`/notifications/${item.id}/read`)
    await refreshSession()
    const refId = item.refId || item.ref_id
    if (item.type === 'REPORT' && refId) return await router.push({ path: '/governance', query: { appealReport: refId } })
    if (item.type === 'APPEAL') return await router.push('/governance')
    if (item.type === 'LLM') return await router.push('/ai-center')
    if (item.type === 'REVIEW_INVITE') return await router.push('/workbench')
    if (['PUBLISH', 'SUBSCRIPTION'].includes(item.type) && refId) return await router.push(`/articles/${refId}`)
    if (refId) await router.push(`/manuscripts/${refId}`)
  } catch (e) { error.value = e.message }
}
async function saveProfile() {
  try { const user = await api.put('/auth/profile', profile.value); session.user = user; localStorage.setItem('journalflow_user', JSON.stringify(user)); notice.value = '个人资料已保存。' }
  catch (e) { error.value = e.message }
}
async function changePassword() {
  try { await api.put('/auth/password', passwords.value); passwords.value = { oldPassword: '', newPassword: '' }; notice.value = '密码修改成功，请妥善保管新密码。' }
  catch (e) { error.value = e.message }
}
async function removeFavorite(item) { try { await api.delete(`/favorites/${mapValue(item, 'manuscriptId', 'manuscript_id')}`); await load() } catch (e) { error.value = e.message } }
async function addSubscription() {
  const body = { categoryId: subscription.value.categoryId ? Number(subscription.value.categoryId) : null, keyword: subscription.value.keyword.trim() || null }
  try { await api.post('/subscriptions', body); subscription.value = { categoryId: '', keyword: '' }; notice.value = '订阅已添加。'; await load() } catch (e) { error.value = e.message }
}
async function removeSubscription(item) { try { await api.delete(`/subscriptions/${item.id}`); await load() } catch (e) { error.value = e.message } }
async function unfollow(item) { try { await api.delete(`/follows/${mapValue(item, 'userId', 'user_id')}`); await load() } catch (e) { error.value = e.message } }

onMounted(() => {
  const user = session.user || {}
  profile.value = { realName: user.realName || '', email: user.email || '', affiliation: user.affiliation || '', researchFields: user.researchFields || '' }
  load()
})
</script>

<template>
  <section class="page-heading"><div><span class="overline">PERSONAL CENTER</span><h1>通知与个人中心</h1><p>管理站内消息、个人资料和内容关系。</p></div></section>
  <p v-if="notice" class="success-banner">{{ notice }}</p><p v-if="error" class="error-banner">{{ error }}</p>
  <div class="tab-strip account-tabs"><button v-for="item in tabs" :key="item[0]" :class="{ active: tab === item[0] }" @click="switchTab(item[0])"><component :is="item[2]" :size="15" />{{ item[1] }}<b v-if="item[0] === 'notifications' && session.unreadCount">{{ session.unreadCount }}</b></button></div>

  <section v-if="tab === 'notifications'" class="panel table-panel">
    <div class="filter-bar"><label class="inline-check"><input v-model="unreadOnly" type="checkbox" @change="page = 1; load()" />仅看未读</label><button class="button button-secondary" @click="load"><RefreshCw :size="15" />刷新</button><button class="button button-primary" @click="markAllRead"><CheckCheck :size="15" />全部已读</button></div>
    <div v-if="loading" class="empty-state">正在加载通知…</div><div v-else class="notification-list"><article v-for="item in rows" :key="item.id" :class="{ unread: !item.readAt }"><span class="notification-icon"><Bell :size="17" /></span><div><strong>{{ item.title }}</strong><p>{{ item.content }}</p><small>{{ item.createdAt }} · {{ item.type }}</small></div><div class="row-actions"><button class="button button-small button-primary" @click="openNotification(item)">查看详情</button><button v-if="!item.readAt" class="button button-small button-secondary" @click="markRead(item)">标为已读</button></div></article><p v-if="!rows.length" class="empty-state">暂无通知</p></div>
  </section>

  <section v-else-if="tab === 'profile'" class="account-grid-layout">
    <form class="panel account-form" @submit.prevent="saveProfile"><div class="panel-title"><div><span class="overline">PROFILE</span><h2>基本资料</h2></div></div><label>真实姓名<input v-model="profile.realName" maxlength="100" /></label><label>邮箱<input v-model="profile.email" type="email" maxlength="100" /></label><label>单位<input v-model="profile.affiliation" maxlength="200" /></label><label>研究领域<textarea v-model="profile.researchFields" rows="4" maxlength="500"></textarea></label><button class="button button-primary">保存资料</button></form>
    <form class="panel account-form" @submit.prevent="changePassword"><div class="panel-title"><div><span class="overline">SECURITY</span><h2>修改密码</h2></div><KeyRound /></div><label>原密码<input v-model="passwords.oldPassword" type="password" required /></label><label>新密码<input v-model="passwords.newPassword" type="password" minlength="6" maxlength="50" required /></label><p class="form-hint">新密码长度为 6–50 位。修改后现有登录令牌仍会在到期前有效。</p><button class="button button-primary">修改密码</button></form>
  </section>

  <section v-else-if="tab === 'favorites'" class="panel relation-list"><article v-for="item in rows" :key="mapValue(item,'manuscriptId','manuscript_id')"><div><router-link :to="`/articles/${mapValue(item,'manuscriptId','manuscript_id')}`"><strong>{{ item.title }}</strong></router-link><small>收藏于 {{ mapValue(item,'createdAt','created_at') }}</small></div><button class="button button-small button-danger" @click="removeFavorite(item)">取消收藏</button></article><p v-if="!rows.length" class="empty-state">暂无收藏文章</p></section>

  <section v-else-if="tab === 'subscriptions'" class="panel relation-list"><form class="subscription-form" @submit.prevent="addSubscription"><select v-model="subscription.categoryId"><option value="">按关键词订阅</option><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option></select><input v-model="subscription.keyword" :disabled="Boolean(subscription.categoryId)" placeholder="关键词（与学科任选其一）" /><button class="button button-primary">新增订阅</button></form><article v-for="item in rows" :key="item.id"><div><strong>{{ mapValue(item,'categoryName','category_name') || item.keyword }}</strong><small>{{ mapValue(item,'categoryId','category_id') ? '学科订阅' : '关键词订阅' }} · {{ mapValue(item,'createdAt','created_at') }}</small></div><button class="button button-small button-danger" @click="removeSubscription(item)">取消订阅</button></article><p v-if="!rows.length" class="empty-state">暂无订阅</p></section>

  <section v-else class="panel relation-list"><article v-for="item in rows" :key="mapValue(item,'userId','user_id')"><div><strong>{{ mapValue(item,'realName','real_name') }}</strong><small>{{ item.affiliation || '未填写单位' }} · {{ mapValue(item,'researchFields','research_fields') || '未填写研究领域' }}</small></div><button class="button button-small button-danger" @click="unfollow(item)">取消关注</button></article><p v-if="!rows.length" class="empty-state">暂无关注用户</p></section>
</template>
