import { describe, it, expect } from 'vitest'
import { validateVariants } from './generateBatch'

describe('validateVariants', () => {
  it('accepts array of text-only objects', () => {
    const input = [{ text: 'Do 10 reps' }, { text: 'Hold for 30 seconds' }]
    const result = validateVariants(input)
    expect(result).toEqual([
      { text: 'Do 10 reps', actionUrl: undefined },
      { text: 'Hold for 30 seconds', actionUrl: undefined },
    ])
  })

  it('accepts array with actionUrl on some items', () => {
    const input = [
      { text: 'Sing C major scale', actionUrl: 'https://www.youtube.com/results?search_query=C+major+scale' },
      { text: 'Drink a glass of water' },
    ]
    const result = validateVariants(input)
    expect(result).not.toBeNull()
    expect(result![0].actionUrl).toBe('https://www.youtube.com/results?search_query=C+major+scale')
    expect(result![1].actionUrl).toBeUndefined()
  })

  it('accepts array where all items have actionUrl', () => {
    const input = [
      { text: 'Do 10 burpees', actionUrl: 'https://www.youtube.com/results?search_query=burpee+form' },
    ]
    expect(validateVariants(input)).not.toBeNull()
  })

  it('returns null for non-array input', () => {
    expect(validateVariants('string')).toBeNull()
    expect(validateVariants(null)).toBeNull()
    expect(validateVariants({ text: 'hi' })).toBeNull()
  })

  it('returns null if any item is not an object', () => {
    expect(validateVariants(['string instead of object'])).toBeNull()
    expect(validateVariants([42])).toBeNull()
  })

  it('returns null if any item has missing or empty text', () => {
    expect(validateVariants([{ text: '' }])).toBeNull()
    expect(validateVariants([{ text: '   ' }])).toBeNull()
    expect(validateVariants([{ actionUrl: 'https://youtube.com' }])).toBeNull()
  })

  it('returns null if actionUrl is present but not a string', () => {
    expect(validateVariants([{ text: 'Do it', actionUrl: 123 }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', actionUrl: null }])).toBeNull()
  })

  it('returns null for empty array', () => {
    expect(validateVariants([])).toBeNull()
  })
})
