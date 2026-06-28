// ============================================================
// Flexmodel SDK — ModelHandle
//
// Per-model convenience methods for data CRUD.
// Each ModelHandle holds model name + projectId context and
// delegates directly to HttpTransport — no chain builder needed.
// ============================================================

import type { HttpTransport } from './http.js'
import type {
  PageDTO, FilterNode, SortItem,
  FindManyOptions, FindOneOptions, CountOptions, CreateOptions,
  UpdateOptions, MergeOptions, SortInput, FieldSelection,
  CreateManyOptions, UpdateManyOptions, DeleteManyOptions,
} from './types.js'
import { serializeFilters, serializeSorts } from './filter-serializer.js'
import { FluentQueryBuilder } from './query-builder.js'

// ---- Normalization helpers ----

/** 将 SortInput 归一化为 SortItem[] */
export function normalizeSorts(input?: SortInput): SortItem[] {
  if (!input) return []
  if (typeof input === 'string') {
    return [parseSortString(input)]
  }
  if (Array.isArray(input)) {
    return input.map(item => typeof item === 'string' ? parseSortString(item) : item)
  }
  // 单个 SortItem 对象
  return [input as SortItem]
}

/** 解析排序字符串：'name' → ASC, 'name:DESC' → DESC */
function parseSortString(s: string): SortItem {
  const colonIdx = s.indexOf(':')
  if (colonIdx === -1) {
    return { field: s, sort: 'ASC' }
  }
  return {
    field: s.slice(0, colonIdx),
    sort: s.slice(colonIdx + 1) as 'ASC' | 'DESC',
  }
}

/** 将 FieldSelection 归一化为 string[] */
export function normalizeFields(input?: FieldSelection): string[] {
  if (!input) return []
  if (typeof input === 'string') {
    return input.split(',').map(s => s.trim()).filter(Boolean)
  }
  return input
}

/**
 * 模型操作句柄 — 绑定到特定模型名的便捷方法集合。
 *
 * @example
 * const handle = client.data.from('Student')
 * await handle.findMany({ where: { age: { _eq: 18 } }, page: 1, size: 20 })
 * await handle.findOne('001', { expand: ['class'] })
 * await handle.create({ name: 'Alice', age: 16 })
 */
export class ModelHandle<TModel = Record<string, unknown>> {
  private readonly http: HttpTransport
  private readonly modelName: string
  private readonly defaultProjectId?: string
  private readonly _projectId?: string

  constructor(
    http: HttpTransport,
    modelName: string,
    defaultProjectId?: string,
    projectIdOverride?: string,
  ) {
    this.http = http
    this.modelName = modelName
    this.defaultProjectId = defaultProjectId
    this._projectId = projectIdOverride
  }

  /** per-call 覆盖 projectId，返回新的 ModelHandle 实例 */
  project(projectId: string): ModelHandle<TModel> {
    return new ModelHandle<TModel>(this.http, this.modelName, this.defaultProjectId, projectId)
  }

