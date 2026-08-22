// itundaface emoji-input infrastructure -- real new pipeline (2026-08-22, direct
// user follow-up after "let's [go] to 3,600-glyph like tossface": AskUserQuestion
// clarified the real target is full emoji-INPUT replacement, matching TossFace's
// actual real use case, not just more fixed-slot UI icons like every itundaface
// batch before this one).
//
// Real gap this closes: itunda had ZERO free-text emoji input anywhere before this
// (confirmed by reading messaging.ts directly -- "emoji" only ever meant one of the
// 5 fixed QUICK_REACTIONS identifiers passed to toggleReaction). This file is the
// actual new capability: a registry mapping real Unicode emoji characters to
// itundaface glyphs, a text-scanning renderer that swaps matched characters inline
// while leaving the underlying message string's real Unicode codepoints completely
// unchanged (same "display-layer only" discipline every itundaface batch has used:
// the stored/sent text is real interoperable Unicode, portable to any client that
// hasn't adopted itundaface, itundaface only changes how THIS client renders it),
// and a real category-grid picker for the chat composer.
//
// Registry today = ItundaFace.tsx's 5 reactions + ItundaFaceSmileys.tsx's 10 --
// deliberately NOT all ~3,600 Unicode emoji in one pass (no image-generation tool
// available here, every glyph is hand-authored SVG path data verified via
// rsvg-convert one at a time -- see project_itunda_own_icons_graphics.md's staged
// category roadmap for the honest, multi-session plan this is phase 1 of).
// UNREGISTERED emoji fall back to the raw system character -- a deliberate,
// graceful degradation matching how any real product stages a font/glyph rollout
// incrementally rather than blocking on 100% coverage before shipping anything.

import { Fragment, type ReactNode } from 'react';
import { ReactionThumbsUp, ReactionHeart, ReactionLaughing, ReactionWow, ReactionSad } from './ItundaFace';
import { ITUNDAFACE_SMILEYS } from './ItundaFaceSmileys';
import { ITUNDAFACE_PEOPLE } from './ItundaFacePeople';
import { ITUNDAFACE_NATURE } from './ItundaFaceNature';
import { ITUNDAFACE_FOOD } from './ItundaFaceFood';
import { ITUNDAFACE_TRAVEL } from './ItundaFaceTravel';
import { ITUNDAFACE_ACTIVITIES } from './ItundaFaceActivities';

type GlyphFn = (props: { size?: number }) => React.ReactElement;

export const ITUNDAFACE_EMOJI: Record<string, GlyphFn> = {
  '👍': ReactionThumbsUp,
  '❤️': ReactionHeart,
  '😂': ReactionLaughing,
  '😮': ReactionWow,
  '😢': ReactionSad,
  ...ITUNDAFACE_SMILEYS,
  ...ITUNDAFACE_PEOPLE,
  ...ITUNDAFACE_NATURE,
  ...ITUNDAFACE_FOOD,
  ...ITUNDAFACE_TRAVEL,
  ...ITUNDAFACE_ACTIVITIES,
};

/** Real category grouping for the picker -- Unicode's own official emoji group
 * names (the same ones TossFace itself organizes by), not invented. Phases 1-6
 * (Smileys & Emotion, People & Body, Animals & Nature, Food & Drink, Travel &
 * Places, Activities) have real itundaface glyphs today; the array shape is
 * deliberately ready for Objects / Symbols / Flags to be appended in later
 * sessions without restructuring the picker UI itself. */
export const ITUNDAFACE_EMOJI_CATEGORIES: { name: string; emoji: string[] }[] = [
  { name: 'Smileys & Emotion', emoji: ['👍', '❤️', '😂', '😮', '😢', ...Object.keys(ITUNDAFACE_SMILEYS)] },
  { name: 'People & Body', emoji: Object.keys(ITUNDAFACE_PEOPLE) },
  { name: 'Animals & Nature', emoji: Object.keys(ITUNDAFACE_NATURE) },
  { name: 'Food & Drink', emoji: Object.keys(ITUNDAFACE_FOOD) },
  { name: 'Travel & Places', emoji: Object.keys(ITUNDAFACE_TRAVEL) },
  { name: 'Activities', emoji: Object.keys(ITUNDAFACE_ACTIVITIES) },
];

