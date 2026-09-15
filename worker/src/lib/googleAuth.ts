const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token'

export function base64url(bytes: ArrayBuffer | Uint8Array | string): string {
  const raw = typeof bytes === 'string' ? bytes : String.fromCharCode(...new Uint8Array(bytes))
  return btoa(raw).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----[A-Z ]+-----/g, '').replace(/\s+/g, '')
  const binary = atob(body)
  const der = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) der[i] = binary.charCodeAt(i)
  return der.buffer
}

function parseServiceAccountKey(saKeyJson: string): { client_email: string; private_key: string } {
  let parsed: unknown
  try {
    parsed = JSON.parse(saKeyJson)
  } catch {
    throw new Error('UR_PLAY_INTEGRITY_SA_KEY is not a service-account key')
  }
  const key = parsed as Record<string, unknown> | null
  if (typeof key?.client_email !== 'string' || typeof key?.private_key !== 'string') {
    throw new Error('UR_PLAY_INTEGRITY_SA_KEY is not a service-account key')
  }
  return { client_email: key.client_email, private_key: key.private_key }
}

/**
 * Exchanges a service-account JSON key for a short-lived access token: an RS256 JWT
 * assertion signed with the key's private key, posted to Google's token endpoint.
 * Minted per request — generation is infrequent enough that caching would only add an
 * expiry path.
 */
export async function serviceAccountAccessToken(
  saKeyJson: string,
  scope: string,
  fetchImpl: typeof fetch = fetch,
): Promise<string> {
  const { client_email, private_key } = parseServiceAccountKey(saKeyJson)
  const privateKey = await crypto.subtle.importKey(
    'pkcs8',
    pemToDer(private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  )

  const iat = Math.floor(Date.now() / 1000)
  const header = base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const claims = base64url(
    JSON.stringify({ iss: client_email, scope, aud: TOKEN_ENDPOINT, iat, exp: iat + 3600 }),
  )
  const signingInput = `${header}.${claims}`
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    privateKey,
    new TextEncoder().encode(signingInput),
  )
  const assertion = `${signingInput}.${base64url(signature)}`

  const res = await fetchImpl(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion,
    }).toString(),
  })
  if (!res.ok) {
    throw new Error(`Google token exchange ${res.status}: ${await res.text()}`)
  }
  const json = (await res.json()) as { access_token?: unknown }
  if (typeof json.access_token !== 'string') {
    throw new Error('Google token exchange returned no access_token')
  }
  return json.access_token
}
