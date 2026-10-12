const ports = { kyc: 5001, bank: 5002 }

export function resolveRemoteEntry({ name, command, remoteHost = 'localhost', remoteBase }) {
  if (!Object.hasOwn(ports, name)) throw new Error('Unknown remote: ' + name)
  if (remoteBase) return remoteBase.replace(/\/$/, '') + '/' + name + '/assets/remoteEntry.js'
  if (command === 'serve') return 'http://' + remoteHost + ':' + ports[name] + '/assets/remoteEntry.js'
  return '/remotes/' + name + '/assets/remoteEntry.js'
}
