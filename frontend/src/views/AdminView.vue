<script setup>
import { onMounted, ref } from 'vue'
import { FolderTree, Pencil, Plus, RefreshCw, ScrollText, Users, X } from 'lucide-vue-next'
import api from '../api'

const tab = ref('users'), rows = ref([]), total = ref(0), page = ref(1), keyword = ref(''), status = ref('')
const loading = ref(false), error = ref(''), notice = ref(''), showUser = ref(false), roleTarget = ref(null), categoryTarget = ref(null)
const size = 10
const userForm = ref({ username: '', password: '123456', realName: '', email: '', affiliation: '', roleCodes: 'AUTHOR' })
const roleCodes = ref(''), categoryForm = ref({ name: '', parentId: '', sortNo: 0 })

async function load() {
  loading.value = true; error.value = ''
  try {
    if (tab.value === 'categories') { rows.value = await api.get('/admin/categories') || []; total.value = rows.value.length }
    else {
      const params = { page: page.value, size }
      if (tab.value === 'users') Object.assign(params, { keyword: keyword.value || undefined, status: status.value === '' ? undefined : Number(status.value) })
      else Object.assign(params, { action: keyword.value || undefined, success: status.value === '' ? undefined : Number(status.value) })
      const data = await api.get(tab.value === 'users' ? '/admin/users' : '/admin/logs', { params })
      rows.value = data.list || []; total.value = data.total || 0
    }
  } catch (e) { error.value = e.message } finally { loading.value = false }
}
function switchTab(value) { tab.value = value; page.value = 1; keyword.value = ''; status.value = ''; load() }
async function createUser() {
  try { await api.post('/admin/users', userForm.value); showUser.value = false; notice.value = '用户已创建。'; await load() }
  catch (e) { error.value = e.message }
}
async function toggleStatus(item) {
  try { await api.put(`/admin/users/${item.id}/status`, { status: String(item.status === 1 ? 0 : 1) }); notice.value = '账号状态已更新。'; await load() }
  catch (e) { error.value = e.message }
}
function editRoles(item) { roleTarget.value = item; roleCodes.value = (item.roles || []).join(',') }
async function saveRoles() {
  try { await api.put(`/admin/users/${roleTarget.value.id}/roles`, { roleCodes: roleCodes.value }); roleTarget.value = null; notice.value = '用户角色已更新。'; await load() }
  catch (e) { error.value = e.message }
}
async function createCategory() {
  const body = { name: categoryForm.value.name, parentId: categoryForm.value.parentId ? Number(categoryForm.value.parentId) : null, sortNo: Number(categoryForm.value.sortNo) }
  try { await api.post('/admin/categories', body); categoryForm.value = { name: '', parentId: '', sortNo: 0 }; notice.value = '学科分类已创建。'; await load() }
  catch (e) { error.value = e.message }
}
function editCategory(item) {
  categoryTarget.value = item
  categoryForm.value = { name: item.name, parentId: item.parentId || '', sortNo: item.sortNo || 0 }
}
function closeCategory() { categoryTarget.value = null; categoryForm.value = { name: '', parentId: '', sortNo: 0 } }
async function saveCategory() {
  const body = { name: categoryForm.value.name, parentId: categoryForm.value.parentId ? Number(categoryForm.value.parentId) : null, sortNo: Number(categoryForm.value.sortNo) }
  try { await api.put(`/admin/categories/${categoryTarget.value.id}`, body); closeCategory(); notice.value = '学科分类已更新。'; await load() }
  catch (e) { error.value = e.message }
}
async function deleteCategory(id) {
  if (!confirm('确定删除该分类？')) return
  try { await api.delete(`/admin/categories/${id}`); notice.value = '分类已删除。'; await load() } catch (e) { error.value = e.message }
}
function move(delta) { const next = page.value + delta, max = Math.max(1, Math.ceil(total.value / size)); if (next >= 1 && next <= max) { page.value = next; load() } }
onMounted(load)
</script>

