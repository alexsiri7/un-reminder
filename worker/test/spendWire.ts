import fixture from './fixtures/spend-wire.txt?raw'

const entries = new Map(
  fixture
    .split('\n')
    .filter((line) => line !== '' && !line.startsWith('#'))
    .map((line) => line.split('\t') as [string, string]),
)

function entry(key: string): string {
  const value = entries.get(key)
  if (value === undefined) throw new Error(`spend-wire.txt has no "${key}" entry`)
  return value
}

/** The app's error handling (#422) reads the same file. */
export const spendWire = {
  capTypes: { daily: entry('cap-type-daily'), monthly: entry('cap-type-monthly') },
  capScopes: { user: entry('cap-scope-user'), global: entry('cap-scope-global') },
  errors: {
    user: { daily: entry('cap-error-user-daily'), monthly: entry('cap-error-user-monthly') },
    global: { daily: entry('cap-error-global-daily'), monthly: entry('cap-error-global-monthly') },
  },
}
