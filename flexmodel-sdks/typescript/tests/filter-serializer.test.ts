import { describe, it, expect } from 'vitest'
import { serializeFilters, serializeSorts } from '../src/filter-serializer'
import {
  filterEq, filterNe, filterGt, filterGte, filterLt, filterLte,
  filterIn, filterNin, filterBetween,
  filterContains, filterNotContains, filterStartsWith, filterEndsWith,
  filterOr, filterAnd,
} from '../src/filter-builder'

describe('filter-builder: field-level constructors', () => {
  it('filterEq', () => {
    expect(filterEq('name', 'Alice')).toEqual({ name: { _eq: 'Alice' } })
  })

  it('filterNe', () => {
    expect(filterNe('status', 'disabled')).toEqual({ status: { _ne: 'disabled' } })
  })

  it('filterGt', () => {
    expect(filterGt('age', 15)).toEqual({ age: { _gt: 15 } })
  })

  it('filterGte', () => {
    expect(filterGte('score', 60)).toEqual({ score: { _gte: 60 } })
  })

  it('filterLt', () => {
    expect(filterLt('age', 18)).toEqual({ age: { _lt: 18 } })
  })

  it('filterLte', () => {
    expect(filterLte('price', 100)).toEqual({ price: { _lte: 100 } })
  })

  it('filterIn', () => {
    expect(filterIn('role', ['admin', 'user'])).toEqual({ role: { _in: ['admin', 'user'] } })
  })

  it('filterNin', () => {
    expect(filterNin('status', ['deleted'])).toEqual({ status: { _nin: ['deleted'] } })
  })

  it('filterBetween', () => {
    expect(filterBetween('age', 10, 20)).toEqual({ age: { _between: [10, 20] } })
  })

  it('filterContains', () => {
    expect(filterContains('name', 'li')).toEqual({ name: { _contains: 'li' } })
  })

  it('filterNotContains', () => {
    expect(filterNotContains('bio', 'spam')).toEqual({ bio: { _not_contains: 'spam' } })
  })

  it('filterStartsWith', () => {
    expect(filterStartsWith('email', 'a@')).toEqual({ email: { _starts_with: 'a@' } })
  })

  it('filterEndsWith', () => {
    expect(filterEndsWith('email', '.com')).toEqual({ email: { _ends_with: '.com' } })
  })
})

describe('filter-builder: logical combinators', () => {
  it('filterOr wraps nodes in _or', () => {
    const result = filterOr(filterEq('a', 1), filterEq('b', 2))
    expect(result).toEqual({ _or: [{ a: { _eq: 1 } }, { b: { _eq: 2 } }] })
  })

  it('filterAnd wraps nodes in _and', () => {
    const result = filterAnd(filterGt('age', 15), filterLt('age', 30))
    expect(result).toEqual({ _and: [{ age: { _gt: 15 } }, { age: { _lt: 30 } }] })
  })

  it('nested OR inside AND', () => {
    const result = filterAnd(
      filterOr(filterEq('classId', 1), filterEq('classId', 2)),
      filterGt('age', 10),
    )
    expect(result).toEqual({
      _and: [
        { _or: [{ classId: { _eq: 1 } }, { classId: { _eq: 2 } }] },
        { age: { _gt: 10 } },
      ],
    })
  })
})

describe('serializeFilters', () => {
  it('returns undefined for empty array', () => {
    expect(serializeFilters([])).toBeUndefined()
  })

  it('returns single filter as-is', () => {
    const filters = [filterEq('name', 'Alice')]
    expect(serializeFilters(filters)).toEqual({ name: { _eq: 'Alice' } })
  })

  it('wraps multiple filters in _and', () => {
    const filters = [filterEq('age', 18), filterGt('age', 15)]
    expect(serializeFilters(filters)).toEqual({
      _and: [{ age: { _eq: 18 } }, { age: { _gt: 15 } }],
    })
  })

  it('preserves raw filter objects', () => {
    const raw = { name: { _eq: 'Alice' }, age: { _gt: 15 } }
    expect(serializeFilters([raw])).toEqual(raw)
  })

  it('wraps raw + field filter in _and', () => {
    const raw = { status: { _in: ['active', 'pending'] } }
    const field = filterEq('classId', 1)
    expect(serializeFilters([raw, field])).toEqual({
      _and: [raw, field],
    })
  })

  it('complex nested where() output serializes correctly', () => {
    const complex = filterOr(
      filterAnd(filterEq('classId', 1), filterGt('age', 15)),
      filterAnd(filterEq('classId', 2), filterLt('age', 12)),
    )
    expect(serializeFilters([complex])).toEqual({
      _or: [
        { _and: [{ classId: { _eq: 1 } }, { age: { _gt: 15 } }] },
        { _and: [{ classId: { _eq: 2 } }, { age: { _lt: 12 } }] },
      ],
    })
  })
})

describe('serializeSorts', () => {
  it('returns undefined for empty array', () => {
    expect(serializeSorts([])).toBeUndefined()
  })

  it('serializes single sort', () => {
    const result = serializeSorts([{ field: 'name', sort: 'ASC' }])
    expect(result).toBe('[{"field":"name","sort":"ASC"}]')
  })

  it('serializes multiple sorts', () => {
    const result = serializeSorts([
      { field: 'name', sort: 'ASC' },
      { field: 'id', sort: 'DESC' },
    ])
    expect(JSON.parse(result!)).toEqual([
      { field: 'name', sort: 'ASC' },
      { field: 'id', sort: 'DESC' },
    ])
  })
})
