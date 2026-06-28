import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ModelHandle, normalizeSorts, normalizeFields } from '../src/model-handle'
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

function createHandle(modelName = 'Student', projectId = 'test-project') {
  const http = new HttpTransport('http://localhost:8080', 'fm_ak_test123')
  return new ModelHandle(http, modelName, projectId)
}

beforeEach(() => {
  vi.restoreAllMocks()
})

// ---- Normalization helpers ----

describe('normalizeSorts', () => {
  it('parses single string as ASC', () => {
    expect(normalizeSorts('name')).toEqual([{ field: 'name', sort: 'ASC' }])
  })

  it('parses string with :DESC', () => {
    expect(normalizeSorts('name:DESC')).toEqual([{ field: 'name', sort: 'DESC' }])
  })

  it('accepts SortItem object', () => {
    expect(normalizeSorts({ field: 'age', sort: 'ASC' })).toEqual([{ field: 'age', sort: 'ASC' }])
  })

  it('parses array of mixed strings', () => {
    expect(normalizeSorts(['name', 'age:DESC'])).toEqual([
      { field: 'name', sort: 'ASC' },
      { field: 'age', sort: 'DESC' },
    ])
  })

  it('returns empty array for undefined', () => {
    expect(normalizeSorts(undefined)).toEqual([])
  })
})

describe('normalizeFields', () => {
  it('parses comma-separated string', () => {
    expect(normalizeFields('class,teacher')).toEqual(['class', 'teacher'])
  })

  it('accepts array', () => {
    expect(normalizeFields(['class', 'teacher'])).toEqual(['class', 'teacher'])
  })

  it('returns empty array for undefined', () => {
    expect(normalizeFields(undefined)).toEqual([])
  })
})

// ---- findMany ----

describe('ModelHandle: findMany', () => {
  it('basic findMany with pagination', async () => {
    const fetchMock = mockFetch({ total: 10, list: [{ id: 1, name: 'Alice' }] })
    const handle = createHandle()

    const result = await handle.findMany({ page: 1, size: 20 })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records?page=1&size=20')
    expect(init.method).toBe('GET')
    expect(result).toEqual({ total: 10, list: [{ id: 1, name: 'Alice' }] })
  })

  it('findMany with where filter', async () => {
    const fetchMock = mockFetch({ total: 1, list: [{ id: 1 }] })
    const handle = createHandle()

    await handle.findMany({ where: { classId: { _eq: 1 } } })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain('filter=')
    const filterParam = new URL(url).searchParams.get('filter')
    expect(JSON.parse(filterParam!)).toEqual({ classId: { _eq: 1 } })
  })

  it('findMany with orderBy string', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })
    const handle = createHandle()

    await handle.findMany({ orderBy: 'name' })

    const sortParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('sort')
    expect(JSON.parse(sortParam!)).toEqual([{ field: 'name', sort: 'ASC' }])
  })

  it('findMany with orderBy DESC string', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })
    const handle = createHandle()

    await handle.findMany({ orderBy: 'name:DESC' })

    const sortParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('sort')
    expect(JSON.parse(sortParam!)).toEqual([{ field: 'name', sort: 'DESC' }])
  })

  it('findMany with expand', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })
    const handle = createHandle()

    await handle.findMany({ expand: ['classId', 'courseIds'] })

    const expandParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('expand')
    expect(expandParam).toBe('classId,courseIds')
  })

  it('findMany with expand as comma-separated string', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })
    const handle = createHandle()

    await handle.findMany({ expand: 'classId,courseIds' })

    const expandParam = new URL(fetchMock.mock.calls[0][0]).searchParams.get('expand')
    expect(expandParam).toBe('classId,courseIds')
  })

  it('findMany with all features combined', async () => {
    const fetchMock = mockFetch({ total: 5, list: [] })
    const handle = createHandle()

    await handle.findMany({
      where: { classId: { _eq: 1 } },
      orderBy: 'name',
      expand: ['classId'],
      page: 2,
      size: 10,
    })

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.pathname).toBe('/api/projects/test-project/models/Student/records')
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('size')).toBe('10')
    expect(url.searchParams.get('expand')).toBe('classId')
    expect(url.searchParams.has('filter')).toBe(true)
    expect(url.searchParams.has('sort')).toBe(true)
  })

  it('findMany with no options', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })
    const handle = createHandle()

    await handle.findMany()

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.pathname).toBe('/api/projects/test-project/models/Student/records')
    // 无额外参数（只有基础路径）
    expect(url.searchParams.toString()).toBe('')
  })
})

// ---- findOne ----

describe('ModelHandle: findOne', () => {
  it('fetches single record by ID', async () => {
    const fetchMock = mockFetch({ id: 42, name: 'Bob' })
    const handle = createHandle()

    const result = await handle.findOne(42)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/42')
    expect(init.method).toBe('GET')
    expect(result).toEqual({ id: 42, name: 'Bob' })
  })

  it('includes expand param', async () => {
    const fetchMock = mockFetch({ id: 1 })
    const handle = createHandle()

    await handle.findOne(1, { expand: ['classId'] })

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.searchParams.get('expand')).toBe('classId')
  })
})

// ---- create ----

