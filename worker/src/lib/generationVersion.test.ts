import { describe, it, expect } from 'vitest'
import { readGenerationVersion } from './generationVersion'
import type { Env } from '../types'

const envWith = (value: string | undefined) => ({ UR_GENERATION_VERSION: value }) as unknown as Env

describe('readGenerationVersion', () => {
  it('reads a positive integer', () => {
    expect(readGenerationVersion(envWith('1'))).toBe(1)
    expect(readGenerationVersion(envWith('42'))).toBe(42)
  })

  it('rejects zero, which the app reserves for unversioned rows', () => {
    expect(readGenerationVersion(envWith('0'))).toBeNull()
  })

  it('rejects non-integers and a missing var', () => {
    expect(readGenerationVersion(envWith('abc'))).toBeNull()
    expect(readGenerationVersion(envWith(''))).toBeNull()
    expect(readGenerationVersion(envWith('-3'))).toBeNull()
    expect(readGenerationVersion(envWith(undefined))).toBeNull()
  })
})
