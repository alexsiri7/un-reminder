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

  it('returns null if actionUrl does not start with https://', () => {
    expect(validateVariants([{ text: 'Do it', actionUrl: 'http://youtube.com/results' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', actionUrl: 'youtube.com/results' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', actionUrl: 'intent://evil' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', actionUrl: 'file:///etc/passwd' }])).toBeNull()
  })

  it('returns null for empty array', () => {
    expect(validateVariants([])).toBeNull()
  })

  it('drops spriteTag when no vocabulary was supplied', () => {
    const result = validateVariants([{ text: 'Do 10 reps', spriteTag: 'astronaut_zero_g' }])
    expect(result).toEqual([{ text: 'Do 10 reps', actionUrl: undefined, spriteTag: undefined }])
  })

  it('keeps a spriteTag drawn from the supplied vocabulary', () => {
    const allowed = new Set(['astronaut_zero_g', 'chef_pan_flip'])
    const result = validateVariants([{ text: 'Do 10 reps', spriteTag: 'chef_pan_flip' }], allowed)
    expect(result![0].spriteTag).toBe('chef_pan_flip')
  })

  it('drops an out-of-vocabulary spriteTag without failing the batch', () => {
    const allowed = new Set(['astronaut_zero_g'])
    const result = validateVariants(
      [
        { text: 'Do 10 reps', spriteTag: 'invented_by_the_model' },
        { text: 'Hold for 30 seconds', spriteTag: 'astronaut_zero_g' },
      ],
      allowed,
    )
    expect(result).not.toBeNull()
    expect(result!).toHaveLength(2)
    expect(result![0].spriteTag).toBeUndefined()
    expect(result![1].spriteTag).toBe('astronaut_zero_g')
  })

  it('drops a non-string spriteTag without failing the batch', () => {
    const allowed = new Set(['astronaut_zero_g'])
    expect(validateVariants([{ text: 'Do it', spriteTag: 42 }], allowed)![0].spriteTag).toBeUndefined()
    expect(validateVariants([{ text: 'Do it', spriteTag: null }], allowed)![0].spriteTag).toBeUndefined()
  })

  it('accepts a variant with no spriteTag when a vocabulary was supplied', () => {
    const allowed = new Set(['astronaut_zero_g'])
    expect(validateVariants([{ text: 'Do it' }], allowed)![0].spriteTag).toBeUndefined()
  })
})
