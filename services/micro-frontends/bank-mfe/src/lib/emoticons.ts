import { apiFetch } from './api';

// Real KakaoTalk Emoticon Store (item 133) -- see backend Emoticon.kt's own doc
// comment: a real purchasable sticker-pack catalog (buy once, own it, same model
// Shop/Insurance enrollment already use), gift a pack to a friend, and send an owned
// emoticon into a real 1:1 or group conversation. Found via a fresh endpoint-coverage
// sweep: a mature, real backend (packs, ownership, gifting, sending) had zero client
// on any of the 3 platforms.

export interface EmoticonPack {
  id: string;
  title: string;
  artistName: string;
  thumbnailUrl: string;
  price: number;
  active: boolean;
  createdAt: string;
}

export interface Emoticon {
  id: string;
  packId: string;
  imageUrl: string;
  sortOrder: number;
}

export interface OwnedEmoticonPack {
  id: string;
  userId: string;
  packId: string;
  source: 'PURCHASED' | 'GIFTED';
  acquiredAt: string;
}

export const fetchEmoticonPacks = () =>
  apiFetch<{ success: boolean; packs: EmoticonPack[] }>('/api/v1/emoticons/packs').then((r) => r.packs);

export const fetchPackEmoticons = (packId: string) =>
  apiFetch<{ success: boolean; emoticons: Emoticon[] }>(`/api/v1/emoticons/packs/${packId}`).then((r) => r.emoticons);

export const fetchOwnedEmoticonPacks = () =>
  apiFetch<{ success: boolean; packs: OwnedEmoticonPack[] }>('/api/v1/emoticons/packs/owned').then((r) => r.packs);

export const purchaseEmoticonPack = (packId: string) =>
  apiFetch<{ success: boolean; ownedPack: OwnedEmoticonPack }>(`/api/v1/emoticons/packs/${packId}/purchase`, { method: 'POST' });

export const giftEmoticonPack = (packId: string, recipientPhoneNumber: string) =>
  apiFetch<{ success: boolean; giftedPack: OwnedEmoticonPack }>(`/api/v1/emoticons/packs/${packId}/gift`, {
    method: 'POST',
    body: JSON.stringify({ recipientPhoneNumber }),
  });

export const sendEmoticon = (conversationId: string, emoticonId: string) =>
  apiFetch(`/api/v1/emoticons/conversations/${conversationId}/send`, {
    method: 'POST',
    body: JSON.stringify({ emoticonId }),
  });

export const sendGroupEmoticon = (groupId: string, emoticonId: string) =>
  apiFetch(`/api/v1/emoticons/groups/${groupId}/send`, {
    method: 'POST',
    body: JSON.stringify({ emoticonId }),
  });

// Rendering a received emoticon message needs its real image, but there's no
// GET-emoticon-by-id endpoint -- only GET .../packs/{packId} lists a pack's own
// emoticons. The catalog is a small, curated, server-seeded set (see EmoticonPack's
// own doc comment), so building one id->imageUrl map across every active pack is
// honest and cheap at this scale, not an N+1 concern. Module-level memoized: the
// catalog doesn't change per-conversation or per-render.
let emoticonImageMapPromise: Promise<Record<string, string>> | null = null;

export const fetchEmoticonImageMap = () => {
  if (!emoticonImageMapPromise) {
    emoticonImageMapPromise = fetchEmoticonPacks()
      .then((packs) => Promise.all(packs.map((p) => fetchPackEmoticons(p.id).catch(() => [] as Emoticon[]))))
      .then((byPack) => Object.fromEntries(byPack.flat().map((e) => [e.id, e.imageUrl])))
      .catch((err) => {
        emoticonImageMapPromise = null;
        throw err;
      });
  }
  return emoticonImageMapPromise;
};
