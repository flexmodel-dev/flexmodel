// ============================================================
// Flexmodel SDK — HTTP Transport Layer
//
// Thin fetch wrapper that:
// - Injects Authorization: Bearer <apiKey> header
// - Throws FlexmodelAuthError on 401
// - Throws FlexmodelApiError on other non-2xx responses
// ============================================================

import { FlexmodelApiError, FlexmodelAuthError } from './errors.js'
import type { RequestOptions } from './types.js'

export class HttpTransport {
  private readonly baseURL: string
  private readonly apiKey?: string
  private activeToken?: string

  constructor(baseURL: string, apiKey?: string) {
    // 去除尾部斜杠，统一路径拼接
    this.baseURL = baseURL.replace(/\/+$/, '')
    this.apiKey = apiKey
  }

  /**
   * 设置当前请求使用的认证令牌（优先级高于构造函数中的 apiKey）。
   * 传入 undefined 可清除，恢复使用默认 apiKey。
   */
  setAuthToken(token?: string): void {
    this.activeToken = token
  }

  /**
   * 发送 HTTP 请求。
   *
   * @param method  HTTP 方法（GET/POST/PUT/PATCH/DELETE）
   * @param path    相对路径，如 /projects/demo/models/Student/records
   * @param options 可选的 params / body / headers
   */
  async request<T>(method: string, path: string, options?: RequestOptions): Promise<T> {
    const url = this.buildUrl(path, options?.params)

    const headers: Record<string, string> = {
      'content-type': 'application/json',
      accept: 'application/json',
      ...options?.headers,
    }

    if (this.activeToken) {
      headers['Authorization'] = `Bearer ${this.activeToken}`
    } else if (this.apiKey) {
      headers['Authorization'] = `Bearer ${this.apiKey}`
    }

    const init: RequestInit = { method, headers }

    if (options?.body !== undefined) {
      init.body = JSON.stringify(options.body)
    }

    const response = await fetch(url, init)

    // 401 → FlexmodelAuthError
    if (response.status === 401) {
      const body = await safeJson(response)
      throw new FlexmodelAuthError(String(body?.message ?? 'Authentication failed'))
    }

    // 其他非 2xx → FlexmodelApiError
    if (!response.ok) {
      const body = await safeJson(response)
      throw new FlexmodelApiError(
        response.status,
        Number(body?.code ?? -1),
        String(body?.message ?? response.statusText),
        body?.details,
      )
    }

    // 204 No Content
    if (response.status === 204) {
      return undefined as T
    }

    return response.json() as Promise<T>
  }

  private buildUrl(path: string, params?: Record<string, string>): string {
    const url = `${this.baseURL}${path}`
    if (!params) return url
    const searchParams = new URLSearchParams()
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) {
        searchParams.set(key, value)
      }
    }
    const qs = searchParams.toString()
    return qs ? `${url}?${qs}` : url
  }
}

/** 安全解析 JSON，失败返回 null */
async function safeJson(response: Response): Promise<Record<string, unknown> | null> {
  try {
    return await response.json()
  } catch {
    return null
  }
}
