import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveRemoteEntry } from '../vite.remote-config.mjs'

test('local development uses the KYC and bank Vite servers', () => {
  assert.equal(resolveRemoteEntry({ name: 'kyc', command: 'serve' }), 'http://localhost:5001/assets/remoteEntry.js')
  assert.equal(resolveRemoteEntry({ name: 'bank', command: 'serve' }), 'http://localhost:5002/assets/remoteEntry.js')
})

test('LAN development uses the configured host address', () => {
  assert.equal(resolveRemoteEntry({ name: 'kyc', command: 'serve', remoteHost: '192.168.1.20' }), 'http://192.168.1.20:5001/assets/remoteEntry.js')
})

test('production uses deployed remote paths', () => {
  assert.equal(resolveRemoteEntry({ name: 'kyc', command: 'build' }), '/remotes/kyc/assets/remoteEntry.js')
  assert.equal(resolveRemoteEntry({ name: 'bank', command: 'build' }), '/remotes/bank/assets/remoteEntry.js')
})

test('an explicit remote base overrides defaults', () => {
  assert.equal(resolveRemoteEntry({ name: 'bank', command: 'serve', remoteBase: 'https://preview.itunda.im/remotes/' }), 'https://preview.itunda.im/remotes/bank/assets/remoteEntry.js')
})

test('unknown remote names fail closed', () => {
  assert.throws(() => resolveRemoteEntry({ name: 'insurance', command: 'serve' }), /Unknown remote/)
})
