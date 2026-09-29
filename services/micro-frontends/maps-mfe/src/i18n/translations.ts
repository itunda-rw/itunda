// Real localization for maps-mfe (Maps product-completeness pass, 2026-09-07) --
// ports the exact same architecture bank-mfe/kyc-mfe's own i18n already proved out
// (see those files' own doc comments for the full "why": itunda's actual launch market
// is Rwanda, where French and Kinyarwanda both matter as much as, or more than,
// English). maps-mfe was extracted from bank-mfe (2026-08-19) into its own Module
// Federation remote and never carried the parent's i18n wiring over -- the one module
// with zero localization at all, unlike every sibling MFE.
//
// Deliberately scoped to the primary, always-visible surface (search bar, save/
// folder-share dialog, live-location-sharing panel, the default explore-state message,
// and the real errors these specific flows can hit) -- not every secondary/edge-case
// string in this 2000+-line screen. All translations here are a good-faith, careful
// effort -- NOT verified by a native speaker of either language -- and should get real
// native-speaker review before being treated as production-final, same honesty
// standard bank-mfe's/kyc-mfe's own i18n files already hold themselves to.

export type Locale = 'en' | 'rw' | 'fr';

export const LOCALES: { code: Locale; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'rw', label: 'Ikinyarwanda' },
  { code: 'fr', label: 'Français' },
];

export const DEFAULT_LOCALE: Locale = 'en';

export type TranslationKey =
  | 'maps.searchPlaceholder'
  | 'maps.searchError'
  | 'maps.exploreDefault'
  | 'maps.folderNamePlaceholder'
  | 'maps.saving'
  | 'maps.save'
  | 'maps.cancel'
  | 'maps.bookmarkSaveError'
  | 'maps.bookmarkRemoveError'
  | 'maps.shareError'
  | 'maps.locationShareTitle'
  | 'maps.shareMyLocation'
  | 'maps.recipientPhonePlaceholder'
  | 'maps.shareFor'
  | 'maps.starting'
  | 'maps.startSharing';

export const translations: Record<Locale, Record<TranslationKey, string>> = {
  en: {
    'maps.searchPlaceholder': 'Search a real place in Rwanda',
    'maps.searchError': 'Could not search for that place.',
    'maps.exploreDefault': 'Search a real place or pick a category above to explore Rwanda.',
    'maps.folderNamePlaceholder': 'Folder name (e.g. Favorites)',
    'maps.saving': 'Saving…',
    'maps.save': 'Save',
    'maps.cancel': 'Cancel',
    'maps.bookmarkSaveError': 'Could not save this place.',
    'maps.bookmarkRemoveError': 'Could not remove this place.',
    'maps.shareError': 'Could not share this place.',
    'maps.locationShareTitle': '📍 Live location sharing',
    'maps.shareMyLocation': '+ Share my location',
    'maps.recipientPhonePlaceholder': "Recipient's phone number",
    'maps.shareFor': 'For',
    'maps.starting': 'Starting…',
    'maps.startSharing': 'Start sharing',
  },
  rw: {
    'maps.searchPlaceholder': 'Shakisha ahantu nyaho mu Rwanda',
    'maps.searchError': 'Ntibishoboka gushakisha ahantu bavuze.',
    'maps.exploreDefault': 'Shakisha ahantu cyangwa uhitemo icyiciro hejuru kugira ngo urebe u Rwanda.',
    'maps.folderNamePlaceholder': "Izina ry'idosiye (urugero: Ibyo nkunda)",
    'maps.saving': 'Kubika…',
    'maps.save': 'Bika',
    'maps.cancel': 'Hagarika',
    'maps.bookmarkSaveError': 'Ntibishoboka kubika aha hantu.',
    'maps.bookmarkRemoveError': 'Ntibishoboka gukuraho aha hantu.',
    'maps.shareError': 'Ntibishoboka gusangira aha hantu.',
    'maps.locationShareTitle': '📍 Gusangira aho uri ako kanya',
    'maps.shareMyLocation': '+ Sangira aho ndi',
    'maps.recipientPhonePlaceholder': "Numero ya telefoni y'uwakira",
    'maps.shareFor': 'Igihe cy\'',
    'maps.starting': 'Gutangira…',
    'maps.startSharing': 'Tangira gusangira',
  },
  fr: {
    'maps.searchPlaceholder': 'Recherchez un lieu réel au Rwanda',
    'maps.searchError': "Impossible de rechercher ce lieu.",
    'maps.exploreDefault': "Recherchez un lieu réel ou choisissez une catégorie ci-dessus pour explorer le Rwanda.",
    'maps.folderNamePlaceholder': 'Nom du dossier (ex. Favoris)',
    'maps.saving': 'Enregistrement…',
    'maps.save': 'Enregistrer',
    'maps.cancel': 'Annuler',
    'maps.bookmarkSaveError': "Impossible d'enregistrer ce lieu.",
    'maps.bookmarkRemoveError': 'Impossible de supprimer ce lieu.',
    'maps.shareError': 'Impossible de partager ce lieu.',
    'maps.locationShareTitle': '📍 Partage de position en direct',
    'maps.shareMyLocation': '+ Partager ma position',
    'maps.recipientPhonePlaceholder': 'Numéro de téléphone du destinataire',
    'maps.shareFor': 'Pendant',
    'maps.starting': 'Démarrage…',
    'maps.startSharing': 'Démarrer le partage',
  },
};
