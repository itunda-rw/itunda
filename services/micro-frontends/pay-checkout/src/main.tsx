import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import CheckoutPage from './CheckoutPage.tsx'
import { I18nProvider } from './i18n/I18nContext'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider>
      <CheckoutPage />
    </I18nProvider>
  </StrictMode>,
)
