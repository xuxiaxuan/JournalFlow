<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BarChart3, Bell, BookOpen, BrainCircuit, Gavel, LayoutDashboard, LogIn, LogOut, Menu, Search, Settings, Sparkles, Workflow, X } from 'lucide-vue-next'
import { hasRole, logout, refreshSession, session } from './session'

const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)
watch(() => route.fullPath, () => { menuOpen.value = false })

function handleUnauthorized() {
  logout()
  if (route.path !== '/login') router.push({ path: '/login', query: { redirect: route.fullPath } })
}

onMounted(() => {
  window.addEventListener('journalflow:unauthorized', handleUnauthorized)
  refreshSession().catch(() => logout())
})
onUnmounted(() => window.removeEventListener('journalflow:unauthorized', handleUnauthorized))

function signOut() {
  logout()
  router.push('/')
}
</script>

<template>
  <header class="topbar">
    <router-link class="brand" to="/">
      <span class="brand-mark"><BookOpen :size="20" /></span>
      <span>JournalFlow</span>
    </router-link>
    <button class="icon-button mobile-menu" :aria-label="menuOpen ? '关闭菜单' : '打开菜单'" @click="menuOpen = !menuOpen"><X v-if="menuOpen" :size="21" /><Menu v-else :size="21" /></button>
    <nav :class="['nav-links', { open: menuOpen }]">
      <router-link to="/"><Search :size="17" />发现</router-link>
      <router-link v-if="session.user" to="/dashboard"><LayoutDashboard :size="17" />概览</router-link>
      <router-link v-if="hasRole('AUTHOR') || hasRole('EDITOR') || hasRole('ADMIN')" to="/manuscripts"><Workflow :size="17" />稿件</router-link>
      <router-link v-if="session.user" to="/workbench"><Sparkles :size="17" />工作台</router-link>
      <router-link v-if="session.user" to="/governance"><Gavel :size="17" />治理</router-link>
      <router-link v-if="hasRole('AUTHOR') || hasRole('EDITOR') || hasRole('ADMIN')" to="/ai-center"><BrainCircuit :size="17" />AI 中心</router-link>
      <router-link v-if="hasRole('EDITOR') || hasRole('ADMIN')" to="/analytics"><BarChart3 :size="17" />数据分析</router-link>
      <router-link v-if="hasRole('ADMIN')" to="/admin"><Settings :size="17" />管理</router-link>
      <router-link v-if="session.user" class="notification-link" to="/account"><Bell :size="17" /><b v-if="session.unreadCount">{{ session.unreadCount }}</b></router-link>
    </nav>
    <div class="nav-user">
      <template v-if="session.user">
        <div class="avatar">{{ session.user.realName?.slice(0, 1) }}</div>
        <div class="user-copy"><strong>{{ session.user.realName }}</strong><small>{{ session.user.roles?.join(' · ') }}</small></div>
        <button class="icon-button" title="退出登录" @click="signOut"><LogOut :size="18" /></button>
      </template>
      <router-link v-else class="button button-primary button-small" to="/login"><LogIn :size="16" />登录</router-link>
    </div>
  </header>

  <main :class="['page-shell', { 'login-shell': route.path === '/login' }]">
    <router-view />
  </main>

  <footer v-if="route.path !== '/login'" class="footer">
    <span>JournalFlow 学术出版协作平台</span>
    <span>大模型辅助 · 人工确认 · 全程可追溯</span>
  </footer>
</template>
