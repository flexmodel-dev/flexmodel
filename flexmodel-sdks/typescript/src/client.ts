// ============================================================
// Flexmodel SDK — FlexmodelClient
//
// Main entry point. Creates DataNamespace for data CRUD.
// Future namespaces (auth, schema, storage, functions) will
// be added as sibling properties.
// ============================================================

import { HttpTransport } from './http.js'
import { DataNamespace } from './data-namespace.js'
import { ModelHandle } from './model-handle.js'
import type { FlexmodelClientOptions } from './types.js'

type SchemaMap = Record<string, Record<string, unknown>>

/**
 * Flexmodel SDK 客户端。
 *
 * @example
 * const client = new FlexmodelClient({
 *   apiKey: 'fm_ak_xxxxx',
 *   projectId: 'demo',
 * })
 *
 * // 便捷方法
 * const { list, total } = await client.data.from('Student').findMany({
 *   where: { age: { _eq: 18 } },
 *   orderBy: 'name',
 *   page: 1,
 *   size: 20,
 * })
 *
 * // Proxy 访问（等价于 from()）
 * const { list, total } = await client.data.Student.findMany({
 *   where: { age: { _eq: 18 } },
 * })
 */
export class FlexmodelClient<
  TSchema extends SchemaMap = SchemaMap,
> {
  private readonly http: HttpTransport
  private readonly defaultProjectId?: string

  /**
   * 数据操作命名空间。
   * 通过 Proxy 支持属性访问模型名：client.data.Student
   */
  readonly data: DataNamespace<TSchema> & { [K in keyof TSchema]: ModelHandle<TSchema[K]> }

  constructor(options: FlexmodelClientOptions = {}) {
    const baseURL = options.baseURL ?? this.getDefaultBaseURL()
    this.http = new HttpTransport(baseURL, options.apiKey)
    this.defaultProjectId = options.projectId

    const namespace = new DataNamespace<TSchema>(this.http, this.defaultProjectId)
    this.data = namespace.asProxy()
  }

  /**
   * 创建带类型约束的客户端实例。
   * 传入 Schema interface 后，data.Student 等属性获得类型推断。
   *
   * @example
   * interface MySchema {
   *   Student: { id: number; name: string; age: number }
   * }
   * const db = client.schema<MySchema>()
   * db.data.Student.findMany({ where: { age: { _eq: 18 } } }) // Student 有类型提示
   */
  schema<T extends SchemaMap>(): FlexmodelClient<T> {
    // schema() 是纯类型级操作，运行时行为不变
    // cast 是安全的：DataNamespace 的 Proxy 已经能拦截任意属性
    return this as unknown as FlexmodelClient<T>
  }

  /** 浏览器环境下默认同源，Node/Deno 需显式提供 baseURL */
  private getDefaultBaseURL(): string {
    if (typeof globalThis !== 'undefined' && 'location' in globalThis) {
      return (globalThis as { location: { origin: string } }).location.origin
    }
    return ''
  }
}
