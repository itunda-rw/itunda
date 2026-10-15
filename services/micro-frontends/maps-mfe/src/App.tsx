import MapView from './MapView';
import { I18nProvider } from './i18n/I18nContext';

function App() {
  return (
    <I18nProvider>
      <div className="maps-app-shell" style={{ height: '100dvh', minHeight: '100svh', overflow: 'hidden' }}>
        <MapView />
      </div>
    </I18nProvider>
  );
}

export default App;
