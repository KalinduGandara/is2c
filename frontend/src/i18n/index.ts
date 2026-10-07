import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './en.json';

// English only for v1; additional bundles (si, ta) can be added without code changes (NFR-010).
void i18n.use(initReactI18next).init({
  resources: { en: { translation: en } },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  returnNull: false,
});

export default i18n;
