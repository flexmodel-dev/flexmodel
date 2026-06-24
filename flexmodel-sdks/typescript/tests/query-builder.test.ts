import { describe, it, expect, vi, beforeEach } from 'vitest'
import { FluentQueryBuilder } from '../src/query-builder'
import { HttpTransport } from '../src/http'

function mockFetch(responseBody: unknown = {}, status = 200) {
  const mock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(responseBody),
    statusText: status === 200 ? 'OK' : 'Error',
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

function createBuilder(modelName = 'Student', projectId = 'test-project') {
  const http = new HttpTransport('http://localhost:8080', 'fm_ak_test123')
  return new FluentQueryBuilder(http, modelName, projectId)
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('FluentQueryBuilder: select', () => {
  it('basic select with pagination', async () => {
    const fetchMock = mockFetch({ total: 10, list: [{ id: 1, name: 'Alice' }] })
    const builder = createBuilder()

    const result = await builder.select().page(1, 20).execute()

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records?page=1&size=20')
    expect(init.method).toBe('GET')
    expect(result).toEqual({ total: 10, list: [{ id: 1, name: 'Alice' }] })
  })

  it('select with eq filter', async () => {
    const fetchMock = mockFetch({ total: 1, list: [{ id: 1 }] })

    await createBuilder().select().eq('classId', 1).execute()

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain('filter=')
    const filterParam = new URL(url).searchParams.get('filter')
    expect(JSON.parse(filterParam!)).toEqual({ classId: { _eq: 1 } })
  })

  it('select with multiple filters (auto AND)', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    await createBuilder().select().eq('classId', 1).gt('age', 15).execute()

    const filterParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('filter')
    expect(JSON.parse(filterParam!)).toEqual({
      _and: [{ classId: { _eq: 1 } }, { age: { _gt: 15 } }],
    })
  })

  it('select with sort', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    await createBuilder().select().orderBy('name', 'ASC').orderBy('id', 'DESC').execute()

    const sortParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('sort')
    expect(JSON.parse(sortParam!)).toEqual([
      { field: 'name', sort: 'ASC' },
      { field: 'id', sort: 'DESC' },
    ])
  })

  it('select with expand', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    await createBuilder().select().expand('classId', 'courseIds').execute()

    const expandParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('expand')
    expect(expandParam).toBe('classId,courseIds')
  })

  it('select with all features combined', async () => {
    const fetchMock = mockFetch({ total: 5, list: [] })

    await createBuilder()
      .select('id', 'name')
      .eq('classId', 1)
      .gt('age', 15)
      .orderBy('name', 'ASC')
      .expand('classId')
      .page(2, 10)
      .execute()

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.pathname).toBe('/api/projects/test-project/models/Student/records')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('size')).toBe('10')
    expect(url.searchParams.get('expand')).toBe('classId')
    expect(url.searchParams.has('filter')).toBe(true)
    expect(url.searchParams.has('sort')).toBe(true)
  })

  it('select defaults (no explicit .select()) — operation defaults to select', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    await createBuilder().eq('age', 18).execute()

    const [url, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('GET')
    expect(url).toContain('/models/Student/records')
    expect(url).toContain('filter=')
  })
})

describe('FluentQueryBuilder: single', () => {
  it('single() returns first item from list', async () => {
    mockFetch({ total: 1, list: [{ id: 1, name: 'Alice' }] })

    const result = await createBuilder().select().eq('id', 1).single()

    expect(result).toEqual({ id: 1, name: 'Alice' })
  })

  it('single() returns null when empty', async () => {
    mockFetch({ total: 0, list: [] })

    const result = await createBuilder().select().eq('id', 999).single()

    expect(result).toBeNull()
  })
})

describe('FluentQueryBuilder: count', () => {
  it('returns total from response', async () => {
    mockFetch({ total: 42, list: [] })

    const count = await createBuilder().count().execute()

    expect(count).toBe(42)
  })

  it('count with filter', async () => {
    const fetchMock = mockFetch({ total: 5, list: [] })

    await createBuilder().count().gt('age', 18).execute()

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.searchParams.get('size')).toBe('0')
    expect(url.searchParams.has('filter')).toBe(true)
  })
})

