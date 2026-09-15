// A throwaway RSA key pair for tests that exercise the service-account JWT exchange; nothing
// here is a real credential.
const RSA_PARAMS = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }

function toPem(label: string, der: ArrayBuffer): string {
  const base64 = btoa(String.fromCharCode(...new Uint8Array(der)))
  const lines = base64.match(/.{1,64}/g) ?? []
  return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----\n`
}

export interface TestServiceAccount {
  /** The JSON a `UR_PLAY_INTEGRITY_SA_KEY` secret would hold. */
  keyJson: string
  clientEmail: string
  publicKey: CryptoKey
}

export async function generateTestServiceAccount(): Promise<TestServiceAccount> {
  const pair = (await crypto.subtle.generateKey(
    { ...RSA_PARAMS, modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]) },
    true,
    ['sign', 'verify'],
  )) as CryptoKeyPair
  const pkcs8 = (await crypto.subtle.exportKey('pkcs8', pair.privateKey)) as ArrayBuffer
  const clientEmail = 'un-reminder-worker@test-project.iam.gserviceaccount.com'
  const keyJson = JSON.stringify({
    type: 'service_account',
    project_id: 'test-project',
    client_email: clientEmail,
    private_key: toPem('PRIVATE KEY', pkcs8),
  })
  return { keyJson, clientEmail, publicKey: pair.publicKey }
}

function fromBase64url(text: string): Uint8Array {
  const binary = atob(text.replace(/-/g, '+').replace(/_/g, '/'))
  return Uint8Array.from(binary, (c) => c.charCodeAt(0))
}

/** Splits a JWT, verifies its RS256 signature with [publicKey] and returns its claims. */
export async function verifyJwt(
  jwt: string,
  publicKey: CryptoKey,
): Promise<{ header: Record<string, unknown>; claims: Record<string, unknown>; valid: boolean }> {
  const [header, claims, signature] = jwt.split('.')
  const valid = await crypto.subtle.verify(
    RSA_PARAMS,
    publicKey,
    fromBase64url(signature),
    new TextEncoder().encode(`${header}.${claims}`),
  )
  const decode = (part: string) =>
    JSON.parse(new TextDecoder().decode(fromBase64url(part))) as Record<string, unknown>
  return { header: decode(header), claims: decode(claims), valid }
}
