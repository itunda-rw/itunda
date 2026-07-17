import { useEffect, useState } from 'react';
import { OverlayProvider } from 'overlay-kit';
import { Suspense } from 'react';
import { getToken, SESSION_EXPIRED_EVENT } from './lib/api';
import LoginPage from './LoginPage';
import BankDashboard from './BankDashboard';

function App() {
  const [authed, setAuthed] = useState(() => getToken() !== null);

  useEffect(() => {
    const onExpired = () => setAuthed(false);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  return (
    <OverlayProvider>
      <Suspense fallback={<div>Loading Bank Data...</div>}>
        {authed ? <BankDashboard onLogout={() => setAuthed(false)} /> : <LoginPage onLogin={() => setAuthed(true)} />}
      </Suspense>
    </OverlayProvider>
  );
}

export default App;
