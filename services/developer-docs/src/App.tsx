import { Route, Routes } from 'react-router-dom';
import Layout from './Layout';
import PayDocsPage from './PayDocsPage';
import PartnerIdentityDocsPage from './PartnerIdentityDocsPage';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<PayDocsPage />} />
        <Route path="/identity" element={<PartnerIdentityDocsPage />} />
      </Route>
    </Routes>
  );
}
