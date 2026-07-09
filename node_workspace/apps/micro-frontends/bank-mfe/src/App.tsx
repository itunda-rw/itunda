import { OverlayProvider } from 'overlay-kit';
import { Suspense } from 'react';
import BankDashboard from './BankDashboard';

function App() {
  return (
    <OverlayProvider>
      <Suspense fallback={<div>Loading Bank Data...</div>}>
        <BankDashboard />
      </Suspense>
    </OverlayProvider>
  );
}

export default App;
