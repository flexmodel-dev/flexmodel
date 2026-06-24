import { describe, it, expect, vi, beforeEach } from 'vitest'
import { FlexmodelClient } from '../src/client'
import { ModelHandle } from '../src/model-handle'
import { DataNamespace } from '../src/data-namespace'

function mockFetch(responseBody: unknown = {}, status = 200) {
  const mock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(responseBody),
    statusText: 'OK',
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('FlexmodelClient: constructor', () => {
  it('creates client with all options', () => {
    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })
    expect(client).toBeInstanceOf(FlexmodelClient)
    expect(client.data).toBeDefined()
  })

  it('creates client with empty options', () => {
    const client = new FlexmodelClient()
    expect(client).toBeInstanceOf(FlexmodelClient)
  })

  it('creates client with only apiKey', () => {
    const client = new FlexmodelClient({ apiKey: 'fm_ak_test' })
    expect(client).toBeInstanceOf(FlexmodelClient)
  })
})

describe('FlexmodelClient: data namespace', () => {
  it('data.from() returns a ModelHandle', () => {
    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      projectId: 'demo',
    })
    const handle = client.data.from('Student')
    expect(handle).toBeInstanceOf(ModelHandle)
  })

  it('data.Student (Proxy) returns the same ModelHandle as from()', () => {
    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      projectId: 'demo',
    })
    const fromHandle = client.data.from('Student')
    const proxyHandle = (client.data as any).Student
    expect(proxyHandle).toBeInstanceOf(ModelHandle)
    // Proxy 和 from() 应返回同一个缓存的实例
    expect(proxyHandle).toBe(fromHandle)
  })
})

describe('FlexmodelClient: schema() type narrowing', () => {
  it('schema<T>() returns same runtime instance', () => {
    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      projectId: 'demo',
    })
    const typed = client.schema<{ Student: { id: number; name: string } }>()
    expect(typed).toBe(client)
  })
})

describe('FlexmodelClient: end-to-end data operations', () => {
  it('findMany with filter and pagination', async () => {
    const fetchMock = mockFetch({
      total: 1,
      list: [{ id: 1, name: 'Alice', age: 18 }],
    })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    const result = await client.data.from('Student').findMany({
      where: { age: { _eq: 18 } },
      orderBy: 'name',
      page: 1,
      size: 20,
    })

    expect(result).toEqual({ total: 1, list: [{ id: 1, name: 'Alice', age: 18 }] })

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.pathname).toBe('/api/projects/demo/models/Student/records')
    expect(url.searchParams.get('page')).toBe('1')
    expect(url.searchParams.get('size')).toBe('20')
    expect(url.searchParams.has('filter')).toBe(true)
    expect(url.searchParams.has('sort')).toBe(true)
  })

  it('findOne returns single record', async () => {
    mockFetch({ id: 42, name: 'Bob' })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    const record = await client.data.from('Student').findOne(42)
    expect(record).toEqual({ id: 42, name: 'Bob' })
  })

  it('create single record', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'New', age: 16 })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    const result = await client.data.from('Student').create({ name: 'New', age: 16 })

    expect(fetchMock.mock.calls[0][1].method).toBe('POST')
    expect(result).toEqual({ id: 1, name: 'New', age: 16 })
  })

  it('update record', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'Updated' })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    await client.data.from('Student').update(1, { data: { name: 'Updated' } })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toContain('/records/1')
    expect(init.method).toBe('PUT')
  })

  it('merge (partial update) record', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'Merged', age: 16 })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    await client.data.from('Student').merge(1, { data: { name: 'Merged' } })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toContain('/records/1')
    expect(init.method).toBe('PATCH')
  })

  it('delete record', async () => {
    const fetchMock = mockFetch(undefined, 204)

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    await client.data.from('Student').delete(1)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toContain('/records/1')
    expect(init.method).toBe('DELETE')
  })

  it('count returns total number', async () => {
    mockFetch({ total: 42, list: [] })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    const count = await client.data.from('Student').count()
    expect(count).toBe(42)
  })

  it('count with filter', async () => {
    const fetchMock = mockFetch({ total: 5, list: [] })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    const total = await client.data.from('Student').count({ where: { age: { _gt: 18 } } })
    expect(total).toBe(5)

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.searchParams.get('size')).toBe('0')
    expect(url.searchParams.has('filter')).toBe(true)
  })
})

describe('FlexmodelClient: project() per-call override', () => {
  it('overrides default projectId', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'default-project',
    })

    await client.data.from('Student').project('other-project').findMany()

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain('/projects/other-project/')
  })
})

describe('FlexmodelClient: Authorization header', () => {
  it('sends Bearer token in header', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    await client.data.from('Student').findMany()

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['Authorization']).toBe('Bearer fm_ak_test')
  })
})
