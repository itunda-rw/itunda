// Real localization for kyc-mfe (2026-08-15) -- ports the exact same architecture
// bank-mfe/merchant-mfe/pay-checkout's own i18n already proved out this session (see
// those files' own doc comments for the full "why": itunda's actual launch market is
// Rwanda, where French and Kinyarwanda both matter as much as, or more than, English).
// This is itunda's real personal identity-verification flow (NIDA/passport) -- as
// high-stakes and as broadly-reached as bank-mfe's own transfer flow, and until now
// the one customer-facing MFE with zero localization at all.
//
// Small, single-screen scope matches the file itself (184 lines, one real component
// plus its submit modal) -- full coverage in one pass. All translations here are a
// good-faith, careful effort -- NOT verified by a native speaker of either language --
// and should get real native-speaker review before being treated as production-final.

export type Locale = 'en' | 'rw' | 'fr';

export const LOCALES: { code: Locale; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'rw', label: 'Ikinyarwanda' },
  { code: 'fr', label: 'Français' },
];

export const DEFAULT_LOCALE: Locale = 'en';

export type TranslationKey =
  | 'kyc.title'
  | 'kyc.signInFirst'
  | 'kyc.subtitle'
  | 'kyc.loadError'
  | 'kyc.pendingReview'
  | 'kyc.verifyIdentity'
  | 'kyc.submitError'
  | 'kyc.submittedTitle'
  | 'kyc.submittedBodyPrefix'
  | 'kyc.submittedBodySuffix'
  | 'kyc.done'
  | 'kyc.chooseDocType'
  | 'kyc.nationalId'
  | 'kyc.passport'
  | 'kyc.nationalIdNumberLabel'
  | 'kyc.passportNumberLabel'
  | 'kyc.documentReferenceLabel'
  | 'kyc.submitting'
  | 'kyc.submitForReview';

export const translations: Record<Locale, Record<TranslationKey, string>> = {
  en: {
    'kyc.title': 'KYC Verification',
    'kyc.signInFirst': 'Sign in from the Home tab first, then come back here to verify your identity.',
    'kyc.subtitle': 'Rwanda National ID (NIDA) or passport verification.',
    'kyc.loadError': 'Could not load your identity status.',
    'kyc.pendingReview': 'Submission pending review',
    'kyc.verifyIdentity': 'Verify your identity',
    'kyc.submitError': 'That submission could not be completed.',
    'kyc.submittedTitle': 'Submitted for review',
    'kyc.submittedBodyPrefix': 'A real reviewer will check your',
    'kyc.submittedBodySuffix': "and update your status -- this isn't instant, real KYC review never is.",
    'kyc.done': 'Done',
    'kyc.chooseDocType': 'Choose a document type and enter its number.',
    'kyc.nationalId': 'National ID',
    'kyc.passport': 'Passport',
    'kyc.nationalIdNumberLabel': '16-digit Rwandan ID number',
    'kyc.passportNumberLabel': 'Passport number',
    'kyc.documentReferenceLabel': 'Document reference (scan/photo reference)',
    'kyc.submitting': 'Submitting…',
    'kyc.submitForReview': 'Submit for review',
  },
  rw: {
    'kyc.title': 'Kwemeza uwo uri we (KYC)',
    'kyc.signInFirst': 'Banza winjire kuri Home, hanyuma usubire hano kwemeza uwo uri we.',
    'kyc.subtitle': 'Kwemeza Indangamuntu (NIDA) cyangwa Pasiporo.',
    'kyc.loadError': 'Ntibishoboka gushakisha uko byifashe kwemeza uwo uri we.',
    'kyc.pendingReview': 'Icyifuzo kiracyategereje isuzuma',
    'kyc.verifyIdentity': 'Emeza uwo uri we',
    'kyc.submitError': 'Icyo cyifuzo ntikyashoboye kohererezwa.',
    'kyc.submittedTitle': 'Yoherejwe kugira ngo isuzumwe',
    'kyc.submittedBodyPrefix': 'Umusuzumyi nyawe azasuzuma',
    'kyc.submittedBodySuffix': "hanyuma avugurure uko byifashe -- ntibihita bibaho, isuzuma nyaryo rya KYC ntiryihuta.",
    'kyc.done': 'Byarangiye',
    'kyc.chooseDocType': 'Hitamo ubwoko bw\'inyandiko hanyuma wandike nomero yayo.',
    'kyc.nationalId': 'Indangamuntu',
    'kyc.passport': 'Pasiporo',
    'kyc.nationalIdNumberLabel': 'Nomero y\'indangamuntu ifite imibare 16',
    'kyc.passportNumberLabel': 'Nomero ya pasiporo',
    'kyc.documentReferenceLabel': 'Inomero y\'inyandiko (ifoto/scan)',
    'kyc.submitting': 'Kohereza…',
    'kyc.submitForReview': 'Ohereza kugira ngo isuzumwe',
  },
  fr: {
    'kyc.title': 'Vérification KYC',
    'kyc.signInFirst': 'Connectez-vous d\'abord depuis l\'onglet Accueil, puis revenez ici pour vérifier votre identité.',
    'kyc.subtitle': 'Carte d\'identité nationale rwandaise (NIDA) ou passeport.',
    'kyc.loadError': 'Impossible de charger votre statut d\'identité.',
    'kyc.pendingReview': 'Soumission en attente d\'examen',
    'kyc.verifyIdentity': 'Vérifier votre identité',
    'kyc.submitError': 'Cette soumission n\'a pas pu être finalisée.',
    'kyc.submittedTitle': 'Soumis pour examen',
    'kyc.submittedBodyPrefix': 'Un véritable examinateur vérifiera votre',
    'kyc.submittedBodySuffix': 'et mettra à jour votre statut -- ce n\'est pas instantané, un vrai contrôle KYC ne l\'est jamais.',
    'kyc.done': 'Terminé',
    'kyc.chooseDocType': 'Choisissez un type de document et saisissez son numéro.',
    'kyc.nationalId': 'Carte d\'identité',
    'kyc.passport': 'Passeport',
    'kyc.nationalIdNumberLabel': 'Numéro de carte d\'identité rwandaise à 16 chiffres',
    'kyc.passportNumberLabel': 'Numéro de passeport',
    'kyc.documentReferenceLabel': 'Référence du document (scan/photo)',
    'kyc.submitting': 'Envoi en cours…',
    'kyc.submitForReview': 'Soumettre pour examen',
  },
};
