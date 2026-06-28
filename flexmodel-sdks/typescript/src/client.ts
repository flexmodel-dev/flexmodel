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
  private defaultProjectId?: string
  private readonly namespace: DataNamespace<TSchema>

  /**
   * 数据操作命名空间。
   * 通过 Proxy 支持属性访问模型名：client.data.Student
   */
  readonly data: DataNamespace<TSchema> & { [K in keyof TSchema]: ModelHandle<TSchema[K]> }

  constructor(options: FlexmodelClientOptions = {}) {
    const baseURL = options.baseURL ?? this.getDefaultBaseURL()
    this.http = new HttpTransport(baseURL, options.apiKey)
    this.defaultProjectId = options.projectId

    this.namespace = new DataNamespace<TSchema>(this.http, this.defaultProjectId)
    this.data = this.namespace.asProxy()
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

  /**
   * 设置当前请求的认证令牌（优先级高于构造函数中的 apiKey）。
   * 传入 undefined 清除，恢复使用默认 apiKey。
   */
  setAuthToken(token?: string): void {
    this.http.setAuthToken(token)
  }

  /**
   * 设置默认 projectId（运行时可变）。
   * 更新客户端及 DataNamespace 的默认 projectId，并清空已缓存的 ModelHandle，
   * 使后续 from() 调用以新 projectId 重建句柄。
   *
   * @example
   * flexmodelClient.setProjectId('my-project')
   */
  setProjectId(projectId?: string): void {
    this.defaultProjectId = projectId
    this.namespace.updateDefaultProjectId(projectId)
  }

  /** 浏览器环境下默认同源，Node/Deno 需显式提供 baseURL */
  private getDefaultBaseURL(): string {
    if (typeof globalThis !== 'undefined' && 'location' in globalThis) {
      return (globalThis as { location: { origin: string } }).location.origin
    }
    return ''
  }
}

// ============================================================
// 预初始化单例 — 从环境变量读取 baseURL
// ============================================================

function getEnvBaseURL(): string {
  const g = globalThis as Record<string, any>
  // Deno
  if (typeof g.Deno !== 'undefined' && g.Deno?.env) {
    const host = g.Deno.env.get('FLEXMODEL_JAVA_HOST') ?? 'localhost'
    const port = g.Deno.env.get('FLEXMODEL_JAVA_PORT') ?? '8080'
    return `http://${host}:${port}`
  }
  // Node.js
  if (typeof g.process !== 'undefined' && g.process?.env) {
    const host = g.process.env['FLEXMODEL_JAVA_HOST'] ?? 'localhost'
    const port = g.process.env['FLEXMODEL_JAVA_PORT'] ?? '8080'
    return `http://${host}:${port}`
  }
  // 浏览器 — 同源
  return ''
}

export const flexmodelClient = new FlexmodelClient({
  baseURL: getEnvBaseURL(),
})
