import { computed, reactive } from 'vue'
import api from './api'

const savedUser = JSON.parse(localStorage.getItem('journalflow_user') || 'null')
export const session = reactive({ user: savedUser, unreadCount: 0 })
export const isLoggedIn = computed(() => Boolean(session.user))

export async function login(username, password) {
  const data = await api.post('/auth/login', { username, password })
  localStorage.setItem('journalflow_token', data.token)
  localStorage.setItem('journalflow_user', JSON.stringify(data.user))
  session.user = data.user
  return data.user
}

export async function refreshSession() {
  if (!localStorage.getItem('journalflow_token')) return
  const data = await api.get('/auth/me')
  session.user = data.user
  session.unreadCount = data.unreadCount
  localStorage.setItem('journalflow_user', JSON.stringify(data.user))
}

export function logout() {
  localStorage.removeItem('journalflow_token')
  localStorage.removeItem('journalflow_user')
  session.user = null
  session.unreadCount = 0
}

export function hasRole(role) {
  return session.user?.roles?.includes(role)
}
