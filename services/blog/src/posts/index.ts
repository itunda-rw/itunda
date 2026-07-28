import { idempotencyKeys } from './idempotency-keys';
import { hardcodedUserId } from './hardcoded-user-id';
import { fakeSuccess } from './fake-success';
import { jwtRevocation } from './jwt-revocation';
import { rateLimiting } from './rate-limiting';
import { theBugThatWasntInfrastructure } from './the-bug-that-wasnt-infrastructure';
import { diskPressureCascadingFailure } from './disk-pressure-cascading-failure';

export interface Post {
  slug: string;
  title: string;
  date: string;
  author: string;
  tags: string[];
  excerpt: string;
  content: string;
}

export const posts: Post[] = [fakeSuccess, hardcodedUserId, idempotencyKeys, jwtRevocation, rateLimiting, theBugThatWasntInfrastructure, diskPressureCascadingFailure].sort(
  (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime(),
);

export function getPost(slug: string): Post | undefined {
  return posts.find((p) => p.slug === slug);
}
