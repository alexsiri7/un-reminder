import fixture from './fixtures/register-wire.txt?raw'

const entries = new Map(
  fixture
    .split('\n')
    .filter((line) => line !== '' && !line.startsWith('#'))
    .map((line) => line.split('\t') as [string, string]),
)

function entry(key: string): string {
  const value = entries.get(key)
  if (value === undefined) throw new Error(`register-wire.txt has no "${key}" entry`)
  return value
}

/** The app's self-registration (#437) reads the same file. */
export const registerWire = {
  path: entry('path'),
  deviceLabelField: entry('device-label-field'),
  capError: entry('cap-error'),
}
