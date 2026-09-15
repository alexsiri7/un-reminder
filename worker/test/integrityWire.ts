import fixture from './fixtures/integrity-wire.txt?raw'

const entries = new Map(
  fixture
    .split('\n')
    .filter((line) => line !== '' && !line.startsWith('#'))
    .map((line) => line.split('\t') as [string, string]),
)

function entry(key: string): string {
  const value = entries.get(key)
  if (value === undefined) throw new Error(`integrity-wire.txt has no "${key}" entry`)
  return value
}

/** The app's RequestyProxyClientTest reads the same file. */
export const integrityWire = {
  header: entry('header'),
  rejectedError: entry('rejected-error'),
}