  /**
   * 创建链式查询构建器 — 高级路径，用于复杂查询场景。
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
  query(): FluentQueryBuilder<TModel> {
    const pid = this._projectId ?? this.defaultProjectId
    return new FluentQueryBuilder<TModel>(this.http, this.modelName, pid)
  }

  // ---- 便捷方法 ----

  /**
   * 分页查询，返回 PageDTO<TModel>。
   *
   * @example
   * const { list, total } = await handle.findMany({
   *   where: { age: { _eq: 18 } },
   *   orderBy: 'name:DESC',
   *   page: 1,
   *   size: 20,
   *   expand: ['classId'],
   * })
   */
  async findMany(options?: FindManyOptions<TModel>): Promise<PageDTO<TModel>> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    const params = this.buildQueryParams(options)
    return this.http.request<PageDTO<TModel>>('GET', base, { params })
  }

  /**
   * 按主键 ID 获取单条记录。
   *
   * @example
   * const student = await handle.findOne('001', { expand: ['classId'] })
   */
  async findOne(id: string | number, options?: FindOneOptions): Promise<TModel> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    const params: Record<string, string> = {}
    const expandFields = normalizeFields(options?.expand)
    if (expandFields.length > 0) {
      params['expand'] = expandFields.join(',')
    }
    return this.http.request<TModel>('GET', `${base}/${id}`, { params })
  }

  /**
   * 创建记录（单条或批量）。
   *
   * 单条创建发送到基础路径，批量创建发送到 /batch 端点。
   *
   * @example
   * const created = await handle.create({ name: 'Alice', age: 16 })
   * const batch = await handle.create([{ name: 'Alice' }, { name: 'Bob' }])
   */
  async create(data: Partial<TModel>, options?: CreateOptions): Promise<TModel>
  async create(data: Partial<TModel>[], options?: CreateOptions): Promise<TModel[]>
  async create(data: Partial<TModel> | Partial<TModel>[], options?: CreateOptions): Promise<TModel | TModel[]> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    if (Array.isArray(data)) {
      return this.http.request<TModel[]>('POST', `${base}/batch`, { body: data })
    }
    return this.http.request<TModel>('POST', base, { body: data })
  }

  /**
   * 批量创建记录 — 发送到 /batch 端点，返回创建的记录列表。
   *
   * @example
   * const created = await handle.createMany([{ name: 'Alice' }, { name: 'Bob' }])
   */
  async createMany(data: Partial<TModel>[], options?: CreateManyOptions): Promise<TModel[]> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    return this.http.request<TModel[]>('POST', `${base}/batch`, { body: data })
  }

  /**
   * 批量更新记录 — 每条记录必须包含 id 字段，发送到 /batch 端点。
   *
   * @example
   * const updated = await handle.updateMany({ data: [{ id: 1, name: 'Alicia' }, { id: 2, name: 'Bob' }] })
   */
  async updateMany(options: UpdateManyOptions<TModel>): Promise<TModel[]> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    return this.http.request<TModel[]>('PUT', `${base}/batch`, { body: options.data })
  }

  /**
   * 批量删除记录 — 传入 ID 列表，发送到 /batch 端点，返回删除数量。
   *
   * @example
   * const deleted = await handle.deleteMany({ ids: [1, 2, 3] })
   */
  async deleteMany(options: DeleteManyOptions): Promise<number> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    return this.http.request<number>('DELETE', `${base}/batch`, { body: options.ids })
  }

  /**
   * 全量更新记录（PUT）。
   *
   * @example
   * const updated = await handle.update('001', { data: { name: 'Alicia', age: 17 } })
   */
  async update(id: string | number, options: UpdateOptions<TModel>): Promise<TModel> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    return this.http.request<TModel>('PUT', `${base}/${id}`, { body: options.data })
  }

  /**
   * 部分更新记录（PATCH）— 只修改指定字段，其余保持原值。
   *
   * @example
   * const merged = await handle.merge('001', { data: { name: 'Alicia' } })
   */
  async merge(id: string | number, options: MergeOptions<TModel>): Promise<TModel> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    return this.http.request<TModel>('PATCH', `${base}/${id}`, { body: options.data })
  }

  /**
   * 删除记录。
   *
   * @example
   * await handle.delete('001')
   */
  async delete(id: string | number): Promise<void> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    return this.http.request<void>('DELETE', `${base}/${id}`)
  }

  /**
   * 计数 — 返回符合条件的记录总数。
   *
   * @example
   * const total = await handle.count({ where: { age: { _gt: 18 } } })
   */
  async count(options?: CountOptions): Promise<number> {
    const pid = this.resolveProjectId()
    const base = this.basePath(pid)
    const params: Record<string, string> = { page: '1', size: '0' }
    if (options?.where) {
      const filter = serializeFilters([options.where])
      if (filter) params['filter'] = JSON.stringify(filter)
    }
    const result = await this.http.request<PageDTO<TModel>>('GET', base, { params })
    return result.total
  }

  // ---- Private helpers ----

  private resolveProjectId(): string {
    const pid = this._projectId ?? this.defaultProjectId
    if (!pid) {
      throw new Error(
        'projectId is required for data operations. ' +
        'Set it via FlexmodelClient({ projectId }) or .project(id) on the model handle.',
      )
    }
    return pid
  }

  private basePath(pid: string): string {
    return `/api/projects/${pid}/models/${this.modelName}/records`
  }

  private buildQueryParams(options?: FindManyOptions<TModel>): Record<string, string> {
    const params: Record<string, string> = {}

    // filter
    if (options?.where) {
      const filter = serializeFilters([options.where])
      if (filter) params['filter'] = JSON.stringify(filter)
    }

    // sort
    const sorts = normalizeSorts(options?.orderBy)
    if (sorts.length > 0) {
      params['sort'] = serializeSorts(sorts)!
    }

    // expand
    const expandFields = normalizeFields(options?.expand)
    if (expandFields.length > 0) {
      params['expand'] = expandFields.join(',')
    }

    // pagination
    if (options?.page !== undefined) {
      params['page'] = String(options.page)
    }
    if (options?.size !== undefined) {
      params['size'] = String(options.size)
    }

    return params
  }
}
