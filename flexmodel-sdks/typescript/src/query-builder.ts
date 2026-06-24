// ============================================================
// Flexmodel SDK — Fluent Query Builder
//
// Chain-style builder for complex data operations.
// Created via ModelHandle.query() — not a standalone entry point.
//
// Maps to backend RecordResource REST endpoints:
//   GET    /api/projects/{projectId}/models/{model}/records
//   GET    /api/projects/{projectId}/models/{model}/records/{id}
//   POST   /api/projects/{projectId}/models/{model}/records
//   PUT    /api/projects/{projectId}/models/{model}/records/{id}
//   PATCH  /api/projects/{projectId}/models/{model}/records/{id}
//   DELETE /api/projects/{projectId}/models/{model}/records/{id}
// ============================================================

import type { HttpTransport } from './http.js'
import type { FilterNode, PageDTO, SortItem } from './types.js'
import {
  filterEq, filterNe, filterGt, filterGte, filterLt, filterLte,
  filterIn, filterNin, filterBetween,
  filterContains, filterNotContains, filterStartsWith, filterEndsWith,
  filterFn,
} from './filter-builder.js'
import type { FilterFn } from './filter-builder.js'
import { serializeFilters, serializeSorts } from './filter-serializer.js'

export type Operation = 'select' | 'insert' | 'update' | 'merge' | 'delete' | 'count'

/**
 * 链式查询构建器 — 高级路径，用于复杂查询场景。
 * 通过 ModelHandle.query() 创建，model 名和 projectId 在构造时固定。
 *
 * @example
 * const result = await client.data.from('Student').query()
 *   .eq('age', 18)
 *   .gt('score', 60)
 *   .orderBy('name')
 *   .expand('class', 'teacher')
 *   .page(1, 20)
 *   .execute()
 */
export class FluentQueryBuilder<TModel = Record<string, unknown>> {
  private readonly http: HttpTransport
  private readonly modelName: string
  private readonly defaultProjectId?: string

  private _operation: Operation = 'select'
  private _selectFields?: string[]
  private _filters: FilterNode[] = []
  private _sorts: SortItem[] = []
  private _expandFields: string[] = []
  private _page?: { number: number; size: number }
  private _data?: Partial<TModel> | Partial<TModel>[]
  private _id?: string | number

  constructor(http: HttpTransport, modelName: string, defaultProjectId?: string) {
    this.http = http
    this.modelName = modelName
    this.defaultProjectId = defaultProjectId
  }

  // ---- 操作入口 ----

  /** 设置查询操作（默认），可选指定投影字段 */
  select(...fields: string[]): this {
    this._operation = 'select'
    if (fields.length > 0) this._selectFields = fields
    return this
  }

  /** 设置插入操作 */
  insert(data: Partial<TModel> | Partial<TModel>[]): this {
    this._operation = 'insert'
    this._data = data
    return this
  }

  /** 设置全量更新操作，指定记录 ID */
  update(id: string | number): this {
    this._operation = 'update'
    this._id = id
    return this
  }

  /** 设置部分更新（PATCH）操作，指定记录 ID */
  merge(id: string | number): this {
    this._operation = 'merge'
    this._id = id
    return this
  }

  /** 设置删除操作，可选指定 ID */
  delete(id?: string | number): this {
    this._operation = 'delete'
    if (id !== undefined) this._id = id
    return this
  }

  /** 设置计数操作 */
  count(): this {
    this._operation = 'count'
    return this
  }

  // ---- 过滤器（链式追加） ----

