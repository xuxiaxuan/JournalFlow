import { describe, it, expect, vi } from 'vitest'
import { normalizeData, fetchAllPages } from './data'

describe('API data compatibility', () => {
  it('maps real MySQL list fields without losing nested values or zero counts', () => {
    expect(normalizeData({ list: [{ abstract_: '摘要', category_name: '人工智能', published_at: '2026-10-03', view_sum: 0 }] }))
      .toEqual({ list: [{ abstract: '摘要', categoryName: '人工智能', publishedAt: '2026-10-03', viewSum: 0 }] })
  })
  it('loads beyond page one and respects the filtered total', async () => {
    const api = { get: vi.fn().mockResolvedValueOnce({ list: [{ id: 1 }], total: 2 }).mockResolvedValueOnce({ list: [{ id: 2 }], total: 2 }) }
    expect(await fetchAllPages(api, '/manuscripts', { status: 'DRAFT' })).toEqual([{ id: 1 }, { id: 2 }])
    expect(api.get).toHaveBeenLastCalledWith('/manuscripts', { params: { status: 'DRAFT', page: 2, size: 100 } })
  })
})
