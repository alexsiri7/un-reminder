import { describe, it, expect } from 'vitest'
import { buildPrompt, validateVariants } from './generateBatch'
import { ACTIVITY_MODES, VARIANT_SHAPES } from '../types'

describe('validateVariants', () => {
  it('accepts array of text-only objects', () => {
    const input = [{ text: 'Do 10 reps', shape: 'STATEMENT' }, { text: 'Hold for 30 seconds', shape: 'TIMEBOXED' }]
    const result = validateVariants(input)
    expect(result).toEqual([
      { text: 'Do 10 reps', shape: 'STATEMENT', modes: [], actionUrl: undefined },
      { text: 'Hold for 30 seconds', shape: 'TIMEBOXED', modes: [], actionUrl: undefined },
    ])
  })

  it('accepts array with actionUrl on some items', () => {
    const input = [
      { text: 'Sing C major scale', shape: 'STATEMENT', actionUrl: 'https://www.youtube.com/results?search_query=C+major+scale' },
      { text: 'Drink a glass of water', shape: 'STATEMENT' },
    ]
    const result = validateVariants(input)
    expect(result).not.toBeNull()
    expect(result![0].actionUrl).toBe('https://www.youtube.com/results?search_query=C+major+scale')
    expect(result![1].actionUrl).toBeUndefined()
  })

  it('accepts array where all items have actionUrl', () => {
    const input = [
      { text: 'Do 10 burpees', shape: 'CHALLENGE', actionUrl: 'https://www.youtube.com/results?search_query=burpee+form' },
    ]
    expect(validateVariants(input)).not.toBeNull()
  })

  it('returns null for non-array input', () => {
    expect(validateVariants('string')).toBeNull()
    expect(validateVariants(null)).toBeNull()
    expect(validateVariants({ text: 'hi', shape: 'TERSE' })).toBeNull()
  })

  it('returns null if any item is not an object', () => {
    expect(validateVariants(['string instead of object'])).toBeNull()
    expect(validateVariants([42])).toBeNull()
  })

  it('returns null if any item has missing or empty text', () => {
    expect(validateVariants([{ text: '', shape: 'TERSE' }])).toBeNull()
    expect(validateVariants([{ text: '   ', shape: 'TERSE' }])).toBeNull()
    expect(validateVariants([{ shape: 'TERSE', actionUrl: 'https://youtube.com' }])).toBeNull()
  })

  it('returns null if actionUrl is present but not a string', () => {
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', actionUrl: 123 }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', actionUrl: null }])).toBeNull()
  })

  it('returns null if actionUrl does not start with https://', () => {
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', actionUrl: 'http://youtube.com/results' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', actionUrl: 'youtube.com/results' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', actionUrl: 'intent://evil' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', actionUrl: 'file:///etc/passwd' }])).toBeNull()
  })

  it('returns null for empty array', () => {
    expect(validateVariants([])).toBeNull()
  })

  it('drops spriteTag when no vocabulary was supplied', () => {
    const result = validateVariants([{ text: 'Do 10 reps', shape: 'STATEMENT', spriteTag: 'astronaut_zero_g' }])
    expect(result).toEqual([{ text: 'Do 10 reps', shape: 'STATEMENT', modes: [], actionUrl: undefined, spriteTag: undefined }])
  })

  it('keeps a spriteTag drawn from the supplied vocabulary', () => {
    const allowed = new Set(['astronaut_zero_g', 'chef_pan_flip'])
    const result = validateVariants([{ text: 'Do 10 reps', shape: 'STATEMENT', spriteTag: 'chef_pan_flip' }], allowed)
    expect(result![0].spriteTag).toBe('chef_pan_flip')
  })

  it('drops an out-of-vocabulary spriteTag without failing the batch', () => {
    const allowed = new Set(['astronaut_zero_g'])
    const result = validateVariants(
      [
        { text: 'Do 10 reps', shape: 'STATEMENT', spriteTag: 'invented_by_the_model' },
        { text: 'Hold for 30 seconds', shape: 'TIMEBOXED', spriteTag: 'astronaut_zero_g' },
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
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', spriteTag: 42 }], allowed)![0].spriteTag).toBeUndefined()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', spriteTag: null }], allowed)![0].spriteTag).toBeUndefined()
  })

  it('accepts a variant with no spriteTag when a vocabulary was supplied', () => {
    const allowed = new Set(['astronaut_zero_g'])
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE' }], allowed)![0].spriteTag).toBeUndefined()
  })

  it('keeps every declared shape', () => {
    const input = VARIANT_SHAPES.map((shape) => ({ text: `A ${shape} message`, shape }))
    const result = validateVariants(input)
    expect(result!.map((v) => v.shape)).toEqual([...VARIANT_SHAPES])
  })

  it('returns null when an item has no shape', () => {
    expect(validateVariants([{ text: 'Do 10 reps' }])).toBeNull()
    expect(validateVariants([{ text: 'Do 10 reps', shape: 'STATEMENT' }, { text: 'Do 20 reps' }])).toBeNull()
  })

  it('returns null when a shape is outside the six shapes', () => {
    expect(validateVariants([{ text: 'Do it', shape: 'statement' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'PLEA' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 42 }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: null }])).toBeNull()
  })

  it('keeps every declared mode and reads a missing or empty modes field as neutral', () => {
    const result = validateVariants([
      { text: 'Count 20 steps', shape: 'TERSE', modes: ['WALKING'] },
      { text: 'Breathe for 60 seconds', shape: 'TIMEBOXED', modes: [] },
      { text: 'Sit up straight', shape: 'STATEMENT' },
      { text: 'Both', shape: 'STATEMENT', modes: ['SITTING', 'TRANSPORT'] },
    ])
    expect(result!.map((v) => v.modes)).toEqual([['WALKING'], [], [], ['SITTING', 'TRANSPORT']])
  })

  it('returns null when modes is not an array of the three modes', () => {
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', modes: 'WALKING' }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', modes: ['walking'] }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', modes: ['CYCLING'] }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', modes: [42] }])).toBeNull()
    expect(validateVariants([{ text: 'Do it', shape: 'TERSE', modes: null }])).toBeNull()
  })

  it('drops a variant written for a mode the habit does not support without failing the batch', () => {
    const result = validateVariants(
      [
        { text: 'Count 20 steps', shape: 'TERSE', modes: ['WALKING'] },
        { text: 'Sit up straight', shape: 'STATEMENT', modes: ['SITTING'] },
        { text: 'Breathe for 60 seconds', shape: 'TIMEBOXED', modes: [] },
      ],
      new Set(),
      ['SITTING'],
    )
    expect(result!.map((v) => v.text)).toEqual(['Sit up straight', 'Breathe for 60 seconds'])
  })

  it('drops a variant tagged for a supported and an unsupported mode together', () => {
    const result = validateVariants(
      [
        { text: 'Sit up straight', shape: 'STATEMENT', modes: ['SITTING'] },
        { text: 'Roll your shoulders as you walk', shape: 'STATEMENT', modes: ['WALKING', 'SITTING'] },
      ],
      new Set(),
      ['SITTING'],
    )
    expect(result!.map((v) => v.text)).toEqual(['Sit up straight'])
  })

  it('counts each variant dropped for an unsupported mode', () => {
    const stats = { droppedForMode: 0 }
    const result = validateVariants(
      [
        { text: 'Count 20 steps', shape: 'TERSE', modes: ['WALKING'] },
        { text: 'Sit up straight', shape: 'STATEMENT', modes: ['SITTING'] },
        { text: 'Roll your shoulders as you walk', shape: 'STATEMENT', modes: ['WALKING', 'SITTING'] },
        { text: 'Breathe for 60 seconds', shape: 'TIMEBOXED', modes: [] },
      ],
      new Set(),
      ['SITTING'],
      stats,
    )
    expect(result!.length).toBe(2)
    expect(stats.droppedForMode).toBe(2)
  })

  it('returns null when every variant was written for an unsupported mode', () => {
    expect(validateVariants([{ text: 'Count 20 steps', shape: 'TERSE', modes: ['WALKING'] }], new Set(), ['SITTING'])).toBeNull()
  })
})

