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

export interface Post {
  slug: string;
  title: string;
  date: string;
  author: string;
  tags: string[];
  excerpt: string;
  content: string;
}

export const posts: Post[] = [theFixWeAlreadyHad, theRiskWeAlreadyKnewAbout, threeFlowsTheFraudEngineNeverSaw, theLinterThatLiedToItselfFirst, theKeyThatCantLeaveThePhone, securityAndSimplicityTogether, theAuditWeSaidWeFinished, aRealDebitCardWithNoCardNetwork, oneReportThreeClients, fakeSuccess, hardcodedUserId, idempotencyKeys, jwtRevocation, rateLimiting, theBugThatWasntInfrastructure, diskPressureCascadingFailure].sort(
  (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime(),
);

export function getPost(slug: string): Post | undefined {
  return posts.find((p) => p.slug === slug);
}
