import { beforeEach, describe, expect, it, vi } from 'vitest'
import api from './api'

describe('api client', () => {
  beforeEach(() => localStorage.clear())

  it('adds the JWT authorization header and unwraps Result data', async () => {
    localStorage.setItem('journalflow_token', 'abc')
    api.defaults.adapter = async (config) => {
      expect(config.headers.Authorization).toBe('Bearer abc')
      return { data: { code: 0, data: { id: 1 } }, status: 200, statusText: 'OK', headers: {}, config }
    }
    await expect(api.get('/test')).resolves.toEqual({ id: 1 })
  })

  it('rejects a successful HTTP response carrying a business error', async () => {
    api.defaults.adapter = async (config) => ({
      data: { code: 40001, message: '参数错误' }, status: 200, statusText: 'OK', headers: {}, config
    })
    await expect(api.get('/test')).rejects.toThrow('参数错误')
  })

  it('clears credentials and emits an event after HTTP 401', async () => {
    localStorage.setItem('journalflow_token', 'expired')
    const listener = vi.fn()
    window.addEventListener('journalflow:unauthorized', listener)
    api.defaults.adapter = async (config) => Promise.reject({
      message: 'Unauthorized', response: { status: 401, data: { message: '登录已失效' } }, config
    })
    await expect(api.get('/test')).rejects.toThrow('登录已失效')
    expect(localStorage.getItem('journalflow_token')).toBeNull()
    expect(listener).toHaveBeenCalledOnce()
    window.removeEventListener('journalflow:unauthorized', listener)
  })
})
