import { createSaroniteLocalRelay } from './local-relay.js';

export interface SaroniteDebuggerCli {
  run(args?: string[]): Promise<number>;
}

function usage(): string {
  return [
    'Saronite Debugger (development only)',
    '',
    'Commands:',
    '  relay [port]   Start a localhost relay',
    '  help            Show this help',
  ].join('\n');
}

export function createSaroniteDebuggerCli(): SaroniteDebuggerCli {
  return {
    async run(args = []) {
      const command = args[0] ?? 'help';
      if (command === 'help' || command === '--help' || command === '-h') {
        process.stdout.write(usage() + '\n');
        return 0;
      }
      if (command !== 'relay') {
        process.stderr.write('Unknown command: ' + command + '\n' + usage() + '\n');
        return 1;
      }
      const parsed = args[1] === undefined ? 0 : Number(args[1]);
      if (!Number.isInteger(parsed) || parsed < 0 || parsed > 65535) {
        process.stderr.write('Port must be an integer from 0 to 65535.\n');
        return 1;
      }
      const relay = createSaroniteLocalRelay('127.0.0.1', parsed);
      await relay.start();
      process.stdout.write(JSON.stringify({ host: relay.host, port: relay.port, token: relay.token }) + '\n');
      await new Promise<void>((resolve) => {
        const shutdown = () => {
          process.off('SIGINT', shutdown);
          process.off('SIGTERM', shutdown);
          void relay.stop().finally(resolve);
        };
        process.once('SIGINT', shutdown);
        process.once('SIGTERM', shutdown);
      });
      return 0;
    },
  };
}

export async function runSaroniteDebuggerCli(args = process.argv.slice(2)): Promise<number> {
  return createSaroniteDebuggerCli().run(args);
}
