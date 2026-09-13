import type { Env } from '../types'

/**
 * The deployed generation version, or null when the var is missing or malformed.
 * A live version is always >= 1: the app stores 0 on rows generated before the Worker
 * reported a version, so those read as stale against any real one.
 */
export function readGenerationVersion(env: Env): number | null {
  const version = parseInt(env.UR_GENERATION_VERSION, 10)
  return Number.isInteger(version) && version >= 1 ? version : null
}
