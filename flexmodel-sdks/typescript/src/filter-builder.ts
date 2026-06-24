// ============================================================
// Flexmodel SDK — Filter Builder Functions
//
// Standalone helpers used both by FluentQueryBuilder (eq/gt/…)
// and inside where() callbacks (f.eq / f.or / f.and).
// ============================================================

import type { FieldFilter, FilterNode } from './types.js'

// ---- Field-level filter constructors ----

export function filterEq(field: string, value: unknown): FieldFilter {
  return { [field]: { _eq: value } }
}

export function filterNe(field: string, value: unknown): FieldFilter {
  return { [field]: { _ne: value } }
}

export function filterGt(field: string, value: unknown): FieldFilter {
  return { [field]: { _gt: value } }
}

export function filterGte(field: string, value: unknown): FieldFilter {
  return { [field]: { _gte: value } }
}

export function filterLt(field: string, value: unknown): FieldFilter {
  return { [field]: { _lt: value } }
}

export function filterLte(field: string, value: unknown): FieldFilter {
  return { [field]: { _lte: value } }
}

export function filterIn(field: string, values: unknown[]): FieldFilter {
  return { [field]: { _in: values } }
}

export function filterNin(field: string, values: unknown[]): FieldFilter {
  return { [field]: { _nin: values } }
}

export function filterBetween(field: string, a: unknown, b: unknown): FieldFilter {
  return { [field]: { _between: [a, b] } }
}

export function filterContains(field: string, str: string): FieldFilter {
  return { [field]: { _contains: str } }
}

export function filterNotContains(field: string, str: string): FieldFilter {
  return { [field]: { _not_contains: str } }
}

export function filterStartsWith(field: string, str: string): FieldFilter {
  return { [field]: { _starts_with: str } }
}

export function filterEndsWith(field: string, str: string): FieldFilter {
  return { [field]: { _ends_with: str } }
}

// ---- Logical combinators ----

export function filterOr(...nodes: FilterNode[]): FilterNode {
  return { _or: nodes }
}

export function filterAnd(...nodes: FilterNode[]): FilterNode {
  return { _and: nodes }
}

// ---- FilterFn: the object passed to where() callbacks ----

/** where() 回调收到的工具对象，用于构建复杂逻辑组合 */
export interface FilterFn {
  eq: typeof filterEq
  ne: typeof filterNe
  gt: typeof filterGt
  gte: typeof filterGte
  lt: typeof filterLt
  lte: typeof filterLte
  in: typeof filterIn
  nin: typeof filterNin
  between: typeof filterBetween
  contains: typeof filterContains
  notContains: typeof filterNotContains
  startsWith: typeof filterStartsWith
  endsWith: typeof filterEndsWith
  or: typeof filterOr
  and: typeof filterAnd
}

/** 预构建的 FilterFn 单例，供 where() 使用 */
export const filterFn: FilterFn = {
  eq: filterEq,
  ne: filterNe,
  gt: filterGt,
  gte: filterGte,
  lt: filterLt,
  lte: filterLte,
  in: filterIn,
  nin: filterNin,
  between: filterBetween,
  contains: filterContains,
  notContains: filterNotContains,
  startsWith: filterStartsWith,
  endsWith: filterEndsWith,
  or: filterOr,
  and: filterAnd,
}
