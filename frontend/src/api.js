import axios from 'axios'
import { normalizeData } from './data'

const api = axios.create({ baseURL: '/api', timeout: 15000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('journalflow_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') return response
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return normalizeData(body?.data)
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('journalflow_token')
      localStorage.removeItem('journalflow_user')
      window.dispatchEvent(new CustomEvent('journalflow:unauthorized'))
    }
    return Promise.reject(new Error(error.response?.data?.message || error.message || '网络异常'))
  }
)

export default api
