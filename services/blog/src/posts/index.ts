import { idempotencyKeys } from './idempotency-keys';
import { hardcodedUserId } from './hardcoded-user-id';
import { fakeSuccess } from './fake-success';
import { jwtRevocation } from './jwt-revocation';
import { rateLimiting } from './rate-limiting';
import { theBugThatWasntInfrastructure } from './the-bug-that-wasnt-infrastructure';
import { diskPressureCascadingFailure } from './disk-pressure-cascading-failure';
import { oneReportThreeClients } from './one-report-three-clients';
import { aRealDebitCardWithNoCardNetwork } from './a-real-debit-card-with-no-card-network';
import { securityAndSimplicityTogether } from './security-and-simplicity-together';
import { theAuditWeSaidWeFinished } from './the-audit-we-said-we-finished';
import { theKeyThatCantLeaveThePhone } from './the-key-that-cant-leave-the-phone';
import { theLinterThatLiedToItselfFirst } from './the-linter-that-lied-to-itself-first';
import { threeFlowsTheFraudEngineNeverSaw } from './three-flows-the-fraud-engine-never-saw';
import { theRiskWeAlreadyKnewAbout } from './the-risk-we-already-knew-about';
import { theFixWeAlreadyHad } from './the-fix-we-already-had';
import { theFormThatClosedOnALie } from './the-form-that-closed-on-a-lie';
import { theMatureFeatureThatForgotWhatItWasSaying } from './the-mature-feature-that-forgot-what-it-was-saying';
import { whatWeFoundOutsideOurOwnReference } from './what-we-found-outside-our-own-reference';
import { theLanguageWeNeverAskedAbout } from './the-language-we-never-asked-about';
import { theFixThatForgotItsOwnLesson } from './the-fix-that-forgot-its-own-lesson';
import { theSwitchThatOnlyFlippedOneRoom } from './the-switch-that-only-flipped-one-room';
import { theScreenWeAllAssumedWasAlreadyDone } from './the-screen-we-all-assumed-was-already-done';
import { theFeatureWeShelvedTwice } from './the-feature-we-shelved-twice';
import { theOutageWeStoppedDebugging } from './the-outage-we-stopped-debugging';
import { theTwoLookupsThatWereSupposedToAgree } from './the-two-lookups-that-were-supposed-to-agree';

export interface Post {
  slug: string;
  title: string;
  date: string;
  author: string;
  tags: string[];
  excerpt: string;
  content: string;
  image?: string;
  imageAlt?: string;
}

export const posts: Post[] = [theTwoLookupsThatWereSupposedToAgree, theOutageWeStoppedDebugging, theFeatureWeShelvedTwice, theScreenWeAllAssumedWasAlreadyDone, theSwitchThatOnlyFlippedOneRoom, theFixThatForgotItsOwnLesson, theLanguageWeNeverAskedAbout, whatWeFoundOutsideOurOwnReference, theMatureFeatureThatForgotWhatItWasSaying, theFormThatClosedOnALie, theFixWeAlreadyHad, theRiskWeAlreadyKnewAbout, threeFlowsTheFraudEngineNeverSaw, theLinterThatLiedToItselfFirst, theKeyThatCantLeaveThePhone, securityAndSimplicityTogether, theAuditWeSaidWeFinished, aRealDebitCardWithNoCardNetwork, oneReportThreeClients, fakeSuccess, hardcodedUserId, idempotencyKeys, jwtRevocation, rateLimiting, theBugThatWasntInfrastructure, diskPressureCascadingFailure].sort(
  (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime(),
);

export interface AuthorProfile {
  name: string;
  role: string;
  bio: string;
}

const authorRoles: Record<string, string> = {
  'Ledger Platform Team': 'Payments & Ledger Engineering',
  'Security Engineering': 'Security Engineering',
  'Infrastructure Team': 'Infrastructure Engineering',
  'Mobile Platform Team': 'Mobile Platform Engineering',
  'Identity Team': 'Identity Engineering',
};

export function getAuthorProfile(name: string): AuthorProfile {
  return {
    name,
    role: authorRoles[name] ?? 'Itunda Engineering',
    bio: 'Building reliable systems for money, services, identity, and everyday digital life.',
  };
}

const coverPalette: Record<string, [string, string]> = {
  payments: ['#7472F4', '#E9E8FF'],
  security: ['#625FE0', '#ECEBFF'],
  identity: ['#6E6BEA', '#F0EFFF'],
  infrastructure: ['#5654C9', '#E7E6FF'],
  mobile: ['#817FF7', '#F2F1FF'],
  platform: ['#7472F4', '#E9E8FF'],
  api: ['#625FE0', '#ECEBFF'],
};

const escapeXml = (value: string) =>
  value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export function getEditorialImage(post: Post): string {
  if (post.image) return post.image;

  const key = post.tags.find((tag) => coverPalette[tag.toLowerCase()])?.toLowerCase() ?? 'platform';
  const [accent, soft] = coverPalette[key];
  const title = escapeXml(post.title);
  const label = escapeXml(post.tags[0] ?? 'engineering').toUpperCase();

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 900">
    <defs>
      <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="${soft}"/>
      </linearGradient>
      <filter id="shadow"><feDropShadow dx="0" dy="24" stdDeviation="28" flood-opacity=".12"/></filter>
    </defs>
    <rect width="1600" height="900" fill="url(#bg)"/>
    <circle cx="1320" cy="120" r="260" fill="${soft}" opacity=".75"/>
    <circle cx="180" cy="760" r="310" fill="${soft}" opacity=".48"/>
    <g filter="url(#shadow)">
      <rect x="420" y="220" width="760" height="460" rx="64" fill="#FFFFFF"/>
      <rect x="490" y="290" width="620" height="16" rx="8" fill="${soft}"/>
      <rect x="490" y="344" width="390" height="42" rx="21" fill="${accent}" opacity=".92"/>
      <rect x="490" y="422" width="500" height="20" rx="10" fill="${soft}"/>
      <rect x="490" y="468" width="430" height="20" rx="10" fill="${soft}"/>
      <circle cx="1030" cy="540" r="76" fill="${soft}"/>
      <circle cx="1030" cy="540" r="44" fill="${accent}"/>
      <path d="M1030 510v60M1000 540h60" stroke="#fff" stroke-width="14" stroke-linecap="round"/>
    </g>
    <text x="490" y="170" font-family="Inter,Arial,sans-serif" font-size="28" font-weight="700" letter-spacing="5" fill="${accent}">${label}</text>
    <text x="490" y="760" font-family="Inter,Arial,sans-serif" font-size="34" font-weight="700" fill="#191F28">${title}</text>
    <text x="490" y="812" font-family="Inter,Arial,sans-serif" font-size="22" fill="#6B7684">ITUNDA TECH · ENGINEERING FOR EVERYDAY LIFE</text>
  </svg>`;

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

export function getPost(slug: string): Post | undefined {
  return posts.find((p) => p.slug === slug);
}
