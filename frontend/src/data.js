// Mapper Map 查询与实体查询共用一致的页面字段。
export function normalizeData(value) {
  if (Array.isArray(value)) return value.map(normalizeData)
  if (!value || typeof value !== 'object') return value
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [
    key === 'abstract_' ? 'abstract' : key.replace(/_([a-z])/g, (_, c) => c.toUpperCase()),
    normalizeData(item)
  ]))
}

export async function fetchAllPages(api, endpoint, params = {}) {
  const rows = []
  for (let page = 1; ; page++) {
    const result = await api.get(endpoint, { params: { ...params, page, size: 100 } })
    const batch = result.list || []
    rows.push(...batch)
    if (!batch.length || rows.length >= result.total) return rows
  }
}