  eq(field: string, value: unknown): this { this._filters.push(filterEq(field, value)); return this }
  ne(field: string, value: unknown): this { this._filters.push(filterNe(field, value)); return this }
  gt(field: string, value: unknown): this { this._filters.push(filterGt(field, value)); return this }
  gte(field: string, value: unknown): this { this._filters.push(filterGte(field, value)); return this }
  lt(field: string, value: unknown): this { this._filters.push(filterLt(field, value)); return this }
  lte(field: string, value: unknown): this { this._filters.push(filterLte(field, value)); return this }
  in(field: string, values: unknown[]): this { this._filters.push(filterIn(field, values)); return this }
  nin(field: string, values: unknown[]): this { this._filters.push(filterNin(field, values)); return this }
  between(field: string, a: unknown, b: unknown): this { this._filters.push(filterBetween(field, a, b)); return this }
  contains(field: string, str: string): this { this._filters.push(filterContains(field, str)); return this }
  notContains(field: string, str: string): this { this._filters.push(filterNotContains(field, str)); return this }
  startsWith(field: string, str: string): this { this._filters.push(filterStartsWith(field, str)); return this }
  endsWith(field: string, str: string): this { this._filters.push(filterEndsWith(field, str)); return this }

  // ---- 逻辑组合 ----

  /** 函数式复杂条件，回调内使用 f.eq / f.or / f.and 等 */
  where(builder: (f: FilterFn) => FilterNode): this {
    this._filters.push(builder(filterFn))
    return this
  }

  /** 原始 filter 对象直接透传 */
  filter(raw: Record<string, unknown>): this {
    this._filters.push(raw as FilterNode)
    return this
  }

  // ---- 排序 ----

  orderBy(field: string, direction: 'ASC' | 'DESC' = 'ASC'): this {
    this._sorts.push({ field, sort: direction })
    return this
  }

  // ---- 关联加载 ----

  expand(...fields: string[]): this {
    this._expandFields.push(...fields)
    return this
  }

  // ---- 分页 ----

  page(number: number, size: number): this {
    this._page = { number, size }
    return this
  }

  // ---- 数据设置（update/merge） ----

  set(data: Partial<TModel>): this {
    this._data = data
    return this
  }

  // ---- 终端方法 ----

  /** 执行查询，根据操作类型返回不同结果 */
  async execute(): Promise<PageDTO<TModel> | TModel | TModel[] | number | void> {
    const pid = this.resolveProjectId()
    const base = `/api/projects/${pid}/models/${this.modelName}/records`

    switch (this._operation) {
      case 'select':
        return this.http.request<PageDTO<TModel>>('GET', base, {
          params: this.buildSelectParams(),
        })

      case 'count':
        return this.executeCount(base)

      case 'insert':
        return this.http.request<TModel | TModel[]>('POST', base, {
          body: this._data,
        })

      case 'update':
        return this.http.request<TModel>('PUT', `${base}/${this._id}`, {
          body: this._data,
        })

      case 'merge':
        return this.http.request<TModel>('PATCH', `${base}/${this._id}`, {
          body: this._data,
        })

      case 'delete':
        return this.http.request<void>('DELETE', `${base}/${this._id}`)
    }
  }

  /** 获取单条记录，无匹配时返回 null */
  async single(): Promise<TModel | null> {
    const pid = this.resolveProjectId()
    const base = `/api/projects/${pid}/models/${this.modelName}/records`
    const result = await this.http.request<PageDTO<TModel>>('GET', base, {
      params: { ...this.buildSelectParams(), page: '1', size: '1' },
    })
    return result.list[0] ?? null
  }

  // ---- Private helpers ----

  private async executeCount(base: string): Promise<number> {
    const params = this.buildSelectParams()
    params['page'] = '1'
    params['size'] = '0'
    const result = await this.http.request<PageDTO<TModel>>('GET', base, { params })
    return result.total
  }

  private resolveProjectId(): string {
    const pid = this.defaultProjectId
    if (!pid) {
      throw new Error(
        'projectId is required for data operations. ' +
        'Set it via FlexmodelClient({ projectId }) or .project(id) on the model handle.',
      )
    }
    return pid
  }

  private buildSelectParams(): Record<string, string> {
    const params: Record<string, string> = {}

    const filter = serializeFilters(this._filters)
    if (filter) params['filter'] = JSON.stringify(filter)

    const sort = serializeSorts(this._sorts)
    if (sort) params['sort'] = sort

    if (this._expandFields.length > 0) {
      params['expand'] = this._expandFields.join(',')
    }

    if (this._page) {
      params['page'] = String(this._page.number)
      params['size'] = String(this._page.size)
    }

    return params
  }
}
