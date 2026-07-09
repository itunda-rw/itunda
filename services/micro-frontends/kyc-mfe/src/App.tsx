import React from 'react';
import { OverlayProvider } from 'overlay-kit';
import KycDashboard from './KycDashboard';

function App() {
  return (
    <OverlayProvider>
      <div style={{ maxWidth: '400px', margin: '0 auto', paddingTop: '40px' }}>
        <KycDashboard />
      </div>
    </OverlayProvider>
  );
}

export default App;
