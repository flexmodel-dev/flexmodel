import { describe, it, expect, vi, beforeEach } from 'vitest'
import { HttpTransport } from '../src/http'
import { FlexmodelApiError, FlexmodelAuthError } from '../src/errors'

function mockFetch(body: unknown, status: number, statusText = '') {
  const mock = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    statusText: statusText || (status === 200 ? 'OK' : 'Error'),
    json: () => Promise.resolve(body),
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('HttpTransport: request basics', () => {
  it('sends GET request with correct URL', async () => {
    const fetchMock = mockFetch({ data: 'ok' }, 200)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('GET', '/api/test')

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/test')
    expect(init.method).toBe('GET')
  })

  it('appends query params', async () => {
    const fetchMock = mockFetch({}, 200)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('GET', '/api/test', {
      params: { page: '1', size: '20' },
    })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/test?page=1&size=20')
  })

  it('sends POST with JSON body', async () => {
    const fetchMock = mockFetch({ id: 1 }, 200)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('POST', '/api/test', {
      body: { name: 'Alice' },
    })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/test')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ name: 'Alice' })
  })

  it('sends PUT request', async () => {
    const fetchMock = mockFetch({}, 200)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('PUT', '/api/test/1', { body: { name: 'Bob' } })

    expect(fetchMock.mock.calls[0][1].method).toBe('PUT')
  })

  it('sends PATCH request', async () => {
    const fetchMock = mockFetch({}, 200)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('PATCH', '/api/test/1', { body: { name: 'Bob' } })

    expect(fetchMock.mock.calls[0][1].method).toBe('PATCH')
  })

  it('sends DELETE request', async () => {
    const fetchMock = mockFetch(undefined, 204)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('DELETE', '/api/test/1')

    expect(fetchMock.mock.calls[0][1].method).toBe('DELETE')
  })

  it('returns undefined for 204 No Content', async () => {
    mockFetch(undefined, 204)
    const http = new HttpTransport('http://localhost:8080')

    const result = await http.request('DELETE', '/api/test/1')
    expect(result).toBeUndefined()
  })

  it('strips trailing slash from baseURL', async () => {
    const fetchMock = mockFetch({}, 200)
    const http = new HttpTransport('http://localhost:8080/')

    await http.request('GET', '/api/test')

    expect(fetchMock.mock.calls[0][0]).toBe('http://localhost:8080/api/test')
  })
})

describe('HttpTransport: API Key injection', () => {
  it('injects Authorization header when apiKey is provided', async () => {
    const fetchMock = mockFetch({}, 200)
    const http = new HttpTransport('http://localhost:8080', 'fm_ak_secret')

    await http.request('GET', '/api/test')

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['Authorization']).toBe('Bearer fm_ak_secret')
  })

  it('omits Authorization header when no apiKey', async () => {
    const fetchMock = mockFetch({}, 200)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('GET', '/api/test')

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['Authorization']).toBeUndefined()
  })
})

describe('HttpTransport: error handling', () => {
  it('throws FlexmodelAuthError on 401', async () => {
    mockFetch({ message: 'Invalid API Key' }, 401)
    const http = new HttpTransport('http://localhost:8080', 'fm_ak_bad')

    await expect(http.request('GET', '/api/test')).rejects.toThrow(FlexmodelAuthError)
  })

  it('FlexmodelAuthError contains message from response', async () => {
    mockFetch({ message: 'Project not allowed' }, 401)
    const http = new HttpTransport('http://localhost:8080', 'fm_ak_bad')

    try {
      await http.request('GET', '/api/test')
      expect.fail('should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(FlexmodelAuthError)
      expect((err as FlexmodelAuthError).message).toBe('Project not allowed')
      expect((err as FlexmodelAuthError).status).toBe(401)
    }
  })

  it('throws FlexmodelApiError on 404', async () => {
    mockFetch({ code: -1, message: 'Record not found' }, 404)
    const http = new HttpTransport('http://localhost:8080')

    await expect(http.request('GET', '/api/test')).rejects.toThrow(FlexmodelApiError)
  })

  it('FlexmodelApiError contains status and code', async () => {
    mockFetch({ code: 42, message: 'Validation failed' }, 400)
    const http = new HttpTransport('http://localhost:8080')

    try {
      await http.request('GET', '/api/test')
      expect.fail('should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(FlexmodelApiError)
      const apiErr = err as FlexmodelApiError
      expect(apiErr.status).toBe(400)
      expect(apiErr.code).toBe(42)
      expect(apiErr.message).toBe('Validation failed')
    }
  })

  it('throws FlexmodelApiError on 500', async () => {
    mockFetch({ code: -1, message: 'Internal error' }, 500)
    const http = new HttpTransport('http://localhost:8080')

    await expect(http.request('GET', '/api/test')).rejects.toThrow(FlexmodelApiError)
  })
})

describe('HttpTransport: headers', () => {
  it('sets content-type and accept headers', async () => {
    const fetchMock = mockFetch({}, 200)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('GET', '/api/test')

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['content-type']).toBe('application/json')
    expect(init.headers['accept']).toBe('application/json')
  })

  it('merges custom headers', async () => {
    const fetchMock = mockFetch({}, 200)
    const http = new HttpTransport('http://localhost:8080')

    await http.request('GET', '/api/test', {
      headers: { 'X-Custom': 'value' },
    })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers['X-Custom']).toBe('value')
    expect(init.headers['content-type']).toBe('application/json')
  })
})
