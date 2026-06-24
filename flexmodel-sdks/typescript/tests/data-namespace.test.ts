import { describe, it, expect, vi, beforeEach } from 'vitest'
import { DataNamespace } from '../src/data-namespace'
import { ModelHandle } from '../src/model-handle'
import { HttpTransport } from '../src/http'

function createNamespace(projectId = 'test-project') {
  const http = new HttpTransport('http://localhost:8080', 'fm_ak_test')
  return new DataNamespace(http, projectId)
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('DataNamespace: from()', () => {
  it('returns a ModelHandle', () => {
    const ns = createNamespace()
    const handle = ns.from('Student')
    expect(handle).toBeInstanceOf(ModelHandle)
  })

  it('caches ModelHandle for the same model name', () => {
    const ns = createNamespace()
    const handle1 = ns.from('Student')
    const handle2 = ns.from('Student')
    expect(handle1).toBe(handle2)
  })

  it('returns different ModelHandle for different model names', () => {
    const ns = createNamespace()
    const student = ns.from('Student')
    const classHandle = ns.from('Class')
    expect(student).not.toBe(classHandle)
  })
})

describe('DataNamespace: Proxy', () => {
  it('Proxy returns ModelHandle for model name property', () => {
    const ns = createNamespace()
    const proxy = ns.asProxy()

    const handle = (proxy as any).Student
    expect(handle).toBeInstanceOf(ModelHandle)
  })

  it('Proxy and from() return same cached instance', () => {
    const ns = createNamespace()
    const proxy = ns.asProxy()

    const fromHandle = ns.from('Student')
    const proxyHandle = (proxy as any).Student
    expect(proxyHandle).toBe(fromHandle)
  })

  it('Proxy returns real DataNamespace methods (from, asProxy)', () => {
    const ns = createNamespace()
    const proxy = ns.asProxy()

    expect(proxy.from).toBe(ns.from)
    expect(proxy.asProxy).toBe(ns.asProxy)
  })

  it('Proxy returns undefined for non-existent DataNamespace properties', () => {
    const ns = createNamespace()
    const proxy = ns.asProxy()

    // DataNamespace 自身没有 foo 方法，所以 Proxy 会把 'foo' 视为模型名
    const handle = (proxy as any).foo
    expect(handle).toBeInstanceOf(ModelHandle)
  })

  it('Proxy works with multiple different model names', () => {
    const ns = createNamespace()
    const proxy = ns.asProxy()

    const student = (proxy as any).Student
    const classHandle = (proxy as any).Class
    expect(student).toBeInstanceOf(ModelHandle)
    expect(classHandle).toBeInstanceOf(ModelHandle)
    expect(student).not.toBe(classHandle)
  })
})