<template>
  <section class="page-heading"><div><span class="overline">SYSTEM ADMINISTRATION</span><h1>系统管理</h1><p>维护用户角色、学科分类并审计关键业务操作。</p></div><button v-if="tab === 'users'" class="button button-primary" @click="showUser = true"><Plus :size="16" />创建用户</button></section>
  <p v-if="notice" class="success-banner">{{ notice }}</p><p v-if="error" class="error-banner">{{ error }}</p>
  <div class="tab-strip"><button :class="{ active: tab === 'users' }" @click="switchTab('users')"><Users :size="15" />用户与角色</button><button :class="{ active: tab === 'categories' }" @click="switchTab('categories')"><FolderTree :size="15" />学科分类</button><button :class="{ active: tab === 'logs' }" @click="switchTab('logs')"><ScrollText :size="15" />操作日志</button></div>
  <section class="panel table-panel">
    <form v-if="tab !== 'categories'" class="filter-bar" @submit.prevent="page = 1; load()"><input v-model="keyword" :placeholder="tab === 'users' ? '用户名或姓名' : '操作类型'" /><select v-model="status"><option value="">全部状态</option><template v-if="tab === 'users'"><option value="1">正常</option><option value="0">禁用</option></template><template v-else><option value="1">成功</option><option value="0">失败</option></template></select><button class="button button-secondary"><RefreshCw :size="15" />查询</button></form>
    <form v-else class="filter-bar category-create" @submit.prevent="createCategory"><input v-model="categoryForm.name" placeholder="新分类名称" required /><select v-model="categoryForm.parentId"><option value="">顶级分类</option><option v-for="item in rows" :key="item.id" :value="item.id">{{ item.name }}</option></select><input v-model="categoryForm.sortNo" type="number" placeholder="排序" /><button class="button button-primary"><Plus :size="15" />新增分类</button></form>
    <div v-if="loading" class="empty-state">正在加载管理数据…</div><div v-else class="table-wrap">
      <table v-if="tab === 'users'"><thead><tr><th>用户</th><th>单位</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td><strong>{{ item.realName }}</strong><small>{{ item.username }} · {{ item.email || '未填写邮箱' }}</small></td><td>{{ item.affiliation || '—' }}</td><td><span v-for="role in item.roles" :key="role" class="status-pill blue role-pill">{{ role }}</span></td><td><span :class="['status-pill', item.status === 1 ? 'green' : 'red']">{{ item.status === 1 ? '正常' : '禁用' }}</span></td><td>{{ item.createdAt }}</td><td><div class="row-actions"><button class="button button-small button-secondary" @click="editRoles(item)">角色</button><button :class="['button','button-small', item.status === 1 ? 'button-danger' : 'button-secondary']" @click="toggleStatus(item)">{{ item.status === 1 ? '禁用' : '启用' }}</button></div></td></tr><tr v-if="!rows.length"><td colspan="6" class="empty-cell">暂无用户</td></tr></tbody></table>
      <table v-else-if="tab === 'categories'"><thead><tr><th>分类</th><th>父分类</th><th>排序</th><th>操作</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td><strong>{{ item.name }}</strong><small>#{{ item.id }}</small></td><td>{{ item.parentId || '顶级' }}</td><td>{{ item.sortNo }}</td><td><div class="row-actions"><button class="button button-small button-secondary" @click="editCategory(item)"><Pencil :size="13" />编辑</button><button class="button button-small button-danger" @click="deleteCategory(item.id)">删除</button></div></td></tr><tr v-if="!rows.length"><td colspan="4" class="empty-cell">暂无分类</td></tr></tbody></table>
      <table v-else><thead><tr><th>操作</th><th>用户</th><th>业务对象</th><th>结果</th><th>IP</th><th>时间</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td><strong>{{ item.action }}</strong><small>#{{ item.id }}</small></td><td>{{ item.real_name || item.realName || item.username || item.user_id || '匿名' }}</td><td>{{ item.object_type || item.objectType }} #{{ item.object_id || item.objectId || '—' }}</td><td><span :class="['status-pill', Number(item.success) === 1 ? 'green' : 'red']">{{ Number(item.success) === 1 ? '成功' : '失败' }}</span></td><td>{{ item.ip || '—' }}</td><td>{{ item.created_at || item.createdAt }}</td></tr><tr v-if="!rows.length"><td colspan="6" class="empty-cell">暂无操作日志</td></tr></tbody></table>
    </div><div v-if="tab !== 'categories'" class="pager"><span>共 {{ total }} 条 · 第 {{ page }}/{{ Math.max(1, Math.ceil(total / size)) }} 页</span><div><button class="button button-small button-secondary" :disabled="page <= 1" @click="move(-1)">上一页</button><button class="button button-small button-secondary" :disabled="page >= Math.ceil(total / size)" @click="move(1)">下一页</button></div></div>
  </section>
  <div v-if="showUser" class="modal-backdrop" @click.self="showUser = false"><form class="modal-card compact-modal" @submit.prevent="createUser"><div class="modal-title"><div><span class="overline">NEW USER</span><h2>创建系统用户</h2></div><button type="button" class="icon-button" @click="showUser = false"><X /></button></div><div class="form-grid"><label>用户名<input v-model="userForm.username" required /></label><label>初始密码<input v-model="userForm.password" minlength="6" required /></label><label>姓名<input v-model="userForm.realName" required /></label><label>邮箱<input v-model="userForm.email" type="email" /></label></div><label>单位<input v-model="userForm.affiliation" /></label><label>角色代码<input v-model="userForm.roleCodes" required /><small>多个角色用逗号分隔：AUTHOR,REVIEWER,EDITOR,ADMIN</small></label><div class="modal-actions"><button type="button" class="button button-secondary" @click="showUser = false">取消</button><button class="button button-primary">创建用户</button></div></form></div>
  <div v-if="roleTarget" class="modal-backdrop" @click.self="roleTarget = null"><form class="modal-card compact-modal" @submit.prevent="saveRoles"><div class="modal-title"><div><span class="overline">ROLE CONTROL</span><h2>调整 {{ roleTarget.realName }} 的角色</h2></div><button type="button" class="icon-button" @click="roleTarget = null"><X /></button></div><label>角色代码<input v-model="roleCodes" required /><small>按最小必要原则授权，至少保留一个角色。</small></label><div class="modal-actions"><button type="button" class="button button-secondary" @click="roleTarget = null">取消</button><button class="button button-primary">保存角色</button></div></form></div>
  <div v-if="categoryTarget" class="modal-backdrop" @click.self="closeCategory"><form class="modal-card compact-modal" @submit.prevent="saveCategory"><div class="modal-title"><div><span class="overline">CATEGORY</span><h2>编辑学科分类</h2></div><button type="button" class="icon-button" @click="closeCategory"><X /></button></div><label>分类名称<input v-model="categoryForm.name" required /></label><label>父分类<select v-model="categoryForm.parentId"><option value="">顶级分类</option><option v-for="item in rows.filter(row => row.id !== categoryTarget.id)" :key="item.id" :value="item.id">{{ item.name }}</option></select></label><label>排序<input v-model.number="categoryForm.sortNo" type="number" /></label><div class="modal-actions"><button type="button" class="button button-secondary" @click="closeCategory">取消</button><button class="button button-primary">保存修改</button></div></form></div>
</template>
