// Mints, revokes and re-caps per-user Worker tokens in the remote UR_TOKENS namespace through wrangler.
//
//   npm run tokens -- mint --label <name>   prints a new token once; only its salted hash is stored
//     --integrity-exempt                     the token skips the Play Integrity gate (debug builds)
//     --daily-cap-cents <n>                  spend caps for this token instead of UR_USER_*_CAP_CENTS
//     --monthly-cap-cents <n>
//   npm run tokens -- disable <id>          revokes the token whose prefix is ur1_<id>
//   npm run tokens -- enable <id>
//   npm run tokens -- caps <id> [--daily-cap-cents <n>] [--monthly-cap-cents <n>]
//                                           replaces the token's cap overrides with exactly these;
//                                           an omitted cap goes back to the Worker default
//
// Needs the same Cloudflare credentials as `wrangler deploy` (login or CLOUDFLARE_API_TOKEN).
import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { applyCaps, createTokenRecord, mintToken, tokenKey } from './tokenRecord.mjs'

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
  console.error(
    'usage: npm run tokens -- mint --label <name> [--integrity-exempt] [caps] | disable <id> | enable <id> | caps <id> [caps]\n' +
      '  caps: [--daily-cap-cents <n>] [--monthly-cap-cents <n>]',
  )
  process.exit(2)
}

const CAP_FLAGS = { '--daily-cap-cents': 'dailyCapCents', '--monthly-cap-cents': 'monthlyCapCents' }

/**
 * Cap overrides named in [args], each a positive integer of cents. [otherFlags] maps the command's
 * remaining flags to how many values each takes; anything else is a typo, not a request to clear.
 */
function capsFromArgs(args, otherFlags = {}) {
  const caps = {}
  for (let i = 0; i < args.length; i++) {
    const field = CAP_FLAGS[args[i]]
    if (field === undefined) {
      const valueCount = otherFlags[args[i]]
      if (valueCount === undefined) usage()
      i += valueCount
      continue
    }
    const cents = Number(args[i + 1])
    if (!Number.isInteger(cents) || cents <= 0) usage()
    caps[field] = cents
    i++
  }
  return caps
}

const describeCaps = ({ dailyCapCents, monthlyCapCents }) =>
  `daily ${dailyCapCents ?? 'default'}, monthly ${monthlyCapCents ?? 'default'} (cents; default = UR_USER_*_CAP_CENTS)`

const [command, ...rest] = process.argv.slice(2)
switch (command) {
  case 'mint': {
    const label = rest[rest.indexOf('--label') + 1]
    if (!rest.includes('--label') || !label) usage()
    const integrityExempt = rest.includes('--integrity-exempt')
    const caps = capsFromArgs(rest, { '--label': 1, '--integrity-exempt': 0 })
    const { id, token } = mintToken()
    putRecord(id, await createTokenRecord(token, label, new Date(), { integrityExempt, ...caps }))
    const kind = integrityExempt ? 'Integrity-exempt token' : 'Token'
    console.log(`\n${kind} for "${label}" (id ${id}), caps ${describeCaps(caps)}.`)
    console.log(`Shown once — it is not stored anywhere:\n\n  ${token}\n`)
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
  case 'caps': {
    const [id, ...flags] = rest
    if (!id || id.startsWith('--')) usage()
    const caps = capsFromArgs(flags)
    const record = getRecord(id)
    putRecord(id, applyCaps(record, caps))
    console.log(`Token ${id} ("${record.label}") caps ${describeCaps(caps)}.`)
    break
  }
  default:
    usage()
}
