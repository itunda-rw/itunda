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

export function getPost(slug: string): Post | undefined {
  return posts.find((p) => p.slug === slug);
}
