import { createRouter, createWebHistory } from 'vue-router'
import ExploreView from './views/ExploreView.vue'
import LoginView from './views/LoginView.vue'
import DashboardView from './views/DashboardView.vue'
import ManuscriptsView from './views/ManuscriptsView.vue'
import WorkbenchView from './views/WorkbenchView.vue'
import ArticleDetailView from './views/ArticleDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: ExploreView },
    { path: '/articles/:id', component: ArticleDetailView },
    { path: '/login', component: LoginView, meta: { guest: true } },
    { path: '/dashboard', component: DashboardView, meta: { auth: true } },
    { path: '/manuscripts', component: ManuscriptsView, meta: { auth: true } },
    { path: '/manuscripts/:id', component: () => import('./views/ManuscriptDetailView.vue'), meta: { auth: true } },
    { path: '/workbench', component: WorkbenchView, meta: { auth: true } },
    { path: '/account', component: () => import('./views/AccountView.vue'), meta: { auth: true } },
    { path: '/governance', component: () => import('./views/GovernanceView.vue'), meta: { auth: true } },
    { path: '/ai-center', component: () => import('./views/AiCenterView.vue'), meta: { auth: true, roles: ['AUTHOR', 'EDITOR', 'ADMIN'] } },
    { path: '/analytics', component: () => import('./views/AnalyticsView.vue'), meta: { auth: true, roles: ['EDITOR', 'ADMIN'] } },
    { path: '/admin', component: () => import('./views/AdminView.vue'), meta: { auth: true, roles: ['ADMIN'] } }
  ],
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const loggedIn = Boolean(localStorage.getItem('journalflow_token'))
  const user = JSON.parse(localStorage.getItem('journalflow_user') || 'null')
  if (to.meta.auth && !loggedIn) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.guest && loggedIn) return '/dashboard'
  if (to.meta.roles && !to.meta.roles.some((role) => user?.roles?.includes(role))) return '/dashboard'
})

export default router
