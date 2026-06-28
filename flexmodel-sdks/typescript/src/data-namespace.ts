// ============================================================
// Flexmodel SDK — DataNamespace
//
// Namespace for data CRUD operations.
// Uses Proxy to allow client.data.Student as shorthand for
// client.data.from('Student') — same runtime result, but
// V2+ with schema definition will enable IDE auto-completion.
// ============================================================

import type { HttpTransport } from './http.js'
import { ModelHandle } from './model-handle.js'

type SchemaMap = Record<string, Record<string, unknown>>

/**
 * 数据操作命名空间 — 所有数据 CRUD 通过此对象访问。
 *
 * @example
 * // V1: 显式选模型
 * const students = await client.data.from('Student').findMany({ where: { age: { _eq: 18 } } })
 *
 * // Proxy 访问（运行时等价于 from()）
 * const students = await client.data.Student.findMany({ where: { age: { _eq: 18 } } })
 */
export class DataNamespace<TSchema extends SchemaMap = SchemaMap> {
  private readonly http: HttpTransport
  private defaultProjectId?: string
  private readonly models = new Map<string, ModelHandle<Record<string, unknown>>>()
  private readonly proxy: DataNamespace<TSchema> & { [K in keyof TSchema]: ModelHandle<TSchema[K]> }

  constructor(http: HttpTransport, defaultProjectId?: string) {
    this.http = http
    this.defaultProjectId = defaultProjectId
    this.proxy = this.createProxy()
  }

  /**
   * 运行时更新默认 projectId，并清空已缓存的 ModelHandle。
   * 后续 from() 调用会以新 projectId 重建句柄。
   */
  updateDefaultProjectId(projectId?: string): void {
    this.defaultProjectId = projectId
    this.models.clear()
  }

  /**
   * 显式选择目标模型，返回 ModelHandle。
   * V1 无 schema 时使用此方法；Proxy 访问运行时等价。
   *
   * @example
   * const handle = client.data.from('Student')
   * await handle.findMany({ where: { age: { _eq: 18 } } })
   */
  from<TModel = Record<string, unknown>>(modelName: string): ModelHandle<TModel> {
    const cached = this.models.get(modelName)
    if (cached) return cached as ModelHandle<TModel>

    const handle = new ModelHandle<TModel>(this.http, modelName, this.defaultProjectId)
    this.models.set(modelName, handle as ModelHandle<Record<string, unknown>>)
    return handle
  }

  /** 获取 Proxy 包装实例，使 client.data.Student 可直接访问 */
  asProxy(): DataNamespace<TSchema> & { [K in keyof TSchema]: ModelHandle<TSchema[K]> } {
    return this.proxy
  }

  private createProxy(): DataNamespace<TSchema> & { [K in keyof TSchema]: ModelHandle<TSchema[K]> } {
    return new Proxy(this, {
      get(target, prop) {
        // symbol 属性（如 Symbol.toStringTag）直接返回自身属性
        if (typeof prop === 'symbol') return (target as any)[prop]
        // DataNamespace 自身方法（from, asProxy 等）直接返回
        if (prop in target) return (target as any)[prop]
        // 任意其他字符串属性 → 视为模型名，返回 ModelHandle
        return target.from(prop as string)
      },
    }) as DataNamespace<TSchema> & { [K in keyof TSchema]: ModelHandle<TSchema[K]> }
  }
}
