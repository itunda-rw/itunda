import MapView from './MapView';
import { I18nProvider } from './i18n/I18nContext';

function App() {
  return (
    <I18nProvider>
      <div className="maps-app-shell" style={{ height: '100vh' }}>
        <MapView />
      </div>
    </I18nProvider>
  );
}

export default App;
