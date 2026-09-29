// Real group-chat header toolbar -- extracted out of GroupThread.tsx (2026-09-06,
// Talk product-completeness pass) to make room for the group message search feature
// (searchGroupMessages was real on the backend since this same pass but had zero
// client UI on any platform) without pushing GroupThread.tsx past its frozen
// file-size-lint baseline.
import { useState } from 'react';
import { Image as ImageIcon, Link as LinkIcon, Megaphone, Receipt, Search, Users } from 'lucide-react';
import { ApiError } from './lib/api';
import { searchGroupMessages, type GroupMessage } from './lib/groupMessaging';
import { useI18n } from './i18n/I18nContext';

export function TalkGroupToolbar({
  groupId,
  onShowMediaGallery,
  onShowLinks,
  onShowManageMembers,
  onShowAnnouncementPoll,
  onShowSplitBills,
  onSearchResultsChange,
}: {
  groupId: string;
  onShowMediaGallery: () => void;
  onShowLinks: () => void;
  onShowManageMembers: () => void;
  onShowAnnouncementPoll: () => void;
  onShowSplitBills: () => void;
  onSearchResultsChange: (results: GroupMessage[] | null) => void;
}) {
  const { t } = useI18n();
  const [showSearch, setShowSearch] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchCount, setSearchCount] = useState<number | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim().length < 2) return;
    setSearching(true);
    setSearchError(null);
    try {
      const results = await searchGroupMessages(groupId, searchQuery.trim());
      setSearchCount(results.length);
      onSearchResultsChange(results);
    } catch (err) {
      setSearchError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSearching(false);
    }
  };

  const clearSearch = () => {
    setSearchQuery('');
    setSearchCount(null);
    setShowSearch(false);
    onSearchResultsChange(null);
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '6px' }}>
        <button type="button" onClick={() => setShowSearch((v) => !v)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Search this group">
          <Search size={20} />
        </button>
        <button type="button" onClick={onShowMediaGallery} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Shared photos">
          <ImageIcon size={20} />
        </button>
        <button type="button" onClick={onShowLinks} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Shared links">
          <LinkIcon size={20} />
        </button>
        <button type="button" onClick={onShowManageMembers} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Manage members">
          <Users size={20} />
        </button>
        <button type="button" onClick={onShowAnnouncementPoll} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Announcement and polls">
          <Megaphone size={20} />
        </button>
        <button type="button" onClick={onShowSplitBills} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Split a bill">
          <Receipt size={20} />
        </button>
      </div>
      {showSearch && (
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search this group"
            minLength={2}
            style={{ flex: 1, padding: '9px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-secondary" disabled={searching || searchQuery.trim().length < 2}>
            {searching ? '…' : 'Search'}
          </button>
          {searchCount !== null && (
            <button type="button" className="itunda-btn itunda-btn-secondary" onClick={clearSearch}>
              Clear
            </button>
          )}
        </form>
      )}
      {searchError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }}>{searchError}</p>}
      {searchCount !== null && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          {searchCount} matching message{searchCount === 1 ? '' : 's'}
        </p>
      )}
    </div>
  );
}
