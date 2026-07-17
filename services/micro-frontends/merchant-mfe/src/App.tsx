import { useEffect, useState } from 'react';
import { OverlayProvider } from 'overlay-kit';
import { getToken, SESSION_EXPIRED_EVENT } from './lib/api';
import LoginPage from './LoginPage';
import MerchantDashboard from './MerchantDashboard';

function App() {
  const [authed, setAuthed] = useState(() => getToken() !== null);

  useEffect(() => {
    const onExpired = () => setAuthed(false);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  return (
    <OverlayProvider>
      {authed ? <MerchantDashboard onLogout={() => setAuthed(false)} /> : <LoginPage onLogin={() => setAuthed(true)} />}
    </OverlayProvider>
  );
}

export default App;
