import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMock = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }))
vi.mock('./api', () => ({ default: apiMock }))

import { hasRole, login, logout, refreshSession, session } from './session'

describe('session', () => {
  beforeEach(() => {
    localStorage.clear()
    logout()
  })

  it('stores token and user after login', async () => {
    apiMock.post.mockResolvedValue({ token: 'jwt-token', user: { id: 8, roles: ['AUTHOR'] } })
    await login('author1', '123456')
    expect(localStorage.getItem('journalflow_token')).toBe('jwt-token')
    expect(session.user.id).toBe(8)
    expect(hasRole('AUTHOR')).toBe(true)
  })

  it('refreshes user and unread notification count', async () => {
    localStorage.setItem('journalflow_token', 'jwt-token')
    apiMock.get.mockResolvedValue({ user: { id: 2, roles: ['EDITOR'] }, unreadCount: 3 })
    await refreshSession()
    expect(session.unreadCount).toBe(3)
    expect(hasRole('EDITOR')).toBe(true)
  })

  it('does not request profile without a token', async () => {
    await refreshSession()
    expect(apiMock.get).not.toHaveBeenCalled()
  })

  it('clears all local session state on logout', () => {
    localStorage.setItem('journalflow_token', 'jwt-token')
    localStorage.setItem('journalflow_user', '{}')
    session.user = { id: 1 }
    session.unreadCount = 5
    logout()
    expect(localStorage.getItem('journalflow_token')).toBeNull()
    expect(session.user).toBeNull()
    expect(session.unreadCount).toBe(0)
  })
})
