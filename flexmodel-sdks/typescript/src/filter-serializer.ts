// ============================================================
// Flexmodel SDK — Filter Serializer
//
// Converts the accumulated FilterNode[] from the query builder
// into the JSON format expected by the backend RecordResource.
// ============================================================

import type { FilterNode } from './types.js'

/**
 * 将过滤器数组序列化为后端期望的 JSON 对象。
 *
 * 规则：
 * - 无过滤器 → undefined（不发 filter 参数）
 * - 单个过滤器 → 直接透传（保持原始 filter 格式）
 * - 多个过滤器 → 用 _and 包裹（隐式 AND 语义）
 *
 * @example
 * // 单字段
 * serializeFilters([{ age: { _eq: 18 } }])
 * → { age: { _eq: 18 } }
 *
 * // 多字段（隐式 AND）
 * serializeFilters([{ age: { _eq: 18 } }, { name: { _contains: 'a' } }])
 * → { _and: [{ age: { _eq: 18 } }, { name: { _contains: 'a' } }] }
 */
export function serializeFilters(filters: FilterNode[]): FilterNode | undefined {
  if (filters.length === 0) return undefined
  if (filters.length === 1) return filters[0]
  return { _and: filters }
}

/**
 * 将排序列表序列化为后端期望的 JSON 字符串。
 *
 * @example
 * serializeSorts([{ field: 'name', sort: 'ASC' }, { field: 'id', sort: 'DESC' }])
 * → '[{"field":"name","sort":"ASC"},{"field":"id","sort":"DESC"}]'
 */
export function serializeSorts(sorts: Array<{ field: string; sort: 'ASC' | 'DESC' }>): string | undefined {
  if (sorts.length === 0) return undefined
  return JSON.stringify(sorts)
}
