import { useEffect, useState } from 'react';
import { OverlayProvider } from 'overlay-kit';
import { Suspense } from 'react';
import { getToken, SESSION_EXPIRED_EVENT } from './lib/api';
import LoginPage from './LoginPage';
import RegisterPage from './RegisterPage';
import BankDashboard from './BankDashboard';

function App() {
  const [authed, setAuthed] = useState(() => getToken() !== null);
  // Real sign-up toggle (2026-08-04) -- see RegisterPage.tsx's own doc comment.
  const [showRegister, setShowRegister] = useState(false);

  useEffect(() => {
    const onExpired = () => setAuthed(false);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  return (
    <OverlayProvider>
      <Suspense fallback={<div>Loading your account…</div>}>
        {authed ? (
          <BankDashboard onLogout={() => setAuthed(false)} />
        ) : showRegister ? (
          <RegisterPage onRegistered={() => setAuthed(true)} onBackToLogin={() => setShowRegister(false)} />
        ) : (
          <LoginPage onLogin={() => setAuthed(true)} onCreateAccount={() => setShowRegister(true)} />
        )}
      </Suspense>
    </OverlayProvider>
  );
}

export default App;
