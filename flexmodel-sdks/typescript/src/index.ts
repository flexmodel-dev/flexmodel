// ============================================================
// Flexmodel SDK — Public API Entry Point
// ============================================================

export { FlexmodelClient, flexmodelClient } from './client.js'
export { DataNamespace } from './data-namespace.js'
export { ModelHandle } from './model-handle.js'
export { FluentQueryBuilder } from './query-builder.js'
export { HttpTransport } from './http.js'
export { FlexmodelError, FlexmodelApiError, FlexmodelAuthError } from './errors.js'
export { normalizeSorts, normalizeFields } from './model-handle.js'
export type {
  FlexmodelClientOptions,
  PageDTO,
  FilterNode,
  FieldFilter,
  SortItem,
  RequestOptions,
  SortInput,
  FieldSelection,
  FindManyOptions,
  FindOneOptions,
  CountOptions,
  CreateOptions,
  UpdateOptions,
  MergeOptions,
  CreateManyOptions,
  UpdateManyOptions,
  DeleteManyOptions,
} from './types.js'
export type { FilterFn } from './filter-builder.js'
export type { Schema, RelationToOne, RelationToMany } from './type-helpers.js'
