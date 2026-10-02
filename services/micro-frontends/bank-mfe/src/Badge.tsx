import { Badge as IDSBadge } from '@itunda/design-system-web';

export function Badge({ text, filled = true, tint = 'var(--itunda-indigo)' }: { text: string; filled?: boolean; tint?: string }) {
  const tone = tint.includes('red') ? 'danger' : tint.includes('green') ? 'success' : 'brand';
  return <IDSBadge text={text} tone={tone} variant={filled ? 'filled' : 'soft'} />;
}