describe('FluentQueryBuilder: insert', () => {
  it('single insert sends POST', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'Alice', age: 16 })

    const data = { name: 'Alice', age: 16, classId: 1 }
    await createBuilder().insert(data).execute()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual(data)
  })

  it('batch insert sends array', async () => {
    const fetchMock = mockFetch([{ id: 1 }, { id: 2 }])

    const data = [
      { name: 'Alice', age: 16 },
      { name: 'Bob', age: 17 },
    ]
    await createBuilder().insert(data).execute()

    const [url, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual(data)
  })
})

describe('FluentQueryBuilder: update', () => {
  it('sends PUT with ID and data', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'Alicia' })

    await createBuilder()
      .update(1)
      .set({ name: 'Alicia', age: 17 })
      .execute()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/1')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toEqual({ name: 'Alicia', age: 17 })
  })
})

describe('FluentQueryBuilder: merge', () => {
  it('sends PATCH with ID and partial data', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'Alicia', age: 16 })

    await createBuilder()
      .merge(1)
      .set({ name: 'Alicia' })
      .execute()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/1')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toEqual({ name: 'Alicia' })
  })
})

describe('FluentQueryBuilder: delete', () => {
  it('delete by ID sends DELETE', async () => {
    const fetchMock = mockFetch(undefined, 204)

    await createBuilder().delete(1).execute()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/1')
    expect(init.method).toBe('DELETE')
  })
})

describe('FluentQueryBuilder: where() complex filter', () => {
  it('OR condition via where()', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    await createBuilder()
      .where((f) => f.or(f.eq('classId', 1), f.gt('age', 15)))
      .execute()

    const filterParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('filter')
    expect(JSON.parse(filterParam!)).toEqual({
      _or: [{ classId: { _eq: 1 } }, { age: { _gt: 15 } }],
    })
  })

  it('nested AND+OR via where()', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    await createBuilder()
      .where((f) =>
        f.or(
          f.and(f.eq('classId', 1), f.gt('age', 15)),
          f.and(f.eq('classId', 2), f.lt('age', 12)),
        ),
      )
      .execute()

    const filterParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('filter')
    expect(JSON.parse(filterParam!)).toEqual({
      _or: [
        { _and: [{ classId: { _eq: 1 } }, { age: { _gt: 15 } }] },
        { _and: [{ classId: { _eq: 2 } }, { age: { _lt: 12 } }] },
      ],
    })
  })
})

describe('FluentQueryBuilder: filter() raw fallback', () => {
  it('passes raw filter object through', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    await createBuilder()
      .filter({ name: { _eq: 'Alice' }, age: { _gt: 15 } })
      .execute()

    const filterParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('filter')
    expect(JSON.parse(filterParam!)).toEqual({ name: { _eq: 'Alice' }, age: { _gt: 15 } })
  })
})

describe('FluentQueryBuilder: missing projectId', () => {
  it('throws when projectId is not set', async () => {
    mockFetch({})
    const http = new HttpTransport('http://localhost:8080')
    const builder = new FluentQueryBuilder(http, 'Student')

    await expect(builder.select().execute()).rejects.toThrow('projectId is required')
  })
})

describe('FluentQueryBuilder: Authorization header', () => {
  it('sends Bearer token in header', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    await createBuilder().select().execute()

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['Authorization']).toBe('Bearer fm_ak_test123')
  })
})

describe('FluentQueryBuilder: via ModelHandle.query()', () => {
  it('query() returns FluentQueryBuilder with correct model context', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })

    const { FlexmodelClient } = await import('../src/client')
    const client = new FlexmodelClient({
      baseURL: 'http://localhost:8080',
      apiKey: 'fm_ak_test',
      projectId: 'demo',
    })

    await client.data.from('Student').query()
      .eq('age', 18)
      .gt('score', 60)
      .orderBy('name')
      .expand('class', 'teacher')
      .page(1, 20)
      .execute()

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.pathname).toBe('/api/projects/demo/models/Student/records')
    expect(url.searchParams.get('page')).toBe('1')
    expect(url.searchParams.get('size')).toBe('20')
    expect(url.searchParams.get('expand')).toBe('class,teacher')
    expect(url.searchParams.has('filter')).toBe(true)
    expect(url.searchParams.has('sort')).toBe(true)
  })
})
