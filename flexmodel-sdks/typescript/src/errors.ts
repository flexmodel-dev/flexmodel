// ============================================================
// Flexmodel SDK — Error Classes
// ============================================================

/** SDK 错误基类 */
export class FlexmodelError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'FlexmodelError'
  }
}

/**
 * 业务错误 — 映射后端 { code, message, success } 响应格式。
 * 非 2xx 且非 401 的响应均抛出此错误。
 */
export class FlexmodelApiError extends FlexmodelError {
  /** HTTP 状态码 */
  readonly status: number
  /** 后端业务错误码 */
  readonly code: number
  /** 后端错误详情（可选） */
  readonly details?: unknown

  constructor(status: number, code: number, message: string, details?: unknown) {
    super(message)
    this.name = 'FlexmodelApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

/**
 * 认证错误 — HTTP 401 响应时抛出。
 * 原因：API Key 无效、已过期、或 projectId 不在 API Key 的 project_ids 白名单中。
 */
export class FlexmodelAuthError extends FlexmodelError {
  /** HTTP 状态码（固定 401） */
  readonly status = 401

  constructor(message: string = 'Authentication failed') {
    super(message)
    this.name = 'FlexmodelAuthError'
  }
}
