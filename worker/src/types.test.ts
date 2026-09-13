import { describe, it, expect } from 'vitest'
import { ACTIVITY_MODES, VARIANT_SHAPES } from './types'

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

describe('ACTIVITY_MODES', () => {
  // The app re-declares this list as the ActivityMode enum
  // (app/src/main/java/net/interstellarai/unreminder/domain/model/ActivityMode.kt), pinned by
  // app/src/test/java/net/interstellarai/unreminder/domain/model/ActivityModeTest.kt. A mode the
  // client does not know drops out of a variant's tags rather than failing the batch, but the
  // habit's supported modes arrive under these names and generateBatch discards a name it does
  // not know — silently widening the habit to all modes — so both pins must change in the same PR.
  it('is pinned to the three modes the app enum declares', () => {
    expect([...ACTIVITY_MODES]).toEqual(['WALKING', 'SITTING', 'TRANSPORT'])
  })
})
