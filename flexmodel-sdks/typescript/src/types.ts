// ============================================================
// Flexmodel SDK — Public Type Definitions
// ============================================================

/** FlexmodelClient 构造参数 */
export interface FlexmodelClientOptions {
  /** API 基础地址，浏览器默认同源（window.location.origin），Node/Deno 需提供 */
  baseURL?: string
  /** API Key（fm_ak_ 前缀），提供后所有请求自动注入 Authorization 头 */
  apiKey?: string
  /** 默认项目 ID，数据 API 使用，可在 per-call 时通过 .project() 覆盖 */
  projectId?: string
}

/** 分页响应 DTO，对应后端 PageDTO */
export interface PageDTO<T> {
  /** 符合条件的总记录数 */
  total: number
  /** 当前页记录列表 */
  list: T[]
}

/** 排序项 */
export interface SortItem {
  field: string
  sort: 'ASC' | 'DESC'
}

/** 字段级过滤器，形如 { fieldName: { _operator: value } } */
export interface FieldFilter {
  [field: string]: { [operator: string]: unknown }
}

/** 过滤器节点 — 字段过滤、逻辑组合、或原始 filter 对象 */
export type FilterNode =
  | FieldFilter
  | { _and: FilterNode[] }
  | { _or: FilterNode[] }
  | Record<string, unknown>

/** HTTP 请求选项 */
export interface RequestOptions {
  /** 查询参数 */
  params?: Record<string, string>
  /** 请求体（自动 JSON 序列化） */
  body?: unknown
  /** 额外请求头 */
  headers?: Record<string, string>
}

// ---- 便捷方法选项对象 ----

/** 排序输入 — 支持字符串简写、SortItem 对象、或数组 */
export type SortInput = string | SortItem | Array<string | SortItem>

/** 字段选择 — 支持逗号分隔字符串或数组 */
export type FieldSelection = string | string[]

/** findMany 选项 */
export interface FindManyOptions<TModel = Record<string, unknown>> {
  /** 过滤条件 */
  where?: FilterNode
  /** 排序 — 'name' | 'name:DESC' | SortItem | [string | SortItem] */
  orderBy?: SortInput
  /** 页码（默认 1） */
  page?: number
  /** 每页条数（默认 15） */
  size?: number
  /** 关联加载字段 */
  expand?: FieldSelection
  /** 投影字段 */
  select?: FieldSelection
}

/** findOne 选项 */
export interface FindOneOptions {
  /** 关联加载字段 */
  expand?: FieldSelection
  /** 投影字段 */
  select?: FieldSelection
}

/** count 选项 */
export interface CountOptions {
  /** 过滤条件 */
  where?: FilterNode
}

/** create 选项（保留扩展位） */
export interface CreateOptions {
  // reserved for future: transaction, returning, etc.
}

/** update 选项 */
export interface UpdateOptions<TModel = Record<string, unknown>> {
  /** 全量替换数据 */
  data: Partial<TModel>
}

/** merge 选项 */
export interface MergeOptions<TModel = Record<string, unknown>> {
  /** 部分更新数据 */
  data: Partial<TModel>
}

/** 批量创建选项（保留扩展位） */
export interface CreateManyOptions {
  // reserved for future: transaction, returning, etc.
}

/** 批量更新选项 */
export interface UpdateManyOptions<TModel = Record<string, unknown>> {
  /** 记录列表，每条记录必须包含 id 字段 */
  data: Partial<TModel>[]
}

/** 批量删除选项 */
export interface DeleteManyOptions {
  /** 要删除的 ID 列表 */
  ids: (string | number)[]
}
