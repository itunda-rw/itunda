/**
 * Hand-written equivalent of granite's real auto-generated `router.gen.ts` -- see
 * pay-bills/router.gen.ts's own header comment for the full account.
 *
 * DO NOT let this drift from require.context.ts's actual keys() if more routes are
 * ever added to this mini-app.
 */
import type { Route as IndexRoute } from './pages/index';
import type { Route as NotFoundRoute } from './pages/_404';

declare module '@granite-js/react-native' {
  interface RegisterScreenInput {
    '/': (typeof IndexRoute)['_inputType'];
    '/_404': (typeof NotFoundRoute)['_inputType'];
  }

  interface RegisterScreen {
    '/': (typeof IndexRoute)['_outputType'];
    '/_404': (typeof NotFoundRoute)['_outputType'];
  }
}
