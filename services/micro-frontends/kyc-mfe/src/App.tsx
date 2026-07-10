import { OverlayProvider } from 'overlay-kit';
import KycDashboard from './KycDashboard';

function App() {
  return (
    <OverlayProvider>
      <div className="kyc-app-shell">
        <KycDashboard />
      </div>
    </OverlayProvider>
  );
}

export default App;
