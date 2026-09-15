// Mints and revokes per-user Worker tokens in the remote UR_TOKENS namespace through wrangler.
//
//   npm run tokens -- mint --label <name>   prints a new token once; only its salted hash is stored
//     --integrity-exempt                     the token skips the Play Integrity gate (debug builds)
//   npm run tokens -- disable <id>          revokes the token whose prefix is ur1_<id>
//   npm run tokens -- enable <id>
//
// Needs the same Cloudflare credentials as `wrangler deploy` (login or CLOUDFLARE_API_TOKEN).
import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createTokenRecord, mintToken, tokenKey } from './tokenRecord.mjs'

const workerDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const wranglerBin = resolve(workerDir, 'node_modules/.bin/wrangler')

function kv(args, { capture = false } = {}) {
  return execFileSync(wranglerBin, ['kv', 'key', ...args, '--binding', 'UR_TOKENS', '--remote'], {
    cwd: workerDir,
    encoding: 'utf8',
    stdio: capture ? ['inherit', 'pipe', 'inherit'] : 'inherit',
  })
}

const putRecord = (id, record) => kv(['put', tokenKey(id), JSON.stringify(record)])
const getRecord = (id) => JSON.parse(kv(['get', tokenKey(id), '--text'], { capture: true }))

function usage() {
  console.error('usage: npm run tokens -- mint --label <name> [--integrity-exempt] | disable <id> | enable <id>')
  process.exit(2)
}

const [command, ...rest] = process.argv.slice(2)
switch (command) {
  case 'mint': {
    const label = rest[rest.indexOf('--label') + 1]
    if (!rest.includes('--label') || !label) usage()
    const integrityExempt = rest.includes('--integrity-exempt')
    const { id, token } = mintToken()
    putRecord(id, await createTokenRecord(token, label, new Date(), { integrityExempt }))
    const kind = integrityExempt ? 'Integrity-exempt token' : 'Token'
    console.log(`\n${kind} for "${label}" (id ${id}). Shown once — it is not stored anywhere:\n\n  ${token}\n`)
    break
  }
  case 'disable':
  case 'enable': {
    const [id] = rest
    if (!id) usage()
    const record = getRecord(id)
    putRecord(id, { ...record, enabled: command === 'enable' })
    console.log(`Token ${id} ("${record.label}") ${command}d.`)
    break
  }
  default:
    usage()
}
