/**
 * Constant-time string equality check to prevent timing side-channels.
 * Uses the platform-native crypto.subtle.timingSafeEqual (implemented in C,
 * genuinely constant-time) rather than a JS loop which V8 may JIT non-uniformly.
 * On length mismatch, performs a dummy same-buffer comparison to keep execution
 * time constant before returning false.
 */
export function timingSafeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder()
  const ab = enc.encode(a)
  const bb = enc.encode(b)
  if (ab.byteLength !== bb.byteLength) {
    // Perform a dummy comparison so execution time doesn't reveal length info.
    crypto.subtle.timingSafeEqual(bb, bb)
    return false
  }
  return crypto.subtle.timingSafeEqual(ab, bb)
}
