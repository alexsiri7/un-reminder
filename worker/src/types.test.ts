import { describe, it, expect } from 'vitest'
import { VARIANT_SHAPES } from './types'

describe('VARIANT_SHAPES', () => {
  // The app re-declares this list by hand as the VariantShape enum
  // (app/src/main/java/net/interstellarai/unreminder/domain/model/VariantShape.kt), pinned by
  // app/src/test/java/net/interstellarai/unreminder/domain/model/VariantShapeTest.kt. A client
  // that meets a shape it does not know rejects the whole batch, and the app ships over days
  // while the worker deploys instantly, so both pins must change in the same PR.
  it('is pinned to the six shapes the app enum declares', () => {
    expect([...VARIANT_SHAPES]).toEqual(['QUESTION', 'STATEMENT', 'CHALLENGE', 'OBSERVATION', 'TERSE', 'TIMEBOXED'])
  })
})