// Matches one emoji "unit": an Extended_Pictographic codepoint (the real Unicode
// property standardly used for emoji detection -- broader plain-symbol properties
// like \p{Emoji} also match digits/#/* which aren't wanted here) optionally
// followed by the U+FE0F emoji-presentation variation selector (needed: '❤' alone
// is Extended_Pictographic but itunda's real stored/registry key is '❤️', the
// 2-codepoint heart+VS16 sequence -- dropping this would silently never match).
const EMOJI_UNIT = /\p{Extended_Pictographic}️?/gu;

/** Renders a message body with any REGISTERED emoji swapped for itundaface's own
 * glyph inline, leaving everything else (including unregistered emoji -- the
 * graceful-degradation path) as plain text. `text` itself is never mutated; this
 * is purely how MessageBubble-style callers render the real stored string. */
export function renderTextWithEmoji(text: string, size = 18): ReactNode {
  const parts: ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;
  for (const match of text.matchAll(EMOJI_UNIT)) {
    const glyph = ITUNDAFACE_EMOJI[match[0]];
    if (!glyph) continue; // unregistered emoji: leave for the surrounding plain-text slice to carry through as-is
    const index = match.index ?? 0;
    if (index > lastIndex) parts.push(<Fragment key={key++}>{text.slice(lastIndex, index)}</Fragment>);
    const Glyph = glyph;
    parts.push(<Glyph key={key++} size={size} />);
    lastIndex = index + match[0].length;
  }
  if (lastIndex === 0) return text; // fast path: nothing registered was found, avoid wrapping in fragments at all
  if (lastIndex < text.length) parts.push(<Fragment key={key++}>{text.slice(lastIndex)}</Fragment>);
  return parts;
}

/** Real chat-composer emoji picker -- did not exist anywhere in itunda before this
 * (the pre-existing "😊 Emoticon" attach-menu item opens a DIFFERENT, unrelated
 * KakaoTalk-style sticker/image picker, see emoticons.ts -- this is Unicode text
 * emoji, inserted into the actual message draft, not a separate sticker message).
 * Tapping a glyph inserts its real Unicode character at the end of `draft` -- the
 * composer's `onChange` handler and the send/typing-indicator logic downstream are
 * completely unaware anything itundaface-specific happened, same "real interop
 * data underneath, itundaface only changes the rendering" discipline as the
 * registry above. */
export function EmojiPicker({ onPick }: { onPick: (emoji: string) => void }) {
  return (
    <div
      role="menu"
      aria-label="Emoji picker"
      style={{
        position: 'absolute',
        bottom: '52px',
        left: 0,
        width: '280px',
        maxHeight: '220px',
        overflowY: 'auto',
        background: 'var(--itunda-white)',
        border: '1px solid var(--itunda-grey-200)',
        borderRadius: '10px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
        padding: '10px',
        zIndex: 10,
      }}
    >
      {ITUNDAFACE_EMOJI_CATEGORIES.map((category) => (
        <div key={category.name} style={{ marginBottom: '8px' }}>
          <div style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', fontWeight: 600, marginBottom: '6px' }}>{category.name}</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: '4px' }}>
            {category.emoji.map((emoji) => {
              const Glyph = ITUNDAFACE_EMOJI[emoji];
              return (
                <button
                  key={emoji}
                  type="button"
                  role="menuitem"
                  aria-label={emoji}
                  onClick={() => onPick(emoji)}
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '6px', borderRadius: '8px', background: 'transparent' }}
                >
                  {Glyph ? <Glyph size={26} /> : <span style={{ fontSize: '20px' }}>{emoji}</span>}
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
