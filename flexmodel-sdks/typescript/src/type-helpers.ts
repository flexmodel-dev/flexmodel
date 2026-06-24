// ============================================================
// Flexmodel SDK — Type Helpers
// ============================================================

/**
 * Schema 映射类型 — 模型名到行类型的映射。
 * 用户定义后传入 client.schema<T>()，获得字段级类型推断。
 *
 * @example
 * interface MySchema {
 *   Student: { id: number; name: string; age: number }
 *   Class:   { id: number; name: string }
 * }
 */
export type Schema<T extends Record<string, Record<string, unknown>> = Record<string, Record<string, unknown>>> = T

/** 单值关联（外键 → 一条关联记录） */
export type RelationToOne<T> = T & { _type: 'relation-to-one' }

/** 多值关联（外键 → 关联记录数组） */
export type RelationToMany<T> = T[] & { _type: 'relation-to-many' }