describe('buildPrompt', () => {
  const prompt = buildPrompt('Stretch', [], '', '', '', [], [...ACTIVITY_MODES], 50)
  const strictPrompt = buildPrompt('Stretch', [], '', '', '', [], [...ACTIVITY_MODES], 50, true)

  it('names all six shapes and asks for a spread across them', () => {
    for (const shape of VARIANT_SHAPES) expect(prompt).toContain(shape)
    expect(prompt).toMatch(/Spread the 50 messages as evenly as possible across all six shapes/)
  })

  it('requires a shape field in the output schema in both prompt variants', () => {
    for (const p of [prompt, strictPrompt]) {
      expect(p).toContain(`"shape": string, exactly one of ${VARIANT_SHAPES.join(', ')}`)
    }
  })

  it('describes only the supported modes and asks for a neutral majority spread across them', () => {
    const sittingOnly = buildPrompt('Meditate', [], '', '', '', [], ['SITTING'], 50)
    expect(sittingOnly).toContain('- SITTING:')
    expect(sittingOnly).not.toContain('- WALKING:')
    expect(sittingOnly).not.toContain('- TRANSPORT:')
    expect(sittingOnly).toContain('"modes": array of strings, each one of SITTING')
    expect(sittingOnly).toMatch(/8\. Write at least half of the messages so they read naturally whatever the user is doing, with "modes": \[\]/)
    expect(sittingOnly).toContain('spread across it')

    for (const p of [prompt, strictPrompt]) {
      for (const mode of ACTIVITY_MODES) expect(p).toContain(`- ${mode}:`)
      expect(p).toContain(`"modes": array of strings, each one of ${ACTIVITY_MODES.join(', ')}`)
      expect(p).toContain('spread across all of them')
    }
  })
})