describe('ModelHandle: create', () => {
  it('single create sends POST', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'Alice', age: 16 })
    const handle = createHandle()

    const data = { name: 'Alice', age: 16, classId: 1 }
    const result = await handle.create(data)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual(data)
    expect(result).toEqual({ id: 1, name: 'Alice', age: 16 })
  })

  it('batch create sends array to /batch endpoint', async () => {
    const fetchMock = mockFetch([{ id: 1 }, { id: 2 }])
    const handle = createHandle()

    const data = [
      { name: 'Alice', age: 16 },
      { name: 'Bob', age: 17 },
    ]
    const result = await handle.create(data)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/batch')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual(data)
    expect(result).toEqual([{ id: 1 }, { id: 2 }])
  })
})

// ---- update ----

describe('ModelHandle: update', () => {
  it('sends PUT with ID and data', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'Alicia' })
    const handle = createHandle()

    const result = await handle.update(1, { data: { name: 'Alicia', age: 17 } })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/1')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toEqual({ name: 'Alicia', age: 17 })
  })
})

// ---- merge ----

describe('ModelHandle: merge', () => {
  it('sends PATCH with ID and partial data', async () => {
    const fetchMock = mockFetch({ id: 1, name: 'Alicia', age: 16 })
    const handle = createHandle()

    await handle.merge(1, { data: { name: 'Alicia' } })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/1')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toEqual({ name: 'Alicia' })
  })
})

// ---- delete ----

describe('ModelHandle: delete', () => {
  it('delete by ID sends DELETE', async () => {
    const fetchMock = mockFetch(undefined, 204)
    const handle = createHandle()

    await handle.delete(1)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/1')
    expect(init.method).toBe('DELETE')
  })
})

// ---- count ----

describe('ModelHandle: count', () => {
  it('returns total from response', async () => {
    mockFetch({ total: 42, list: [] })
    const handle = createHandle()

    const count = await handle.count()
    expect(count).toBe(42)
  })

  it('count with filter', async () => {
    const fetchMock = mockFetch({ total: 5, list: [] })
    const handle = createHandle()

    const total = await handle.count({ where: { age: { _gt: 18 } } })

    const url = new URL(fetchMock.mock.calls[0][0])
    expect(url.searchParams.get('size')).toBe('0')
    expect(url.searchParams.has('filter')).toBe(true)
    expect(total).toBe(5)
  })
})

// ---- createMany ----

describe('ModelHandle: createMany', () => {
  it('sends POST to /batch with array', async () => {
    const fetchMock = mockFetch([{ id: 1, name: 'Alice' }, { id: 2, name: 'Bob' }])
    const handle = createHandle()

    const data = [
      { name: 'Alice', age: 16 },
      { name: 'Bob', age: 17 },
    ]
    const result = await handle.createMany(data)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/batch')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual(data)
    expect(result).toEqual([{ id: 1, name: 'Alice' }, { id: 2, name: 'Bob' }])
  })
})

// ---- updateMany ----

describe('ModelHandle: updateMany', () => {
  it('sends PUT to /batch with array', async () => {
    const fetchMock = mockFetch([{ id: 1, name: 'Alicia' }, { id: 2, name: 'BobUpdated' }])
    const handle = createHandle()

    const data = [
      { id: 1, name: 'Alicia' },
      { id: 2, name: 'BobUpdated' },
    ]
    const result = await handle.updateMany({ data })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/batch')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body)).toEqual(data)
    expect(result).toEqual([{ id: 1, name: 'Alicia' }, { id: 2, name: 'BobUpdated' }])
  })
})

// ---- deleteMany ----

describe('ModelHandle: deleteMany', () => {
  it('sends DELETE to /batch with ids', async () => {
    const fetchMock = mockFetch(3)
    const handle = createHandle()

    const ids = [1, 2, 3]
    const result = await handle.deleteMany({ ids })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/projects/test-project/models/Student/records/batch')
    expect(init.method).toBe('DELETE')
    expect(JSON.parse(init.body)).toEqual(ids)
    expect(result).toBe(3)
  })
})

// ---- project() override ----

describe('ModelHandle: project() override', () => {
  it('overrides default projectId', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })
    const handle = createHandle('Student', 'default-project')

    const overridden = handle.project('other-project')
    await overridden.findMany()

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain('/projects/other-project/')
  })

  it('project() returns new ModelHandle instance', () => {
    const handle = createHandle('Student', 'default-project')
    const overridden = handle.project('other-project')
    expect(overridden).not.toBe(handle)
  })
})

// ---- missing projectId ----

describe('ModelHandle: missing projectId', () => {
  it('throws when projectId is not set', async () => {
    mockFetch({})
    const http = new HttpTransport('http://localhost:8080')
    const handle = new ModelHandle(http, 'Student')

    await expect(handle.findMany()).rejects.toThrow('projectId is required')
  })
})

// ---- Authorization header ----

describe('ModelHandle: Authorization header', () => {
  it('sends Bearer token in header', async () => {
    const fetchMock = mockFetch({ total: 0, list: [] })
    const handle = createHandle()

    await handle.findMany()

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['Authorization']).toBe('Bearer fm_ak_test123')
  })
})
